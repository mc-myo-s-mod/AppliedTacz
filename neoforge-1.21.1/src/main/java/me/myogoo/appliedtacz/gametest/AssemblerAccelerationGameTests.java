package me.myogoo.appliedtacz.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.init.ModRecipe;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.crafting.AssemblerRecipeMatcher;
import me.myogoo.appliedtacz.registry.ModBlocks;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder(AppliedTaCZ.MODID)
@PrefixGameTestTemplate(false)
public final class AssemblerAccelerationGameTests {
    private static final BlockPos ROOT = new BlockPos(1, 1, 1);

    private AssemblerAccelerationGameTests() {
    }

    @GameTest(template = "empty3x3x3")
    public static void accelerationInventoryPersistsAndDropsExactlyOnce(GameTestHelper helper) {
        for (var entry : ModBlocks.BLOCKS.getEntries()) {
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

            CompoundTag tag = assembler.saveWithFullMetadata(helper.getLevel().registryAccess());
            tag.remove("Jobs");
            tag.put("PendingOutput", new ItemStack(Items.DIAMOND).save(helper.getLevel().registryAccess()));
            tag.putInt("PendingOutputCount", 130);
            tag.putInt("CraftTicks", 7);
            var restored = new AEMolecularAssemblerBlockEntity(helper.absolutePos(ROOT), block.defaultBlockState());
            restored.setLevel(helper.getLevel());
            restored.loadTag(tag, helper.getLevel().registryAccess());
            helper.assertTrue(restored.getInstalledUpgrades(AEItems.SPEED_CARD) == 5,
                    "Expected all acceleration cards after save/load");
            helper.assertTrue(savedJobs(restored, helper).getCompound(0).getInt("CraftTicks") == 7,
                    "Expected in-progress craft time unchanged by save/load");
            List<ItemStack> drops = restored.collectPendingDrops();
            helper.assertTrue(drops.stream().filter(stack -> stack.is(AEItems.SPEED_CARD.asItem()))
                    .mapToInt(ItemStack::getCount).sum() == 5, "Expected every installed card to drop once");
            helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount).sum() == 130, "Expected the complete pending output to survive");
            helper.assertTrue(restored.collectPendingDrops().isEmpty(), "Expected no duplicated second drops");

