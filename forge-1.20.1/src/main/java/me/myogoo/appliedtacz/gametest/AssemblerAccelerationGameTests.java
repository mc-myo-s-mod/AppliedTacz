package me.myogoo.appliedtacz.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.AutoCraftingMenu;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.init.ModRecipe;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.crafting.AssemblerRecipeMatcher;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.event.RegisterGameTestsEvent;

import java.util.ArrayList;
import java.util.List;

public final class AssemblerAccelerationGameTests {
    private static final BlockPos ROOT = new BlockPos(1, 1, 1);

    private AssemblerAccelerationGameTests() {
    }

    public static void register(RegisterGameTestsEvent event) {
        event.register(AssemblerAccelerationGameTests.class);
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void accelerationInventoryPersistsAndDropsExactlyOnce(GameTestHelper helper) {
        for (var entry : AETaCZBlock.BLOCkS.getEntries()) {
            if (!(entry.get() instanceof MolecularAssemblerBlock block)) {
                continue;
            }
            var assembler = new AEMolecularAssemblerBlockEntity(helper.absolutePos(ROOT), block.defaultBlockState());
            assembler.setLevel(helper.getLevel());
            var upgrades = assembler.getUpgrades();
            helper.assertTrue(upgrades.size() == 5, "Expected five upgrade slots");
            helper.assertTrue(upgrades.addItems(new ItemStack(Items.DIRT)).getCount() == 1,
                    "Expected ordinary items to be rejected");
            helper.assertTrue(upgrades.addItems(new ItemStack(AEItems.CAPACITY_CARD.asItem())).getCount() == 1,
                    "Expected unrelated upgrade cards to be rejected");
            helper.assertTrue(upgrades.addItems(new ItemStack(AEItems.SPEED_CARD.asItem(), 6)).getCount() == 1,
                    "Expected exactly five acceleration cards to fit");

            CompoundTag tag = assembler.saveWithFullMetadata();
            tag.remove("Jobs");
            tag.put("PendingOutput", new ItemStack(Items.DIAMOND).save(new CompoundTag()));
            tag.putInt("PendingOutputCount", 130);
            tag.putInt("CraftTicksRemaining", 7);
            var restored = new AEMolecularAssemblerBlockEntity(helper.absolutePos(ROOT), block.defaultBlockState());
            restored.setLevel(helper.getLevel());
            restored.loadTag(tag);
            helper.assertTrue(restored.getInstalledUpgrades(AEItems.SPEED_CARD) == 5,
                    "Expected all acceleration cards after save/load");
            helper.assertTrue(jobs(restored).getCompound(0).getInt("CraftTicksRemaining") == 7,
                    "Expected in-progress craft time unchanged by save/load");
            List<ItemStack> drops = restored.collectPendingDrops();
            helper.assertTrue(drops.stream().filter(stack -> stack.is(AEItems.SPEED_CARD.asItem()))
                    .mapToInt(ItemStack::getCount).sum() == 5, "Expected every installed card to drop once");
            helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount).sum() == 130, "Expected the complete pending output to survive");
            helper.assertTrue(restored.collectPendingDrops().isEmpty(), "Expected no duplicated second drops");

            restored.getUpgrades().addItems(new ItemStack(AEItems.SPEED_CARD.asItem()));
            tag.remove("Upgrades");
            restored.loadTag(tag);
            helper.assertTrue(restored.getInstalledUpgrades(AEItems.SPEED_CARD) == 0,
                    "Expected legacy saves without upgrade data to load with empty upgrade slots");
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void accelerationShortensActualCraftWithoutDuplicatingOutput(GameTestHelper helper) {
        runTimingTest(helper, AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get());
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void extendedAdvancesEightJobsAtEveryAccelerationSpeed(GameTestHelper helper) {
        runTimingTest(helper, AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_AMMO.get());
    }

    private static void runTimingTest(GameTestHelper helper, MolecularAssemblerBlock block) {
        helper.setBlock(ROOT, block);
        helper.setBlock(ROOT.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(30, () -> {
            var assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
            CraftCase craft = findCraft(helper);
            int[] durations = block.isExtended() ? new int[] {5, 4, 3, 3, 2, 1} : new int[] {10, 8, 6, 5, 4, 2};
            int capacity = block.isExtended() ? 8 : 1;
            for (int cards = 0; cards <= 5; cards++) {
                if (cards > 0) {
                    assembler.getUpgrades().addItems(new ItemStack(AEItems.SPEED_CARD.asItem(), cards));
                }
                assembler.getPatternInventory().setItemDirect(0, craft.pattern().copy());
                var details = PatternDetailsHelper.decodePattern(craft.pattern(), helper.getLevel());
                for (int job = 0; job < capacity; job++) {
                    KeyCounter[] inputs = copyInputs(craft.inputs());
                    helper.assertTrue(assembler.pushPattern(details, inputs), "Expected powered assembler to accept recipe");
                    helper.assertTrue(inputs[0].isEmpty(), "Expected accepted ingredients to be consumed once");
                    helper.assertTrue(jobs(assembler).getCompound(job).getInt("CraftTicksRemaining") == durations[cards],
                            "Expected acceleration-dependent duration for " + cards + " cards");
                }
                KeyCounter[] rejected = copyInputs(craft.inputs());
                helper.assertTrue(!assembler.pushPattern(details, rejected) && !rejected[0].isEmpty(),
                        "Expected busy rejection to preserve input");
                assembler.getUpgrades().clear();
                for (int tick = 1; tick < durations[cards]; tick++) {
                    assembler.tickingRequest(assembler.getGridNode(), 1);
                }
                helper.assertTrue(assembler.isCrafting()
                        && jobs(assembler).stream().allMatch(job -> ((CompoundTag) job).getInt("CraftTicksRemaining") == 1),
                        "Expected card removal to preserve the already accepted craft duration");
                assembler.tickingRequest(assembler.getGridNode(), 1);
                helper.assertTrue(!assembler.isCrafting()
                        && jobs(assembler).stream().allMatch(job -> ((CompoundTag) job).getInt("CraftTicksRemaining") == 0),
                        "Expected completion exactly at the accelerated duration");
                for (int tick = 0; tick < 3; tick++) {
                    assembler.tickingRequest(assembler.getGridNode(), 1);
                }
                List<ItemStack> drops = assembler.collectPendingDrops();
                helper.assertTrue(drops.stream()
                        .filter(stack -> AEItemKey.of(stack).equals(AEItemKey.of(craft.output())))
                        .mapToInt(ItemStack::getCount).sum() == capacity * craft.output().getCount(),
                        "Expected blocked output retries to preserve every craft result exactly once");
                helper.assertTrue(assembler.collectPendingDrops().isEmpty(), "Expected no repeated output drop");
            }
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void extendedQueueRoundtripPreservesEveryJobAndDropsOnce(GameTestHelper helper) {
        var block = AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_AMMO.get();
        var assembler = new AEMolecularAssemblerBlockEntity(helper.absolutePos(ROOT), block.defaultBlockState());
        assembler.setLevel(helper.getLevel());
        CompoundTag tag = assembler.saveWithFullMetadata();
        ListTag pending = new ListTag();
        int total = 0;
        for (int i = 0; i < 8; i++) {
            pending.add(job(new ItemStack(Items.DIAMOND, 130 + i), i + 1, i % 2 == 0,
                    helper.absolutePos(ROOT.east()), Direction.WEST));
            total += 130 + i;
        }
        tag.put("Jobs", pending);
        tag.put("PendingOutput", new ItemStack(Items.EMERALD).save(new CompoundTag()));
        assembler.loadTag(tag);
        helper.assertTrue(jobs(assembler).equals(pending), "Expected all eight routes, counts and timers unchanged");
        ItemStack preview = assembler.getPendingOutput();
        preview.setCount(1);
        helper.assertTrue(assembler.getPendingOutput().getCount() == 130, "Expected a defensive first-job preview");
        CompoundTag saved = assembler.saveWithFullMetadata();
        helper.assertTrue(!saved.contains("PendingOutput"), "Expected only the new queue format to be written");
        var restored = new AEMolecularAssemblerBlockEntity(helper.absolutePos(ROOT),
                AETaCZBlock.TACZ_ASSEMBLER_AMMO.get().defaultBlockState());
        restored.setLevel(helper.getLevel());
        restored.loadTag(saved);
        helper.assertTrue(jobs(restored).equals(pending), "Expected loaded jobs not to be truncated by tier capacity");
        List<ItemStack> drops = restored.collectPendingDrops();
        helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.DIAMOND))
                .mapToInt(ItemStack::getCount).sum() == total
                && drops.stream().noneMatch(stack -> stack.getCount() > stack.getMaxStackSize())
                && restored.collectPendingDrops().isEmpty(), "Expected every queued output to drop exactly once");
        tag.put("Jobs", new ListTag());
        restored.loadTag(tag);
        helper.assertTrue(restored.getPendingOutput().isEmpty(), "Expected an empty Jobs list to override legacy output");
        tag.remove("Jobs");
        tag.putInt("CraftTicksRemaining", 20);
        restored.loadTag(tag);
        helper.assertTrue(jobs(restored).size() == 1
                && jobs(restored).getCompound(0).getInt("CraftTicksRemaining") == 20
                && !jobs(restored).getCompound(0).getBoolean("InternalOutput"),
                "Expected legacy duration and false internal route to survive migration");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void blockedJobDoesNotStallOtherRoutesOrDuplicatePartialOutput(GameTestHelper helper) {
        helper.setBlock(ROOT, AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_AMMO.get());
        helper.setBlock(ROOT.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(ROOT.east(), Blocks.CHEST);
        helper.setBlock(ROOT.north(), Blocks.CHEST);
        helper.runAfterDelay(30, () -> {
            var assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
            var partialChest = (ChestBlockEntity) helper.getBlockEntity(ROOT.east());
            var otherChest = (ChestBlockEntity) helper.getBlockEntity(ROOT.north());
            for (int slot = 0; slot < partialChest.getContainerSize(); slot++) {
                partialChest.setItem(slot, new ItemStack(Items.DIRT, 64));
            }
            partialChest.setItem(0, new ItemStack(Items.DIAMOND, 63));
            CompoundTag tag = assembler.saveWithFullMetadata();
            ListTag pending = new ListTag();
            pending.add(job(new ItemStack(Items.DIAMOND, 130), 0, true, null, null));
            pending.add(job(new ItemStack(Items.DIAMOND, 3), 1, false,
                    helper.absolutePos(ROOT.east()), Direction.WEST));
            pending.add(job(new ItemStack(Items.EMERALD, 2), 2, false,
                    helper.absolutePos(ROOT.north()), Direction.SOUTH));
            tag.put("Jobs", pending);
            assembler.loadTag(tag);
            assembler.tickingRequest(assembler.getGridNode(), 1);
            ListTag afterTick = jobs(assembler);
            helper.assertTrue(afterTick.size() == 3 && partialChest.getItem(0).getCount() == 64
                    && afterTick.getCompound(1).getInt("PendingOutputCount") == 2
                    && afterTick.getCompound(2).getInt("CraftTicksRemaining") == 1 && assembler.isCrafting(),
                    "Expected blocked first job not to stop partial output or other timers");
            assembler.loadTag(assembler.saveWithFullMetadata());
            helper.assertTrue(jobs(assembler).equals(afterTick), "Expected partial remainder and routes after reload");
            partialChest.setItem(0, ItemStack.EMPTY);
            assembler.tickingRequest(assembler.getGridNode(), 1);
            helper.assertTrue(jobs(assembler).size() == 1 && !assembler.isCrafting()
                    && partialChest.getItem(0).is(Items.DIAMOND) && partialChest.getItem(0).getCount() == 2
                    && otherChest.getItem(0).is(Items.EMERALD) && otherChest.getItem(0).getCount() == 2,
                    "Expected independent completed routes and only the blocked internal job remaining");
            List<ItemStack> drops = assembler.collectPendingDrops();
            helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount).sum() == 130 && assembler.collectPendingDrops().isEmpty(),
                    "Expected only undelivered output to drop once");
            helper.succeed();
        });
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = AppliedTaCZ.MODID, template = "empty3x3x3")
    public static void extendedRecipesRequireEightMatchingBasicAssemblers(GameTestHelper helper) {
        for (MolecularAssemblerBlock block : List.of(AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_GUN.get(),
                AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_AMMO.get(), AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get())) {
            var kind = block.getKind();
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
            var recipe = helper.getLevel().getRecipeManager().byKey(id).orElseThrow();
            helper.assertTrue(recipe instanceof CraftingRecipe, "Expected registered extended crafting recipe " + id);
            var crafting = (CraftingRecipe) recipe;
            var input = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
            for (int slot = 0; slot < 9; slot++) {
                input.setItem(slot, slot == 4 ? new ItemStack(AEItems.CALCULATION_PROCESSOR.asItem())
                        : new ItemStack(kind.assemblerBlock()));
            }
            ItemStack result = crafting.assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(crafting.matches(input, helper.getLevel()) && result.is(block.asItem())
                    && result.getCount() == 1 && block.isExtended() && !block.isUniversal()
                    && block.partCount() == 1 && WorkbenchUpgradeKind.fromAppliedWorkbench(block) == kind,
                    "Expected one correctly bound compact extended assembler");
            input.setItem(0, ItemStack.EMPTY);
            helper.assertTrue(!crafting.matches(input, helper.getLevel()), "Expected missing assembler to reject recipe");
            input.setItem(0, new ItemStack(kind == WorkbenchUpgradeKind.AMMO_WORKBENCH
                    ? AETaCZBlock.TACZ_ASSEMBLER_GUN.get() : AETaCZBlock.TACZ_ASSEMBLER_AMMO.get()));
            helper.assertTrue(!crafting.matches(input, helper.getLevel()), "Expected mixed assembler kinds to reject recipe");
        }
        helper.succeed();
    }

    private static ListTag jobs(AEMolecularAssemblerBlockEntity assembler) {
        return assembler.saveWithFullMetadata().getList("Jobs", Tag.TAG_COMPOUND);
    }

    private static CompoundTag job(ItemStack output, int ticks, boolean internal, BlockPos returnPos,
            Direction returnFace) {
        CompoundTag tag = new CompoundTag();
        ItemStack single = output.copy();
        single.setCount(1);
        tag.put("PendingOutput", single.save(new CompoundTag()));
        tag.putInt("PendingOutputCount", output.getCount());
        tag.putInt("CraftTicksRemaining", ticks);
        tag.putBoolean("InternalOutput", internal);
        if (returnPos != null) {
            tag.putLong("ReturnPos", returnPos.asLong());
        }
        if (returnFace != null) {
            tag.putInt("ReturnFace", returnFace.get3DDataValue());
        }
        return tag;
    }

    private static CraftCase findCraft(GameTestHelper helper) {
        var index = TimelessAPI.getCommonBlockIndex(WorkbenchUpgradeKind.AMMO_WORKBENCH.defaultBlockId()).orElseThrow();
        for (var recipe : helper.getLevel().getRecipeManager()
                .getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
            if ((index.getFilter() != null && !index.getFilter().contains(recipe.getId()))
                    || index.getData().getTabs().stream().noneMatch(tab -> tab.id().equals(recipe.getTab()))) {
                continue;
            }
            ItemStack output = recipe.getResultItem(helper.getLevel().registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            List<GenericStack> ingredients = new ArrayList<>();
            boolean valid = true;
            for (var ingredient : recipe.getInputs()) {
                ItemStack[] choices = ingredient.getIngredient().getItems();
                if (choices.length == 0 || ingredient.getCount() <= 0) {
                    valid = false;
                    break;
                }
                ingredients.add(new GenericStack(AEItemKey.of(choices[0]), ingredient.getCount()));
            }
            if (!valid || ingredients.isEmpty() || !AssemblerRecipeMatcher.matches(recipe, copyInputs(ingredients))) {
                continue;
            }
            ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(ingredients.toArray(GenericStack[]::new),
                    new GenericStack[] {new GenericStack(AEItemKey.of(output), output.getCount())});
            return new CraftCase(pattern, ingredients, output);
        }
        throw new IllegalStateException("Expected at least one craftable TaCZ recipe");
    }

    private static KeyCounter[] copyInputs(List<GenericStack> inputs) {
        KeyCounter counter = new KeyCounter();
        for (GenericStack input : inputs) {
            counter.add(input.what(), input.amount());
        }
        return new KeyCounter[] {counter};
    }

    private record CraftCase(ItemStack pattern, List<GenericStack> inputs, ItemStack output) {
    }
}
