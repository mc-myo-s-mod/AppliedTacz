package me.myogoo.appliedtacz.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import appeng.menu.AutoCraftingMenu;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.manager.CommonDataManager;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
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
import me.myogoo.appliedtacz.init.AETaCZBlock;
import me.myogoo.appliedtacz.init.AETaczMenu;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

public final class AppliedTaczGameTests {
    private static final BlockPos ROOT = new BlockPos(1, 1, 1);
    private static final BlockPos PROVIDER = new BlockPos(1, 2, 1);
    private static final BlockPos ENERGY = new BlockPos(0, 1, 1);
    private static final Direction[] HORIZONTAL_FACINGS = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private AppliedTaczGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(AppliedTaczGameTests.class);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void officialWorkbenchMenusKeepCustomTypeAndRecipeSelection(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        for (Block block : List.of(AETaCZBlock.GUN_SMITH_TABLE.get(), AETaCZBlock.AMMO_ASSEMBLY_TABLE.get(),
                AETaCZBlock.ATTACHMENT_TABLE.get())) {
            helper.setBlock(ROOT, block);
            var workbench = (AEGunSmithTableBlockEntity) helper.getBlockEntity(ROOT);
            ResourceLocation blockId = AETaCZWorkbenchIndex.getDefaultBlockId(block);
            workbench.setId(blockId);
            var menu = new AEGunSmithTableMenu(0, player.getInventory(), workbench);
            helper.assertTrue(menu.getType() == AETaczMenu.AE_GUN_SMITH_TABLE.get()
                            && blockId.equals(menu.getBlockId()),
                    "Expected the official TaCZ constructor to retain the AppliedTaCZ menu type and workbench id");
            var recipe = helper.getLevel().getRecipeManager()
                    .getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()).stream()
                    .filter(candidate -> menu.canSelectRecipe(candidate.getId(), helper.getLevel().getRecipeManager()))
                    .findFirst().orElseThrow();
            menu.requestRecipeSelection(recipe.getId());
            helper.assertTrue(recipe.getId().equals(menu.consumeRecipeSelection())
                            && menu.consumeRecipeSelection() == null,
                    "Expected official recipe selection to be consumed exactly once");
            helper.assertTrue(!menu.canSelectRecipe(ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID,
                            "missing_recipe"), helper.getLevel().getRecipeManager()),
                    "Expected invalid recipe ids to remain rejected by the official selection API");
            var clientMenu = new AEGunSmithTableMenu(0, player.getInventory(), null, blockId);
            helper.assertTrue(clientMenu.getType() == AETaczMenu.AE_GUN_SMITH_TABLE.get()
                            && blockId.equals(clientMenu.getBlockId()),
                    "Expected the client constructor to preserve the workbench id without its block entity");
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void assemblerMatcherHandlesOverlappingIngredients(GameTestHelper helper) {
        GunSmithTableRecipe recipe = testRecipe(
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1),
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 1)
        );
        KeyCounter[] inputs = counters(Items.IRON_INGOT, 1, Items.GOLD_INGOT, 1);

        if (!AssemblerRecipeMatcher.matches(recipe, inputs)) {
            helper.fail("Expected overlapping ingredients to allocate", BlockPos.ZERO);
        }
        AssemblerRecipeMatcher.consume(inputs);
        if (!inputs[0].isEmpty() || !inputs[1].isEmpty()) {
            helper.fail("Expected matcher consume to empty accepted inputs", BlockPos.ZERO);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void assemblerMatcherRejectsMissingExtraAndWrongOutput(GameTestHelper helper) {
        GunSmithTableRecipe recipe = testRecipe(
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT, Items.GOLD_INGOT), 1),
                new GunSmithTableIngredient(Ingredient.of(Items.IRON_INGOT), 1)
        );
        ItemStack namedOutput = new ItemStack(Items.DIAMOND);
        namedOutput.setHoverName(Component.literal("exact"));

        if (AssemblerRecipeMatcher.matches(recipe, counters(Items.GOLD_INGOT, 1))) {
            helper.fail("Expected missing input to fail", BlockPos.ZERO);
        }
        if (AssemblerRecipeMatcher.matches(recipe,
                counters(Items.IRON_INGOT, 1, Items.GOLD_INGOT, 1, Items.COPPER_INGOT, 1))) {
            helper.fail("Expected extra input to fail", BlockPos.ZERO);
        }
        if (AssemblerRecipeMatcher.outputsMatch(namedOutput,
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)))) {
            helper.fail("Expected missing output tag to fail", BlockPos.ZERO);
        }
        if (AssemblerRecipeMatcher.outputsMatch(new ItemStack(Items.DIAMOND, 2),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)))) {
            helper.fail("Expected wrong output count to fail", BlockPos.ZERO);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void supportedWorkbenchStacksKeepTheirIdentity(GameTestHelper helper) {
        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            if (WorkbenchUpgradeKind.forAssembler(kind.defaultBlockId()) != kind
                    || WorkbenchUpgradeKind.assemblerBlockFor(kind.defaultBlockId()) != kind.assemblerBlock()
                    || !AETaCZWorkbenchIndex.getDefaultBlockId(kind.assemblerBlock()).equals(kind.defaultBlockId())
                    || !((BlockItemDataAccessor) kind.assemblerBlock().asItem())
                            .getBlockId(new ItemStack(kind.assemblerBlock())).equals(kind.defaultBlockId())) {
                helper.fail("Expected original TaCZ workbench to keep its dedicated assembler", ROOT);
            }
            TransientCraftingContainer ingredients = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
            AETaCZWorkbenchIndex.Entry entry = AETaCZWorkbenchIndex.entries().stream()
                    .filter(candidate -> candidate.blockId().equals(kind.defaultBlockId())).findFirst().orElseThrow();
            ingredients.setItem(0, BlockItemBuilder.create(entry.baseWorkbench()).setId(entry.blockId()).build());
            ingredients.setItem(1, new ItemStack(AEBlocks.MOLECULAR_ASSEMBLER.block()));
            ingredients.setItem(2, new ItemStack(AEBlocks.PATTERN_PROVIDER.block()));
            CraftingRecipe recipe = helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, ingredients, helper.getLevel()).orElseThrow();
            if (!recipe.assemble(ingredients, helper.getLevel().registryAccess()).is(kind.assemblerResult().asItem())) {
                helper.fail("Expected dynamic upgrade recipe to produce the compact dedicated assembler", ROOT);
            }
        }
        ResourceLocation customId = ResourceLocation.fromNamespaceAndPath("custom", "ammo_workbench");
        if (WorkbenchUpgradeKind.forAssembler(customId) != null
                || WorkbenchUpgradeKind.assemblerBlockFor(customId) != AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get()) {
            helper.fail("Expected non-original workbench ID to route to the universal assembler", ROOT);
        }
        AETaCZWorkbenchIndex.Entry loaded = AETaCZWorkbenchIndex.entries().get(0);
        ItemStack customAssembler = new AETaCZWorkbenchIndex.Entry(customId, loaded.baseWorkbench(), loaded.kind())
                .createAssemblerStack();
        if (!customAssembler.is(loaded.kind().assemblerBlock().asItem())
                || !((BlockItemDataAccessor) customAssembler.getItem()).getBlockId(customAssembler).equals(customId)) {
            helper.fail("Expected supported workbench entry to keep its assembler category and custom identity", ROOT);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
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
                    new net.minecraftforge.event.server.ServerStartedEvent(helper.getLevel().getServer()));
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
                var input = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
                input.setItem(0, BlockItemBuilder.create(entry.baseWorkbench()).setId(entry.blockId()).build());
                helper.assertTrue(((BlockItemDataAccessor) input.getItem(0).getItem()).getBlockId(input.getItem(0))
                                .equals(id), "Expected addon fixture to use a mutable TaCZ workbench item");
                input.setItem(1, new ItemStack(AEBlocks.MOLECULAR_ASSEMBLER.asItem()));
                input.setItem(2, new ItemStack(AEBlocks.PATTERN_PROVIDER.asItem()));
                var recipe = recipeManager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
                helper.assertTrue(ItemStack.matches(recipe.assemble(input, helper.getLevel().registryAccess()),
                                entry.createAssemblerStack()), "Expected addon assembler recipe to retain its exact workbench ID");
                for (int slot = 0; slot < 9; slot++) {
                    input.setItem(slot, slot == 4 ? AEItems.CALCULATION_PROCESSOR.stack() : entry.createAssemblerStack());
                }
                var extendedRecipe = recipeManager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                        .orElseThrow();
                helper.assertTrue(ItemStack.matches(extendedRecipe.assemble(input, helper.getLevel().registryAccess()),
                                extended), "Expected eight bound basic assemblers to produce the same bound Extended variant");
                input.setItem(0, new ItemStack(kind.assemblerBlock()));
                helper.assertTrue(recipeManager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isEmpty(),
                        "Expected mixed workbench IDs to reject every recipe, including old item-only Extended recipes");
            }
        } finally {
            fixtureIds.forEach(blocks::remove);
            recipeManager.replaceRecipes(originalRecipes);
        }
        helper.succeed();
    }


    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void compactAssemblerRecipesRequireExactWorkbenchAndProvider(GameTestHelper helper) {
        for (var entry : AETaCZWorkbenchIndex.entries()) {
            TransientCraftingContainer input = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
            input.setItem(0, BlockItemBuilder.create(entry.baseWorkbench()).setId(entry.blockId()).build());
            input.setItem(1, new ItemStack(AEBlocks.MOLECULAR_ASSEMBLER.block()));
            input.setItem(2, new ItemStack(AEBlocks.PATTERN_PROVIDER.block()));
            CraftingRecipe recipe = helper.getLevel().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
            if (!recipe.getId().getPath().startsWith("assembler_upgrade/")
                    || recipe.getIngredients().size() != 3
                    || !ItemStack.matches(result, entry.createAssemblerStack()) || result.getCount() != 1) {
                helper.fail("Expected one compact assembler from the exact three-part recipe for " + entry.blockId(), ROOT);
            }
            input.setItem(2, ItemStack.EMPTY);
            if (recipe.matches(input, helper.getLevel())) {
                helper.fail("Expected missing pattern provider to reject crafting", ROOT);
            }
            input.setItem(0, entry.createAppliedStack());
            if (recipe.matches(input, helper.getLevel())) {
                helper.fail("Expected old AppliedTaCZ workbench and assembler recipe to be rejected", ROOT);
            }
            input.setItem(2, new ItemStack(AEBlocks.PATTERN_PROVIDER.block()));
            if (recipe.matches(input, helper.getLevel())) {
                helper.fail("Expected AppliedTaCZ workbench to remain invalid even with a provider", ROOT);
            }
            ResourceLocation wrongId = ResourceLocation.fromNamespaceAndPath("custom", "recipe_identity_probe");
            ItemStack wrongWorkbench = BlockItemBuilder.create(entry.baseWorkbench()).setId(wrongId).build();
            if (((BlockItemDataAccessor) wrongWorkbench.getItem()).getBlockId(wrongWorkbench).equals(wrongId)) {
                input.setItem(0, wrongWorkbench);
                if (recipe.matches(input, helper.getLevel())) {
                    helper.fail("Expected another TaCZ BlockId to reject the dedicated recipe", ROOT);
                }
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void removedLargeAssemblerIdsAreNotRegistered(GameTestHelper helper) {
        for (String path : List.of("molecular_assembler_ammo", "molecular_assembler_attachment",
                "molecular_assembler_gun", "molecular_assembler_universal")) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, path);
            helper.assertTrue(!BuiltInRegistries.BLOCK.containsKey(id) && !BuiltInRegistries.ITEM.containsKey(id),
                    "Expected removed large assembler block and item ID to stay absent: " + id);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void universalAssemblersHaveNoCraftingRecipes(GameTestHelper helper) {
        TagKey<Item> hidden = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", "hidden_from_recipe_viewers"));
        for (Block block : List.of(AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get(),
                AETaCZBlock.MOLECULAR_ASSEMBLER.get())) {
            if (!new ItemStack(block).is(hidden)) {
                helper.fail("Expected every universal assembler in the loaded recipe-viewer hidden tag", ROOT);
            }
        }
        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            if (new ItemStack(kind.assemblerResult()).is(hidden)) {
                helper.fail("Expected dedicated compact assemblers to remain visible in recipe viewers", ROOT);
            }
        }
        for (var recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess());
            if (result.is(AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get().asItem())
                    || result.is(AETaCZBlock.MOLECULAR_ASSEMBLER.get().asItem())) {
                helper.fail("Expected universal assemblers to be command-only, found " + recipe.getId(), ROOT);
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void placingAssemblerItemsPreservesAllFacings(GameTestHelper helper) {
        for (MolecularAssemblerBlock block : List.of(AETaCZBlock.MOLECULAR_ASSEMBLER.get(),
                AETaCZBlock.TACZ_ASSEMBLER_AMMO.get(), AETaCZBlock.TACZ_ASSEMBLER_ATTACHMENT.get(),
                AETaCZBlock.TACZ_ASSEMBLER_GUN.get(), AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get(),
                AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_AMMO.get(),
                AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get(),
                AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_GUN.get())) {
            assertPlacesSingleBlock(helper, block, new ItemStack(block));
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void compactAssemblerHasSixFacesAndIndependentDrops(GameTestHelper helper) {
        MolecularAssemblerBlock block = AETaCZBlock.MOLECULAR_ASSEMBLER.get();
        if (!block.defaultBlockState().is(BlockTags.create(new ResourceLocation("tacz", "interact_key/whitelist")))) {
            helper.fail("Expected compact assembler to support the TaCZ interact key", ROOT);
        }
        helper.setBlock(ROOT.above(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        placeAssembler(helper, new ItemStack(block));
        BlockPos neighborPos = ROOT.east();
        helper.setBlock(neighborPos, block.defaultBlockState());
        AEMolecularAssemblerBlockEntity assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
        AEMolecularAssemblerBlockEntity neighbor =
                (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(neighborPos);
        if (!helper.getBlockState(ROOT.above()).is(AEBlocks.CREATIVE_ENERGY_CELL.block())
                || assembler.getMenuRootAssembler() != assembler || neighbor.getMenuRootAssembler() != neighbor) {
            helper.fail("Expected neighboring compact assemblers to occupy independent single blocks", ROOT);
        }
        ItemStack pattern = encodeCraft(findCraft(helper, WorkbenchUpgradeKind.AMMO_WORKBENCH));
        assembler.getPatternInventory().setItemDirect(0, pattern);
        setPendingOutput(assembler, new ItemStack(Items.DIAMOND, 3));
        AEMolecularAssemblerBlockEntity restored = new AEMolecularAssemblerBlockEntity(
                helper.absolutePos(ROOT), assembler.getBlockState());
        restored.loadTag(assembler.saveWithFullMetadata());
        if (!ItemStack.matches(restored.getPatternInventory().getStackInSlot(0), pattern)
                || restored.getPendingOutput().getCount() != 3) {
            helper.fail("Expected compact assembler patterns and pending output to survive NBT roundtrip", ROOT);
        }
        helper.runAfterDelay(20, () -> {
            if (!assembler.getMainNode().isActive() || assembler.getGridNode() == neighbor.getGridNode()
                    || assembler.getGridConnectableSides(BlockOrientation.NORTH_UP).size() != 6) {
                helper.fail("Expected a compact assembler powered from above with its own grid node", ROOT);
            }
            for (Direction side : Direction.values()) {
                if (assembler.getGridNode(side) != assembler.getGridNode()
                        || !assembler.getCapability(Capabilities.IN_WORLD_GRID_NODE_HOST, side).isPresent()
                        || !assembler.getCapability(Capabilities.CRAFTING_MACHINE, side).isPresent()) {
                    helper.fail("Expected grid and crafting capabilities on compact assembler face " + side, ROOT);
                }
            }
            helper.destroyBlock(ROOT);
            if (helper.getBlockEntity(neighborPos) != neighbor || !helper.getBlockState(neighborPos).is(block)) {
                helper.fail("Expected breaking one compact assembler to preserve its neighbor", neighborPos);
            }
            helper.runAfterDelay(2, () -> {
                List<ItemStack> drops = helper.getEntities(EntityType.ITEM, ROOT, 2.0).stream()
                        .map(ItemEntity::getItem).toList();
                if (drops.stream().filter(stack -> stack.is(block.asItem())).mapToInt(ItemStack::getCount).sum() != 1
                        || drops.stream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() != 3
                        || drops.stream().filter(stack -> ItemStack.matches(stack, pattern)).count() != 1
                        || !assembler.collectPendingDrops().isEmpty()) {
                    helper.fail("Expected exactly one compact machine, its pattern and pending output to drop", ROOT);
                }
                helper.succeed();
            });
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void compactAssemblerPlacementPreservesOccupiedNeighbors(GameTestHelper helper) {
        helper.setBlock(ROOT.above(), Blocks.STONE);
        helper.setBlock(ROOT.east(), Blocks.STONE);
        placeAssembler(helper, assemblerStack(AETaCZBlock.TACZ_ASSEMBLER_AMMO.get(),
                WorkbenchUpgradeKind.AMMO_WORKBENCH.defaultBlockId()));
        if (!helper.getBlockState(ROOT).is(AETaCZBlock.TACZ_ASSEMBLER_AMMO.get())
                || !helper.getBlockState(ROOT.above()).is(Blocks.STONE)
                || !helper.getBlockState(ROOT.east()).is(Blocks.STONE)) {
            helper.fail("Expected single-block placement to preserve occupied neighbors", ROOT);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void breakingAssemblerDropsOneMachineWithBlockId(GameTestHelper helper) {
        MolecularAssemblerBlock block = AETaCZBlock.TACZ_ASSEMBLER_AMMO.get();
        ResourceLocation blockId = WorkbenchUpgradeKind.AMMO_WORKBENCH.defaultBlockId();
        placeAssembler(helper, assemblerStack(block, blockId));
        helper.destroyBlock(ROOT);
        helper.runAfterDelay(2, () -> {
            List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, ROOT, 4.0).stream()
                    .filter(entity -> entity.getItem().is(block.asItem())).toList();
            if (drops.size() != 1 || drops.get(0).getItem().getCount() != 1) {
                helper.fail("Expected exactly one assembler item drop", ROOT);
            }
            ItemStack drop = drops.get(0).getItem();
            if (!(drop.getItem() instanceof BlockItemDataAccessor accessor)
                    || !accessor.getBlockId(drop).equals(blockId)) {
                helper.fail("Expected dropped assembler to preserve its TaCZ workbench id", ROOT);
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void poweredPatternProviderCraftsActualAmmoRecipe(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) AETaCZBlock.TACZ_ASSEMBLER_AMMO.get();
        placeAssemblerFacing(helper, block, assemblerStack(block, WorkbenchUpgradeKind.AMMO_WORKBENCH.defaultBlockId()),
                Direction.NORTH);
        helper.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());

        CraftCase[] craft = new CraftCase[1];
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROVIDER);
                    craft[0] = findCraft(helper, WorkbenchUpgradeKind.AMMO_WORKBENCH);

                    CraftCase rejectedCraft = findCraft(helper, WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH);
                    KeyCounter[] mismatchedInputs = copyCounters(rejectedCraft);
                    if (pushCraft(provider, rejectedCraft, mismatchedInputs)) {
                        helper.fail("Expected ammo assembler to reject attachment recipe "
                                + rejectedCraft.recipeId(), PROVIDER);
                    }
                    if (allEmpty(mismatchedInputs)) {
                        helper.fail("Expected dedicated assembler rejection to preserve inputs", PROVIDER);
                    }

                    KeyCounter[] acceptedInputs = copyCounters(craft[0]);
                    if (!pushCraft(provider, craft[0], acceptedInputs)) {
                        helper.fail("Expected powered provider to push an actual TaCZ ammo recipe into assembler "
                                + craft[0].recipeId(), PROVIDER);
                    }
                    if (!allEmpty(acceptedInputs)) {
                        helper.fail("Expected accepted inputs to be consumed", PROVIDER);
                    }
                    if (!((AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT)).isCrafting()) {
                        helper.fail("Expected accepted craft to begin its timed operation", ROOT);
                    }

                    KeyCounter[] rejectedInputs = copyCounters(craft[0]);
                    if (pushCraft(provider, craft[0], rejectedInputs)) {
                        helper.fail("Expected busy assembler to reject a second immediate push", PROVIDER);
                    }
                    if (allEmpty(rejectedInputs)) {
                        helper.fail("Expected rejected inputs to remain untouched", PROVIDER);
                    }
                })
                .thenExecuteAfter(3, () -> {
                    AEMolecularAssemblerBlockEntity assembler =
                            (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
                    if (!assembler.isCrafting() || returnInventoryContains(
                            (PatternProviderBlockEntity) helper.getBlockEntity(PROVIDER), craft[0].output())) {
                        helper.fail("Expected crafting to take 10 active ticks before returning output", ROOT);
                    }
                })
                .thenExecuteAfter(14, () -> {
                    PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROVIDER);
                    if (!returnInventoryContains(provider, craft[0].output())) {
                        helper.fail("Expected output to be delivered to the original pattern provider return inventory",
                                PROVIDER);
                    }
                })
                .thenSucceed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3", timeoutTicks = 300)
    public static void compactPatternsCompleteActualCpuRequest(GameTestHelper helper) {
        runInternalCpuRequest(helper, AETaCZBlock.MOLECULAR_ASSEMBLER.get());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3", timeoutTicks = 300)
    public static void compactAmmoCompletesCpuRequest(GameTestHelper helper) {
        runInternalCpuRequest(helper, AETaCZBlock.TACZ_ASSEMBLER_AMMO.get());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3", timeoutTicks = 300)
    public static void compactAttachmentCompletesCpuRequest(GameTestHelper helper) {
        runInternalCpuRequest(helper, AETaCZBlock.TACZ_ASSEMBLER_ATTACHMENT.get());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3", timeoutTicks = 300)
    public static void compactGunCompletesCpuRequest(GameTestHelper helper) {
        runInternalCpuRequest(helper, AETaCZBlock.TACZ_ASSEMBLER_GUN.get());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3", timeoutTicks = 300)
    public static void compactUniversalCompletesAmmoCpuRequest(GameTestHelper helper) {
        runInternalCpuRequest(helper, AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3", timeoutTicks = 300)
    public static void compactUniversalCompletesAttachmentCpuRequest(GameTestHelper helper) {
        runInternalCpuRequest(helper, AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get(),
                WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3", timeoutTicks = 300)
    public static void compactUniversalCompletesGunCpuRequest(GameTestHelper helper) {
        runInternalCpuRequest(helper, AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get(),
                WorkbenchUpgradeKind.GUN_SMITH_TABLE);
    }

    private static void runInternalCpuRequest(GameTestHelper helper, MolecularAssemblerBlock block) {
        runInternalCpuRequest(helper, block,
                block.isUniversal() ? WorkbenchUpgradeKind.AMMO_WORKBENCH : block.getKind());
    }

    private static void runInternalCpuRequest(GameTestHelper helper, MolecularAssemblerBlock block,
            WorkbenchUpgradeKind requestedKind) {
        ItemStack machine = block.isUniversal() ? new ItemStack(block)
                : assemblerStack(block, block.getKind().defaultBlockId());
        placeAssemblerFacing(helper, block, machine, Direction.NORTH);
        BlockPos chestPos = new BlockPos(0, 1, 0);
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(chestPos, AEBlocks.CHEST.block());
        helper.setBlock(new BlockPos(0, 2, 0), AEBlocks.CRAFTING_STORAGE_4K.block());
        ((ChestBlockEntity) helper.getBlockEntity(chestPos)).getInternalInventory()
                .setItemDirect(1, new ItemStack(AEItems.ITEM_CELL_4K.asItem()));

        CraftCase[] craft = new CraftCase[1];
        AtomicReference<Future<ICraftingPlan>> calculation = new AtomicReference<>();
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    AEMolecularAssemblerBlockEntity assembler =
                            (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
                    craft[0] = findCraft(helper, requestedKind);
                    for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
                        CraftCase candidate = findCraft(helper, kind);
                        if (!block.isUniversal() && kind != block.getKind()) {
                            KeyCounter[] rejectedInputs = copyCounters(candidate);
                            if (assembler.getCapability(Capabilities.CRAFTING_MACHINE, Direction.WEST).resolve()
                                    .orElseThrow().pushPattern(PatternDetailsHelper.decodePattern(encodeCraft(candidate),
                                            helper.getLevel()), rejectedInputs, Direction.WEST)
                                    || allEmpty(rejectedInputs)) {
                                helper.fail("Expected dedicated assembler to reject other categories without consuming inputs", ROOT);
                            }
                        }
                        assembler.getPatternInventory().setItemDirect(kind.ordinal(),
                                encodeCraft(candidate));
                    }
                    if (assembler.getAvailablePatterns().size() != (block.isUniversal() ? 3 : 1)) {
                        helper.fail("Expected the assembler to advertise exactly its supported recipe categories", ROOT);
                    }
                    var grid = assembler.getMainNode().getGrid();
                    var source = new MachineSource(assembler);
                    if (grid == null || grid.getCraftingService().getCpus().isEmpty()) {
                        helper.fail("Expected a connected crafting CPU", ROOT);
                    }
                    for (GenericStack input : craft[0].inputs()) {
                        if (grid.getStorageService().getInventory().insert(input.what(), input.amount(),
                                Actionable.MODULATE, source) != input.amount()) {
                            helper.fail("Expected recipe inputs to be inserted into ME storage", chestPos);
                        }
                    }
                    calculation.set(grid.getCraftingService().beginCraftingCalculation(helper.getLevel(), () -> source,
                            AEItemKey.of(craft[0].output()), craft[0].output().getCount(),
                            CalculationStrategy.REPORT_MISSING_ITEMS));
                })
                .thenWaitUntil(() -> {
                    if (!calculation.get().isDone()) {
                        helper.fail("Waiting for AE2 crafting calculation", ROOT);
                    }
                })
                .thenExecute(() -> {
                    AEMolecularAssemblerBlockEntity assembler =
                            (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
                    try {
                        ICraftingPlan plan = calculation.get().get();
                        if (plan.simulation()) {
                            helper.fail("Expected internal pattern to produce a craftable plan", ROOT);
                        }
                        var result = assembler.getMainNode().getGrid().getCraftingService()
                                .submitJob(plan, null, null, true, new MachineSource(assembler));
                        if (!result.successful()) {
                            helper.fail("Expected CPU submission to succeed: " + result.errorCode(), ROOT);
                        }
                    } catch (Exception exception) {
                        throw new IllegalStateException("Internal assembler CPU request failed", exception);
                    }
                })
                .thenWaitUntil(() -> {
                    if (!((AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT)).isCrafting()) {
                        helper.fail("Waiting to observe the CPU-requested craft in progress", ROOT);
                    }
                })
                .thenWaitUntil(() -> {
                    AEMolecularAssemblerBlockEntity assembler =
                            (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
                    var grid = assembler.getMainNode().getGrid();
                    long output = grid.getStorageService().getInventory().getAvailableStacks()
                            .get(AEItemKey.of(craft[0].output()));
                    if (output != craft[0].output().getCount()
                            || grid.getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy())) {
                        helper.fail("Waiting for CPU completion and crafted output in ME storage", ROOT);
                    }
                    if (!assembler.getPendingOutput().isEmpty() || assembler.isCrafting()) {
                        helper.fail("Expected assembler to be idle after CPU completion", ROOT);
                    }
                    for (GenericStack input : craft[0].inputs()) {
                        if (grid.getStorageService().getInventory().getAvailableStacks().get(input.what()) != 0) {
                            helper.fail("Expected CPU to consume the stored recipe inputs", chestPos);
                        }
                    }
                })
                .thenExecute(() -> {
                    AEMolecularAssemblerBlockEntity assembler =
                            (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
                    assembler.getPatternInventory().clear();
                    if (!assembler.getAvailablePatterns().isEmpty()
                            || assembler.getMainNode().getGrid().getCraftingService().getCraftables(key -> true)
                                    .contains(AEItemKey.of(craft[0].output()))) {
                        helper.fail("Expected removing the pattern to remove its network crafting offer", ROOT);
                    }
                })
                .thenSucceed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void assemblerPersistsPatternsLargeOutputAndCraftTime(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get();
        placeAssemblerFacing(helper, block, new ItemStack(block), Direction.NORTH);
        AEMolecularAssemblerBlockEntity assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
        ItemStack pattern = encodeCraft(findCraft(helper, WorkbenchUpgradeKind.AMMO_WORKBENCH));
        assembler.getPatternInventory().setItemDirect(0, pattern);
        if (!assembler.getPatternInventory().addItems(new ItemStack(Items.DIRT)).is(Items.DIRT)) {
            helper.fail("Expected pattern inventory to reject non-pattern items", ROOT);
        }
        for (int count : new int[] { 128, 256 }) {
            CompoundTag tag = assembler.saveWithFullMetadata();
            tag.remove("Jobs");
            tag.put("PendingOutput", new ItemStack(Items.DIAMOND).save(new CompoundTag()));
            tag.putInt("PendingOutputCount", count);
            tag.putInt("CraftTicksRemaining", 7);
            tag.putBoolean("InternalOutput", true);
            assembler.loadTag(tag);
            CompoundTag saved = assembler.saveWithFullMetadata();
            AEMolecularAssemblerBlockEntity restored = new AEMolecularAssemblerBlockEntity(
                    helper.absolutePos(ROOT), assembler.getBlockState());
            restored.loadTag(saved);
            if (restored.getPendingOutput().getCount() != count
                    || !ItemStack.matches(restored.getPatternInventory().getStackInSlot(0), pattern)
                    || restored.saveWithFullMetadata().getList("Jobs", Tag.TAG_COMPOUND)
                            .getCompound(0).getInt("CraftTicksRemaining") != 7
                    || !restored.saveWithFullMetadata().getList("Jobs", Tag.TAG_COMPOUND)
                            .getCompound(0).getBoolean("InternalOutput")) {
                helper.fail("Expected patterns, large output, route and craft time to survive NBT roundtrip", ROOT);
            }
            List<ItemStack> drops = restored.collectPendingDrops();
            if (drops.stream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() != count
                    || drops.stream().noneMatch(stack -> ItemStack.matches(stack, pattern))
                    || drops.stream().anyMatch(stack -> stack.getCount() > stack.getMaxStackSize())
                    || !restored.collectPendingDrops().isEmpty()) {
                helper.fail("Expected pending output and patterns to drop exactly once", ROOT);
            }
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void universalPatternProviderCraftsDistinctRecipeKinds(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get();
        placeAssemblerFacing(helper, block, new ItemStack(block), Direction.NORTH);
        helper.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());

        CraftCase[] crafts = new CraftCase[3];
        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROVIDER);
                    crafts[0] = findCraft(helper, WorkbenchUpgradeKind.AMMO_WORKBENCH);
                    pushUniversalCraft(helper, provider, crafts[0], "ammo");
                })
                .thenWaitUntil(() -> waitUntilReturned(helper, crafts[0]))
                .thenExecute(() -> {
                    PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROVIDER);
                    crafts[1] = findCraft(helper, WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH);
                    pushUniversalCraft(helper, provider, crafts[1], "attachment");
                })
                .thenWaitUntil(() -> waitUntilReturned(helper, crafts[1]))
                .thenExecute(() -> {
                    PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROVIDER);
                    crafts[2] = findCraft(helper, WorkbenchUpgradeKind.GUN_SMITH_TABLE);
                    pushUniversalCraft(helper, provider, crafts[2], "gun");
                })
                .thenWaitUntil(() -> waitUntilReturned(helper, crafts[2]))
                .thenSucceed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void taggedDedicatedAssemblersDoNotCraftUniversal(GameTestHelper helper) {
        TransientCraftingContainer container = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        container.setItem(WorkbenchUpgradeKind.AMMO_WORKBENCH.ordinal(), assemblerStack(AETaCZBlock.TACZ_ASSEMBLER_AMMO.get(),
                WorkbenchUpgradeKind.AMMO_WORKBENCH.defaultBlockId()));
        container.setItem(WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH.ordinal(), assemblerStack(AETaCZBlock.TACZ_ASSEMBLER_ATTACHMENT.get(),
                WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH.defaultBlockId()));
        container.setItem(WorkbenchUpgradeKind.GUN_SMITH_TABLE.ordinal(), assemblerStack(AETaCZBlock.TACZ_ASSEMBLER_GUN.get(),
                WorkbenchUpgradeKind.GUN_SMITH_TABLE.defaultBlockId()));

        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            ItemStack taggedAssembler = container.getItem(kind.ordinal());
            if (!((BlockItemDataAccessor) taggedAssembler.getItem()).getBlockId(taggedAssembler)
                    .equals(kind.defaultBlockId())) {
                helper.fail("Expected tagged dedicated assembler to retain its dedicated workbench ID", ROOT);
            }
        }
        if (helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, helper.getLevel())
                .isPresent()) {
            helper.fail("Expected tagged dedicated assemblers to have no universal crafting recipe", ROOT);
        }
        ItemStack result = new ItemStack(AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get());
        if (result.getItem() instanceof BlockItemDataAccessor
                || result.hasTag() && result.getTag().contains(BlockItemDataAccessor.BLOCK_ID)) {
            helper.fail("Expected command-obtained universal assembler to remain unbound", ROOT);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void compactVariantsPlaceOneCellWithoutCraftingUniversal(GameTestHelper helper) {
        TransientCraftingContainer container = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        List<MolecularAssemblerBlock> blocks = List.of(AETaCZBlock.TACZ_ASSEMBLER_AMMO.get(),
                AETaCZBlock.TACZ_ASSEMBLER_ATTACHMENT.get(), AETaCZBlock.TACZ_ASSEMBLER_GUN.get(),
                AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get());
        for (MolecularAssemblerBlock block : blocks) {
            if (!block.defaultBlockState().is(BlockTags.create(ResourceLocation.fromNamespaceAndPath("tacz", "interact_key/whitelist")))) {
                helper.fail("Expected one-cell assembler with TaCZ interact-key support", ROOT);
            }
            assertPlacesSingleBlock(helper, block, new ItemStack(block));
            helper.setBlock(ROOT.above(), Blocks.STONE);
            helper.setBlock(ROOT.east(), Blocks.STONE);
            placeAssembler(helper, new ItemStack(block));
            if (!helper.getBlockState(ROOT).is(block) || !helper.getBlockState(ROOT.above()).is(Blocks.STONE)
                    || !helper.getBlockState(ROOT.east()).is(Blocks.STONE)) {
                helper.fail("Expected compact item placement to leave adjacent occupied cells intact", ROOT);
            }
            AEMolecularAssemblerBlockEntity assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
            if (assembler.getMenuRootAssembler() != assembler
                    || assembler.getGridConnectableSides(BlockOrientation.NORTH_UP).size() != 6) {
                helper.fail("Expected compact assembler to be its own root with six connection faces", ROOT);
            }
            if (!block.isUniversal()) {
                container.setItem(block.getKind().ordinal(), assemblerStack(block, block.getKind().defaultBlockId()));
                if (!block.getKind().defaultBlockId().equals(assembler.getId())) {
                    helper.fail("Expected untagged compact item to retain its default workbench binding", ROOT);
                }
            }
            clearAssembler(helper);
            helper.setBlock(ROOT.above(), Blocks.AIR);
            helper.setBlock(ROOT.east(), Blocks.AIR);
        }
        if (helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, helper.getLevel())
                .isPresent()) {
            helper.fail("Expected three compact specialized assemblers to have no universal crafting recipe", ROOT);
        }
        if (new ItemStack(AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get()).getItem() instanceof BlockItemDataAccessor) {
            helper.fail("Expected command-obtained compact universal to remain unbound", ROOT);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void assemblerMenusStayLocalAndRejectRemovedMachine(GameTestHelper helper) {
        MolecularAssemblerBlock block = AETaCZBlock.TACZ_ASSEMBLER_GUN.get();
        placeAssemblerFacing(helper, block, assemblerStack(block, WorkbenchUpgradeKind.GUN_SMITH_TABLE.defaultBlockId()),
                Direction.NORTH);
        AEMolecularAssemblerBlockEntity assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
        helper.setBlock(ROOT.east(), AETaCZBlock.TACZ_ASSEMBLER_AMMO.get());
        AEMolecularAssemblerBlockEntity neighbor =
                (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT.east());
        if (assembler.getMenuRootAssembler() != assembler || neighbor.getMenuRootAssembler() != neighbor) {
            helper.fail("Expected neighboring assemblers to open their own menus", ROOT);
        }
        helper.destroyBlock(ROOT);
        if (assembler.getMenuRootAssembler() != null || neighbor.getMenuRootAssembler() != neighbor) {
            helper.fail("Expected removed machine menu to invalidate without affecting its neighbor", ROOT);
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void assemblerMenuHasNineLiveReturnSlots(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) AETaCZBlock.TACZ_ASSEMBLER_AMMO.get();
        placeAssemblerFacing(helper, block, assemblerStack(block, WorkbenchUpgradeKind.AMMO_WORKBENCH.defaultBlockId()),
                Direction.NORTH);
        AEMolecularAssemblerBlockEntity assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
        setPendingOutput(assembler, new ItemStack(Items.DIAMOND, 3));
        Player player = helper.makeMockPlayer();
        Vec3 playerPos = helper.absoluteVec(Vec3.atCenterOf(ROOT));
        player.setPos(playerPos.x, playerPos.y, playerPos.z);

        AEMolecularAssemblerMenu menu = new AEMolecularAssemblerMenu(1, player.getInventory(), assembler);
        if (assembler.isNetworkPowered() != assembler.getMainNode().isPowered()
                || assembler.isNetworkOnline() != assembler.getMainNode().isOnline()
                || assembler.hasBootedGrid() != assembler.getMainNode().hasGridBooted()) {
            helper.fail("Expected assembler network flags to reflect live grid state", ROOT);
        }
        if (!menu.workbenchId.equals(WorkbenchUpgradeKind.AMMO_WORKBENCH.defaultBlockId().toString())) {
            helper.fail("Expected menu to expose dedicated assembler workbench id", ROOT);
        }

        var returnSlots = menu.getSlots(appeng.menu.SlotSemantics.STORAGE);
        helper.assertTrue(returnSlots.size() == 9, "Expected exactly nine live return slots");
        helper.assertTrue(returnSlots.stream().allMatch(slot -> slot.mayPlace(new ItemStack(Items.DIRT))),
                "Expected return slots to accept player inputs like a Pattern Provider");
        menu.setCarried(new ItemStack(Items.DIRT, 17));
        menu.clicked(returnSlots.get(0).index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && returnSlots.get(0).getItem().getCount() == 17,
                "Expected normal click to move every carried item into the offline return buffer");
        player.getInventory().setItem(0, new ItemStack(Items.DIRT, 23));
        menu.quickMoveStack(player, menu.getSlots(appeng.menu.SlotSemantics.PLAYER_HOTBAR).get(0).index);
        helper.assertTrue(player.getInventory().getItem(0).isEmpty()
                        && returnSlots.get(0).getItem().is(Items.DIRT)
                        && returnSlots.get(0).getItem().getCount() == 40
                        && assembler.getPatternInventory().isEmpty(),
                "Expected ordinary shift-click to merge into the return buffer without item loss");
        menu.clicked(returnSlots.get(0).index, 0, ClickType.PICKUP, player);
        helper.assertTrue(assembler.getReturnInventory().isEmpty() && menu.getCarried().is(Items.DIRT)
                        && menu.getCarried().getCount() == 40,
                "Expected buffered player items to remain fully recoverable while offline");
        menu.setCarried(ItemStack.EMPTY);
        assembler.getReturnInventory().setStack(8, new GenericStack(AEItemKey.of(Items.EMERALD), 3));
        helper.assertTrue(returnSlots.get(8).getItem().is(Items.EMERALD)
                && returnSlots.get(8).getItem().getCount() == 3, "Expected ninth slot to reflect real buffer");
        menu.quickMoveStack(player, returnSlots.get(8).index);
        helper.assertTrue(assembler.getReturnInventory().isEmpty()
                && player.getInventory().items.stream().anyMatch(stack -> stack.is(Items.EMERALD)
                        && stack.getCount() == 3), "Expected return stack to shift-click into player inventory");
        ItemStack pattern = encodeCraft(findCraft(helper, WorkbenchUpgradeKind.AMMO_WORKBENCH));
        if (menu.slots.size() != 59 || !menu.getSlot(0).mayPlace(pattern)
                || menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT))) {
            helper.fail("Expected nine return slots, nine patterns, five upgrades and 36 player slots", ROOT);
        }
        player.getInventory().setItem(0, pattern.copy());
        menu.quickMoveStack(player, menu.getSlots(appeng.menu.SlotSemantics.PLAYER_HOTBAR).get(0).index);
        if (!ItemStack.matches(assembler.getPatternInventory().getStackInSlot(0), pattern)
                || !player.getInventory().getItem(0).isEmpty()
                || assembler.getPendingOutput().getCount() != 3) {
            helper.fail("Expected hotbar shift-click to insert the encoded pattern into the assembler", ROOT);
        }
        menu.quickMoveStack(player, 0);
        if (!assembler.getPatternInventory().getStackInSlot(0).isEmpty()
                || player.getInventory().items.stream().noneMatch(stack -> ItemStack.matches(stack, pattern))) {
            helper.fail("Expected pattern shift-click to return the encoded pattern to the player", ROOT);
        }
        var upgradeSlot = menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).get(0);
        if (!upgradeSlot.mayPlace(AEItems.SPEED_CARD.stack()) || upgradeSlot.mayPlace(AEItems.CAPACITY_CARD.stack())) {
            helper.fail("Expected upgrade slot to accept only acceleration cards", ROOT);
        }
        player.getInventory().setItem(0, AEItems.SPEED_CARD.stack());
        menu.quickMoveStack(player, menu.getSlots(appeng.menu.SlotSemantics.PLAYER_HOTBAR).get(0).index);
        if (assembler.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD) != 1
                || !player.getInventory().getItem(0).isEmpty()) {
            helper.fail("Expected hotbar shift-click to install an acceleration card", ROOT);
        }
        menu.quickMoveStack(player, upgradeSlot.index);
        if (assembler.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD) != 0
                || player.getInventory().items.stream().noneMatch(AEItems.SPEED_CARD::isSameAs)) {
            helper.fail("Expected upgrade shift-click to return the acceleration card", ROOT);
        }
        helper.assertTrue(assembler.getPendingOutput().getCount() == 3,
                "Expected return inventory interaction to leave unfinished craft unchanged");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3", timeoutTicks = 160)
    public static void manuallyInsertedReturnsRetryAndWakeOnActiveNetwork(GameTestHelper helper) {
        helper.setBlock(ROOT, AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get());
        var assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
        var player = helper.makeMockPlayer();
        var pos = helper.absolutePos(ROOT);
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        var menu = new AEMolecularAssemblerMenu(22, player.getInventory(), assembler);
        var slot = menu.getSlots(appeng.menu.SlotSemantics.STORAGE).get(0);
        menu.setCarried(new ItemStack(Items.COPPER_INGOT, 17));
        menu.clicked(slot.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && slot.getItem().getCount() == 17,
                "Expected player insertion into the offline return buffer");
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.startSequence()
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(assembler.getMainNode().isActive() && slot.getItem().getCount() == 17,
                            "Expected an active network without storage to retain every buffered item");
                    helper.setBlock(ENERGY.north(), AEBlocks.CHEST.block());
                    ((ChestBlockEntity) helper.getBlockEntity(ENERGY.north())).getInternalInventory()
                            .setItemDirect(1, AEItems.ITEM_CELL_4K.stack());
                })
                .thenWaitUntil(() -> {
                    var stored = assembler.getMainNode().getGrid().getStorageService().getInventory()
                            .getAvailableStacks().get(AEItemKey.of(Items.COPPER_INGOT));
                    helper.assertTrue(stored == 17 && assembler.getReturnInventory().isEmpty(),
                            "Expected a waiting return buffer to retry when ME storage becomes available");
                })
                .thenExecuteAfter(10, () -> {
                    player.getInventory().setItem(0, new ItemStack(Items.COPPER_INGOT, 23));
                    menu.quickMoveStack(player,
                            menu.getSlots(appeng.menu.SlotSemantics.PLAYER_HOTBAR).get(0).index);
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

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void assemblerMenuInvalidatesAfterMachineBreak(GameTestHelper helper) {
        MolecularAssemblerBlock block = (MolecularAssemblerBlock) AETaCZBlock.TACZ_ASSEMBLER_AMMO.get();
        placeAssemblerFacing(helper, block, assemblerStack(block, WorkbenchUpgradeKind.AMMO_WORKBENCH.defaultBlockId()),
                Direction.NORTH);
        AEMolecularAssemblerBlockEntity assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
        Player player = helper.makeMockPlayer();
        Vec3 playerPos = helper.absoluteVec(Vec3.atCenterOf(ROOT));
        player.setPos(playerPos.x, playerPos.y, playerPos.z);
        AEMolecularAssemblerMenu menu = new AEMolecularAssemblerMenu(1, player.getInventory(), assembler);
        if (!menu.stillValid(player)) {
            helper.fail("Expected intact assembler menu to be valid", ROOT);
        }

        helper.destroyBlock(ROOT);
        menu.broadcastChanges();
        if (menu.isValidMenu()) {
            helper.fail("Expected assembler menu to invalidate after machine break", ROOT);
        }
        helper.succeed();
    }

    private static void assertPlacesSingleBlock(GameTestHelper helper, MolecularAssemblerBlock block, ItemStack stack) {
        for (Direction facing : HORIZONTAL_FACINGS) {
            helper.setBlock(ROOT.above(), Blocks.STONE);
            helper.setBlock(ROOT.east(), Blocks.STONE);
            placeAssemblerFacing(helper, block, stack, facing);
            AEMolecularAssemblerBlockEntity assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
            if (helper.getBlockState(ROOT).getValue(MolecularAssemblerBlock.FACING) != facing
                    || !helper.getBlockState(ROOT.above()).is(Blocks.STONE)
                    || !helper.getBlockState(ROOT.east()).is(Blocks.STONE)
                    || assembler.getGridConnectableSides(BlockOrientation.NORTH_UP).size() != 6
                    || !assembler.getCapability(Capabilities.CRAFTING_MACHINE, Direction.UP).isPresent()) {
                helper.fail("Expected one assembler cell with its facing and six connection faces", ROOT);
            }
            clearAssembler(helper);
            helper.setBlock(ROOT.above(), Blocks.AIR);
            helper.setBlock(ROOT.east(), Blocks.AIR);
        }
    }

    private static void placeAssembler(GameTestHelper helper, ItemStack stack) {
        helper.setBlock(ROOT.below(), Blocks.STONE);
        stack.useOn(new UseOnContext(helper.getLevel(), helper.makeMockPlayer(),
                InteractionHand.MAIN_HAND, stack, new BlockHitResult(helper.absoluteVec(Vec3.atCenterOf(ROOT.below())),
                Direction.UP, helper.absolutePos(ROOT.below()), false)));
    }

    private static void placeAssemblerFacing(GameTestHelper helper, MolecularAssemblerBlock block, ItemStack stack,
            Direction facing) {
        BlockState rootState = block.defaultBlockState()
                .setValue(MolecularAssemblerBlock.FACING, facing);
        helper.setBlock(ROOT.below(), Blocks.STONE);
        helper.setBlock(ROOT, rootState);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(ROOT), rootState, helper.makeMockPlayer(), stack);
    }

    private static void clearAssembler(GameTestHelper helper) {
        helper.getLevel().setBlock(helper.absolutePos(ROOT), Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        helper.setBlock(ROOT.below(), Blocks.AIR);
    }

    private static ItemStack assemblerStack(Block block, ResourceLocation blockId) {
        return BlockItemBuilder.create(block).setId(blockId).build();
    }

    private static void setPendingOutput(AEMolecularAssemblerBlockEntity assembler, ItemStack stack) {
        CompoundTag tag = assembler.saveWithFullMetadata();
        tag.remove("Jobs");
        tag.put("PendingOutput", stack.save(new CompoundTag()));
        assembler.loadTag(tag);
    }

    private static GunSmithTableRecipe testRecipe(GunSmithTableIngredient... ingredients) {
        return new GunSmithTableRecipe(
                ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, "test/overlap"),
                new GunSmithTableResult(new ItemStack(Items.DIAMOND),
                        ResourceLocation.fromNamespaceAndPath("tacz", "misc")),
                List.of(ingredients));
    }

    private static KeyCounter[] counters(Object... itemAndAmountPairs) {
        KeyCounter[] counters = new KeyCounter[itemAndAmountPairs.length / 2];
        for (int i = 0; i < itemAndAmountPairs.length; i += 2) {
            counters[i / 2] = new KeyCounter();
            counters[i / 2].add(AEItemKey.of((Item) itemAndAmountPairs[i]),
                    (int) itemAndAmountPairs[i + 1]);
        }
        return counters;
    }

    private static boolean pushCraft(PatternProviderBlockEntity provider, CraftCase craft, KeyCounter[] inputs) {
        ItemStack pattern = encodeCraft(craft);
        if (!provider.getLogic().getPatternInv().addItems(pattern).isEmpty()) {
            return false;
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(pattern, provider.getLevel());
        return provider.getLogic().pushPattern(details, inputs);
    }

    private static ItemStack encodeCraft(CraftCase craft) {
        return PatternDetailsHelper.encodeProcessingPattern(craft.inputs().toArray(GenericStack[]::new),
                new GenericStack[] { outputStack(craft.output()) });
    }

    private static void assertReturned(GameTestHelper helper, CraftCase craft) {
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROVIDER);
        if (!returnInventoryContains(provider, craft.output())) {
            helper.fail("Expected output to be delivered to the original pattern provider return inventory",
                    PROVIDER);
        }
    }

    private static void waitUntilReturned(GameTestHelper helper, CraftCase craft) {
        PatternProviderBlockEntity provider = (PatternProviderBlockEntity) helper.getBlockEntity(PROVIDER);
        if (!returnInventoryContains(provider, craft.output())) {
            helper.fail("Expected output to be delivered to the original pattern provider return inventory",
                    PROVIDER);
        }
    }

    private static void pushUniversalCraft(GameTestHelper helper, PatternProviderBlockEntity provider, CraftCase craft,
            String label) {
        KeyCounter[] inputs = copyCounters(craft);
        if (!pushCraft(provider, craft, inputs)) {
            helper.fail("Expected universal assembler to accept " + label + " recipe " + craft.recipeId(), PROVIDER);
        }
        if (!allEmpty(inputs)) {
            helper.fail("Expected universal assembler to consume " + label + " inputs", PROVIDER);
        }
    }

    private static CraftCase findCraft(GameTestHelper helper, WorkbenchUpgradeKind kind) {
        var index = TimelessAPI.getCommonBlockIndex(kind.defaultBlockId())
                .orElseThrow(() -> new IllegalStateException("Missing TaCZ workbench index " + kind.defaultBlockId()));
        for (GunSmithTableRecipe recipe : helper.getLevel().getRecipeManager()
                .getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
            if ((index.getFilter() != null && !index.getFilter().contains(recipe.getId()))
                    || index.getData().getTabs().stream().noneMatch(tab -> tab.id().equals(recipe.getTab()))) {
                continue;
            }

            CraftCase craft = createCraftCase(recipe, helper);
            if (craft != null && AssemblerRecipeMatcher.matches(recipe, copyCounters(craft))) {
                return craft;
            }
        }
        throw new IllegalStateException("Could not find a craftable TaCZ recipe for " + kind);
    }

    private static CraftCase createCraftCase(GunSmithTableRecipe recipe, GameTestHelper helper) {
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
        return new CraftCase(recipe.getId(), inputs, output.copy(), counters);
    }

    private static GenericStack outputStack(ItemStack output) {
        return new GenericStack(AEItemKey.of(output), output.getCount());
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
}