            restored.getUpgrades().addItems(new ItemStack(AEItems.SPEED_CARD.asItem()));
            tag.remove("Upgrades");
            restored.loadTag(tag, helper.getLevel().registryAccess());
            helper.assertTrue(restored.getInstalledUpgrades(AEItems.SPEED_CARD) == 0,
                    "Expected legacy saves without upgrade data to load with empty upgrade slots");
        }
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void accelerationShortensActualCraftWithoutDuplicatingOutput(GameTestHelper helper) {
        assertAcceleratedCrafts(helper, false);
    }

    @GameTest(template = "empty3x3x3")
    public static void extendedRunsEightAcceleratedCraftsAndRejectsNinth(GameTestHelper helper) {
        assertAcceleratedCrafts(helper, true);
    }

    private static void assertAcceleratedCrafts(GameTestHelper helper, boolean extended) {
        helper.setBlock(ROOT, extended ? ModBlocks.EXTENDED_TACZ_ASSEMBLER_AMMO.get()
                : ModBlocks.TACZ_ASSEMBLER_AMMO.get());
        ((AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT)).setId(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID);
        helper.setBlock(ROOT.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(30, () -> {
            var assembler = (AEMolecularAssemblerBlockEntity) helper.getBlockEntity(ROOT);
            CraftCase craft = findCraft(helper);
            int[] durations = extended ? new int[] {5, 4, 3, 3, 2, 1} : new int[] {10, 8, 6, 5, 4, 2};
            int capacity = extended ? 8 : 1;
            for (int cards = 0; cards <= 5; cards++) {
                if (cards > 0) {
                    assembler.getUpgrades().addItems(new ItemStack(AEItems.SPEED_CARD.asItem(), cards));
                }
                assembler.getPatternInventory().setItemDirect(0, craft.pattern().copy());
                var details = PatternDetailsHelper.decodePattern(craft.pattern(), helper.getLevel());
                for (int job = 0; job < capacity; job++) {
                    KeyCounter[] inputs = copyInputs(craft.inputs());
                    helper.assertTrue(assembler.pushPattern(details, inputs), "Expected free job slot " + job);
                    helper.assertTrue(inputs[0].isEmpty(), "Expected accepted ingredients to be consumed once");
                }
                ListTag accepted = savedJobs(assembler, helper);
                helper.assertTrue(accepted.size() == capacity, "Expected every job to be accepted independently");
                for (Tag saved : accepted) {
                    helper.assertTrue(((CompoundTag) saved).getInt("CraftTicks") == durations[cards],
                            "Expected acceleration-dependent duration for " + cards + " cards");
                }
                KeyCounter[] rejected = copyInputs(craft.inputs());
                helper.assertTrue(!assembler.pushPattern(details, rejected) && !rejected[0].isEmpty(),
                        "Expected busy rejection to preserve input");
                assembler.getUpgrades().clear();
                for (int tick = 1; tick < durations[cards]; tick++) {
                    assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
                }
                helper.assertTrue(assembler.isCrafting(), "Expected accepted jobs to remain active until their last tick");
                for (Tag saved : savedJobs(assembler, helper)) {
                    helper.assertTrue(((CompoundTag) saved).getInt("CraftTicks") == 1,
                            "Expected all jobs to advance together and retain their duration after card removal");
                }
                assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
                helper.assertTrue(!assembler.isCrafting() && !assembler.getReturnInventory().isEmpty(),
                        "Expected completion exactly at the accelerated duration");
                for (Tag saved : savedJobs(assembler, helper)) {
                    helper.assertTrue(((CompoundTag) saved).getInt("CraftTicks") == 0,
                            "Expected any result that exceeds the return buffer to retain a completed job");
                }
                for (int tick = 0; tick < 3; tick++) {
                    assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
                }
                List<ItemStack> drops = assembler.collectPendingDrops();
                helper.assertTrue(drops.stream()
                        .filter(stack -> AEItemKey.of(stack).equals(AEItemKey.of(craft.output())))
                        .mapToInt(ItemStack::getCount).sum() == craft.output().getCount() * capacity,
                        "Expected blocked output retries to preserve every craft result exactly once");
                helper.assertTrue(assembler.collectPendingDrops().isEmpty(), "Expected no repeated output drop");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty3x3x3")
    public static void eightJobsRoundTripAndDropFullCountsWithoutLegacyDuplication(GameTestHelper helper) {
        helper.setBlock(ROOT, ModBlocks.EXTENDED_TACZ_ASSEMBLER_AMMO.get());
        AEMolecularAssemblerBlockEntity assembler = helper.getBlockEntity(ROOT);
        CompoundTag tag = assembler.saveWithFullMetadata(helper.getLevel().registryAccess());
        ListTag jobs = new ListTag();
        int total = 0;
        for (int job = 0; job < 8; job++) {
            int count = 130 + job;
            jobs.add(savedJob(helper, Items.DIAMOND, count, 20 - job,
                    helper.absolutePos(ROOT.offset(job, 0, 0)), Direction.values()[job % 6]));
            total += count;
        }
        tag.put("Jobs", jobs);
        tag.put("PendingOutput", new ItemStack(Items.EMERALD).save(helper.getLevel().registryAccess()));
        tag.putInt("PendingOutputCount", 99);
        assembler.loadTag(tag, helper.getLevel().registryAccess());
        CompoundTag saved = assembler.saveWithFullMetadata(helper.getLevel().registryAccess());
        assembler.loadTag(saved, helper.getLevel().registryAccess());
        helper.assertTrue(savedJobs(assembler, helper).equals(jobs),
                "Expected all eight full counts, legacy-length timers and independent destinations after reload");
        helper.assertTrue(!saved.contains("PendingOutput"), "Expected the migrated save to contain Jobs only");
        assembler.getPendingOutputPreview().setCount(1);
        helper.assertTrue(assembler.getPendingOutputPreview().getCount() == 130,
                "Expected preview mutation not to affect the first job");
        var normal = new AEMolecularAssemblerBlockEntity(helper.absolutePos(ROOT),
                ModBlocks.TACZ_ASSEMBLER_AMMO.get().defaultBlockState());
        normal.setLevel(helper.getLevel());
        normal.loadTag(saved, helper.getLevel().registryAccess());
        helper.assertTrue(savedJobs(normal, helper).size() == 8,
                "Expected loaded work not to be truncated by the normal tier capacity");
        List<ItemStack> drops = assembler.collectPendingDrops();
        helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.DIAMOND))
                .mapToInt(ItemStack::getCount).sum() == total, "Expected full output from every job exactly once");
        helper.assertTrue(drops.stream().noneMatch(stack -> stack.is(Items.EMERALD)),
                "Expected Jobs to suppress the legacy fields rather than duplicate them");
        helper.assertTrue(assembler.collectPendingDrops().isEmpty(), "Expected no repeated job drops");
        helper.succeed();
    }

    @GameTest(template = "empty3x3x3")
    public static void blockedJobDoesNotStallPartialExternalOrInternalReturns(GameTestHelper helper) {
        helper.setBlock(ROOT, ModBlocks.EXTENDED_TACZ_ASSEMBLER_AMMO.get());
        helper.setBlock(ROOT.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(ROOT.above(), AEBlocks.DRIVE.block());
        helper.setBlock(ROOT.east(), Blocks.BARREL);
        DriveBlockEntity drive = helper.getBlockEntity(ROOT.above());
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
        BarrelBlockEntity barrel = helper.getBlockEntity(ROOT.east());
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
            barrel.setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        barrel.setItem(0, new ItemStack(Items.DIAMOND, 63));
        helper.runAfterDelay(30, () -> {
            AEMolecularAssemblerBlockEntity assembler = helper.getBlockEntity(ROOT);
            helper.assertTrue(drive.getMainNode().isActive()
                    && drive.getMainNode().getGrid() == assembler.getMainNode().getGrid(),
                    "Expected the drive's connectable bottom face on the powered assembler grid");
            helper.assertTrue(assembler.getStorage().insert(AEItemKey.of(Items.EMERALD), 11,
                    Actionable.SIMULATE, IActionSource.ofMachine(assembler)) == 11,
                    "Expected mounted storage to accept the internal return before testing job scheduling");
            CompoundTag tag = assembler.saveWithFullMetadata(helper.getLevel().registryAccess());
            ListTag jobs = new ListTag();
            jobs.add(savedJob(helper, Items.DIAMOND, 130, 0, helper.absolutePos(ROOT.north()), Direction.SOUTH));
            jobs.add(savedJob(helper, Items.DIAMOND, 10, 1, helper.absolutePos(ROOT.east()), Direction.WEST));
            jobs.add(savedJob(helper, Items.EMERALD, 11, 2, null, null));
            tag.put("Jobs", jobs);
            assembler.loadTag(tag, helper.getLevel().registryAccess());
            assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
            ListTag afterFirstTick = savedJobs(assembler, helper);
            helper.assertTrue(afterFirstTick.size() == 3
                    && afterFirstTick.getCompound(1).getInt("PendingOutputCount") == 9
                    && afterFirstTick.getCompound(2).getInt("CraftTicks") == 1,
                    "Expected blocked first job not to stop partial output or later job progress");
            helper.assertTrue(barrel.getItem(0).getCount() == 64, "Expected exactly one item accepted externally");
            helper.assertTrue(assembler.isCrafting(),
                    "Expected a later timed job to keep crafting active while the first job is blocked");
            assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
            helper.assertTrue(savedJobs(assembler, helper).size() == 2,
                    "Expected internal job to finish independently of two blocked external jobs");
            helper.assertTrue(assembler.getMainNode().getGrid().getStorageService().getInventory()
                    .getAvailableStacks().get(AEItemKey.of(Items.EMERALD)) == 11,
                    "Expected the internal route to return its exact output to ME storage");
            barrel.setItem(1, ItemStack.EMPTY);
            assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
            helper.assertTrue(savedJobs(assembler, helper).size() == 1 && barrel.getItem(1).getCount() == 9,
                    "Expected partial external remainder to retry without duplicating the first inserted item");
            helper.setBlock(ROOT.north(), Blocks.BARREL);
            assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
            BarrelBlockEntity finalDestination = helper.getBlockEntity(ROOT.north());
            int returned = 0;
            for (int slot = 0; slot < finalDestination.getContainerSize(); slot++) {
                returned += finalDestination.getItem(slot).getCount();
            }
            helper.assertTrue(returned == 130 && savedJobs(assembler, helper).isEmpty(),
                    "Expected unblocking the last destination to return every remaining item and drain all jobs");
            CompoundTag drained = assembler.saveWithFullMetadata(helper.getLevel().registryAccess());
            assembler.loadTag(drained, helper.getLevel().registryAccess());
            helper.assertTrue(savedJobs(assembler, helper).isEmpty() && assembler.acceptsPlans(),
                    "Expected empty Jobs to remain empty and reusable after reload");
            CraftCase craft = findCraft(helper);
            assembler.getPatternInventory().setItemDirect(0, craft.pattern().copy());
            helper.assertTrue(assembler.pushPattern(PatternDetailsHelper.decodePattern(craft.pattern(), helper.getLevel()),
                    copyInputs(craft.inputs())), "Expected a new craft after the final job drained and reloaded");
            helper.succeed();
        });
    }

    @GameTest(template = "empty3x3x3")
    public static void returnBufferPreservesOversizedOutputAcrossFullSlotsAndReload(GameTestHelper helper) {
        helper.setBlock(ROOT, ModBlocks.TACZ_ASSEMBLER_AMMO.get());
        helper.setBlock(ROOT.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(30, () -> {
            AEMolecularAssemblerBlockEntity assembler = helper.getBlockEntity(ROOT);
            var registries = helper.getLevel().registryAccess();
            CompoundTag tag = assembler.saveWithFullMetadata(registries);
            ListTag pending = new ListTag();
            pending.add(savedJob(helper, Items.DIAMOND, 600, 2, null, null));
            tag.put("Jobs", pending);
            assembler.loadTag(tag, registries);
            assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
            helper.assertTrue(assembler.getReturnInventory().isEmpty(),
                    "Expected unfinished output to remain inaccessible to return inventory");
            assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
            helper.assertTrue(assembler.getReturnInventory().size() == 9
                    && savedJobs(assembler, helper).getCompound(0).getInt("PendingOutputCount") == 24,
                    "Expected nine full stacks and the oversized remainder retained in the job");
            CompoundTag saved = assembler.saveWithFullMetadata(registries);
            assembler.loadTag(saved, registries);
            helper.assertTrue(saved.getList("ReturnInventory", Tag.TAG_COMPOUND)
                    .equals(assembler.saveWithFullMetadata(registries).getList("ReturnInventory", Tag.TAG_COMPOUND)),
                    "Expected all nine return slots to survive reload");
            helper.assertTrue(helper.getLevel().getCapability(appeng.api.AECapabilities.GENERIC_INTERNAL_INV,
                    helper.absolutePos(ROOT), Direction.UP) == null,
                    "Expected no external return capability that could accept ingredients while the assembler is busy");
            ItemStack taken = assembler.getReturnInventory().createMenuWrapper().extractItem(8, 64, false);
            assembler.tickingRequest(assembler.getMainNode().getNode(), 1);
            helper.assertTrue(taken.getCount() == 64 && savedJobs(assembler, helper).isEmpty()
                    && assembler.tickingRequest(assembler.getMainNode().getNode(), 1)
                            != appeng.api.networking.ticking.TickRateModulation.SLEEP,
                    "Expected freed GUI space to accept the remainder and blocked buffer to keep retrying");
            List<ItemStack> drops = assembler.collectPendingDrops();
            helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount).sum() + taken.getCount() == 600
                    && assembler.collectPendingDrops().isEmpty(),
                    "Expected GUI extraction and break drops to account for every item exactly once");
            helper.succeed();
        });
    }

    private static CompoundTag savedJob(GameTestHelper helper, net.minecraft.world.item.Item item, int count,
            int ticks, BlockPos destination, Direction face) {
        CompoundTag job = new CompoundTag();
        job.put("PendingOutput", new ItemStack(item).save(helper.getLevel().registryAccess()));
        job.putInt("PendingOutputCount", count);
        job.putInt("CraftTicks", ticks);
        if (destination != null) {
            job.putLong("ReturnPos", destination.asLong());
        }
        if (face != null) {
            job.putInt("ReturnFace", face.get3DDataValue());
        }
        return job;
    }

    private static CraftCase findCraft(GameTestHelper helper) {
        var index = TimelessAPI.getCommonBlockIndex(AETaCZWorkbenchIds.AMMO_WORKBENCH_ID).orElseThrow();
        for (var holder : helper.getLevel().getRecipeManager()
                .getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
            GunSmithTableRecipe recipe = holder.value();
            if ((index.getFilter() != null && !index.getFilter().contains(holder.id()))
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
            ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(ingredients,
                    List.of(new GenericStack(AEItemKey.of(output), output.getCount())));
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

    private static ListTag savedJobs(AEMolecularAssemblerBlockEntity assembler, GameTestHelper helper) {
        return assembler.saveWithFullMetadata(helper.getLevel().registryAccess()).getList("Jobs", Tag.TAG_COMPOUND);
    }

    private record CraftCase(ItemStack pattern, List<GenericStack> inputs, ItemStack output) {
    }
}
