package me.myogoo.appliedtacz.gametest;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.AutoCraftingMenu;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(AppliedTaCZ.MODID)
@PrefixGameTestTemplate(false)
public final class AssemblerStackGameTests {
    private static final BlockPos ROOT = new BlockPos(1, 1, 1);
    private static final ResourceLocation OTHER_WORKBENCH =
            new ResourceLocation(AppliedTaCZ.MODID, "stack_test_other_workbench");

    private AssemblerStackGameTests() {
    }

    @GameTest(template = "empty3x3x3")
    public static void allAssemblerItemsStackTo64(GameTestHelper helper) {
        for (var entry : AETaCZBlock.BLOCkS.getEntries()) {
            if (entry.get() instanceof MolecularAssemblerBlock block) {
                ItemStack stack = new ItemStack(block);
                helper.assertTrue(stack.getMaxStackSize() == 64, "Expected every assembler tier to stack to 64");
                assertStacksTo64(helper, stack, stack.copy());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void craftedAssemblersStackAndRejectMixedWorkbenchIds(GameTestHelper helper) {
        var entries = AETaCZWorkbenchIndex.entries();
        helper.assertTrue(!entries.isEmpty(), "Expected TaCZ workbench fixtures");
        var recipes = helper.getLevel().getRecipeManager();
        for (var entry : entries) {
            var input = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
            input.setItem(0, BlockItemBuilder.create(entry.baseWorkbench()).setId(entry.blockId()).build());
            input.setItem(1, AEBlocks.MOLECULAR_ASSEMBLER.stack());
            input.setItem(2, AEBlocks.PATTERN_PROVIDER.stack());
            var basicRecipe = recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            ItemStack basic = basicRecipe.assemble(input, helper.getLevel().registryAccess());
            assertStacksTo64(helper, entry.createAssemblerStack(), basic);

            for (int slot = 0; slot < 9; slot++) {
                input.setItem(slot, slot == 4 ? AEItems.CALCULATION_PROCESSOR.stack() : basic.copy());
            }
            var recipe = recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            ItemStack extended = recipe.assemble(input, helper.getLevel().registryAccess());
            assertStacksTo64(helper, entry.createExtendedAssemblerStack(), extended);
            helper.assertTrue(!ItemStack.isSameItemSameTags(basic, extended),
                    "Expected normal and Extended tiers to remain distinct");

            ItemStack other = basic.copy();
            ((IBlock) other.getItem()).setBlockId(other, OTHER_WORKBENCH);
            helper.assertTrue(!ItemStack.isSameItemSameTags(basic, other),
                    "Expected distinct workbench IDs not to merge");
            input.setItem(0, other);
            helper.assertTrue(recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isEmpty(),
                    "Expected eight-input recipes to reject mixed workbench IDs");
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void placedAssemblersDropMergeableStacksWithoutLosingWorkbenchIds(GameTestHelper helper) {
        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            for (var block : List.of(kind.assemblerBlock(), kind.extendedAssemblerBlock())) {
                for (ResourceLocation id : List.of(kind.defaultBlockId(), OTHER_WORKBENCH)) {
                    ItemStack original = BlockItemBuilder.create(block).setId(id).build();
                    helper.setBlock(ROOT, block);
                    block.setPlacedBy(helper.getLevel(), helper.absolutePos(ROOT), helper.getBlockState(ROOT),
                            null, original);
                    helper.destroyBlock(ROOT);
                    List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, ROOT, 2.0);
                    helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1,
                            "Expected exactly one empty assembler drop");
                    ItemStack drop = drops.get(0).getItem();
                    helper.assertTrue(((IBlock) drop.getItem()).getBlockId(drop).equals(id),
                            "Expected placement and breaking to preserve the exact workbench ID");
                    assertStacksTo64(helper, original, drop);
                    drops.forEach(ItemEntity::discard);
                }
            }
        }
        helper.succeed();
    }

    private static void assertStacksTo64(GameTestHelper helper, ItemStack first, ItemStack second) {
        helper.assertTrue(ItemStack.isSameItemSameTags(first, second),
                "Expected identical assembler item and NBT");
        SimpleContainer inventory = new SimpleContainer(1);
        inventory.setItem(0, first.copyWithCount(63));
        helper.assertTrue(inventory.addItem(second.copyWithCount(1)).isEmpty()
                        && inventory.getItem(0).getCount() == 64,
                "Expected 63 existing assemblers and one matching assembler to merge into 64");
    }
}
