package me.myogoo.appliedtacz.block.blcokentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.inventories.ItemTransfer;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.crafting.AssemblerRecipeMatcher;
import me.myogoo.appliedtacz.crafting.AssemblerCraftingSpeed;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.registry.ModBlockEntities;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AEMolecularAssemblerBlockEntity extends AEGunSmithTableBlockEntity
        implements ICraftingMachine, IGridTickable, ICraftingProvider, InternalInventoryHost, IUpgradeableObject {
    public static final int PATTERN_SLOTS = 9;
    public static final int UPGRADE_SLOTS = 5;
    private static final int MAX_SAVED_CRAFT_TICKS = 20;
    private static final String PENDING_OUTPUT_TAG = "PendingOutput";
    private static final String RETURN_POS_TAG = "ReturnPos";
    private static final String RETURN_FACE_TAG = "ReturnFace";
    private static final String JOBS_TAG = "Jobs";

    private final List<CraftJob> jobs = new ArrayList<>();
    private boolean clientCrafting;
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(getBlockState().getBlock(),
            UPGRADE_SLOTS, this::saveChanges);
    private final AppEngInternalInventory patterns = new AppEngInternalInventory(this, PATTERN_SLOTS, 1,
            new IAEItemFilter() {
                @Override
                public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
                    return stack.is(AEItems.PROCESSING_PATTERN.asItem());
                }
            });

    public AEMolecularAssemblerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.AE_MOLECULAR_ASSEMBLER.get(), pos, blockState);
        getMainNode().addService(IGridTickable.class, this);
        getMainNode().addService(ICraftingProvider.class, this);
    }

    public InternalInventory getPatternInventory() {
        return patterns;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    private int getCraftTicks() {
        return AssemblerCraftingSpeed.craftTicks(isExtended(), getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    public boolean isExtended() {
        return getBlockState().getBlock() instanceof MolecularAssemblerBlock block && block.isExtended();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        saveChanges();
        ICraftingProvider.requestUpdate(getMainNode());
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        if (level == null || getBlockState().getValue(MolecularAssemblerBlock.PART) != 0) {
            return List.of();
        }
        List<IPatternDetails> available = new ArrayList<>();
        for (ItemStack stack : patterns) {
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, level);
            if (details == null || !details.supportsPushInputsToExternalInventory()) {
                continue;
            }
            KeyCounter[] inputs = new KeyCounter[details.getInputs().length];
            boolean valid = true;
            for (int i = 0; i < inputs.length; i++) {
                var input = details.getInputs()[i];
                var choices = input.getPossibleInputs();
                if (choices.length == 0 || choices[0].amount() <= 0 || input.getMultiplier() <= 0
                        || input.getMultiplier() > Long.MAX_VALUE / choices[0].amount()) {
                    valid = false;
                    break;
                }
                inputs[i] = new KeyCounter();
                inputs[i].add(choices[0].what(), choices[0].amount() * input.getMultiplier());
            }
            if (valid && findRecipe(details, inputs) != null && !available.contains(details)) {
                available.add(details);
            }
        }
        return available;
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        for (ItemStack pattern : patterns) {
            if (details.getDefinition().equals(AEItemKey.of(pattern))) {
                return startCraft(details, inputs, null, null);
            }
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        return !acceptsPlans();
    }

    public boolean isCrafting() {
        return level != null && level.isClientSide
                ? clientCrafting && isNetworkPowered() && isNetworkOnline() && hasBootedGrid()
                : jobs.stream().anyMatch(job -> job.remainingTicks > 0) && getMainNode().isActive()
                        && getRootAssembler() == this;
    }

    public @Nullable ICraftingMachine getCraftingMachine(@Nullable Direction side) {
        Level level = getLevel();
        BlockState state = getBlockState();
        if (side == null
                || level == null
                || !(state.getBlock() instanceof MolecularAssemblerBlock block)
                || side == Direction.UP && block.partCount() != 1
                || !block.isValidPart(state)
                || block.isInternalFace(state, getBlockPos(), level, side)
                || getRootAssembler() == null) {
            return null;
        }
        return this;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        AEMolecularAssemblerBlockEntity root = getRootAssembler();
        ItemStack icon = new ItemStack((root != null ? root : this).getBlockState().getBlock());
        return new PatternContainerGroup(AEItemKey.of(icon), icon.getHoverName(), List.of());
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        AEMolecularAssemblerBlockEntity root = getRootAssembler();
        if (root == null) {
            return false;
        }
        if (root != this) {
            return root.pushPatternFrom(getBlockPos(), patternDetails, inputs, ejectionDirection);
        }
        return pushPatternFrom(getBlockPos(), patternDetails, inputs, ejectionDirection);
    }

    private boolean pushPatternFrom(BlockPos receivingPos, IPatternDetails patternDetails, KeyCounter[] inputs,
            Direction ejectionDirection) {
        return startCraft(patternDetails, inputs, receivingPos.relative(ejectionDirection),
                ejectionDirection.getOpposite());
    }

    private boolean startCraft(IPatternDetails patternDetails, KeyCounter[] inputs,
            @Nullable BlockPos destination, @Nullable Direction destinationFace) {
        Level level = getLevel();
        if (level == null || level.isClientSide || !acceptsPlans()
                || !patternDetails.supportsPushInputsToExternalInventory()) {
            return false;
        }

        GunSmithTableRecipe recipe = findRecipe(patternDetails, inputs);
        if (recipe == null) {
            return false;
        }

        var energy = getEnergySource();
        if (energy == null
                || energy.extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) < 1
                || energy.extractAEPower(1, Actionable.MODULATE, PowerMultiplier.CONFIG) < 1) {
            return false;
        }

        jobs.add(new CraftJob(recipe.getResultItem(level.registryAccess()).copy(), getCraftTicks(),
                destination, destinationFace));
        AssemblerRecipeMatcher.consume(inputs);
        saveChanges();
        markForUpdate();
        wakeTicker();
        return true;
    }

    private @Nullable GunSmithTableRecipe findRecipe(IPatternDetails patternDetails, KeyCounter[] inputs) {
        Level level = getLevel();
        MolecularAssemblerBlock block = getAssemblerBlock();
        WorkbenchUpgradeKind kind = block != null && !block.isUniversal() ? getDedicatedAssemblerKind(block) : null;
        if (level == null || block == null || (!block.isUniversal() && kind == null)) {
            return null;
        }

        for (RecipeHolder<GunSmithTableRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
            GunSmithTableRecipe recipe = holder.value();
            ItemStack expected = recipe.getResultItem(level.registryAccess());
            if ((block.isUniversal() || isRecipeForWorkbench(holder.id(), recipe, kind))
                    && AssemblerRecipeMatcher.outputsMatch(expected, patternDetails.getOutputs())
                    && AssemblerRecipeMatcher.matches(recipe, inputs)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean isRecipeForWorkbench(ResourceLocation recipeId, GunSmithTableRecipe recipe,
            WorkbenchUpgradeKind kind) {
        ResourceLocation blockId = AETaCZWorkbenchIds.getMenuBlockId(this);
        return TimelessAPI.getCommonBlockIndex(blockId)
                .filter(index -> WorkbenchUpgradeKind.forAssembler(blockId) == kind)
                .filter(index -> index.getFilter() == null || index.getFilter().contains(recipeId))
                .filter(index -> index.getData().getTabs().stream().anyMatch(tab -> tab.id().equals(recipe.getTab())))
                .isPresent();
    }

    @Override
    public boolean acceptsPlans() {
        AEMolecularAssemblerBlockEntity root = getRootAssembler();
        if (root != null && root != this) {
            return root.acceptsPlans();
        }
        return root != null && jobs.size() < (isExtended() ? 8 : 1) && getMainNode().isActive();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, jobs.isEmpty());
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!getMainNode().isActive() || getRootAssembler() != this) {
            return jobs.isEmpty() ? TickRateModulation.SLEEP : TickRateModulation.SLOWER;
        }
        boolean crafting = isCrafting();
        for (CraftJob job : jobs) {
            if (job.remainingTicks > 0) {
                job.remainingTicks--;
                saveChanges();
            }
            if (job.remainingTicks == 0) {
                pushPendingOutput(job);
            }
        }
        jobs.removeIf(job -> job.output.isEmpty());
        if (crafting != isCrafting()) {
            markForUpdate();
        }
        return jobs.isEmpty() ? TickRateModulation.SLEEP
                : isCrafting() ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    private void pushPendingOutput(CraftJob job) {
        Level level = getLevel();
        if (job.output.isEmpty() || level == null) {
            return;
        }
        int before = job.output.getCount();
        if (job.returnPos == null) {
            var storage = getStorage();
            if (storage == null) {
                return;
            }
            long inserted = storage.insert(AEItemKey.of(job.output), before, Actionable.MODULATE,
                    new MachineSource(this));
            job.output.shrink((int) inserted);
        } else {
            if (job.returnFace == null || !level.hasChunkAt(job.returnPos)) {
                return;
            }
            ItemTransfer inventory = InternalInventory.wrapExternal(level, job.returnPos, job.returnFace);
            if (inventory == null) {
                return;
            }
            job.output = inventory.addItems(job.output.copy());
        }
        if (job.output.isEmpty() || job.output.getCount() != before) {
            saveChanges();
            markForUpdate();
        }
    }

    private void wakeTicker() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    public List<ItemStack> collectPendingDrops() {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack upgrade : upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
        upgrades.clear();
        for (int slot = 0; slot < patterns.size(); slot++) {
            if (!patterns.getStackInSlot(slot).isEmpty()) {
                drops.add(patterns.getStackInSlot(slot).copy());
                patterns.setItemDirect(slot, ItemStack.EMPTY);
            }
        }
        if (!jobs.isEmpty()) {
            for (CraftJob job : jobs) {
                while (!job.output.isEmpty()) {
                    drops.add(job.output.split(Math.min(64, job.output.getMaxStackSize())));
                }
            }
            jobs.clear();
            saveChanges();
        }
        return drops;
    }

    public ItemStack getPendingOutputPreview() {
        AEMolecularAssemblerBlockEntity root = getRootAssembler();
        return root != null && !root.jobs.isEmpty() ? root.jobs.getFirst().output.copy() : ItemStack.EMPTY;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        patterns.clear();
        patterns.readFromNBT(tag, "Patterns", registries);
        upgrades.clear();
        upgrades.readFromNBT(tag, "Upgrades", registries);
        jobs.clear();
        if (tag.contains(JOBS_TAG)) {
            for (Tag saved : tag.getList(JOBS_TAG, Tag.TAG_COMPOUND)) {
                loadJob((CompoundTag) saved, registries);
            }
        } else {
            loadJob(tag, registries);
        }
        ICraftingProvider.requestUpdate(getMainNode());
    }

    private void loadJob(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack output = tag.contains(PENDING_OUTPUT_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound(PENDING_OUTPUT_TAG)) : ItemStack.EMPTY;
        if (!output.isEmpty() && tag.contains("PendingOutputCount", Tag.TAG_INT)) {
            output.setCount(Math.max(0, tag.getInt("PendingOutputCount")));
        }
        if (!output.isEmpty()) {
            jobs.add(new CraftJob(output, Math.clamp(tag.getInt("CraftTicks"), 0, MAX_SAVED_CRAFT_TICKS),
                    tag.contains(RETURN_POS_TAG, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(RETURN_POS_TAG)) : null,
                    tag.contains(RETURN_FACE_TAG, Tag.TAG_INT)
                            ? Direction.from3DDataValue(tag.getInt(RETURN_FACE_TAG)) : null));
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        patterns.writeToNBT(tag, "Patterns", registries);
        upgrades.writeToNBT(tag, "Upgrades", registries);
        ListTag savedJobs = new ListTag();
        for (CraftJob job : jobs) {
            CompoundTag saved = new CompoundTag();
            saved.putInt("CraftTicks", job.remainingTicks);
            saved.put(PENDING_OUTPUT_TAG, job.output.copyWithCount(1).save(registries));
            saved.putInt("PendingOutputCount", job.output.getCount());
            if (job.returnPos != null) {
                saved.putLong(RETURN_POS_TAG, job.returnPos.asLong());
            }
            if (job.returnFace != null) {
                saved.putInt(RETURN_FACE_TAG, job.returnFace.get3DDataValue());
            }
            savedJobs.add(saved);
        }
        tag.put(JOBS_TAG, savedJobs);
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(isCrafting());
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean crafting = data.readBoolean();
        changed |= clientCrafting != crafting;
        clientCrafting = crafting;
        return changed;
    }

    public @Nullable AEMolecularAssemblerBlockEntity getRootAssembler() {
        Level level = getLevel();
        BlockState state = getBlockState();
        if (level == null || !(state.getBlock() instanceof MolecularAssemblerBlock block) || !block.isValidPart(state)) {
            return null;
        }
        BlockPos root = block.getRootPos(getBlockPos(), state);
        if (!level.hasChunkAt(root)) {
            return null;
        }
        BlockState rootState = level.getBlockState(root);
        if (!rootState.is(block)
                || rootState.getValue(MolecularAssemblerBlock.PART) != 0
                || rootState.getValue(MolecularAssemblerBlock.FACING) != state.getValue(MolecularAssemblerBlock.FACING)
                || !block.isStructureLoadedAndValid(root, rootState, level)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(root);
        return entity instanceof AEMolecularAssemblerBlockEntity assembler ? assembler : null;
    }

    private @Nullable MolecularAssemblerBlock getAssemblerBlock() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof MolecularAssemblerBlock block ? block : null;
    }

    private @Nullable WorkbenchUpgradeKind getDedicatedAssemblerKind(MolecularAssemblerBlock block) {
        WorkbenchUpgradeKind kind = block.kind();
        if (kind != null) {
            return AETaCZWorkbenchIndex.getKind(AETaCZWorkbenchIds.getMenuBlockId(this))
                    .filter(found -> found == kind)
                    .orElse(null);
        }
        return null;
    }

    private static final class CraftJob {
        private ItemStack output;
        private int remainingTicks;
        private final @Nullable BlockPos returnPos;
        private final @Nullable Direction returnFace;

        private CraftJob(ItemStack output, int remainingTicks, @Nullable BlockPos returnPos,
                @Nullable Direction returnFace) {
            this.output = output;
            this.remainingTicks = remainingTicks;
            this.returnPos = returnPos;
            this.returnFace = returnFace;
        }
    }
}
