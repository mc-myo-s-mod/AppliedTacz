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
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.manager.CommonDataManager;
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
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
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
    public static void compactAssemblerOccupiesOneBlockAndConnectsOnSixFaces(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.MOLECULAR_ASSEMBLER.get();
        for (Direction side : Direction.values()) {
            helper.setBlock(ROOT.relative(side), Blocks.STONE);
        }
        placeAssembler(helper, new ItemStack(ModItems.MOLECULAR_ASSEMBLER.get()));
        helper.assertBlockPresent(block, ROOT);
        for (Direction side : Direction.values()) {
            helper.assertBlockPresent(Blocks.STONE, ROOT.relative(side));
        }
        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        helper.assertTrue(root.isValidMenuRoot(), "Expected compact assembler to be its own root");
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
            helper.assertValueEqual(menu.slots.size(), 59,
                    "Expected nine return slots, nine patterns, five upgrades and player inventory layout");
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
    public static void compactVariantsPlaceOnlyOneCell(GameTestHelper helper) {
        for (Block registered : List.of(ModBlocks.TACZ_ASSEMBLER_AMMO.get(),
                ModBlocks.TACZ_ASSEMBLER_ATTACHMENT.get(), ModBlocks.TACZ_ASSEMBLER_GUN.get(),
                ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get(), ModBlocks.EXTENDED_TACZ_ASSEMBLER_AMMO.get(),
                ModBlocks.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get(), ModBlocks.EXTENDED_TACZ_ASSEMBLER_GUN.get())) {
            MolecularAssemblerBlock block = (MolecularAssemblerBlock) registered;
            for (Direction side : Direction.values()) {
                helper.setBlock(ROOT.relative(side), Blocks.STONE);
            }
            placeAssembler(helper, new ItemStack(block));
            helper.assertBlockPresent(block, ROOT);
            AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
            helper.assertTrue(root.getType().isValid(helper.getBlockState(ROOT)), "Expected registered block entity support");
            helper.assertTrue(root.isValidMenuRoot(), "Expected single-cell root");
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
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void compactAssemblerRecipesRequireExactWorkbenchAndProvider(GameTestHelper helper) {
        for (var entry : AETaCZWorkbenchIndex.entries()) {
            ItemStack workbench = BlockItemBuilder.create(entry.baseWorkbench()).setId(entry.blockId()).build();
            ItemStack assembler = new ItemStack(AEBlocks.MOLECULAR_ASSEMBLER.asItem());
            ItemStack provider = new ItemStack(AEBlocks.PATTERN_PROVIDER.asItem());
            CraftingInput input = CraftingInput.of(3, 1, List.of(workbench, assembler, provider));
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
    public static void removedLargeAssemblerIdsAreNotRegistered(GameTestHelper helper) {
        for (String path : List.of("molecular_assembler_ammo", "molecular_assembler_attachment",
                "molecular_assembler_gun", "molecular_assembler_universal")) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, path);
            helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(id) && !BuiltInRegistries.ITEM.containsKey(id),
                    "Expected removed large assembler block and item ID to stay absent: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void universalAssemblersHaveNoCraftingRecipes(GameTestHelper helper) {
        TagKey<Item> hidden = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "hidden_from_recipe_viewers"));
        for (Item item : List.of(ModItems.TACZ_ASSEMBLER_UNIVERSAL.get(),
                ModItems.MOLECULAR_ASSEMBLER.get())) {
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
                            || result.is(ModItems.MOLECULAR_ASSEMBLER.get()),
                    "Expected universal assemblers to be command-only, found " + holder.id());
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void universalAssemblerItemRemainsUnbound(GameTestHelper helper) {
        ItemStack universal = new ItemStack(ModItems.TACZ_ASSEMBLER_UNIVERSAL.get());
        helper.assertTrue(universal.getItem() instanceof BlockItemDataAccessor accessor
                        && DefaultAssets.EMPTY_BLOCK_ID.equals(accessor.getBlockId(universal)),
                "Expected universal assembler item to have no bound TaCZ BlockId");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void breakingAssemblerDropsOneAssemblerWithBlockId(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.TACZ_ASSEMBLER_AMMO.get();
        ResourceLocation blockId = AETaCZWorkbenchIds.AMMO_WORKBENCH_ID;
        placeAssembler(helper, assemblerStack(block, blockId));

        helper.destroyBlock(ROOT);
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> drops = helper.getEntities(EntityType.ITEM).stream()
                    .filter(entity -> entity.getItem().is(ModItems.TACZ_ASSEMBLER_AMMO.get()))
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
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.TACZ_ASSEMBLER_AMMO.get();
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
                : ModBlocks.TACZ_ASSEMBLER_AMMO.get(), AETaCZWorkbenchIds.AMMO_WORKBENCH_ID);
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
        BlockPos energyPos = ROOT.above();
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
                : ModItems.TACZ_ASSEMBLER_UNIVERSAL.get()));
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
                                ? ModItems.MOLECULAR_ASSEMBLER.get() : ModItems.TACZ_ASSEMBLER_UNIVERSAL.get()))
                .mapToInt(item -> item.getItem().getCount()).sum(), 1, "Expected exactly one assembler item drop");
        helper.assertTrue(root.collectPendingDrops().isEmpty(), "Expected no duplicate drops after breaking machine");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void supportedWorkbenchStacksKeepTheirIdentity(GameTestHelper helper) {
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
        helper.assertTrue(additionalAssembler.is(loadedEntry.kind().assemblerBlock().asItem())
                        && ((BlockItemDataAccessor) additionalAssembler.getItem()).getBlockId(additionalAssembler)
                                .equals(ResourceLocation.parse("custom:ammo_workbench")),
                "Expected supported workbench entry to keep its assembler category and custom identity");
        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            helper.assertValueEqual(AETaCZWorkbenchIndex.getDefaultBlockId(kind.assemblerBlock()),
                    kind.defaultBlockId(), "Expected dedicated assembler to keep its original workbench id");
            ItemStack assembler = new ItemStack(kind.assemblerBlock());
            helper.assertTrue(assembler.getItem() instanceof BlockItemDataAccessor accessor
                            && accessor.getBlockId(assembler).equals(kind.defaultBlockId()),
                    "Expected untagged dedicated assembler item to keep its original workbench id");
        }
        for (Block registered : List.of(ModBlocks.TACZ_ASSEMBLER_AMMO.get(),
                ModBlocks.TACZ_ASSEMBLER_ATTACHMENT.get(), ModBlocks.TACZ_ASSEMBLER_GUN.get())) {
            MolecularAssemblerBlock block = (MolecularAssemblerBlock) registered;
            helper.assertValueEqual(WorkbenchUpgradeKind.fromAppliedWorkbench(block), block.kind(),
                    "Expected dedicated assembler category recognition");
            helper.assertValueEqual(AETaCZWorkbenchIndex.getDefaultBlockId(block), block.kind().defaultBlockId(),
                    "Expected dedicated assembler default binding unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void addonWorkbenchesProduceBoundBasicAndExtendedAssemblers(GameTestHelper helper)
            throws ReflectiveOperationException {
        var field = CommonAssetsManager.class.getDeclaredField("blockIndex");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var index = (CommonDataManager<CommonBlockIndex>) field.get(CommonAssetsManager.get());
        Map<ResourceLocation, CommonBlockIndex> blocks = index.getAllData();
        var recipeManager = helper.getLevel().getRecipeManager();
        var originalRecipes = new ArrayList<>(recipeManager.getRecipes());
        var fixtureIds = new ArrayList<ResourceLocation>();
        try {
            for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("appliedtacz_gametest",
                        "addon_" + kind.defaultBlockId().getPath());
                var source = TimelessAPI.getCommonBlockIndex(kind.defaultBlockId()).orElseThrow();
                String basePath = switch (kind) {
                    case GUN_SMITH_TABLE -> "workbench_b";
                    case AMMO_WORKBENCH -> "workbench_a";
                    case ATTACHMENT_WORKBENCH -> "workbench_c";
                };
                var base = (net.minecraft.world.item.BlockItem) BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("tacz", basePath));
                blocks.put(id, new CommonBlockIndex() {
                    @Override
                    public net.minecraft.world.item.BlockItem getBlock() {
                        return base;
                    }

                    @Override
                    public com.tacz.guns.resource.pojo.data.block.BlockData getData() {
                        return source.getData();
                    }

                    @Override
                    public com.tacz.guns.resource.filter.RecipeFilter getFilter() {
                        return source.getFilter();
                    }
                });
                fixtureIds.add(id);
            }
            me.myogoo.appliedtacz.event.WorkbenchUpgradeRecipeEvents.onServerStarted(
                    new net.neoforged.neoforge.event.server.ServerStartedEvent(helper.getLevel().getServer()));
            for (ResourceLocation id : fixtureIds) {
                var entry = AETaCZWorkbenchIndex.entries().stream()
                        .filter(candidate -> candidate.blockId().equals(id)).findFirst().orElseThrow();
                var kind = entry.kind();
                helper.assertTrue(WorkbenchUpgradeKind.forAssembler(id) == kind
                                && WorkbenchUpgradeKind.assemblerBlockFor(id) == kind.assemblerBlock(),
                        "Expected dynamically indexed workbench to use its supported assembler category");
                ItemStack extended = entry.createExtendedAssemblerStack();
                helper.assertTrue(extended.is(kind.extendedAssemblerBlock().asItem())
                                && ((BlockItemDataAccessor) extended.getItem()).getBlockId(extended).equals(id),
                        "Expected creative Extended stack to retain addon workbench identity");
                var grid = new ArrayList<ItemStack>();
                grid.add(BlockItemBuilder.create(entry.baseWorkbench()).setId(entry.blockId()).build());
                helper.assertTrue(((BlockItemDataAccessor) grid.get(0).getItem()).getBlockId(grid.get(0)).equals(id),
                        "Expected addon fixture to use a mutable TaCZ workbench item");
                grid.add(new ItemStack(AEBlocks.MOLECULAR_ASSEMBLER.asItem()));
                grid.add(new ItemStack(AEBlocks.PATTERN_PROVIDER.asItem()));
                var input = CraftingInput.of(3, 1, grid);
                var recipe = recipeManager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow().value();
                helper.assertTrue(ItemStack.matches(recipe.assemble(input, helper.getLevel().registryAccess()),
                                entry.createAssemblerStack()), "Expected addon assembler recipe to retain its exact workbench ID");
                grid = new ArrayList<>();
                for (int slot = 0; slot < 9; slot++) {
                    grid.add(slot == 4 ? AEItems.CALCULATION_PROCESSOR.stack() : entry.createAssemblerStack());
                }
                input = CraftingInput.of(3, 3, grid);
                var extendedRecipe = recipeManager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                        .orElseThrow().value();
                helper.assertTrue(ItemStack.matches(extendedRecipe.assemble(input, helper.getLevel().registryAccess()),
                                extended), "Expected eight bound basic assemblers to produce the same bound Extended variant");
                grid.set(0, new ItemStack(kind.assemblerBlock()));
                input = CraftingInput.of(3, 3, grid);
                helper.assertTrue(recipeManager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isEmpty(),
                        "Expected mixed workbench IDs to reject every recipe, including old item-only Extended recipes");
            }
        } finally {
            fixtureIds.forEach(blocks::remove);
            recipeManager.replaceRecipes(originalRecipes);
        }
        helper.succeed();
    }


    @GameTest(template = "empty3x3x3")
    public static void universalPatternProviderCraftsDifferentRecipeKinds(GameTestHelper helper) {
        assertUniversalPatternProviderCraftsDifferentRecipeKinds(helper, ModBlocks.MOLECULAR_ASSEMBLER.get());
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
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.TACZ_ASSEMBLER_GUN.get();
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
    public static void assemblerMenuHasNineLiveReturnSlots(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.TACZ_ASSEMBLER_AMMO.get();
        placeAssembler(helper, assemblerStack(block, AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));
        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        setPendingOutput(root, new ItemStack(Items.DIAMOND, 3), helper);
        Player player = menuPlayer(helper);

        AEMolecularAssemblerMenu menu = new AEMolecularAssemblerMenu(1, player.getInventory(), root);
        var returnSlots = menu.getSlots(SlotSemantics.STORAGE);
        helper.assertTrue(returnSlots.size() == 9, "Expected exactly nine live return slots");
        helper.assertTrue(returnSlots.stream().allMatch(slot -> slot.mayPlace(new ItemStack(Items.DIRT))),
                "Expected return slots to accept player inputs like a Pattern Provider");
        menu.setCarried(new ItemStack(Items.DIRT, 17));
        menu.clicked(returnSlots.get(0).index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && returnSlots.get(0).getItem().getCount() == 17,
                "Expected normal click to move every carried item into the offline return buffer");
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 23));
        menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).get(0).index);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty()
                        && returnSlots.get(0).getItem().is(Items.DIRT)
                        && returnSlots.get(0).getItem().getCount() == 40
                        && root.getPatternInventory().isEmpty(),
                "Expected ordinary shift-click to merge into the return buffer without item loss");
        menu.clicked(returnSlots.get(0).index, 0, ClickType.PICKUP, player);
        helper.assertTrue(root.getReturnInventory().isEmpty() && menu.getCarried().is(Items.DIRT)
                        && menu.getCarried().getCount() == 40,
                "Expected buffered player items to remain fully recoverable while offline");
        menu.setCarried(ItemStack.EMPTY);
        root.getReturnInventory().setStack(8, new GenericStack(AEItemKey.of(Items.EMERALD), 3));
        helper.assertTrue(returnSlots.get(8).getItem().is(Items.EMERALD)
                && returnSlots.get(8).getItem().getCount() == 3, "Expected ninth slot to reflect real buffer");
        menu.quickMoveStack(player, returnSlots.get(8).index);
        helper.assertTrue(root.getReturnInventory().isEmpty()
                && player.getInventory().items.stream().anyMatch(stack -> stack.is(Items.EMERALD)
                        && stack.getCount() == 3), "Expected return stack to shift-click into player inventory");
        CraftCase craft = findCraftForWorkbench(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID, helper);
        ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(craft.inputs(),
                List.of(outputStack(craft.output())));
        helper.assertValueEqual(menu.slots.size(), 59, "Expected nine return slots, nine patterns, five upgrades and player inventory");
        helper.assertTrue(menu.getSlot(0).mayPlace(pattern), "Expected processing pattern slot");
        helper.assertFalse(menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)), "Expected no ordinary items in pattern slot");
        player.getInventory().setItem(0, pattern.copy());
        menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).get(0).index);
        helper.assertTrue(ItemStack.matches(root.getPatternInventory().getStackInSlot(0), pattern)
                        && player.getInventory().getItem(0).isEmpty(),
                "Expected shift-click to insert player pattern into assembler");
        menu.quickMoveStack(player, 0);
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
                "Expected pattern transfer to leave unfinished craft untouched");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 160)
    public static void manuallyInsertedReturnsRetryAndWakeOnActiveNetwork(GameTestHelper helper) {
        helper.setBlock(ROOT, ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get());
        AEMolecularAssemblerBlockEntity assembler = helper.getBlockEntity(ROOT);
        var player = menuPlayer(helper);
        var menu = new AEMolecularAssemblerMenu(22, player.getInventory(), assembler);
        var slot = menu.getSlots(SlotSemantics.STORAGE).get(0);
        menu.setCarried(new ItemStack(Items.COPPER_INGOT, 17));
        menu.clicked(slot.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && slot.getItem().getCount() == 17,
                "Expected player insertion into the offline return buffer");
        var energyPos = ROOT.above();
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(assembler.getMainNode().isActive() && slot.getItem().getCount() == 17,
                            "Expected an active network without storage to retain every buffered item");
                    helper.setBlock(energyPos.east(), AEBlocks.DRIVE.block());
                    DriveBlockEntity drive = helper.getBlockEntity(energyPos.east());
                    drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_4K.stack());
                })
                .thenWaitUntil(() -> {
                    var stored = assembler.getMainNode().getGrid().getStorageService().getInventory()
                            .getAvailableStacks().get(AEItemKey.of(Items.COPPER_INGOT));
                    helper.assertTrue(stored == 17 && assembler.getReturnInventory().isEmpty(),
                            "Expected a waiting return buffer to retry when ME storage becomes available");
                })
                .thenExecuteAfter(10, () -> {
                    player.getInventory().setItem(0, new ItemStack(Items.COPPER_INGOT, 23));
                    menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).get(0).index);
                    helper.assertTrue(player.getInventory().getItem(0).isEmpty(),
                            "Expected ordinary shift-click into an idle active assembler");
                })
                .thenWaitUntil(() -> {
                    var stored = assembler.getMainNode().getGrid().getStorageService().getInventory()
                            .getAvailableStacks().get(AEItemKey.of(Items.COPPER_INGOT));
                    helper.assertTrue(stored == 40 && assembler.getReturnInventory().isEmpty()
                                    && assembler.getPatternInventory().isEmpty() && !assembler.isCrafting(),
                            "Expected manual insertion to wake network return ticking without crafting or item loss");
                })
                .thenSucceed();
    }

    @GameTest(template = "empty3x3x3")
    public static void assemblerMenuInvalidatesWhenBlockBreaks(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) ModBlocks.TACZ_ASSEMBLER_AMMO.get();
        placeAssembler(helper, assemblerStack(block, AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));

        Player player = menuPlayer(helper);
        AEMolecularAssemblerBlockEntity root = helper.getBlockEntity(ROOT);
        AEMolecularAssemblerMenu menu = new AEMolecularAssemblerMenu(1, player.getInventory(), root);
        helper.assertTrue(menu.stillValid(player), "Expected intact assembler menu to be valid");

        helper.destroyBlock(ROOT);
        menu.broadcastChanges();
        helper.assertFalse(menu.isValidMenu(), "Expected removed assembler to invalidate the menu");
        helper.succeed();
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
        BlockPos provider = ROOT.east();
        BlockPos energy = provider.below();
        helper.setBlock(provider, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(energy, AEBlocks.CREATIVE_ENERGY_CELL.block());
        return new NetworkPositions(provider, energy);
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
