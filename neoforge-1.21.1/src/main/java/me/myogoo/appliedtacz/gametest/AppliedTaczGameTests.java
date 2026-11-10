package me.myogoo.appliedtacz.gametest;

import appeng.api.AECapabilities;
import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.SlotSemantics;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.init.ModRecipe;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.crafting.AssemblerRecipeMatcher;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.registry.ModBlocks;
import me.myogoo.appliedtacz.registry.ModItems;
import me.myogoo.appliedtacz.registry.ModMenus;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(AppliedTaCZ.MODID)
@PrefixGameTestTemplate(false)
public final class AppliedTaczGameTests {
    private static final BlockPos ROOT = new BlockPos(1, 1, 1);

    private AppliedTaczGameTests() {
    }

    @GameTest(template = "empty3x3x3")
    public static void officialWorkbenchMenusKeepCustomTypeAndRecipeSelection(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (Block block : List.of(ModBlocks.GUN_SMITH_TABLE.get(), ModBlocks.AMMO_ASSEMBLY_TABLE.get(),
                ModBlocks.ATTACHMENT_TABLE.get())) {
            helper.setBlock(ROOT, block);
            AEGunSmithTableBlockEntity workbench = helper.getBlockEntity(ROOT);
            ResourceLocation blockId = AETaCZWorkbenchIds.getDefaultBlockId(block);
            workbench.setId(blockId);
            AEGunSmithTableMenu menu = new AEGunSmithTableMenu(0, player.getInventory(), workbench);
            helper.assertTrue(menu.getType() == ModMenus.AE_GUN_SMITH_TABLE.get()
                            && blockId.equals(menu.getBlockId()),
                    "Expected the official TaCZ constructor to retain the AppliedTaCZ menu type and workbench id");
            var recipe = helper.getLevel().getRecipeManager()
                    .getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()).stream()
                    .filter(holder -> menu.canSelectRecipe(holder.id(), helper.getLevel().getRecipeManager()))
                    .findFirst().orElseThrow();
            menu.requestRecipeSelection(recipe.id());
            helper.assertTrue(recipe.id().equals(menu.consumeRecipeSelection())
                            && menu.consumeRecipeSelection() == null,
                    "Expected official recipe selection to be consumed exactly once");
            AEGunSmithTableMenu clientMenu = new AEGunSmithTableMenu(0, player.getInventory(), null, blockId);
            helper.assertTrue(clientMenu.getType() == ModMenus.AE_GUN_SMITH_TABLE.get()
                            && blockId.equals(clientMenu.getBlockId()),
                    "Expected the client-side constructor to preserve the transmitted workbench id without its block entity");
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void assemblerMatcherHandlesOverlappingIngredients(GameTestHelper helper) {
        GunSmithTableRecipe recipe = testRecipe(
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1),
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 1)
        );
        KeyCounter[] inputs = counters(Items.IRON_INGOT, 1, Items.GOLD_INGOT, 1);

