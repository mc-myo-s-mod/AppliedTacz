package me.myogoo.appliedtacz.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.Actionable;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.helpers.InventoryAction;
import appeng.menu.SlotSemantics;
import com.google.gson.Gson;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.crafting.AssemblerRecipeMatcher;
import me.myogoo.appliedtacz.crafting.PatternEncodingInputs;
import me.myogoo.appliedtacz.crafting.TaCZPatternEncodingRecipes;
import me.myogoo.appliedtacz.registry.ModBlocks;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;
import me.myogoo.appliedtacz.parts.TaCZPatternEncodingPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@GameTestHolder(AppliedTaCZ.MODID)
@PrefixGameTestTemplate(false)
public final class PatternEncodingGameTests {
    private static final BlockPos TERMINAL = new BlockPos(1, 1, 1);
    private static final BlockPos ASSEMBLER = new BlockPos(1, 1, 2);

    private PatternEncodingGameTests() {
    }

    @GameTest(template = "empty3x3x3")
    public static void encodingRetainsAllInputsAndAmounts(GameTestHelper helper) {
        for (int size : new int[] {1, 9, 10, 81, 82}) {
            var recipe = recipe(Collections.nCopies(size,
                    new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 130)));
            List<AEItemKey> choices = Collections.nCopies(size, AEItemKey.of(Items.GOLD_INGOT));
            ItemStack encoded = TaCZPatternEncodingRecipes.encode(helper.getLevel(), recipe, choices);
            helper.assertTrue(encoded.isEmpty() == (size > 81), "Expected exact 81-input encoding boundary");
            if (size > 81) {
                continue;
            }
            var details = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
            helper.assertTrue(details != null, "Expected a real decodable AE2 pattern");
            helper.assertTrue(PatternEncodingInputs.restoredChoices(recipe,
                    ((AEProcessingPattern) details).getSparseInputs()).equals(choices),
                    "Expected every sparse ingredient choice and overstacked amount to round-trip");
            KeyCounter inputs = new KeyCounter();
            for (var input : details.getInputs()) {
                var choice = input.getPossibleInputs()[0];
                inputs.add(choice.what(), Math.multiplyExact(choice.amount(), input.getMultiplier()));
            }
            helper.assertTrue(inputs.get(AEItemKey.of(Items.GOLD_INGOT)) == 130L * size,
                    "Expected selected alternatives and overstacked amounts to survive encoding");
            helper.assertTrue(AssemblerRecipeMatcher.matches(recipe, new KeyCounter[] {inputs}),
                    "Expected encoded inputs to satisfy the assembler's exact allocation");
            helper.assertTrue(AssemblerRecipeMatcher.outputsMatch(recipe.getOutput(), details.getOutputs()),
                    "Expected exact output key and count");
        }
        var zero = recipe(List.of(new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 0)));
        helper.assertTrue(!PatternEncodingInputs.isEncodable(zero)
                && TaCZPatternEncodingRecipes.encode(helper.getLevel(), zero,
                        List.of(AEItemKey.of(Items.IRON_INGOT))).isEmpty(), "Expected zero requirement rejection");
        var valid = recipe(List.of(new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 2)));
        helper.assertTrue(TaCZPatternEncodingRecipes.encode(helper.getLevel(), valid,
                List.of(AEItemKey.of(Items.DIAMOND))).isEmpty(), "Expected unrelated key rejection");
        helper.assertTrue(TaCZPatternEncodingRecipes.encode(helper.getLevel(), valid, List.of()).isEmpty(),
                "Expected missing selection rejection");
        verifyRestoredInputMatching(helper);
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 120)
    public static void terminalEncodingIsServerValidatedAndLossless(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        var position = helper.absolutePos(TERMINAL);
        player.setPos(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
        helper.setBlock(TERMINAL, AEBlocks.CABLE_BUS.block());
        var host = PartHelper.getPartHost(helper.getLevel(), position);
        helper.assertTrue(host != null, "Expected cable host");
        host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), null, player);
        IPartItem<?> partItem = IPartItem.byId(ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID,
                "tacz_pattern_encoding_terminal"));
        helper.assertTrue(partItem != null, "Expected registered terminal part item");
        var terminal = (TaCZPatternEncodingPart) host.addPart(partItem, Direction.NORTH, player);
        helper.assertTrue(terminal != null, "Expected terminal to attach to cable");
        helper.setBlock(TERMINAL.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(TERMINAL.east(), AEBlocks.DRIVE.block());
        ((DriveBlockEntity) helper.getBlockEntity(TERMINAL.east())).getInternalInventory()
                .setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        helper.setBlock(ASSEMBLER, ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get());
        helper.runAfterDelay(30, () -> {
            var entry = TaCZPatternEncodingRecipes.getWorkbenches().stream()
                    .flatMap(id -> TaCZPatternEncodingRecipes.getRecipes(helper.getLevel(), id).stream())
                    .filter(candidate -> PatternEncodingInputs.isEncodable(candidate.recipe())).findFirst().orElseThrow();
            var menu = new TaCZPatternEncodingMenu(7, player.getInventory(), terminal);
            helper.assertTrue(menu.stillValid(player), "Expected usable nearby terminal");
            var storage = terminal.getMainNode().getGrid().getStorageService().getInventory();
            var blankKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
            helper.assertTrue(terminal.getMainNode().isActive()
                    && storage.insert(blankKey, 3, Actionable.SIMULATE, menu.getActionSource()) == 3,
                    "Expected powered network with writable storage for shift-click regression");
            helper.assertTrue(menu.canSelectRecipe(entry.id().toString()),
                    "Expected JEI validity without stocked ingredients");
            menu.selectRecipe(entry.id().toString());
            helper.assertTrue(menu.recipeId.equals(entry.id().toString()), "Expected authoritative recipe selection");
            helper.assertTrue(!menu.canEncode(), "Expected blank-pattern requirement");
            player.getInventory().setItem(0, AEItems.BLANK_PATTERN.stack(3));
            var playerBlankSlot = menu.slots.stream()
                    .filter(slot -> slot.container == player.getInventory()
                            && slot.getItem().is(AEItems.BLANK_PATTERN.asItem())).findFirst().orElseThrow();
            menu.quickMoveStack(player, playerBlankSlot.index);
            helper.assertTrue(terminal.getBlankPatternInventory().getStackInSlot(0).getCount() == 3
                    && player.getInventory().getItem(0).isEmpty()
                    && storage.getAvailableStacks().get(blankKey) == 0,
                    "Expected shift-click to prioritize the real blank slot over writable ME storage");
            menu.broadcastChanges();
            helper.assertTrue(menu.canEncode(), "Expected valid recipe plus blank to enable encoding");
            verifyWorkbenchTransfers(helper, menu, terminal, entry);

            String selected = menu.recipeId;
            menu.selectRecipe("appliedtacz:nonexistent_recipe");
            menu.selectWorkbench("not a resource id");
            menu.cycleIngredient(-1);
            menu.cycleIngredient(81);
            helper.assertTrue(menu.recipeId.equals(selected), "Expected invalid actions to preserve selection");
            var ghost = menu.getIngredientSlots()[0];
            ItemStack before = ghost.getItem().copy();
            menu.setFilter(ghost.index, new ItemStack(Items.DIAMOND, 64));
            menu.clicked(ghost.index, 0, ClickType.PICKUP, player);
            menu.clicked(ghost.index, 0, ClickType.CLONE, player);
            for (InventoryAction action : InventoryAction.values()) {
                menu.doAction(null, action, ghost.index, 0);
            }
            helper.assertTrue(ItemStack.matches(before, ghost.getItem()) && menu.getCarried().isEmpty()
                    && menu.quickMoveStack(player, ghost.index).isEmpty(),
                    "Expected all native and AE2 ghost interaction paths to be read-only");

            terminal.getEncodedPatternInventory().setItemDirect(0, new ItemStack(Items.DIRT));
            menu.encode();
            helper.assertTrue(terminal.getBlankPatternInventory().getStackInSlot(0).getCount() == 3
                    && terminal.getEncodedPatternInventory().getStackInSlot(0).is(Items.DIRT),
                    "Expected occupied output to reject without deleting or consuming items");
            terminal.getEncodedPatternInventory().setItemDirect(0, ItemStack.EMPTY);
            menu.encode();
            helper.assertTrue(terminal.getBlankPatternInventory().getStackInSlot(0).getCount() == 2,
                    "Expected exactly one blank pattern consumed");
            ItemStack encoded = terminal.getEncodedPatternInventory().getStackInSlot(0).copy();
            var details = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
            helper.assertTrue(details != null, "Expected encoded AE2 processing pattern");
            verifyPatternRestoration(helper, menu, terminal, encoded);
            menu.encode();
            helper.assertTrue(terminal.getBlankPatternInventory().getStackInSlot(0).getCount() == 2
                    && ItemStack.matches(encoded, terminal.getEncodedPatternInventory().getStackInSlot(0)),
                    "Expected repeated action not to overwrite or duplicate output");

            KeyCounter input = new KeyCounter();
            for (var requirement : details.getInputs()) {
                var choice = requirement.getPossibleInputs()[0];
                input.add(choice.what(), choice.amount() * requirement.getMultiplier());
            }
            var assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ASSEMBLER);
            assembler.getPatternInventory().setItemDirect(0, encoded.copy());
            helper.assertTrue(assembler.pushPattern(details, new KeyCounter[] {input}) && input.isEmpty(),
                    "Expected existing assembler to accept terminal-created pattern");

            CompoundTag saved = new CompoundTag();
            terminal.writeToNBT(saved, helper.getLevel().registryAccess());
            terminal.clearContent();
            terminal.readFromNBT(saved, helper.getLevel().registryAccess());
            helper.assertTrue(terminal.getBlankPatternInventory().getStackInSlot(0).getCount() == 2
                    && ItemStack.matches(encoded, terminal.getEncodedPatternInventory().getStackInSlot(0)),
                    "Expected both real pattern inventories to survive save/load");
            menu.quickMoveStack(player, menu.getSlots(SlotSemantics.ENCODED_PATTERN).get(0).index);
            helper.assertTrue(terminal.getEncodedPatternInventory().getStackInSlot(0).isEmpty()
                    && player.getInventory().items.stream().filter(stack -> ItemStack.matches(encoded, stack))
                            .mapToInt(ItemStack::getCount).sum() == 1
                    && storage.getAvailableStacks().get(AEItemKey.of(encoded)) == 0,
                    "Expected shift-click output to conserve exactly one encoded pattern in player inventory");
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (ItemStack.matches(encoded, player.getInventory().getItem(i))) {
                    terminal.getEncodedPatternInventory().setItemDirect(0, player.getInventory().removeItem(i, 1));
                    break;
                }
            }
            List<ItemStack> drops = new ArrayList<>();
            terminal.addAdditionalDrops(drops, false);
            helper.assertTrue(drops.size() == 2 && drops.stream().mapToInt(ItemStack::getCount).sum() == 3,
                    "Expected only two blanks and one encoded pattern, never ghost ingredients");
            terminal.clearContent();
            drops.clear();
            terminal.addAdditionalDrops(drops, false);
            helper.assertTrue(drops.isEmpty(), "Expected no duplicated drops after clearing");

            terminal.getBlankPatternInventory().setItemDirect(0, AEItems.BLANK_PATTERN.stack(1));
            player.setPos(position.getX() + 100, position.getY(), position.getZ());
            menu.encode();
            helper.assertTrue(terminal.getBlankPatternInventory().getStackInSlot(0).getCount() == 1
                    && terminal.getEncodedPatternInventory().getStackInSlot(0).isEmpty(),
                    "Expected distant player to be rejected without consuming blank");
            helper.succeed();
        });
    }

    private static void verifyRestoredInputMatching(GameTestHelper helper) {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var diamond = AEItemKey.of(Items.DIAMOND);
        var overlapping = recipe(List.of(
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 130),
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 130),
                new GunSmithTableIngredient(Ingredient.of(Items.DIAMOND), 130)));
        helper.assertTrue(PatternEncodingInputs.restoredChoices(overlapping, List.of(
                new GenericStack(iron, 130), new GenericStack(diamond, 130), new GenericStack(gold, 130)))
                .equals(List.of(gold, iron, diamond)),
                "Expected reordered overlapping ingredients to restore without a greedy mismatch");
        helper.assertTrue(PatternEncodingInputs.restoredChoices(overlapping, List.of(
                new GenericStack(gold, 131), new GenericStack(iron, 130), new GenericStack(diamond, 130))).isEmpty(),
                "Expected excess ingredient amounts to reject restoration");
        helper.assertTrue(PatternEncodingInputs.restoredChoices(overlapping, List.of(
                new GenericStack(iron, 260), new GenericStack(diamond, 130))).isEmpty(),
                "Expected merged external inputs to reject rather than invent per-slot alternatives");
    }

    private static void verifyPatternRestoration(GameTestHelper helper, TaCZPatternEncodingMenu menu,
                                                TaCZPatternEncodingPart terminal, ItemStack encoded) {
        var outputSlot = menu.getSlots(SlotSemantics.ENCODED_PATTERN).get(0);
        var ghosts = java.util.Arrays.stream(menu.getIngredientSlots()).map(slot -> slot.getItem().copy()).toList();
        ItemStack blanks = terminal.getBlankPatternInventory().getStackInSlot(0).copy();
        String workbench = menu.workbenchId;
        int revision = menu.selectionRevision;
        menu.broadcastChanges();
        menu.broadcastChanges();
        helper.assertTrue(menu.selectionRevision == revision, "Expected encoding and unchanged ticks not to reload choices");
        menu.clicked(outputSlot.index, 0, ClickType.PICKUP, menu.getPlayer());
        helper.assertTrue(ItemStack.matches(encoded, menu.getCarried()) && outputSlot.getItem().isEmpty(),
                "Expected normal pickup to conserve the encoded pattern");
        menu.selectWorkbench(workbench);
        helper.assertTrue(menu.recipeId.isEmpty(), "Expected browsing another selection to clear the prior recipe");
        menu.clicked(outputSlot.index, 0, ClickType.PICKUP, menu.getPlayer());
        menu.broadcastChanges();
        var restored = TaCZPatternEncodingRecipes.findRecipe(helper.getLevel(), ResourceLocation.tryParse(menu.recipeId));
        helper.assertTrue(menu.getCarried().isEmpty() && restored != null
                && TaCZPatternEncodingRecipes.isAllowed(ResourceLocation.tryParse(menu.workbenchId), restored)
                && menu.selectionRevision > revision, "Expected normal reinsertion to restore an allowed recipe and reveal it");
        for (int i = 0; i < ghosts.size(); i++) {
            helper.assertTrue(ItemStack.matches(ghosts.get(i), menu.getIngredientSlots()[i].getItem()),
                    "Expected reinsertion to restore all sparse ingredient choices and exact counts");
        }
        var reopened = new TaCZPatternEncodingMenu(9, menu.getPlayerInventory(), terminal);
        helper.assertTrue(!reopened.recipeId.isEmpty() && reopened.selectionRevision == 1,
                "Expected opening a terminal containing a pattern to restore and reveal its recipe");
        for (int i = 0; i < ghosts.size(); i++) {
            helper.assertTrue(ItemStack.matches(ghosts.get(i), reopened.getIngredientSlots()[i].getItem()),
                    "Expected reopening to preserve every encoded sparse choice");
        }
        menu.quickMoveStack(menu.getPlayer(), outputSlot.index);
        menu.selectWorkbench(workbench);
        var playerSlot = menu.slots.stream()
                .filter(slot -> slot.container == menu.getPlayerInventory()
                        && ItemStack.matches(encoded, slot.getItem())).findFirst().orElseThrow();
        menu.quickMoveStack(menu.getPlayer(), playerSlot.index);
        menu.broadcastChanges();
        helper.assertTrue(ItemStack.matches(encoded, outputSlot.getItem()) && playerSlot.getItem().isEmpty()
                && !menu.recipeId.isEmpty(), "Expected shift insertion to prioritize the pattern slot and restore selection");
        menu.encode();
        helper.assertTrue(ItemStack.matches(blanks, terminal.getBlankPatternInventory().getStackInSlot(0))
                && ItemStack.matches(encoded, outputSlot.getItem()) && !menu.canEncode(),
                "Expected viewing and occupied-slot encoding to preserve all real items");

        var pattern = (AEProcessingPattern) PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        var inputs = new ArrayList<>(pattern.getSparseInputs());
        for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            if (input != null) {
                inputs.set(i, new GenericStack(input.what(), input.amount() + 1));
                break;
            }
        }
        ItemStack excess = PatternDetailsHelper.encodeProcessingPattern(inputs, pattern.getSparseOutputs());
        ItemStack unrelated = TaCZPatternEncodingRecipes.encode(helper.getLevel(),
                recipe(List.of(new GunSmithTableIngredient(Ingredient.of(Items.DIRT), 1))),
                List.of(AEItemKey.of(Items.DIRT)));
        for (var invalid : List.of(AEItems.PROCESSING_PATTERN.stack(), excess, unrelated)) {
            terminal.getEncodedPatternInventory().setItemDirect(0, invalid.copy());
            menu.broadcastChanges();
            helper.assertTrue(menu.recipeId.isEmpty() && !menu.canEncode()
                    && ItemStack.matches(invalid, outputSlot.getItem())
                    && ItemStack.matches(blanks, terminal.getBlankPatternInventory().getStackInSlot(0)),
                    "Expected malformed or nonmatching patterns to clear stale selection without item mutation");
            for (var ghost : menu.getIngredientSlots()) {
                helper.assertTrue(ghost.getItem().isEmpty(), "Expected rejected patterns not to leave stale ingredients");
            }
        }
        helper.assertTrue(!outputSlot.mayPlace(new ItemStack(Items.DIRT)),
                "Expected the encoded slot to reject unrelated real items");
        terminal.getEncodedPatternInventory().setItemDirect(0, encoded.copy());
        menu.broadcastChanges();
        helper.assertTrue(!menu.recipeId.isEmpty(), "Expected valid pattern to restore after rejected patterns");
    }

    private static void verifyWorkbenchTransfers(GameTestHelper helper, TaCZPatternEncodingMenu menu,
                                                 TaCZPatternEncodingPart terminal,
                                                 TaCZPatternEncodingRecipes.Entry recipe) {
        Gson gson = new Gson();
        for (var workbench : TaCZPatternEncodingRecipes.getWorkbenches()) {
            var candidate = TaCZPatternEncodingRecipes.getRecipes(helper.getLevel(), workbench).stream()
                    .filter(entry -> PatternEncodingInputs.isEncodable(entry.recipe())).findFirst();
            if (candidate.isEmpty()) {
                continue;
            }
            receiveSelection(menu, gson.toJson(new TaCZPatternEncodingMenu.RecipeSelection(
                    workbench.toString(), candidate.get().id().toString())));
            helper.assertTrue(menu.workbenchId.equals(workbench.toString())
                    && menu.recipeId.equals(candidate.get().id().toString()),
                    "Expected each workbench category to select its exact recipe and tab");
        }

        var source = TimelessAPI.getAllCommonBlockIndex().stream()
                .filter(entry -> TaCZPatternEncodingRecipes.isAllowed(entry.getKey(), recipe)).findFirst().orElseThrow();
        var target = TimelessAPI.getAllCommonBlockIndex().stream()
                .filter(entry -> !entry.getKey().equals(source.getKey())).findFirst().orElseThrow();
        var original = target.getValue();
        var playerPosition = menu.getPlayer().position();
        ItemStack originalOutput = terminal.getEncodedPatternInventory().getStackInSlot(0).copy();
        try {
            // A temporary duplicate models two workbenches accepting the same recipe; always restore the pack index.
            target.setValue(source.getValue());
            menu.selectRecipe(source.getKey().toString(), recipe.id().toString());
            int revision = menu.selectionRevision;
            receiveSelection(menu, gson.toJson(new TaCZPatternEncodingMenu.RecipeSelection(
                    target.getKey().toString(), recipe.id().toString())));
            helper.assertTrue(menu.workbenchId.equals(target.getKey().toString())
                    && menu.recipeId.equals(recipe.id().toString()) && menu.selectionRevision == revision + 1,
                    "Expected overlap transfer to honor the source category, not retain the previous tab");
            for (int i = 0; i < menu.getIngredientSlots().length; i++) {
                var stack = menu.getIngredientSlots()[i].getItem();
                if (i < recipe.recipe().getInputs().size()) {
                    var requirement = recipe.recipe().getInputs().get(i);
                    var input = GenericStack.fromItemStack(stack);
                    helper.assertTrue(input != null && input.what() instanceof AEItemKey key
                            && requirement.getIngredient().test(key.getReadOnlyStack())
                            && input.amount() == requirement.getCount(), "Expected matching recipe ghost inputs and counts");
                } else {
                    helper.assertTrue(stack.isEmpty(), "Expected unused ghost slots to be empty");
                }
            }

            menu.selectRecipe(source.getKey().toString(), recipe.id().toString());
            revision = menu.selectionRevision;
            receiveSelection(menu, gson.toJson(new TaCZPatternEncodingMenu.RecipeSelection(
                    source.getKey().toString(), recipe.id().toString())));
            helper.assertTrue(menu.selectionRevision == revision + 1,
                    "Expected repeated same-recipe transfer to notify the client to reveal selection again");
            target.setValue(new CommonBlockIndex() {
                @Override
                public BlockData getData() {
                    return source.getValue().getData();
                }

                @Override
                public RecipeFilter getFilter() {
                    return new RecipeFilter() {
                        @Override
                        public boolean contains(ResourceLocation id) {
                            return false;
                        }
                    };
                }
            });
            terminal.getEncodedPatternInventory().setItemDirect(0, new ItemStack(Items.DIRT));
            menu.broadcastChanges();
            menu.selectRecipe(source.getKey().toString(), recipe.id().toString());
            revision = menu.selectionRevision;
            var ghosts = java.util.Arrays.stream(menu.getIngredientSlots()).map(slot -> slot.getItem().copy()).toList();
            ItemStack blanks = terminal.getBlankPatternInventory().getStackInSlot(0).copy();
            ItemStack output = terminal.getEncodedPatternInventory().getStackInSlot(0).copy();
            var invalidPayloads = new ArrayList<>(List.of("null", "{}"));
            for (var selection : List.of(
                    new TaCZPatternEncodingMenu.RecipeSelection(null, recipe.id().toString()),
                    new TaCZPatternEncodingMenu.RecipeSelection(source.getKey().toString(), null),
                    new TaCZPatternEncodingMenu.RecipeSelection("not a resource id", recipe.id().toString()),
                    new TaCZPatternEncodingMenu.RecipeSelection("x".repeat(257), recipe.id().toString()),
                    new TaCZPatternEncodingMenu.RecipeSelection("appliedtacz:missing_workbench", recipe.id().toString()),
                    new TaCZPatternEncodingMenu.RecipeSelection(source.getKey().toString(), "appliedtacz:missing_recipe"),
                    new TaCZPatternEncodingMenu.RecipeSelection(target.getKey().toString(), recipe.id().toString()))) {
                helper.assertTrue(!menu.canSelectRecipe(selection.workbenchId(), selection.recipeId()),
                        "Expected invalid workbench and recipe pairs to be rejected in JEI preview");
                invalidPayloads.add(gson.toJson(selection));
            }
            invalidPayloads.add(gson.toJson(new TaCZPatternEncodingMenu.RecipeSelection(
                    source.getKey().toString(), recipe.id().toString())));
            for (int i = 0; i < invalidPayloads.size(); i++) {
                if (i == invalidPayloads.size() - 1) {
                    menu.getPlayer().setPos(playerPosition.x + 100, playerPosition.y, playerPosition.z);
                }
                receiveSelection(menu, invalidPayloads.get(i));
                helper.assertTrue(menu.workbenchId.equals(source.getKey().toString())
                        && menu.recipeId.equals(recipe.id().toString()) && menu.selectionRevision == revision
                        && ItemStack.matches(blanks, terminal.getBlankPatternInventory().getStackInSlot(0))
                        && ItemStack.matches(output, terminal.getEncodedPatternInventory().getStackInSlot(0)),
                        "Expected invalid or distant-client transfer to preserve selection and real pattern slots");
                for (int slot = 0; slot < ghosts.size(); slot++) {
                    helper.assertTrue(ItemStack.matches(ghosts.get(slot), menu.getIngredientSlots()[slot].getItem()),
                            "Expected rejected transfer to preserve all 81 ghost slots");
                }
            }
            menu.getPlayer().setPos(playerPosition.x, playerPosition.y, playerPosition.z);
            var deniedMenu = new TaCZPatternEncodingMenu(8, menu.getPlayerInventory(), terminal) {
                @Override
                public boolean stillValid(Player player) {
                    return false;
                }
            };
            String deniedWorkbench = deniedMenu.workbenchId;
            receiveSelection(deniedMenu, gson.toJson(new TaCZPatternEncodingMenu.RecipeSelection(
                    source.getKey().toString(), recipe.id().toString())));
            helper.assertTrue(deniedMenu.workbenchId.equals(deniedWorkbench) && deniedMenu.recipeId.isEmpty()
                    && deniedMenu.selectionRevision == 0
                    && ItemStack.matches(blanks, terminal.getBlankPatternInventory().getStackInSlot(0))
                    && ItemStack.matches(output, terminal.getEncodedPatternInventory().getStackInSlot(0)),
                    "Expected denied menu validity to reject otherwise valid transfer without mutating patterns");
            for (var slot : deniedMenu.getIngredientSlots()) {
                helper.assertTrue(slot.getItem().isEmpty(), "Expected denied menu not to populate ghost ingredients");
            }
        } finally {
            target.setValue(original);
            menu.getPlayer().setPos(playerPosition.x, playerPosition.y, playerPosition.z);
            terminal.getEncodedPatternInventory().setItemDirect(0, originalOutput);
            menu.selectRecipe(source.getKey().toString(), recipe.id().toString());
        }
    }

    private static void receiveSelection(TaCZPatternEncodingMenu menu, String json) {
        menu.receiveClientAction("selectRecipeForWorkbench", json);
    }

    private static GunSmithTableRecipe recipe(List<GunSmithTableIngredient> ingredients) {
        return new GunSmithTableRecipe(new GunSmithTableResult(new ItemStack(Items.DIAMOND, 3), null), ingredients);
    }
}
