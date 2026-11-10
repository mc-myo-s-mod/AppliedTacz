package me.myogoo.appliedtacz.client;

import appeng.api.config.ActionItems;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Icon;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;
import appeng.client.gui.me.common.StackSizeRenderer;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.SlotPosition;
import appeng.client.gui.style.TerminalStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.Tooltips;
import appeng.menu.SlotSemantics;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import me.myogoo.appliedtacz.crafting.PatternEncodingInputs;
import me.myogoo.appliedtacz.config.AppliedTaczClientConfig;
import me.myogoo.appliedtacz.crafting.TaCZPatternEncodingRecipes;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TaCZPatternEncodingScreen extends MEStorageScreen<TaCZPatternEncodingMenu> {
    private static final int WIDTH = 272;
    private static final int VISIBLE_RECIPES = 3;
    private final Scrollbar recipeScroll;
    private final Scrollbar ingredientScroll;
    private final RecipeButton[] recipeButtons = new RecipeButton[VISIBLE_RECIPES];
    private final WorkbenchTabs workbenchTabs;
    private final ActionButton encodeButton;
    private final ToggleButton layoutToggle;
    private final TerminalStyle terminalLayout;
    private final SlotPosition ingredientLayout;
    private final int ingredientBottom;
    private List<TaCZPatternEncodingRecipes.Entry> recipes = List.of();
    private String displayedWorkbench = "";
    private String displayedRecipe = "";
    private int displayedSelectionRevision = -1;
    private ItemStack displayedPattern = ItemStack.EMPTY;
    private GenericStack patternOutput;

    public TaCZPatternEncodingScreen(TaCZPatternEncodingMenu menu, Inventory inventory, Component title,
            ScreenStyle style) {
        super(menu, inventory, title, style);
        imageWidth = WIDTH;
        terminalLayout = getStyle().getTerminalStyle();
        // This dedicated style follows the client-wide preference, including reopened screens.
        terminalLayout.setSlotsPerRow(AppliedTaczClientConfig.compactPatternTerminal() ? 9 : 13);
        repo.setRowSize(terminalLayout.getSlotsPerRow());
        ingredientLayout = getStyle().getSlots().get(SlotSemantics.PROCESSING_INPUTS.id());
        ingredientBottom = ingredientLayout.getBottom();
        layoutToggle = new ToggleButton(Icon.TERMINAL_STYLE_SMALL, Icon.TERMINAL_STYLE_FULL, this::setCompactLayout);
        layoutToggle.setState(terminalLayout.getSlotsPerRow() == 9);
        layoutToggle.setTooltipOn(List.of(Component.translatable("gui.appliedtacz.pattern_terminal.layout_compact"),
                Component.translatable("gui.appliedtacz.pattern_terminal.switch_layout")));
        layoutToggle.setTooltipOff(List.of(Component.translatable("gui.appliedtacz.pattern_terminal.layout_wide"),
                Component.translatable("gui.appliedtacz.pattern_terminal.switch_layout")));
        addToLeftToolbar(layoutToggle);
        recipeScroll = widgets.addScrollBar("recipesScroll", Scrollbar.SMALL);
        recipeScroll.setCaptureMouseWheel(false);
        ingredientScroll = widgets.addScrollBar("ingredientsScroll", Scrollbar.SMALL);
        ingredientScroll.setRange(0, 24, 3);
        ingredientScroll.setCaptureMouseWheel(false);
        widgets.add("recipesWheel", new ScrollArea(recipeScroll));
        widgets.add("ingredientsWheel", new ScrollArea(ingredientScroll));
        for (int i = 0; i < recipeButtons.length; i++) {
            recipeButtons[i] = new RecipeButton();
            widgets.add("recipe" + i, recipeButtons[i]);
        }
        workbenchTabs = new WorkbenchTabs();
        widgets.add("workbenches", workbenchTabs);
        encodeButton = new ActionButton(ActionItems.ENCODE, menu::encode);
        widgets.add("encodePattern", encodeButton);
    }

    private void setCompactLayout(boolean compact) {
        AppliedTaczClientConfig.setCompactPatternTerminal(compact);
        terminalLayout.setSlotsPerRow(compact ? 9 : 13);
        layoutToggle.setState(compact);
        resize(minecraft, width, height);
    }

    @Override
    public void init() {
        repo.setRowSize(terminalLayout.getSlotsPerRow());
        repo.updateView();
        super.init();
        int offset = (13 - terminalLayout.getSlotsPerRow()) * 9;
        if (offset != 0) {
            menu.slots.replaceAll(slot -> slot instanceof RepoSlot repoSlot
                    ? new RepoSlot(repo, repoSlot.getRepoViewIndex(), slot.x + offset, slot.y) : slot);
        }
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        boolean workbenchChanged = !displayedWorkbench.equals(menu.workbenchId);
        if (workbenchChanged) {
            displayedWorkbench = menu.workbenchId;
            var id = ResourceLocation.tryParse(displayedWorkbench);
            recipes = id == null ? List.of() : TaCZPatternEncodingRecipes.getRecipes(minecraft.level, id);
            recipeScroll.setCurrentScroll(0);
            recipeScroll.setRange(0, Math.max(0, recipes.size() - VISIBLE_RECIPES), 1);
        }
        if (workbenchChanged || !displayedRecipe.equals(menu.recipeId)
                || displayedSelectionRevision != menu.selectionRevision) {
            displayedRecipe = menu.recipeId;
            displayedSelectionRevision = menu.selectionRevision;
            workbenchTabs.reveal(ResourceLocation.tryParse(displayedWorkbench));
            ingredientScroll.setCurrentScroll(0);
            for (int i = 0; i < recipes.size(); i++) {
                if (recipes.get(i).id().toString().equals(displayedRecipe)) {
                    int top = recipeScroll.getCurrentScroll();
                    if (i < top || i >= top + VISIBLE_RECIPES) {
                        recipeScroll.setCurrentScroll(Math.min(i, Math.max(0, recipes.size() - VISIBLE_RECIPES)));
                    }
                    break;
                }
            }
        }
        int first = recipeScroll.getCurrentScroll();
        for (int i = 0; i < recipeButtons.length; i++) {
            int index = first + i;
            recipeButtons[i].setRecipe(index < recipes.size() ? recipes.get(index) : null);
        }
        setTextContent("recipePage", recipes.isEmpty()
                ? Component.translatable("gui.appliedtacz.pattern_terminal.no_recipes")
                : Component.translatable("gui.appliedtacz.pattern_terminal.page", first + 1,
                        Math.min(first + VISIBLE_RECIPES, recipes.size()), recipes.size()));
        ingredientLayout.setBottom(ingredientBottom + ingredientScroll.getCurrentScroll() * 18);
        try {
            repositionSlots(SlotSemantics.PROCESSING_INPUTS);
        } finally {
            // Keep the base position intact for a later resize or layout toggle.
            ingredientLayout.setBottom(ingredientBottom);
        }
        var slots = menu.getIngredientSlots();
        for (int i = 0; i < slots.length; i++) {
            int row = i / 3 - ingredientScroll.getCurrentScroll();
            slots[i].setActive(row >= 0 && row < 3);
        }
        encodeButton.active = menu.canEncode();
        setTextContent("encodingStatus", menu.encodingError.isEmpty() ? Component.empty()
                : Component.translatable(menu.encodingError));
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTicks) {
        // Keep AE2's resizable ME grid, but generate the wider TaCZ layout without a new texture atlas.
        BackgroundGenerator.draw(imageWidth, imageHeight, graphics, x, y);
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                Icon.SLOT_BACKGROUND.getBlitter().dest(x + slot.x - 1, y + slot.y - 1).blit(graphics);
            }
        }
        graphics.hLine(x + 8, x + WIDTH - 9, y + imageHeight - 100, 0xff8b8b8b);
        drawScrollTrack(graphics, x, y, recipeScroll);
        drawScrollTrack(graphics, x, y, ingredientScroll);
        int gridBottom = y + imageHeight - 178;
        graphics.fill(x + 249, y + 27, x + 263, gridBottom - 1, 0xff737373);
        graphics.fill(x + 250, y + 28, x + 262, gridBottom - 2, 0xff373737);
    }

    private static void drawScrollTrack(GuiGraphics graphics, int x, int y, Scrollbar scrollbar) {
        var bounds = scrollbar.getBounds();
        graphics.fill(x + bounds.getX() - 1, y + bounds.getY() - 1,
                x + bounds.getX() + bounds.getWidth() + 1, y + bounds.getY() + bounds.getHeight() + 1, 0xff737373);
        graphics.fill(x + bounds.getX(), y + bounds.getY(),
                x + bounds.getX() + bounds.getWidth(), y + bounds.getY() + bounds.getHeight(), 0xff373737);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        var inputs = menu.getIngredientSlots();
        for (int i = 0; i < inputs.length; i++) {
            Slot slot = inputs[i];
            if (slot.isActive() && x >= leftPos + slot.x && x < leftPos + slot.x + 16
                    && y >= topPos + slot.y && y < topPos + slot.y + 16) {
                if (button == 0) {
                    menu.cycleIngredient(i);
                }
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        if (menu.getSlotSemantic(slot) == SlotSemantics.ENCODED_PATTERN) {
            // shortcut: keep native drag feedback; extend if Shift output quantities are needed during dragging.
            if (isQuickCrafting || (minecraft.options.touchscreen().get()
                    && (minecraft.mouseHandler.isLeftPressed() || minecraft.mouseHandler.isRightPressed()))) {
                super.renderSlot(graphics, slot);
                return;
            }
            var pattern = slot.getItem();
            // Keep AE2's Shift output icon, but never decorate it with the physical pattern count.
            graphics.renderItem(pattern, slot.x, slot.y);
            if (hasShiftDown()) {
                if (!ItemStack.matches(pattern, displayedPattern)) {
                    displayedPattern = pattern.copy();
                    var details = PatternDetailsHelper.decodePattern(pattern, minecraft.level);
                    patternOutput = details == null ? null : details.getPrimaryOutput();
                }
                if (patternOutput != null) {
                    StackSizeRenderer.renderSizeLabel(graphics, font, slot.x, slot.y,
                            patternOutput.what().formatAmount(patternOutput.amount(), AmountFormat.SLOT));
                }
            }
            return;
        }
        super.renderSlot(graphics, slot);
        if (isCraftableInput(slot)) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 100);
            StackSizeRenderer.renderSizeLabel(graphics, font, slot.x - 11, slot.y - 11, "+", false);
            graphics.pose().popPose();
        }
    }

    private boolean isCraftableInput(Slot slot) {
        if (slot == null || menu.getSlotSemantic(slot) != SlotSemantics.PROCESSING_INPUTS) {
            return false;
        }
        var stack = GenericStack.fromItemStack(slot.getItem());
        return stack != null && repo.isCraftable(stack.what());
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        var lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot != null && menu.getSlotSemantic(hoveredSlot) == SlotSemantics.PROCESSING_INPUTS) {
            var ingredient = GenericStack.fromItemStack(stack);
            if (ingredient != null) {
                lines.add(Tooltips.getAmountTooltip(ButtonToolTips.Amount, ingredient));
            }
            if (isCraftableInput(hoveredSlot)) {
                lines.add(ButtonToolTips.Craftable.text().withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.translatable("gui.appliedtacz.pattern_terminal.cycle_ingredient")
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private final class RecipeButton extends Button {
        private TaCZPatternEncodingRecipes.Entry entry;
        private ItemStack result = ItemStack.EMPTY;

        private RecipeButton() {
            super(0, 0, 124, 18, Component.empty(), button -> {
                var row = (RecipeButton) button;
                if (row.entry != null) {
                    menu.selectRecipe(row.entry.id().toString());
                }
            }, DEFAULT_NARRATION);
        }

        private void setRecipe(TaCZPatternEncodingRecipes.Entry entry) {
            visible = entry != null;
            if (this.entry == entry) {
                return;
            }
            this.entry = entry;
            active = visible && PatternEncodingInputs.isEncodable(entry.recipe());
            result = entry == null ? ItemStack.EMPTY : entry.recipe().getResultItem(minecraft.level.registryAccess());
            setMessage(result.getHoverName());
            var tooltip = result.getHoverName().copy().append(" ×" + result.getCount());
            if (entry != null && !active) {
                tooltip.append("\n").append(Component.translatable(entry.recipe().getInputs().size() > 81
                        ? "gui.appliedtacz.pattern_terminal.too_many_ingredients"
                        : "gui.appliedtacz.pattern_terminal.invalid_recipe"));
            }
            setTooltip(entry == null ? null : Tooltip.create(tooltip));
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            boolean selected = entry != null && entry.id().toString().equals(menu.recipeId);
            int border = selected ? 0xff83b9ca : isHoveredOrFocused() ? 0xffeeeeee : 0xff777777;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, border);
            graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1,
                    selected ? 0xff465c64 : 0xff373737);
            graphics.renderItem(result, getX() + 1, getY() + 1);
            graphics.renderItemDecorations(font, result, getX() + 1, getY() + 1);
            String name = font.plainSubstrByWidth(getMessage().getString(), width - 24);
            graphics.drawString(font, name, getX() + 22, getY() + 5, active ? 0xffeeeeee : 0xff888888, false);
        }
    }

    private static class ScrollArea implements ICompositeWidget {
        protected Point position = new Point(0, 0);
        private int width;
        private int height;
        private final Scrollbar scrollbar;

        private ScrollArea(Scrollbar scrollbar) {
            this.scrollbar = scrollbar;
        }

        @Override
        public void setPosition(Point position) {
            this.position = position;
        }

        @Override
        public void setSize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public Rect2i getBounds() {
            return new Rect2i(position.getX(), position.getY(), width, height);
        }

        @Override
        public boolean onMouseWheel(Point mousePos, double delta) {
            if (scrollbar != null) {
                scrollbar.onMouseWheel(mousePos, delta);
            }
            return true;
        }
    }

    private final class WorkbenchTabs extends ScrollArea {
        private final List<ResourceLocation> ids = TaCZPatternEncodingRecipes.getWorkbenches();
        private final List<TabButton> buttons = new ArrayList<>();
        private final Button previous;
        private final Button next;
        private int first;
        private int horizontalOffset;
        private Rect2i screenBounds = new Rect2i(0, 0, 0, 0);

        private WorkbenchTabs() {
            super(null);
            for (var id : ids) {
                ItemStack icon = TimelessAPI.getCommonBlockIndex(id)
                        .map(index -> BlockItemBuilder.create(index.getBlock().getBlock()).setId(id).build())
                        .orElse(ItemStack.EMPTY);
                var name = TimelessAPI.getClientBlockIndex(id)
                        .map(index -> Component.translatable(index.getName()))
                        .orElse(Component.translatable("gui.appliedtacz.assembler.workbench_name"));
                var tab = new TabButton(icon, name, button -> menu.selectWorkbench(id.toString()));
                tab.setStyle(TabButton.Style.HORIZONTAL);
                buttons.add(tab);
            }
            previous = Button.builder(Component.literal("↑"), button -> scroll(-1)).size(11, 12).build();
            next = Button.builder(Component.literal("↓"), button -> scroll(1)).size(11, 12).build();
        }

        @Override
        public void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
            screenBounds = bounds;
            buttons.forEach(addWidget);
            addWidget.accept(previous);
            addWidget.accept(next);
        }

        @Override
        public void updateBeforeRender() {
            int x = screenBounds.getX() + position.getX();
            int y = screenBounds.getY() + position.getY();
            var previousBounds = getBounds();
            int tabWidth = previousBounds.getWidth();
            int tabHeight = previousBounds.getHeight();
            int previousX = screenBounds.getX() + previousBounds.getX();
            var exclusions = TaCZPatternEncodingScreen.this.getExclusionZones();
            // View-cell and addon upgrade panels can occupy the default tab column.
            for (int i = 0; i < exclusions.size(); i++) {
                var zone = exclusions.get(i);
                if (zone.getX() == previousX && zone.getY() == y
                        && zone.getWidth() == tabWidth && zone.getHeight() == tabHeight) {
                    continue;
                }
                if (x < zone.getX() + zone.getWidth() && x + tabWidth > zone.getX()
                        && y < zone.getY() + zone.getHeight() && y + tabHeight > zone.getY()) {
                    x = zone.getX() + zone.getWidth() + 2;
                    i = -1;
                }
            }
            horizontalOffset = x - screenBounds.getX() - position.getX();
            for (int i = 0; i < buttons.size(); i++) {
                var button = buttons.get(i);
                button.visible = i >= first && i < first + 3;
                button.setX(x);
                button.setY(y + (i - first) * 22);
                button.setSelected(ids.get(i).toString().equals(menu.workbenchId));
            }
            previous.setX(x);
            next.setX(x + 11);
            previous.setY(y + 67);
            next.setY(y + 67);
            previous.visible = next.visible = ids.size() > 3;
            previous.active = first > 0;
            next.active = first + 3 < ids.size();
        }

        @Override
        public Rect2i getBounds() {
            var bounds = super.getBounds();
            return new Rect2i(bounds.getX() + horizontalOffset, bounds.getY(),
                    bounds.getWidth(), bounds.getHeight());
        }

        private void reveal(ResourceLocation id) {
            int index = ids.indexOf(id);
            if (index >= 0 && (index < first || index >= first + 3)) {
                first = Math.min(index, Math.max(0, ids.size() - 3));
            }
        }

        private void scroll(int delta) {
            first = Math.max(0, Math.min(first + delta, Math.max(0, ids.size() - 3)));
        }

        @Override
        public boolean onMouseWheel(Point mousePos, double delta) {
            scroll(delta < 0 ? 1 : -1);
            return true;
        }
    }
}