        helper.assertTrue(AssemblerRecipeMatcher.matches(recipe, inputs),
                "Expected overlapping ingredients to allocate");
        AssemblerRecipeMatcher.consume(inputs);
        helper.assertTrue(inputs[0].isEmpty() && inputs[1].isEmpty(),
                "Expected matcher consume to empty accepted inputs");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void assemblerMatcherRejectsMissingExtraAndWrongOutput(GameTestHelper helper) {
        GunSmithTableRecipe recipe = testRecipe(
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1),
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 1)
        );
        ItemStack namedOutput = new ItemStack(Items.DIAMOND);
        namedOutput.set(DataComponents.CUSTOM_NAME, Component.literal("exact"));

        helper.assertFalse(AssemblerRecipeMatcher.matches(recipe, counters(Items.GOLD_INGOT, 1)),
                "Expected missing input to fail");
        helper.assertFalse(AssemblerRecipeMatcher.matches(recipe,
                        counters(Items.IRON_INGOT, 1, Items.GOLD_INGOT, 1, Items.COPPER_INGOT, 1)),
                "Expected extra input to fail");
        helper.assertFalse(AssemblerRecipeMatcher.outputsMatch(namedOutput,
                        List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))),
                "Expected missing output components to fail");
        helper.assertFalse(AssemblerRecipeMatcher.outputsMatch(new ItemStack(Items.DIAMOND, 2),
                        List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))),
                "Expected wrong output count to fail");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void placingAssemblerItemsBuildsAllFootprints(GameTestHelper helper) {
        MolecularAssemblerBlock ammo = (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get();
        helper.assertValueEqual(ammo.partCount(), 4, "Expected ammo assembler to occupy 2x1x2");
        helper.assertFalse(ammo.isValidPart(ammo.defaultBlockState().setValue(MolecularAssemblerBlock.PART, 4)),
                "Expected ammo part 4 to be outside the footprint");
        assertPlacesFootprint(helper, ammo, assemblerStack(ammo, AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));
        assertPlacesFootprint(helper, (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_ATTACHMENT.get(),
                assemblerStack(ModBlocks.MOLECULAR_ASSEMBLER_ATTACHMENT.get(), AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID));
        assertPlacesFootprint(helper, (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_GUN.get(),
                assemblerStack(ModBlocks.MOLECULAR_ASSEMBLER_GUN.get(), AETaCZWorkbenchIds.WORKBENCH_B_ID));
        assertPlacesFootprint(helper, (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_UNIVERSAL.get(),
                new ItemStack(ModItems.MOLECULAR_ASSEMBLER_UNIVERSAL.get()));
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void compactAssemblerOccupiesOneBlockAndConnectsOnSixFaces(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER.get();
        helper.assertValueEqual(block.partCount(), 1, "Expected compact assembler to occupy one block");
        helper.assertFalse(block.isValidPart(block.defaultBlockState().setValue(MolecularAssemblerBlock.PART, 1)),
                "Expected compact assembler to reject additional parts");
        for (Direction side : Direction.values()) {
            helper.setBlock(ROOT.relative(side), Blocks.STONE);
        }
        placeAssembler(helper, new ItemStack(ModItems.MOLECULAR_ASSEMBLER.get()));
        helper.assertBlockPresent(block, ROOT);
        for (Direction side : Direction.values()) {
            helper.assertBlockPresent(Blocks.STONE, ROOT.relative(side));
        }
        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        helper.assertTrue(root.getRootAssembler() == root, "Expected compact assembler to be its own root");
        helper.assertTrue(DefaultAssets.EMPTY_BLOCK_ID.equals(root.getId()),
                "Expected compact assembler to have no dedicated workbench binding");
        helper.setBlock(ROOT.east(), block);
        helper.setBlock(ROOT.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(root.isNetworkPowered(), "Expected compact assembler to receive power from above");
            for (Direction side : Direction.values()) {
                helper.assertTrue(root.getGridNode(side) != null,
                        "Expected a grid node on compact face " + side);
                helper.assertTrue(root.getCableConnectionType(side) != AECableType.NONE,
                        "Expected cable connection on compact face " + side);
                helper.assertTrue(helper.getLevel().getCapability(AECapabilities.CRAFTING_MACHINE,
                                helper.absolutePos(ROOT), side) == root,
                        "Expected crafting capability on compact face " + side);
            }
            AEMolecularAssemblerMenu menu = new AEMolecularAssemblerMenu(1, menuPlayer(helper).getInventory(), root);
            helper.assertValueEqual(menu.slots.size(), 51, "Expected patterns, five upgrades and player inventory layout");
            helper.assertTrue(menu.workbenchId.isEmpty(), "Expected all-workbench scope in the compact menu");
            helper.destroyBlock(ROOT);
            menu.broadcastChanges();
            helper.assertFalse(menu.isValidMenu(), "Expected destroyed compact assembler to invalidate its menu");
            helper.assertBlockPresent(block, ROOT.east());
            helper.assertBlockPresent(AEBlocks.CREATIVE_ENERGY_CELL.block(), ROOT.above());
            helper.assertValueEqual(helper.getEntities(EntityType.ITEM).stream()
                            .filter(item -> item.getItem().is(ModItems.MOLECULAR_ASSEMBLER.get()))
                            .mapToInt(item -> item.getItem().getCount()).sum(),
                    1, "Expected one compact assembler drop without removing its neighbor");
            helper.succeed();
        });
    }

    @GameTest(template = "empty3x3x3")
    public static void compactVariantsPlaceOnlyOneCellAndKeepLegacyFootprints(GameTestHelper helper) {
        for (Block registered : List.of(ModBlocks.TACZ_ASSEMBLER_AMMO.get(),
                ModBlocks.TACZ_ASSEMBLER_ATTACHMENT.get(), ModBlocks.TACZ_ASSEMBLER_GUN.get(),
                ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get(), ModBlocks.EXTENDED_TACZ_ASSEMBLER_AMMO.get(),
                ModBlocks.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get(), ModBlocks.EXTENDED_TACZ_ASSEMBLER_GUN.get())) {
            MolecularAssemblerBlock block = (MolecularAssemblerBlock) registered;
            helper.assertValueEqual(block.partCount(), 1, "Expected compact variant to occupy one cell");
            for (Direction side : Direction.values()) {
                helper.setBlock(ROOT.relative(side), Blocks.STONE);
            }
            placeAssembler(helper, new ItemStack(block));
            helper.assertBlockPresent(block, ROOT);
            AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
            helper.assertTrue(root.getType().isValid(helper.getBlockState(ROOT)), "Expected registered block entity support");
            helper.assertTrue(root.getRootAssembler() == root, "Expected single-cell root");
            helper.assertTrue(root.getId().equals(block.isUniversal() ? DefaultAssets.EMPTY_BLOCK_ID
                    : block.kind().defaultBlockId()), "Expected correct workbench binding");
            helper.assertTrue(new AEMolecularAssemblerMenu(1, menuPlayer(helper).getInventory(), root).isExtended()
                    == block.isExtended(), "Expected menu to preserve assembler tier");
            for (Direction side : Direction.values()) {
                helper.assertBlockPresent(Blocks.STONE, ROOT.relative(side));
                helper.assertTrue(root.getCraftingMachine(side) == root, "Expected crafting access on " + side);
                helper.assertTrue(root.getCableConnectionType(side) != AECableType.NONE,
                        "Expected cable connection on " + side);
            }
            helper.destroyBlock(ROOT);
            for (Direction side : Direction.values()) {
                helper.assertBlockPresent(Blocks.STONE, ROOT.relative(side));
            }
            helper.assertValueEqual(helper.getEntities(EntityType.ITEM).stream()
                    .filter(item -> item.getItem().is(block.asItem())).mapToInt(item -> item.getItem().getCount()).sum(),
                    1, "Expected exactly one compact variant drop");
        }
        helper.assertValueEqual(((MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get()).partCount(), 4,
                "Expected legacy ammo footprint unchanged");
        helper.assertValueEqual(((MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_ATTACHMENT.get()).partCount(), 4,
                "Expected legacy attachment footprint unchanged");
        helper.assertValueEqual(((MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_GUN.get()).partCount(), 8,
                "Expected legacy gun footprint unchanged");
        helper.assertValueEqual(((MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_UNIVERSAL.get()).partCount(), 8,
                "Expected legacy universal footprint unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void compactAssemblerRecipesRequireOriginalWorkbenchAndProvider(GameTestHelper helper) {
        for (var entry : AETaCZWorkbenchIndex.entries()) {
            ItemStack workbench = BlockItemBuilder.create(entry.baseWorkbench()).setId(entry.blockId()).build();
            ItemStack assembler = new ItemStack(AEBlocks.MOLECULAR_ASSEMBLER.asItem());
            ItemStack provider = new ItemStack(AEBlocks.PATTERN_PROVIDER.asItem());
            CraftingInput input = CraftingInput.of(3, 1, List.of(workbench, assembler, provider));
            if (WorkbenchUpgradeKind.forAssembler(entry.blockId()) == null) {
                helper.assertTrue(helper.getLevel().getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isEmpty(),
                        "Expected custom workbenches to have no command-only universal assembler recipe");
                continue;
            }
            var holder = helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            var recipe = holder.value();
            ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(holder.id().getPath().startsWith("assembler_upgrade/"),
                    "Expected a compact assembler recipe for " + entry.blockId());
            helper.assertValueEqual(recipe.getIngredients().size(), 3, "Expected exactly three ingredients");
            helper.assertTrue(ItemStack.matches(result, entry.createAssemblerStack()),
                    "Expected the dedicated assembler to retain its original TaCZ BlockId");
            helper.assertValueEqual(result.getCount(), 1, "Expected exactly one compact assembler");
            helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(workbench, assembler)), helper.getLevel()),
                    "Expected missing pattern provider to reject crafting");
            helper.assertFalse(recipe.matches(CraftingInput.of(2, 1, List.of(entry.createAppliedStack(), assembler)),
                    helper.getLevel()), "Expected the old AppliedTaCZ workbench recipe to be rejected");
            helper.assertFalse(recipe.matches(CraftingInput.of(3, 1,
                    List.of(entry.createAppliedStack(), assembler, provider)), helper.getLevel()),
                    "Expected AppliedTaCZ workbench to remain invalid even with a provider");
            ResourceLocation wrongId = ResourceLocation.fromNamespaceAndPath("custom", "recipe_identity_probe");
            ItemStack wrongWorkbench = BlockItemBuilder.create(entry.baseWorkbench()).setId(wrongId).build();
            if (((BlockItemDataAccessor) wrongWorkbench.getItem()).getBlockId(wrongWorkbench).equals(wrongId)) {
                helper.assertFalse(recipe.matches(CraftingInput.of(3, 1, List.of(wrongWorkbench, assembler, provider)),
                        helper.getLevel()), "Expected another TaCZ BlockId to reject the dedicated recipe");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void extendedRecipesRequireEightMatchingAssemblersAndCalculationProcessor(GameTestHelper helper) {
        List<Block> normal = List.of(ModBlocks.TACZ_ASSEMBLER_AMMO.get(), ModBlocks.TACZ_ASSEMBLER_ATTACHMENT.get(),
                ModBlocks.TACZ_ASSEMBLER_GUN.get());
        List<Block> extended = List.of(ModBlocks.EXTENDED_TACZ_ASSEMBLER_AMMO.get(),
                ModBlocks.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get(), ModBlocks.EXTENDED_TACZ_ASSEMBLER_GUN.get());
        for (int kind = 0; kind < normal.size(); kind++) {
            List<ItemStack> grid = new ArrayList<>();
            for (int slot = 0; slot < 9; slot++) {
                grid.add(slot == 4 ? AEItems.CALCULATION_PROCESSOR.stack() : new ItemStack(normal.get(kind)));
            }
            CraftingInput input = CraftingInput.of(3, 3, grid);
            var recipe = helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow().value();
            ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(extended.get(kind).asItem()) && result.getCount() == 1,
                    "Expected exactly one corresponding Extended assembler");
            helper.assertTrue(((BlockItemDataAccessor) result.getItem()).getBlockId(result)
                    .equals(((MolecularAssemblerBlock) extended.get(kind)).kind().defaultBlockId()),
                    "Expected Extended result to retain its workbench category");
            grid.set(0, new ItemStack(normal.get((kind + 1) % normal.size())));
            helper.assertFalse(recipe.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                    "Expected mixed assembler categories to be rejected");
            grid.set(0, ItemStack.EMPTY);
            helper.assertFalse(recipe.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                    "Expected seven assemblers to be rejected");
            grid.set(0, new ItemStack(normal.get(kind)));
            grid.set(4, AEItems.ENGINEERING_PROCESSOR.stack());
            helper.assertFalse(recipe.matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                    "Expected the center to require a calculation processor");
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void universalAssemblersHaveNoCraftingRecipes(GameTestHelper helper) {
        TagKey<Item> hidden = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "hidden_from_recipe_viewers"));
        for (Item item : List.of(ModItems.TACZ_ASSEMBLER_UNIVERSAL.get(),
                ModItems.MOLECULAR_ASSEMBLER_UNIVERSAL.get(), ModItems.MOLECULAR_ASSEMBLER.get())) {
            helper.assertTrue(new ItemStack(item).is(hidden),
                    "Expected every universal assembler in the loaded recipe-viewer hidden tag");
        }
        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            helper.assertFalse(new ItemStack(kind.assemblerResult()).is(hidden),
                    "Expected dedicated compact assemblers to remain visible in recipe viewers");
        }
        for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
            ItemStack result = holder.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertFalse(result.is(ModItems.TACZ_ASSEMBLER_UNIVERSAL.get())
                            || result.is(ModItems.MOLECULAR_ASSEMBLER_UNIVERSAL.get())
                            || result.is(ModItems.MOLECULAR_ASSEMBLER.get()),
                    "Expected universal assemblers to be command-only, found " + holder.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void legacyUniversalAssemblerItemRemainsUnbound(GameTestHelper helper) {
        ItemStack universal = new ItemStack(ModItems.MOLECULAR_ASSEMBLER_UNIVERSAL.get());
        helper.assertTrue(universal.getItem() instanceof BlockItemDataAccessor accessor
                        && DefaultAssets.EMPTY_BLOCK_ID.equals(accessor.getBlockId(universal)),
                "Expected universal assembler item to have no bound TaCZ BlockId");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void blockedAssemblerPlacementDoesNotMutateWorld(GameTestHelper helper) {
        helper.setBlock(ROOT.above(), Blocks.STONE);
        placeAssembler(helper, assemblerStack(ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get(),
                AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));

        helper.assertBlockPresent(Blocks.AIR, ROOT);
        helper.assertBlockPresent(Blocks.STONE, ROOT.above());
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void breakingAnyPartDropsOneAssemblerWithBlockId(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get();
        ResourceLocation blockId = AETaCZWorkbenchIds.AMMO_WORKBENCH_ID;
        placeAssembler(helper, assemblerStack(block, blockId));

        Direction facing = helper.getBlockState(ROOT).getValue(MolecularAssemblerBlock.FACING);
        helper.destroyBlock(block.getPartPos(ROOT, facing, 1));
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> drops = helper.getEntities(EntityType.ITEM).stream()
                    .filter(entity -> entity.getItem().is(ModItems.MOLECULAR_ASSEMBLER_AMMO.get()))
                    .toList();
            helper.assertValueEqual(drops.size(), 1, "Expected exactly one assembler item drop");
            ItemStack drop = drops.getFirst().getItem();
            helper.assertTrue(drop.getItem() instanceof BlockItemDataAccessor accessor && accessor.getBlockId(drop).equals(blockId),
                    "Expected dropped assembler to preserve its TaCZ workbench id");
            helper.succeed();
        });
    }

    @GameTest(template = "empty3x3x3")
    public static void poweredPatternProviderCraftsActualAmmoRecipe(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get();
        placeAssembler(helper, assemblerStack(block, AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));
        NetworkPositions network = placeProviderNetwork(helper);

        CraftCase[] craft = new CraftCase[1];
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    craft[0] = findCraftForWorkbench(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID, helper);
                    var details = addPattern(provider, craft[0]);
                    KeyCounter[] acceptedInputs = copyCounters(craft[0]);
                    helper.assertTrue(provider.getLogic().pushPattern(details, acceptedInputs),
                            "Expected powered provider to push an actual TaCZ ammo recipe into assembler "
                                    + craft[0].recipeId());
                    helper.assertTrue(allEmpty(acceptedInputs), "Expected accepted inputs to be consumed");

                    KeyCounter[] rejectedInputs = copyCounters(craft[0]);
                    helper.assertFalse(provider.getLogic().pushPattern(details, rejectedInputs),
                            "Expected busy assembler to reject a second immediate push");
                    helper.assertFalse(allEmpty(rejectedInputs), "Expected rejected inputs to remain untouched");
                })
                .thenExecuteAfter(5, () -> {
                    AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
                    helper.assertTrue(root.isCrafting(), "Expected craft to remain active before 10 ticks");
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    helper.assertFalse(returnInventoryContains(provider, craft[0].output()),
                            "Expected no output before timed craft completes");
                })
                .thenExecuteAfter(14, () -> {
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    helper.assertTrue(returnInventoryContains(provider, craft[0].output()),
                            "Expected output to be delivered to the original pattern provider return inventory");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 300)
    public static void internalPatternsCompleteActualCpuRequest(GameTestHelper helper) {
        assertInternalPatternsCompleteActualCpuRequest(helper, false);
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 300)
    public static void compactAssemblerAdvertisesAllKindsAndCompletesCpuRequest(GameTestHelper helper) {
        assertInternalPatternsCompleteActualCpuRequest(helper, true);
    }

    private static void assertInternalPatternsCompleteActualCpuRequest(GameTestHelper helper, boolean compact) {
        assertInternalPatternsCompleteActualCpuRequest(helper, compact ? ModBlocks.MOLECULAR_ASSEMBLER.get()
                : ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get(), AETaCZWorkbenchIds.AMMO_WORKBENCH_ID);
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 300)
    public static void compactAmmoCompletesCpuRequestAndRejectsOtherCategories(GameTestHelper helper) {
        assertInternalPatternsCompleteActualCpuRequest(helper, ModBlocks.TACZ_ASSEMBLER_AMMO.get(),
                AETaCZWorkbenchIds.AMMO_WORKBENCH_ID);
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 300)
    public static void compactAttachmentCompletesCpuRequestAndRejectsOtherCategories(GameTestHelper helper) {
        assertInternalPatternsCompleteActualCpuRequest(helper, ModBlocks.TACZ_ASSEMBLER_ATTACHMENT.get(),
                AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID);
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 300)
    public static void compactGunCompletesCpuRequestAndRejectsOtherCategories(GameTestHelper helper) {
        assertInternalPatternsCompleteActualCpuRequest(helper, ModBlocks.TACZ_ASSEMBLER_GUN.get(),
                DefaultAssets.DEFAULT_BLOCK_ID);
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 300)
    public static void extendedAmmoCompletesCpuRequestAndRejectsOtherCategories(GameTestHelper helper) {
        assertInternalPatternsCompleteActualCpuRequest(helper, ModBlocks.EXTENDED_TACZ_ASSEMBLER_AMMO.get(),
                AETaCZWorkbenchIds.AMMO_WORKBENCH_ID);
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 300)
    public static void extendedAttachmentCompletesCpuRequestAndRejectsOtherCategories(GameTestHelper helper) {
        assertInternalPatternsCompleteActualCpuRequest(helper, ModBlocks.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get(),
                AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID);
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 300)
    public static void extendedGunCompletesCpuRequestAndRejectsOtherCategories(GameTestHelper helper) {
        assertInternalPatternsCompleteActualCpuRequest(helper, ModBlocks.EXTENDED_TACZ_ASSEMBLER_GUN.get(),
                DefaultAssets.DEFAULT_BLOCK_ID);
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 300)
    public static void compactUniversalCompletesCpuRequestAndAcceptsAllCategories(GameTestHelper helper) {
        assertInternalPatternsCompleteActualCpuRequest(helper, ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get(),
                AETaCZWorkbenchIds.AMMO_WORKBENCH_ID);
    }

    private static void assertInternalPatternsCompleteActualCpuRequest(GameTestHelper helper, Block block,
            ResourceLocation workbenchId) {
        MolecularAssemblerBlock assemblerBlock = (MolecularAssemblerBlock) block;
        placeAssembler(helper, new ItemStack(block));
        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        BlockPos energyPos = assemblerBlock.partCount() == 1 ? ROOT.above() : ROOT.below();
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(energyPos.east(), AEBlocks.DRIVE.block());
        helper.setBlock(energyPos.west(), AEBlocks.CRAFTING_STORAGE_64K.block());
        DriveBlockEntity drive = helper.getBlockEntity(energyPos.east());
        drive.getInternalInventory().setItemDirect(0, new ItemStack(AEItems.ITEM_CELL_64K.asItem()));
        CraftCase craft = findCraftForWorkbench(workbenchId, helper);
        var source = IActionSource.ofMachine(root);
        AtomicReference<Future<ICraftingPlan>> calculation = new AtomicReference<>();
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    var grid = root.getMainNode().getGrid();
                    helper.assertTrue(grid != null && !grid.getCraftingService().getCpus().isEmpty(),
                            "Expected connected Crafting CPU without a Pattern Provider");
                    root.getPatternInventory().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                            craft.inputs(), List.of(outputStack(craft.output()))));
                    int slot = 1;
                    for (ResourceLocation otherId : List.of(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID,
                            AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID, DefaultAssets.DEFAULT_BLOCK_ID)) {
                        if (!otherId.equals(workbenchId)) {
                            CraftCase other = findCraftForWorkbench(otherId, helper);
                            root.getPatternInventory().setItemDirect(slot++, PatternDetailsHelper.encodeProcessingPattern(
                                    other.inputs(), List.of(outputStack(other.output()))));
                        }
                    }
                    helper.assertValueEqual(root.getAvailablePatterns().size(), assemblerBlock.isUniversal() ? 3 : 1,
                            assemblerBlock.isUniversal() ? "Expected universal assembler to advertise all three categories"
                                    : "Expected only matching internal pattern to be advertised");
                    helper.assertTrue(grid.getCraftingService().isCraftable(AEItemKey.of(craft.output())),
                            "Expected stored pattern to be advertised to the network");
                    for (GenericStack input : craft.inputs()) {
                        helper.assertValueEqual(grid.getStorageService().getInventory().insert(input.what(),
                                        input.amount(), Actionable.MODULATE, source), input.amount(),
                                "Expected recipe inputs in ME storage");
                    }
                    calculation.set(grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                            () -> source, AEItemKey.of(craft.output()), craft.output().getCount(),
                            CalculationStrategy.REPORT_MISSING_ITEMS));
                })
                .thenWaitUntil(() -> helper.assertTrue(calculation.get().isDone(), "Waiting for crafting calculation"))
                .thenExecute(() -> {
                    try {
                        ICraftingPlan plan = calculation.get().get();
                        helper.assertFalse(plan.simulation(), "Expected no missing ingredients");
                        var result = root.getMainNode().getGrid().getCraftingService()
                                .submitJob(plan, null, null, true, source);
                        helper.assertTrue(result.successful(), "Expected CPU submission: " + result.errorCode());
                    } catch (Exception exception) {
                        throw new IllegalStateException("CPU calculation failed", exception);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(root.isCrafting(), "Waiting for CPU to start assembler"))
                .thenExecuteAfter(2, () -> helper.assertTrue(root.isCrafting(),
                        "Expected internal craft to retain its tier-specific duration"))
                .thenWaitUntil(() -> {
                    var grid = root.getMainNode().getGrid();
                    var stored = grid.getStorageService().getInventory().getAvailableStacks();
                    helper.assertValueEqual(stored.get(AEItemKey.of(craft.output())),
                            (long) craft.output().getCount(), "Expected output returned through CPU into storage");
                    helper.assertFalse(grid.getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy()),
                            "Expected completed CPU");
                    helper.assertFalse(root.isCrafting(), "Expected stopped assembler animation");
                    helper.assertTrue(root.getPendingOutputPreview().isEmpty(), "Expected empty output buffer");
                    for (GenericStack input : craft.inputs()) {
                        helper.assertValueEqual(stored.get(input.what()), 0L, "Expected ingredients consumed by CPU");
                    }
                })
                .thenExecute(() -> {
                    root.getPatternInventory().clear();
                    helper.assertFalse(root.getMainNode().getGrid().getCraftingService()
                            .isCraftable(AEItemKey.of(craft.output())), "Expected removed patterns withdrawn from AE2");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty3x3x3")
    public static void assemblerPersistsPatternsLargeOutputAndCraftTime(GameTestHelper helper) {
        assertAssemblerPersistsPatternsLargeOutputAndCraftTime(helper, false);
    }

    @GameTest(template = "empty3x3x3")
    public static void compactAssemblerPersistsPatternsOutputAndCraftTime(GameTestHelper helper) {
        assertAssemblerPersistsPatternsLargeOutputAndCraftTime(helper, true);
    }

    private static void assertAssemblerPersistsPatternsLargeOutputAndCraftTime(GameTestHelper helper, boolean compact) {
        placeAssembler(helper, new ItemStack(compact ? ModItems.MOLECULAR_ASSEMBLER.get()
                : ModItems.MOLECULAR_ASSEMBLER_UNIVERSAL.get()));
        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        CraftCase craft = findCraftForWorkbench(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID, helper);
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(craft.inputs(),
                List.of(outputStack(craft.output())));
        for (int slot = 0; slot < AEMolecularAssemblerBlockEntity.PATTERN_SLOTS; slot++) {
            root.getPatternInventory().setItemDirect(slot, pattern.copy());
        }
        helper.assertTrue(root.getPatternInventory().addItems(new ItemStack(Items.DIRT)).is(Items.DIRT),
                "Expected pattern slots to reject ordinary items");
        for (int count : new int[] { 128, 256 }) {
            CompoundTag tag = root.saveWithFullMetadata(helper.getLevel().registryAccess());
            tag.remove("Jobs");
            tag.put("PendingOutput", new ItemStack(Items.DIAMOND).save(helper.getLevel().registryAccess()));
            tag.putInt("PendingOutputCount", count);
            tag.putInt("CraftTicks", 7);
            root.loadTag(tag, helper.getLevel().registryAccess());
            CompoundTag saved = root.saveWithFullMetadata(helper.getLevel().registryAccess());
            root.getPatternInventory().clear();
            root.loadTag(saved, helper.getLevel().registryAccess());
            helper.assertValueEqual(root.getPendingOutputPreview().getCount(), count,
                    "Expected large pending output to survive save/reload");
            helper.assertValueEqual(root.saveWithFullMetadata(helper.getLevel().registryAccess())
                            .getList("Jobs", Tag.TAG_COMPOUND).getCompound(0).getInt("CraftTicks"),
                    7, "Expected remaining craft time to survive reload");
            for (int slot = 0; slot < AEMolecularAssemblerBlockEntity.PATTERN_SLOTS; slot++) {
                helper.assertTrue(ItemStack.matches(pattern, root.getPatternInventory().getStackInSlot(slot)),
                        "Expected all nine pattern slots to survive reload");
            }
        }
        helper.destroyBlock(ROOT);
        var drops = helper.getEntities(EntityType.ITEM);
        helper.assertValueEqual(drops.stream().filter(item -> item.getItem().is(Items.DIAMOND))
                .mapToInt(item -> item.getItem().getCount()).sum(), 256, "Expected buffered output to drop exactly once");
        helper.assertValueEqual(drops.stream().filter(item -> item.getItem().is(AEItems.PROCESSING_PATTERN.asItem()))
                .mapToInt(item -> item.getItem().getCount()).sum(), 9, "Expected stored patterns to drop exactly once");
        helper.assertValueEqual(drops.stream().filter(item -> item.getItem().is(compact
                                ? ModItems.MOLECULAR_ASSEMBLER.get() : ModItems.MOLECULAR_ASSEMBLER_UNIVERSAL.get()))
                .mapToInt(item -> item.getItem().getCount()).sum(), 1, "Expected exactly one assembler item drop");
        helper.assertTrue(root.collectPendingDrops().isEmpty(), "Expected no duplicate drops after breaking machine");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void additionalWorkbenchUsesUniversalAssembler(GameTestHelper helper) {
        helper.assertValueEqual(WorkbenchUpgradeKind.assemblerBlockFor(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID),
                ModBlocks.TACZ_ASSEMBLER_AMMO.get(), "Expected original ammo table to stay dedicated");
        helper.assertValueEqual(WorkbenchUpgradeKind.assemblerBlockFor(AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID),
                ModBlocks.TACZ_ASSEMBLER_ATTACHMENT.get(), "Expected original attachment table to stay dedicated");
        helper.assertValueEqual(WorkbenchUpgradeKind.assemblerBlockFor(DefaultAssets.DEFAULT_BLOCK_ID),
                ModBlocks.TACZ_ASSEMBLER_GUN.get(), "Expected original gun table to stay dedicated");
        helper.assertValueEqual(WorkbenchUpgradeKind.assemblerBlockFor(ResourceLocation.parse("custom:ammo_workbench")),
                ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get(), "Expected additional gunpack table to use Universal");
        var loadedEntry = AETaCZWorkbenchIndex.entries().getFirst();
        ItemStack additionalAssembler = new AETaCZWorkbenchIndex.Entry(
                ResourceLocation.parse("custom:ammo_workbench"), loadedEntry.baseWorkbench(), loadedEntry.kind())
                .createAssemblerStack();
        helper.assertTrue(additionalAssembler.is(ModItems.TACZ_ASSEMBLER_UNIVERSAL.get()),
                "Expected additional workbench entry to create a Universal assembler item");
        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            helper.assertValueEqual(AETaCZWorkbenchIndex.getDefaultBlockId(kind.assemblerBlock()),
                    kind.defaultBlockId(), "Expected dedicated assembler to keep its original workbench id");
            ItemStack assembler = new ItemStack(kind.assemblerBlock());
            helper.assertTrue(assembler.getItem() instanceof BlockItemDataAccessor accessor
                            && accessor.getBlockId(assembler).equals(kind.defaultBlockId()),
                    "Expected untagged dedicated assembler item to keep its original workbench id");
        }
        for (Block legacy : List.of(ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get(),
                ModBlocks.MOLECULAR_ASSEMBLER_ATTACHMENT.get(), ModBlocks.MOLECULAR_ASSEMBLER_GUN.get())) {
            MolecularAssemblerBlock block = (MolecularAssemblerBlock) legacy;
            helper.assertValueEqual(WorkbenchUpgradeKind.fromAppliedWorkbench(block), block.kind(),
                    "Expected legacy category recognition");
            helper.assertValueEqual(AETaCZWorkbenchIndex.getDefaultBlockId(block), block.kind().defaultBlockId(),
                    "Expected legacy default binding unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void universalPatternProviderCraftsDifferentRecipeKinds(GameTestHelper helper) {
        assertUniversalPatternProviderCraftsDifferentRecipeKinds(helper, ModBlocks.MOLECULAR_ASSEMBLER_UNIVERSAL.get());
    }

    @GameTest(template = "empty3x3x3")
    public static void compactUniversalPatternProviderCraftsAllThreeRecipeKinds(GameTestHelper helper) {
        assertUniversalPatternProviderCraftsDifferentRecipeKinds(helper, ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get());
    }

    private static void assertUniversalPatternProviderCraftsDifferentRecipeKinds(GameTestHelper helper, Block block) {
        placeAssembler(helper, new ItemStack(block));
        NetworkPositions network = placeProviderNetwork(helper);

        CraftCase[] crafts = new CraftCase[3];
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    crafts[0] = findCraftForWorkbench(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID, helper);
                    crafts[1] = findCraftForWorkbench(AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID, helper);
                    crafts[2] = findCraftForWorkbench(DefaultAssets.DEFAULT_BLOCK_ID, helper);
                    pushAcceptedCraft(helper, provider, crafts[0]);
                })
                .thenWaitUntil(() -> {
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    helper.assertTrue(returnInventoryContains(provider, crafts[0].output()),
                            "Expected universal assembler to return ammo recipe output");
                })
                .thenExecute(() -> {
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    pushAcceptedCraft(helper, provider, crafts[1]);
                })
                .thenWaitUntil(() -> {
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    helper.assertTrue(returnInventoryContains(provider, crafts[1].output()),
                            "Expected universal assembler to return attachment recipe output");
                })
                .thenExecute(() -> {
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    pushAcceptedCraft(helper, provider, crafts[2]);
                })
                .thenWaitUntil(() -> {
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    helper.assertTrue(returnInventoryContains(provider, crafts[2].output()),
                            "Expected universal assembler to return gun recipe output");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty3x3x3")
    public static void dedicatedAssemblerRejectsWrongRecipeKind(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_GUN.get();
        placeAssembler(helper, assemblerStack(block, DefaultAssets.DEFAULT_BLOCK_ID));
        NetworkPositions network = placeProviderNetwork(helper);

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    PatternProviderBlockEntity provider = helper.getBlockEntity(network.provider());
                    CraftCase craft = findCraftForWorkbench(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID, helper);
                    IPatternDetails details = addPattern(provider, craft);
                    KeyCounter[] rejectedInputs = copyCounters(craft);
                    helper.assertFalse(provider.getLogic().pushPattern(details, rejectedInputs),
                            "Expected dedicated gun assembler to reject an ammo recipe");
                    helper.assertFalse(allEmpty(rejectedInputs),
                            "Expected wrong-kind rejected inputs to remain untouched");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty3x3x3")
    public static void assemblerMenuRootResolvesFromEveryPart(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_GUN.get();
        placeAssembler(helper, assemblerStack(block, DefaultAssets.DEFAULT_BLOCK_ID));

        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        Direction facing = helper.getBlockState(ROOT).getValue(MolecularAssemblerBlock.FACING);
        for (int part = 0; part < block.partCount(); part++) {
            AEMolecularAssemblerBlockEntity partEntity = helper.getBlockEntity(block.getPartPos(ROOT, facing, part));
            helper.assertTrue(partEntity.getRootAssembler() == root,
                    "Expected assembler part " + part + " to resolve the menu root");
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void assemblerMenuShowsReadOnlyPendingSnapshot(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get();
        placeAssembler(helper, assemblerStack(block, AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));
        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        setPendingOutput(root, new ItemStack(Items.DIAMOND, 3), helper);
        Player player = menuPlayer(helper);

        AEMolecularAssemblerMenu menu = new AEMolecularAssemblerMenu(1, player.getInventory(), root);
        helper.assertTrue(ItemStack.isSameItemSameComponents(menu.getPendingOutput(), new ItemStack(Items.DIAMOND))
                        && menu.getPendingOutput().getCount() == 3,
                "Expected menu to show a copy of the pending output");

        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)),
                "Expected preview slot to reject inserts");
        helper.assertFalse(menu.getSlot(0).mayPickup(player),
                "Expected preview slot to reject pickups");
        helper.assertTrue(menu.getSlot(0).remove(1).isEmpty(),
                "Expected preview slot remove to return empty");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty(),
                "Expected pending output shift-click to return nothing");
        helper.assertFalse(menu.canDragTo(menu.getSlot(0)),
                "Expected pending output slot to reject drag placement");

        ItemStack copy = menu.getPendingOutput();
        copy.setCount(1);
        helper.assertTrue(ItemStack.isSameItemSameComponents(root.getPendingOutputPreview(), new ItemStack(Items.DIAMOND))
                        && root.getPendingOutputPreview().getCount() == 3,
                "Expected preview interaction and copy mutation to leave assembler pending output unchanged");
        CraftCase craft = findCraftForWorkbench(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID, helper);
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(craft.inputs(),
                List.of(outputStack(craft.output())));
        helper.assertValueEqual(menu.slots.size(), 51, "Expected preview, nine patterns, five upgrades and player inventory");
        helper.assertTrue(menu.getSlot(1).mayPlace(pattern), "Expected processing pattern slot");
        helper.assertFalse(menu.getSlot(1).mayPlace(new ItemStack(Items.DIRT)), "Expected no ordinary items in pattern slot");
        player.getInventory().setItem(0, pattern.copy());
        menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).get(0).index);
        helper.assertTrue(ItemStack.matches(root.getPatternInventory().getStackInSlot(0), pattern)
                        && player.getInventory().getItem(0).isEmpty(),
                "Expected shift-click to insert player pattern into assembler");
        menu.quickMoveStack(player, 1);
        helper.assertTrue(root.getPatternInventory().getStackInSlot(0).isEmpty(),
                "Expected shift-click to return stored pattern to player");
        var upgradeSlot = menu.getSlots(SlotSemantics.UPGRADE).get(0);
        helper.assertTrue(upgradeSlot.mayPlace(AEItems.SPEED_CARD.stack()), "Expected acceleration card slot");
        helper.assertFalse(upgradeSlot.mayPlace(AEItems.CAPACITY_CARD.stack()), "Expected unrelated cards rejected");
        player.getInventory().setItem(0, AEItems.SPEED_CARD.stack());
        menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).get(0).index);
        helper.assertTrue(root.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD) == 1
                        && player.getInventory().getItem(0).isEmpty(), "Expected acceleration card shift-click insertion");
        menu.quickMoveStack(player, upgradeSlot.index);
        helper.assertTrue(root.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD) == 0
                        && player.getInventory().items.stream().anyMatch(stack -> stack.is(AEItems.SPEED_CARD.asItem())),
                "Expected acceleration card shift-click extraction");
        helper.assertValueEqual(root.getPendingOutputPreview().getCount(), 3,
                "Expected pattern transfer to leave output preview untouched");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void assemblerMenuInvalidatesWhenStructureBreaks(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get();
        placeAssembler(helper, assemblerStack(block, AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));

        Player player = menuPlayer(helper);
        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        AEMolecularAssemblerMenu menu = new AEMolecularAssemblerMenu(1, player.getInventory(), root);
        helper.assertTrue(menu.stillValid(player), "Expected intact assembler menu to be valid");

        Direction facing = helper.getBlockState(ROOT).getValue(MolecularAssemblerBlock.FACING);
        helper.destroyBlock(block.getPartPos(ROOT, facing, 1));
        menu.broadcastChanges();
        helper.assertFalse(menu.isValidMenu(), "Expected broken assembler structure to invalidate the menu");
        helper.succeed();
    }

    private static void assertPlacesFootprint(GameTestHelper helper, MolecularAssemblerBlock block, ItemStack stack) {
        placeAssembler(helper, stack);
        BlockState rootState = helper.getBlockState(ROOT);
        helper.assertTrue(rootState.is(block), "Expected root block to be placed");
        Direction facing = rootState.getValue(MolecularAssemblerBlock.FACING);

        for (int part = 0; part < block.partCount(); part++) {
            BlockPos partPos = block.getPartPos(ROOT, facing, part);
            BlockState partState = helper.getBlockState(partPos);
            helper.assertTrue(partState.is(block), "Expected assembler part " + part + " to be placed");
            helper.assertValueEqual(partState.getValue(MolecularAssemblerBlock.PART), part,
                    "Expected assembler part property to match");
            helper.assertValueEqual(block.getRootPos(partPos, partState), ROOT,
                    "Expected assembler part to resolve the root");
        }
        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        helper.assertTrue(root.getCraftingMachine(Direction.UP) == null,
                "Expected existing multiblock assembler to keep its upper face closed");
        helper.assertTrue(root.getCableConnectionType(Direction.UP) == AECableType.NONE,
                "Expected existing multiblock assembler to keep its upper cable face closed");
        helper.destroyBlock(ROOT);
    }

    private static void placeAssembler(GameTestHelper helper, ItemStack stack) {
        helper.setBlock(ROOT.below(), Blocks.STONE);
        stack.useOn(new UseOnContext(helper.getLevel(), helper.makeMockPlayer(GameType.SURVIVAL),
                InteractionHand.MAIN_HAND, stack, new BlockHitResult(helper.absoluteVec(Vec3.atCenterOf(ROOT.below())),
                Direction.UP, helper.absolutePos(ROOT.below()), false)));
    }

    private static Player menuPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 playerPos = helper.absoluteVec(Vec3.atCenterOf(ROOT));
        player.setPos(playerPos.x, playerPos.y, playerPos.z);
        return player;
    }

    private static void setPendingOutput(AEMolecularAssemblerBlockEntity assembler, ItemStack stack,
            GameTestHelper helper) {
        CompoundTag tag = assembler.saveWithFullMetadata(helper.getLevel().registryAccess());
        tag.remove("Jobs");
        tag.put("PendingOutput", stack.save(helper.getLevel().registryAccess()));
        assembler.loadTag(tag, helper.getLevel().registryAccess());
    }

    private static NetworkPositions placeProviderNetwork(GameTestHelper helper) {
        BlockState rootState = helper.getBlockState(ROOT);
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) rootState.getBlock();
        Direction facing = rootState.getValue(MolecularAssemblerBlock.FACING);
        for (int part = block.partCount() / 2; part < block.partCount(); part++) {
            BlockPos partPos = block.getPartPos(ROOT, facing, part);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos provider = partPos.relative(side);
                BlockPos energy = provider.below();
                if (isInsideTemplate(provider)
                        && isInsideTemplate(energy)
                        && helper.getBlockState(provider).isAir()
                        && helper.getBlockState(energy).isAir()) {
                    helper.setBlock(provider, AEBlocks.PATTERN_PROVIDER.block());
                    helper.setBlock(energy, AEBlocks.CREATIVE_ENERGY_CELL.block());
                    return new NetworkPositions(provider, energy);
                }
            }
        }
        throw new IllegalStateException("Could not place AE2 provider beside assembler footprint");
    }

    private static boolean isInsideTemplate(BlockPos pos) {
        return pos.getX() >= 0 && pos.getX() < 3
                && pos.getY() >= 0 && pos.getY() < 3
                && pos.getZ() >= 0 && pos.getZ() < 3;
    }

    private static ItemStack assemblerStack(Block block, ResourceLocation blockId) {
        return BlockItemBuilder.create(block).setId(blockId).build();
    }

    private static GunSmithTableRecipe testRecipe(GunSmithTableIngredient... ingredients) {
        return new GunSmithTableRecipe(
                new GunSmithTableResult(new ItemStack(Items.DIAMOND),
                        ResourceLocation.fromNamespaceAndPath("tacz", "misc")),
                List.of(ingredients));
    }

    private static KeyCounter[] counters(Object... itemAndAmountPairs) {
        KeyCounter[] counters = new KeyCounter[itemAndAmountPairs.length / 2];
        for (int i = 0; i < itemAndAmountPairs.length; i += 2) {
            counters[i / 2] = new KeyCounter();
            counters[i / 2].add(AEItemKey.of((net.minecraft.world.item.Item) itemAndAmountPairs[i]),
                    (int) itemAndAmountPairs[i + 1]);
        }
        return counters;
    }

    private static CraftCase findCraftForWorkbench(ResourceLocation workbenchId, GameTestHelper helper) {
        var index = TimelessAPI.getCommonBlockIndex(workbenchId)
                .orElseThrow(() -> new IllegalStateException("Missing TaCZ workbench index " + workbenchId));
        for (RecipeHolder<GunSmithTableRecipe> holder : helper.getLevel().getRecipeManager()
                .getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
            GunSmithTableRecipe recipe = holder.value();
            if ((index.getFilter() != null && !index.getFilter().contains(holder.id()))
                    || index.getData().getTabs().stream().noneMatch(tab -> tab.id().equals(recipe.getTab()))) {
                continue;
            }

            CraftCase craft = createCraftCase(holder.id(), recipe, helper);
            if (craft != null && AssemblerRecipeMatcher.matches(recipe, copyCounters(craft))) {
                return craft;
            }
        }
        throw new IllegalStateException("Could not find a craftable TaCZ recipe for " + workbenchId);
    }

    private static CraftCase createCraftCase(ResourceLocation recipeId, GunSmithTableRecipe recipe,
            GameTestHelper helper) {
        ItemStack output = recipe.getResultItem(helper.getLevel().registryAccess());
        if (output.isEmpty()) {
            return null;
        }

        List<GenericStack> inputs = new ArrayList<>();
        KeyCounter[] counters = new KeyCounter[recipe.getInputs().size()];
        for (int slot = 0; slot < recipe.getInputs().size(); slot++) {
            GunSmithTableIngredient ingredient = recipe.getInputs().get(slot);
            ItemStack[] choices = ingredient.getIngredient().getItems();
            if (ingredient.getCount() <= 0 || choices.length == 0) {
                return null;
            }

            AEItemKey key = AEItemKey.of(choices[0]);
            if (key == null) {
                return null;
            }
            inputs.add(new GenericStack(key, ingredient.getCount()));
            counters[slot] = new KeyCounter();
            counters[slot].add(key, ingredient.getCount());
        }
        return new CraftCase(recipeId, inputs, output.copy(), counters);
    }

    private static GenericStack outputStack(ItemStack output) {
        return new GenericStack(AEItemKey.of(output), output.getCount());
    }

    private static IPatternDetails addPattern(PatternProviderBlockEntity provider, CraftCase craft) {
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(
                craft.inputs(), List.of(outputStack(craft.output())));
        provider.getLogic().getPatternInv().addItems(pattern);
        return provider.getLogic().getAvailablePatterns().stream()
                .filter(details -> AssemblerRecipeMatcher.outputsMatch(craft.output(), details.getOutputs()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Encoded pattern was not available"));
    }

    private static void pushAcceptedCraft(GameTestHelper helper, PatternProviderBlockEntity provider, CraftCase craft) {
        IPatternDetails details = addPattern(provider, craft);
        KeyCounter[] acceptedInputs = copyCounters(craft);
        helper.assertTrue(provider.getLogic().pushPattern(details, acceptedInputs),
                "Expected universal assembler to accept TaCZ recipe " + craft.recipeId());
        helper.assertTrue(allEmpty(acceptedInputs), "Expected universal assembler to consume accepted inputs");
    }

    private static KeyCounter[] copyCounters(CraftCase craft) {
        KeyCounter[] copy = new KeyCounter[craft.counters().length];
        for (int i = 0; i < craft.counters().length; i++) {
            copy[i] = new KeyCounter();
            for (var entry : craft.counters()[i]) {
                copy[i].add(entry.getKey(), entry.getLongValue());
            }
        }
        return copy;
    }

    private static boolean allEmpty(KeyCounter[] counters) {
        for (KeyCounter counter : counters) {
            if (!counter.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean returnInventoryContains(PatternProviderBlockEntity provider, ItemStack output) {
        AEItemKey outputKey = AEItemKey.of(output);
        for (int slot = 0; slot < provider.getLogic().getReturnInv().size(); slot++) {
            GenericStack stack = provider.getLogic().getReturnInv().getStack(slot);
            if (stack != null && stack.what().equals(outputKey) && stack.amount() == output.getCount()) {
                return true;
            }
        }
        return false;
    }

    private record CraftCase(ResourceLocation recipeId, List<GenericStack> inputs, ItemStack output,
            KeyCounter[] counters) {
    }

    private record NetworkPositions(BlockPos provider, BlockPos energy) {
    }
}
