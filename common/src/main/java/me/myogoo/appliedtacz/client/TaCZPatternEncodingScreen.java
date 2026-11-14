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
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.SlotPosition;
import appeng.client.gui.style.TerminalStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.Scrollbar;
import appeng.client.gui.widgets.TabButton;
import appeng.client.gui.widgets.ToggleButton;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.Tooltips;
import appeng.menu.SlotSemantics;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import me.myogoo.appliedtacz.client.renderer.AssemblerWorkbenchBadge;
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
    private static final int VISIBLE_RECIPES = 3;
    private final Scrollbar recipeScroll;
    private final Scrollbar ingredientScroll;
    private final RecipeButton[] recipeButtons = new RecipeButton[VISIBLE_RECIPES];
    private final ActionButton encodeButton;
    private final ToggleButton layoutToggle;
    private final TerminalStyle terminalLayout;
    private final int maxRepoColumns;
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
        super(menu, inventory, GuiText.Terminal.text(), style);
        terminalLayout = getStyle().getTerminalStyle();
        imageWidth = terminalLayout.getScreenWidth();
        maxRepoColumns = Math.min(13, (imageWidth - 30) / 18);
        // This dedicated style follows the client-wide preference, including reopened screens.
        terminalLayout.setSlotsPerRow(AppliedTaczClientConfig.compactPatternTerminal() ? 9 : maxRepoColumns);
        repo.setRowSize(terminalLayout.getSlotsPerRow());
        ingredientLayout = getStyle().getSlots().get(SlotSemantics.PROCESSING_INPUTS.id());
        ingredientBottom = ingredientLayout.getBottom();
        layoutToggle = new ToggleButton(Icon.TERMINAL_STYLE_SMALL, Icon.TERMINAL_STYLE_FULL, this::setCompactLayout);
        layoutToggle.setState(terminalLayout.getSlotsPerRow() == 9);
        layoutToggle.setTooltipOn(List.of(Component.translatable("gui.appliedtacz.pattern_terminal.layout_compact"),
                Component.translatable("gui.appliedtacz.pattern_terminal.switch_layout")));
        layoutToggle.setTooltipOff(List.of(Component.translatable("gui.appliedtacz.pattern_terminal.layout_wide"),
                Component.translatable("gui.appliedtacz.pattern_terminal.switch_layout")));
        if (maxRepoColumns > 9) {
            addToLeftToolbar(layoutToggle);
        }
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
        widgets.add("workbenches", new WorkbenchTabs());
        encodeButton = new ActionButton(ActionItems.ENCODE, menu::encode);
        widgets.add("encodePattern", encodeButton);
    }

    private void setCompactLayout(boolean compact) {
        AppliedTaczClientConfig.setCompactPatternTerminal(compact);
        terminalLayout.setSlotsPerRow(compact ? 9 : maxRepoColumns);
        layoutToggle.setState(compact);
        resize(minecraft, width, height);
    }

    @Override
    public void init() {
        repo.setRowSize(terminalLayout.getSlotsPerRow());
        repo.updateView();
        super.init();
        int offset = (maxRepoColumns - terminalLayout.getSlotsPerRow()) * 9;
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
            var workbenchName = id == null
                    ? Component.translatable("gui.appliedtacz.assembler.workbench_name")
                    : TimelessAPI.getClientBlockIndex(id)
                            .map(index -> Component.translatable(index.getName()))
                            .orElse(Component.translatable("gui.appliedtacz.assembler.workbench_name"));
            var workbenchLabel = getStyle().getText().get("workbench");
            if (workbenchLabel != null) {
                String name = workbenchName.getString();
                int maxWidth = workbenchLabel.getMaxWidth();
                if (font.width(name) > maxWidth) {
                    name = font.plainSubstrByWidth(name, maxWidth - font.width("…")) + "…";
                }
                setTextContent("workbench", Component.literal(name));
            }
            recipeScroll.setCurrentScroll(0);
            recipeScroll.setRange(0, Math.max(0, recipes.size() - VISIBLE_RECIPES), 1);
        }
        if (workbenchChanged || !displayedRecipe.equals(menu.recipeId)
                || displayedSelectionRevision != menu.selectionRevision) {
            displayedRecipe = menu.recipeId;
            displayedSelectionRevision = menu.selectionRevision;
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
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTicks) {
        // Let AE2 draw the inherited terminal, including first/last rows, search and pinned-row overlays.
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTicks);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            if (!(slot instanceof RepoSlot)
                    && (slot.x < 0 || slot.x + 16 > imageWidth || slot.y < 0 || slot.y + 16 > imageHeight)) {
                // Fixed slots are baked into the footer; preserve add-on slots outside our frame.
                Icon.SLOT_BACKGROUND.getBlitter().dest(x + slot.x - 1, y + slot.y - 1).blit(graphics);
            }
        }
        drawScrollTrack(graphics, x, y, recipeScroll);
        drawScrollTrack(graphics, x, y, ingredientScroll);
    }

    private void drawScrollTrack(GuiGraphics graphics, int x, int y, Scrollbar scrollbar) {
        var bounds = scrollbar.getBounds();
        getStyle().getImage("encodingScrollTrack")
                .dest(x + bounds.getX() - 1, y + bounds.getY() - 1).blit(graphics);
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
            AssemblerWorkbenchBadge.render(graphics, font, pattern, slot.x, slot.y);
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
            float textScale = getStyle().getText().get("workbench").getScale();
            int maxTextWidth = (int) ((width - 24) / textScale);
            String name = getMessage().getString();
            if (font.width(name) > maxTextWidth) {
                name = font.plainSubstrByWidth(name, maxTextWidth - font.width("…")) + "…";
            }
            graphics.pose().pushPose();
            graphics.pose().translate(getX() + 22, getY() + (height - font.lineHeight * textScale) / 2, 1);
            graphics.pose().scale(textScale, textScale, 1);
            graphics.drawString(font, name, 0, 0, active ? 0xffeeeeee : 0xff888888, false);
            graphics.pose().popPose();
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
        }

        @Override
        public void populateScreen(Consumer<AbstractWidget> addWidget, Rect2i bounds, AEBaseScreen<?> screen) {
            screenBounds = bounds;
            buttons.forEach(addWidget);
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
                    x = zone.getX() + zone.getWidth() + 1;
                    i = -1;
                }
            }
            horizontalOffset = x - screenBounds.getX() - position.getX();
            int tabPitch = getStyle().getWidget("workbenchTab1").getTop();
            for (int i = 0; i < buttons.size(); i++) {
                var button = buttons.get(i);
                // Match each AE2 version's overlapping horizontal-tab sprites and hitboxes.
                var tabStyle = getStyle().getWidget(i == buttons.size() - 1 ? "workbenchTab2" : "workbenchTab0");
                button.setX(x + tabStyle.getLeft());
                button.setY(y + i * tabPitch);
                button.setWidth(tabStyle.getWidth());
                button.setHeight(tabStyle.getHeight());
                button.setSelected(ids.get(i).toString().equals(menu.workbenchId));
            }
        }

        @Override
        public Rect2i getBounds() {
            var bounds = super.getBounds();
            int tabPitch = getStyle().getWidget("workbenchTab1").getTop();
            int height = buttons.isEmpty() ? 0
                    : (buttons.size() - 1) * tabPitch + getStyle().getWidget("workbenchTab2").getHeight();
            return new Rect2i(bounds.getX() + horizontalOffset, bounds.getY(),
                    bounds.getWidth(), height);
        }

        @Override
        public boolean onMouseWheel(Point mousePos, double delta) {
            return true;
        }
    }
}
