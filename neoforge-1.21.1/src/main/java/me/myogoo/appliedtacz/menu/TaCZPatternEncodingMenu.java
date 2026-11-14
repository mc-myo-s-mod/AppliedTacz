package me.myogoo.appliedtacz.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.InventoryAction;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.locator.MenuHostLocator;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.common.MEStorageMenu;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.InaccessibleSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.ConfigInventory;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import me.myogoo.appliedtacz.crafting.AssemblerRecipeMatcher;
import me.myogoo.appliedtacz.crafting.PatternEncodingInputs;
import me.myogoo.appliedtacz.crafting.TaCZPatternEncodingRecipes;
import me.myogoo.appliedtacz.crafting.TaCZPatternMetadata;
import me.myogoo.appliedtacz.parts.TaCZPatternEncodingPart;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TaCZPatternEncodingMenu extends MEStorageMenu {
    public static final MenuType<TaCZPatternEncodingMenu> TYPE = IMenuTypeExtension.create(
            TaCZPatternEncodingMenu::fromNetwork);

    public record RecipeSelection(String workbenchId, String recipeId) {
    }

    static {
        MenuOpener.addOpener(TYPE, TaCZPatternEncodingMenu::open);
    }

    private final TaCZPatternEncodingPart terminal;
    private final ConfigInventory ingredients = ConfigInventory.configStacks(AEProcessingPattern.MAX_INPUT_SLOTS).allowOverstacking(true).build();
    private final AppEngSlot[] ingredientSlots = new AppEngSlot[AEProcessingPattern.MAX_INPUT_SLOTS];
    private final List<AEItemKey> selectedIngredients = new ArrayList<>();
    private GunSmithTableRecipe selectedRecipe;
    private AEItemKey lastEncodedPattern;
    private final Slot blankSlot;
    private final Slot encodedSlot;

    @GuiSync(0)
    public String workbenchId = "";
    @GuiSync(1)
    public String recipeId = "";
    @GuiSync(2)
    public boolean readyToEncode;
    @GuiSync(3)
    public String encodingError = "";
    @GuiSync(4)
    public int selectionRevision;

    public TaCZPatternEncodingMenu(int id, Inventory inventory, TaCZPatternEncodingPart terminal) {
        super(TYPE, id, inventory, terminal);
        this.terminal = terminal;
        var wrapper = ingredients.createMenuWrapper();
        for (int i = 0; i < ingredientSlots.length; i++) {
            ingredientSlots[i] = new InaccessibleSlot(wrapper, i) {
                @Override
                public ItemStack remove(int amount) {
                    return ItemStack.EMPTY;
                }
            };
            ingredientSlots[i].setNotDraggable();
            addSlot(ingredientSlots[i], SlotSemantics.PROCESSING_INPUTS);
        }
        blankSlot = addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.BLANK_PATTERN,
                terminal.getBlankPatternInventory(), 0), SlotSemantics.BLANK_PATTERN);
        encodedSlot = addSlot(new AppEngSlot(terminal.getEncodedPatternInventory(), 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(AEItems.PROCESSING_PATTERN.asItem());
            }
        }, SlotSemantics.ENCODED_PATTERN);
        registerClientAction("selectWorkbench", String.class, this::selectWorkbench);
        registerClientAction("selectRecipe", String.class, this::selectRecipe);
        registerClientAction("selectRecipeForWorkbench", RecipeSelection.class, selection -> {
            if (selection != null) {
                selectRecipe(selection.workbenchId(), selection.recipeId());
            }
        });
        registerClientAction("cycleIngredient", Integer.class, index -> {
            if (index != null) {
                cycleIngredient(index);
            }
        });
        registerClientAction("encode", this::encode);
        if (isServerSide()) {
            var workbenches = TaCZPatternEncodingRecipes.getWorkbenches();
            if (!workbenches.isEmpty()) {
                workbenchId = workbenches.get(0).toString();
            }
            if (stillValid(getPlayer())) {
                restoreEncodedPattern();
            }
            updateEncodingState();
        }
    }

    private static TaCZPatternEncodingMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf data) {
        var locator = MenuLocators.readFromPacket(data);
        var terminal = locator.locate(inventory.player, TaCZPatternEncodingPart.class);
        if (terminal == null) {
            throw new IllegalStateException("Missing TaCZ pattern terminal");
        }
        var menu = new TaCZPatternEncodingMenu(id, inventory, terminal);
        menu.setLocator(locator);
        menu.setReturnedFromSubScreen(data.readBoolean());
        return menu;
    }

    private static boolean open(Player player, MenuHostLocator locator, boolean returning) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        var terminal = locator.locate(player, TaCZPatternEncodingPart.class);
        if (terminal == null || !terminal.mayOpen(player)) {
            return false;
        }
        var provider = new SimpleMenuProvider((id, inventory, ignored) -> {
            var menu = new TaCZPatternEncodingMenu(id, inventory, terminal);
            menu.setLocator(locator);
            return menu;
        }, Component.translatable("item.appliedtacz.tacz_pattern_encoding_terminal"));
        serverPlayer.openMenu(provider, data -> {
            MenuLocators.writeToPacket(data, locator);
            data.writeBoolean(returning);
        });
        return true;
    }

    public void selectWorkbench(String id) {
        if (isClientSide()) {
            sendClientAction("selectWorkbench", id);
            return;
        }
        ResourceLocation parsed = parseId(id);
        if (!stillValid(getPlayer()) || parsed == null
                || !TaCZPatternEncodingRecipes.getWorkbenches().contains(parsed)) {
            return;
        }
        workbenchId = parsed.toString();
        clearRecipe();
        broadcastChanges();
    }

    public boolean canSelectRecipe(String id) {
        var entry = TaCZPatternEncodingRecipes.findRecipe(getPlayer().level(), parseId(id));
        return entry != null && PatternEncodingInputs.isEncodable(entry.recipe())
                && TaCZPatternEncodingRecipes.getWorkbenches().stream()
                        .anyMatch(workbench -> TaCZPatternEncodingRecipes.isAllowed(workbench, entry));
    }

    public void selectRecipe(String id) {
        if (isClientSide()) {
            sendClientAction("selectRecipe", id);
            return;
        }
        if (!stillValid(getPlayer()) || !canSelectRecipe(id)) {
            return;
        }
        var entry = TaCZPatternEncodingRecipes.findRecipe(getPlayer().level(), parseId(id));
        ResourceLocation workbench = parseId(workbenchId);
        if (!TaCZPatternEncodingRecipes.isAllowed(workbench, entry)) {
            workbench = TaCZPatternEncodingRecipes.getWorkbenches().stream()
                    .filter(candidate -> TaCZPatternEncodingRecipes.isAllowed(candidate, entry)).findFirst().orElse(null);
        }
        if (workbench == null) {
            return;
        }
        selectRecipe(workbench.toString(), id);
    }

    public boolean canSelectRecipe(String workbench, String id) {
        ResourceLocation parsed = parseId(workbench);
        var entry = TaCZPatternEncodingRecipes.findRecipe(getPlayer().level(), parseId(id));
        return parsed != null && entry != null && PatternEncodingInputs.isEncodable(entry.recipe())
                && TaCZPatternEncodingRecipes.getWorkbenches().contains(parsed)
                && TaCZPatternEncodingRecipes.isAllowed(parsed, entry);
    }

    public void selectRecipe(String workbench, String id) {
        if (isClientSide()) {
            sendClientAction("selectRecipeForWorkbench", new RecipeSelection(workbench, id));
            return;
        }
        if (!stillValid(getPlayer()) || !canSelectRecipe(workbench, id)) {
            return;
        }
        var entry = TaCZPatternEncodingRecipes.findRecipe(getPlayer().level(), parseId(id));
        List<AEItemKey> choices = new ArrayList<>(entry.recipe().getInputs().size());
        for (int i = 0; i < entry.recipe().getInputs().size(); i++) {
            choices.add(defaultChoice(entry.recipe(), i));
        }
        workbenchId = parseId(workbench).toString();
        recipeId = entry.id().toString();
        selectedRecipe = entry.recipe();
        selectedIngredients.clear();
        selectedIngredients.addAll(choices);
        refreshIngredients();
        selectionRevision++;
        broadcastChanges();
    }

    private AEItemKey defaultChoice(GunSmithTableRecipe recipe, int index) {
        var ingredient = recipe.getInputs().get(index).getIngredient();
        var grid = terminal.getMainNode().getGrid();
        if (grid != null) {
            AEItemKey best = null;
            long bestAmount = 0;
            for (var entry : grid.getStorageService().getCachedInventory()) {
                if (entry.getLongValue() > bestAmount && entry.getKey() instanceof AEItemKey key
                        && ingredient.test(key.getReadOnlyStack())) {
                    best = key;
                    bestAmount = entry.getLongValue();
                }
            }
            if (best != null) {
                return best;
            }
            for (var key : grid.getCraftingService().getCraftables(candidate -> candidate instanceof AEItemKey)) {
                if (key instanceof AEItemKey item && ingredient.test(item.getReadOnlyStack())) {
                    return item;
                }
            }
        }
        return PatternEncodingInputs.choices(recipe, index).get(0);
    }

    public void cycleIngredient(int index) {
        if (isClientSide()) {
            sendClientAction("cycleIngredient", index);
            return;
        }
        if (!stillValid(getPlayer()) || !selectionIsCurrent() || index < 0 || index >= selectedIngredients.size()) {
            return;
        }
        List<AEItemKey> choices = PatternEncodingInputs.choices(selectedRecipe, index);
        if (!choices.isEmpty()) {
            int next = (choices.indexOf(selectedIngredients.get(index)) + 1) % choices.size();
            selectedIngredients.set(index, choices.get(next));
            refreshIngredients();
            broadcastChanges();
        }
    }

    public void encode() {
        if (isClientSide()) {
            sendClientAction("encode");
            return;
        }
        if (!stillValid(getPlayer())) {
            return;
        }
        if (!java.util.Objects.equals(AEItemKey.of(encodedSlot.getItem()), lastEncodedPattern)) {
            broadcastChanges();
            return;
        }
        updateEncodingState();
        if (!readyToEncode) {
            return;
        }
        ItemStack pattern = TaCZPatternEncodingRecipes.encode(getPlayer().level(), selectedRecipe, selectedIngredients);
        if (pattern.isEmpty()) {
            return;
        }
        TaCZPatternMetadata.set(pattern, parseId(workbenchId), getPlayer());
        // Only mutate real items after every server-side recipe and output check has succeeded.
        if (encodedSlot.getItem().isEmpty()) {
            terminal.getBlankPatternInventory().extractItem(0, 1, false);
        }
        terminal.getEncodedPatternInventory().setItemDirect(0, pattern);
        lastEncodedPattern = AEItemKey.of(pattern);
        broadcastChanges();
    }

    public boolean canEncode() {
        return readyToEncode;
    }

    public AppEngSlot[] getIngredientSlots() {
        return ingredientSlots;
    }

    public @Nullable GunSmithTableRecipe getSelectedRecipe() {
        var entry = TaCZPatternEncodingRecipes.findRecipe(getPlayer().level(), parseId(recipeId));
        return entry == null ? null : entry.recipe();
    }

    private boolean selectionIsCurrent() {
        var entry = TaCZPatternEncodingRecipes.findRecipe(getPlayer().level(), parseId(recipeId));
        return entry != null && entry.recipe() == selectedRecipe
                && TaCZPatternEncodingRecipes.isAllowed(parseId(workbenchId), entry)
                && !PatternEncodingInputs.validatedInputs(entry.recipe(), selectedIngredients).isEmpty();
    }

    private void refreshIngredients() {
        ingredients.clear();
        var inputs = PatternEncodingInputs.validatedInputs(selectedRecipe, selectedIngredients);
        for (int i = 0; i < inputs.size(); i++) {
            ingredients.setStack(i, inputs.get(i));
        }
    }

    private void clearRecipe() {
        recipeId = "";
        selectedRecipe = null;
        selectedIngredients.clear();
        ingredients.clear();
    }

    private void restoreEncodedPattern() {
        AEItemKey key = AEItemKey.of(encodedSlot.getItem());
        if (java.util.Objects.equals(key, lastEncodedPattern)) {
            return;
        }
        lastEncodedPattern = key;
        if (key == null) {
            return;
        }
        clearRecipe();
        selectionRevision++;
        if (!(PatternDetailsHelper.decodePattern(key, getPlayer().level()) instanceof AEProcessingPattern pattern)) {
            return;
        }
        var workbenches = new ArrayList<>(TaCZPatternEncodingRecipes.getWorkbenches());
        if (TaCZPatternMetadata.hasWorkbenchId(encodedSlot.getItem())) {
            ResourceLocation encodedWorkbench = TaCZPatternMetadata.getWorkbenchId(encodedSlot.getItem());
            if (encodedWorkbench == null || !workbenches.contains(encodedWorkbench)) {
                return;
            }
            workbenches.clear();
            workbenches.add(encodedWorkbench);
        } else {
            ResourceLocation previousWorkbench = parseId(workbenchId);
            if (workbenches.remove(previousWorkbench)) {
                workbenches.add(0, previousWorkbench);
            }
        }
        for (var workbench : workbenches) {
            for (var entry : TaCZPatternEncodingRecipes.getRecipes(getPlayer().level(), workbench)) {
                if (!AssemblerRecipeMatcher.outputsMatch(entry.recipe().getResultItem(getPlayer().level().registryAccess()),
                        pattern.getOutputs())) {
                    continue;
                }
                var choices = PatternEncodingInputs.restoredChoices(entry.recipe(), pattern.getSparseInputs());
                if (!choices.isEmpty()) {
                    workbenchId = workbench.toString();
                    recipeId = entry.id().toString();
                    selectedRecipe = entry.recipe();
                    selectedIngredients.addAll(choices);
                    refreshIngredients();
                    return;
                }
            }
        }
    }

    private void updateEncodingState() {
        encodingError = "";
        if (!selectionIsCurrent()) {
            encodingError = "gui.appliedtacz.pattern_encoding.select_recipe";
        } else if (!encodedSlot.getItem().isEmpty()
                && (encodedSlot.getItem().getCount() != 1
                        || !encodedSlot.getItem().is(AEItems.PROCESSING_PATTERN.asItem())
                        || !(PatternDetailsHelper.decodePattern(encodedSlot.getItem(), getPlayer().level())
                                instanceof AEProcessingPattern))) {
            encodingError = "gui.appliedtacz.pattern_encoding.output_occupied";
        } else if (encodedSlot.getItem().isEmpty() && !blankSlot.getItem().is(AEItems.BLANK_PATTERN.asItem())) {
            encodingError = "gui.appliedtacz.pattern_encoding.no_blank_pattern";
        }
        readyToEncode = encodingError.isEmpty();
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player) && terminal.mayOpen(player);
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            if (!stillValid(getPlayer())) {
                setValidMenu(false);
                return;
            }
            if (selectedRecipe != null && !selectionIsCurrent()) {
                clearRecipe();
            }
            restoreEncodedPattern();
            updateEncodingState();
        }
        super.broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!isIngredientSlot(slotId) && (isClientSide() || stillValid(player))) {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override
    protected int transferStackToMenu(ItemStack input) {
        int initialCount = input.getCount();
        if (blankSlot.mayPlace(input)) {
            input = blankSlot.safeInsert(input);
            if (input.isEmpty()) {
                return initialCount;
            }
        }
        if (encodedSlot.mayPlace(input)) {
            input = encodedSlot.safeInsert(input);
            if (input.isEmpty()) {
                return initialCount;
            }
        }
        int transferred = initialCount - input.getCount();
        return transferred + super.transferStackToMenu(input);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return isIngredientSlot(index) || !stillValid(player) ? ItemStack.EMPTY : super.quickMoveStack(player, index);
    }

    @Override
    public void doAction(ServerPlayer player, InventoryAction action, int slot, long id) {
        if (!isIngredientSlot(slot) && stillValid(getPlayer())) {
            super.doAction(player, action, slot, id);
        }
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !isIngredientSlot(slot.index) && super.canDragTo(slot);
    }

    private boolean isIngredientSlot(int index) {
        return index >= 0 && index < slots.size() && slots.get(index) instanceof InaccessibleSlot;
    }

    private static @Nullable ResourceLocation parseId(String value) {
        return value == null || value.length() > 256 ? null : ResourceLocation.tryParse(value);
    }
}
