package me.myogoo.appliedtacz.block.blcokentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.DimensionalBlockPos;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.me.helpers.MachineSource;
import appeng.util.Platform;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.crafting.AssemblerRecipeMatcher;
import me.myogoo.appliedtacz.crafting.AssemblerCraftingSpeed;
import me.myogoo.appliedtacz.crafting.TaCZPatternEncodingRecipes;
import me.myogoo.appliedtacz.crafting.TaCZPatternMetadata;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.init.AETaCZBlockEntity;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AEMolecularAssemblerBlockEntity extends AEBaseBlockEntity
        implements IGridConnectedBlockEntity, IGridTickable, ICraftingProvider, InternalInventoryHost, IUpgradeableObject,
        PatternContainer {
    public static final int PATTERN_SLOTS = 9;
    public static final int UPGRADE_SLOTS = 5;
    private static final String JOBS_TAG = "Jobs";
    private static final String ID_TAG = "BlockId";
    private static final String PENDING_OUTPUT_TAG = "PendingOutput";
    private static final String PENDING_COUNT_TAG = "PendingOutputCount";
    private static final String PATTERNS_TAG = "Patterns";
    private static final String CRAFT_TICKS_TAG = "CraftTicksRemaining";
    private static final String INTERNAL_OUTPUT_TAG = "InternalOutput";
    private static final String RETURN_POS_TAG = "ReturnPos";
    private static final String RETURN_FACE_TAG = "ReturnFace";
    private static final double POWER_PER_CRAFT = 1.0;

    private final IManagedGridNode mainNode = createMainNode()
            .setVisualRepresentation(getBlockState().getBlock())
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setInWorldNode(true)
            .setTagName("main")
            .setFlags(GridFlags.REQUIRE_CHANNEL);
    private final AppEngInternalInventory patternInventory = new AppEngInternalInventory(this, PATTERN_SLOTS, 1,
            new IAEItemFilter() {
                @Override
                public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                    return acceptsPattern(stack);
                }
            });
    private final MachineSource actionSource = new MachineSource(this);
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(getBlockState().getBlock(),
            UPGRADE_SLOTS, this::saveChanges);
    private List<IPatternDetails> patterns = List.of();
    private final LazyOptional<ICraftingMachine> craftingMachine = LazyOptional.of(() -> new ICraftingMachine() {
        @Override
        public PatternContainerGroup getCraftingMachineInfo() {
            return machineInfo();
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
            return ejectionDirection != null
                    && acceptPattern(patternDetails, inputs, worldPosition.relative(ejectionDirection),
                            ejectionDirection.getOpposite());
        }

        @Override
        public boolean acceptsPlans() {
            return canAcceptPlans();
        }
    });

    private @Nullable ResourceLocation id;
    private final List<CraftJob> jobs = new ArrayList<>();
    private boolean clientCrafting;
    private boolean clientNetworkPowered;
    private boolean clientNetworkOnline;
    private boolean clientGridBooted;

    public AEMolecularAssemblerBlockEntity(BlockPos pos, BlockState blockState) {
        super(AETaCZBlockEntity.AE_MOLECULAR_ASSEMBLER.get(), pos, blockState);
        this.mainNode.setIdlePowerUsage(POWER_PER_CRAFT).addService(IGridTickable.class, this)
                .addService(ICraftingProvider.class, this);
    }

    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE);
    }

    public void setId(@Nullable ResourceLocation id) {
        this.id = id;
        if (this.level != null) {
            updatePatterns();
            saveChanges();
            markForUpdate();
        }
    }

    public @Nullable ResourceLocation getId() {
        return this.id;
    }

    @Override
    public IManagedGridNode getMainNode() {
        return this.mainNode;
    }

    @Override
    public @Nullable IGridNode getGridNode() {
        return this.mainNode.getNode();
    }

    @Override
    public @Nullable IGridNode getGridNode(@Nullable Direction dir) {
        return canExposeNodeOnSide(dir) ? this.mainNode.getNode() : null;
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        Set<Direction> sides = EnumSet.allOf(Direction.class);
        sides.removeIf(side -> !canExposeNodeOnSide(side));
        return sides;
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        this.mainNode.loadFromNBT(tag);
        this.id = tag.contains(ID_TAG, Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString(ID_TAG))
                : AETaCZWorkbenchIndex.getDefaultBlockId(getBlockState().getBlock());
        this.jobs.clear();
        if (tag.contains(JOBS_TAG, Tag.TAG_LIST)) {
            for (Tag entry : tag.getList(JOBS_TAG, Tag.TAG_COMPOUND)) {
                loadJob((CompoundTag) entry);
            }
        } else {
            loadJob(tag);
        }
        this.patternInventory.clear();
        this.patternInventory.readFromNBT(tag, PATTERNS_TAG);
        this.upgrades.clear();
        this.upgrades.readFromNBT(tag, "Upgrades");
        updatePatterns();
    }

    private void loadJob(CompoundTag tag) {
        ItemStack output = ItemStack.of(tag.getCompound(PENDING_OUTPUT_TAG));
        if (tag.contains(PENDING_COUNT_TAG, Tag.TAG_INT)) {
            output.setCount(Math.max(0, tag.getInt(PENDING_COUNT_TAG)));
        }
        if (!output.isEmpty()) {
            this.jobs.add(new CraftJob(output, Math.max(0, Math.min(20, tag.getInt(CRAFT_TICKS_TAG))),
                    tag.contains(RETURN_POS_TAG, Tag.TAG_LONG) ? BlockPos.of(tag.getLong(RETURN_POS_TAG)) : null,
                    tag.contains(RETURN_FACE_TAG, Tag.TAG_INT)
                            ? Direction.from3DDataValue(tag.getInt(RETURN_FACE_TAG)) : null,
                    tag.getBoolean(INTERNAL_OUTPUT_TAG)));
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        this.mainNode.saveToNBT(tag);
        this.patternInventory.writeToNBT(tag, PATTERNS_TAG);
        this.upgrades.writeToNBT(tag, "Upgrades");
        if (this.id != null) {
            tag.putString(ID_TAG, this.id.toString());
        }
        ListTag savedJobs = new ListTag();
        for (CraftJob job : this.jobs) {
            CompoundTag savedJob = new CompoundTag();
            ItemStack output = job.output.copy();
            output.setCount(1);
            savedJob.put(PENDING_OUTPUT_TAG, output.save(new CompoundTag()));
            savedJob.putInt(PENDING_COUNT_TAG, job.output.getCount());
            savedJob.putInt(CRAFT_TICKS_TAG, job.remainingTicks);
            savedJob.putBoolean(INTERNAL_OUTPUT_TAG, job.internalOutput);
            if (job.returnPos != null) {
                savedJob.putLong(RETURN_POS_TAG, job.returnPos.asLong());
            }
            if (job.returnFace != null) {
                savedJob.putInt(RETURN_FACE_TAG, job.returnFace.get3DDataValue());
            }
            savedJobs.add(savedJob);
        }
        tag.put(JOBS_TAG, savedJobs);
    }

    @Override
    protected void writeToStream(FriendlyByteBuf data) {
        data.writeBoolean(this.id != null);
        if (this.id != null) {
            data.writeResourceLocation(this.id);
        }
        data.writeBoolean(this.mainNode.isPowered());
        data.writeBoolean(this.mainNode.isOnline());
        data.writeBoolean(this.mainNode.hasGridBooted());
        data.writeBoolean(isCrafting());
    }

    @Override
    protected boolean readFromStream(FriendlyByteBuf data) {
        ResourceLocation oldId = this.id;
        this.id = data.readBoolean() ? data.readResourceLocation() : null;
        boolean networkPowered = data.readBoolean();
        boolean networkOnline = data.readBoolean();
        boolean gridBooted = data.readBoolean();
        boolean crafting = data.readBoolean();
        boolean changed = !java.util.Objects.equals(oldId, this.id)
                || networkPowered != this.clientNetworkPowered
                || networkOnline != this.clientNetworkOnline
                || gridBooted != this.clientGridBooted || crafting != this.clientCrafting;
        this.clientNetworkPowered = networkPowered;
        this.clientNetworkOnline = networkOnline;
        this.clientGridBooted = gridBooted;
        this.clientCrafting = crafting;
        return changed;
    }

    @Override
    public void onReady() {
        super.onReady();
        this.mainNode.create(this.level, this.worldPosition);
        updatePatterns();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.mainNode.destroy();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.mainNode.destroy();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        scheduleInit();
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == Capabilities.IN_WORLD_GRID_NODE_HOST && canExposeNodeOnSide(side)) {
            return LazyOptional.of(() -> this).cast();
        }
        if (cap == Capabilities.CRAFTING_MACHINE && canExposeCraftingOnSide(side)) {
            return this.craftingMachine.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, this.jobs.isEmpty(), true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.jobs.isEmpty()) {
            return TickRateModulation.SLEEP;
        }
        if (!this.mainNode.isActive()) {
            return TickRateModulation.SLEEP;
        }
        for (var iterator = this.jobs.iterator(); iterator.hasNext();) {
            CraftJob job = iterator.next();
            if (job.remainingTicks > 0) {
                job.remainingTicks--;
                saveChanges();
                if (job.remainingTicks == 0) {
                    markForUpdate();
                }
            }
            if (job.remainingTicks == 0 && tryReturnOutput(job)) {
                iterator.remove();
                saveChanges();
            }
        }
        return this.jobs.isEmpty() ? TickRateModulation.SLEEP
                : this.jobs.stream().anyMatch(job -> job.remainingTicks > 0)
                        ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        syncNetworkState();
        updatePatterns();
        if (!this.jobs.isEmpty() && this.mainNode.isActive()) {
            wakeDevice();
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    public ItemStack createDropStack() {
        if (getBlockState().getBlock() instanceof MolecularAssemblerBlock block && block.isUniversal()) {
            return new ItemStack(block);
        }
        ResourceLocation blockId = getEffectiveBlockId();
        ItemStack stack = BlockItemBuilder.create(getBlockState().getBlock()).setId(blockId).build();
        if (stack.getItem() instanceof BlockItemDataAccessor accessor) {
            accessor.setBlockId(stack, blockId);
        }
        return stack;
    }

    public void popPendingOutput() {
        if (this.level != null && !this.jobs.isEmpty()) {
            for (ItemStack stack : collectPendingDrops()) {
                Block.popResource(this.level, this.worldPosition, stack);
            }
        }
    }

    public List<ItemStack> collectPendingDrops() {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack pattern : this.patternInventory) {
            if (!pattern.isEmpty()) {
                drops.add(pattern.copy());
            }
        }
        this.patternInventory.clear();
        for (ItemStack upgrade : this.upgrades) {
            if (!upgrade.isEmpty()) {
                drops.add(upgrade.copy());
            }
        }
        this.upgrades.clear();
        for (CraftJob job : this.jobs) {
            ItemStack output = job.output.copy();
            while (!output.isEmpty()) {
                drops.add(output.split(Math.min(64, output.getMaxStackSize())));
            }
        }
        this.jobs.clear();
        saveChanges();
        return drops;
    }

    public AppEngInternalInventory getPatternInventory() {
        return this.patternInventory;
    }

    @Override
    public @Nullable IGrid getGrid() {
        return this.mainNode.getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return this.patternInventory;
    }

    @Override
    public long getTerminalSortOrder() {
        return this.worldPosition.asLong();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        return machineInfo();
    }

    public boolean acceptsPattern(ItemStack stack) {
        if (this.level == null || !stack.is(AEItems.PROCESSING_PATTERN.asItem())) {
            return false;
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(stack, this.level);
        if (!(details instanceof AEProcessingPattern processing)) {
            return false;
        }
        KeyCounter[] inputs = new KeyCounter[] { new KeyCounter() };
        for (GenericStack input : processing.getSparseInputs()) {
            if (input != null) {
                inputs[0].add(input.what(), input.amount());
            }
        }
        return findRecipe(details, inputs) != null;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    private int getCraftTicks() {
        return AssemblerCraftingSpeed.craftTicks(isExtended(), getInstalledUpgrades(AEItems.SPEED_CARD));
    }

    private boolean isExtended() {
        return getBlockState().getBlock() instanceof MolecularAssemblerBlock block && block.isExtended();
    }

    public boolean isCrafting() {
        return isClientSide()
                ? this.clientCrafting && this.clientNetworkPowered && this.clientNetworkOnline
                : this.jobs.stream().anyMatch(job -> job.remainingTicks > 0) && this.mainNode.isActive();
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        updatePatterns();
    }

    private void updatePatterns() {
        if (isClientSide()) {
            return;
        }
        List<IPatternDetails> available = new ArrayList<>();
        for (ItemStack stack : this.patternInventory) {
            IPatternDetails details = PatternDetailsHelper.decodePattern(stack, this.level);
            if (details instanceof AEProcessingPattern processing) {
                KeyCounter[] inputs = new KeyCounter[] { new KeyCounter() };
                for (GenericStack input : processing.getSparseInputs()) {
                    if (input != null) {
                        inputs[0].add(input.what(), input.amount());
                    }
                }
                if (findRecipe(details, inputs) != null) {
                    available.add(details);
                }
            }
        }
        this.patterns = List.copyOf(available);
        ICraftingProvider.requestUpdate(this.mainNode);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return this.patterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        return this.patterns.contains(patternDetails) && acceptPattern(patternDetails, inputs, null, null);
    }

    @Override
    public boolean isBusy() {
        return !canAcceptPlans();
    }

    public boolean isNetworkPowered() {
        return this.level != null && this.level.isClientSide()
                ? this.clientNetworkPowered
                : this.mainNode.isPowered();
    }

    public boolean isNetworkOnline() {
        return this.level != null && this.level.isClientSide()
                ? this.clientNetworkOnline
                : this.mainNode.isOnline();
    }

    public boolean hasBootedGrid() {
        return this.level != null && this.level.isClientSide()
                ? this.clientGridBooted
                : this.mainNode.hasGridBooted();
    }

    public ItemStack getPendingOutput() {
        return this.jobs.isEmpty() ? ItemStack.EMPTY : this.jobs.get(0).output.copy();
    }

    public String getMenuWorkbenchId() {
        return getBlockState().getBlock() instanceof MolecularAssemblerBlock block && block.isUniversal()
                ? ""
                : getEffectiveBlockId().toString();
    }

    public @Nullable AEMolecularAssemblerBlockEntity getMenuRootAssembler() {
        return isValidMenuRoot() ? this : null;
    }

    public boolean isValidMenuRoot() {
        Level level = getLevel();
        return level != null && level.hasChunkAt(this.worldPosition)
                && level.getBlockEntity(this.worldPosition) == this && !isRemoved();
    }

    public boolean mayOpenMenu(Player player) {
        return isValidMenuRoot() && Platform.hasPermissions(new DimensionalBlockPos(this), player);
    }

    private PatternContainerGroup machineInfo() {
        ItemStack icon = createDropStack();
        Component name = getDisplayName();
        if (getBlockState().getBlock() instanceof MolecularAssemblerBlock block && !block.isUniversal()) {
            ResourceLocation workbenchId = getEffectiveBlockId();
            Component workbenchName = DefaultAssets.DEFAULT_BLOCK_ID.equals(workbenchId)
                    ? Component.translatable("gui.appliedtacz.assembler.workbench.gun")
                    : workbenchId.equals(ResourceLocation.fromNamespaceAndPath("tacz", "ammo_workbench"))
                            ? Component.translatable("gui.appliedtacz.assembler.workbench.ammo")
                            : workbenchId.equals(ResourceLocation.fromNamespaceAndPath("tacz", "attachment_workbench"))
                                    ? Component.translatable("gui.appliedtacz.assembler.workbench.attachment")
                                    : TimelessAPI.getCommonBlockIndex(workbenchId)
                                            .map(index -> index.getPojo()).map(pojo -> pojo.getName())
                                            .map(Component::translatable)
                                            .orElseGet(() -> Component.translatable(
                                                    "gui.appliedtacz.assembler.workbench_name"));
            name = Component.translatable(block.isExtended() ? "gui.appliedtacz.assembler.extended_title"
                    : "gui.appliedtacz.assembler.title", workbenchName);
        }
        return new PatternContainerGroup(AEItemKey.of(icon), name, List.of());
    }

    private boolean acceptPattern(IPatternDetails patternDetails, KeyCounter[] inputs, @Nullable BlockPos returnPos,
            @Nullable Direction returnFace) {
        if (!patternDetails.supportsPushInputsToExternalInventory() || this.level == null
                || this.level.isClientSide || !canAcceptPlans()) {
            return false;
        }

        GunSmithTableRecipe recipe = findRecipe(patternDetails, inputs);
        if (recipe == null || !chargeCraft()) {
            return false;
        }
        this.jobs.add(new CraftJob(recipe.getResultItem(this.level.registryAccess()).copy(), getCraftTicks(),
                returnPos == null ? null : returnPos.immutable(), returnFace, returnPos == null));
        AssemblerRecipeMatcher.consume(inputs);
        saveChanges();
        markForUpdate();
        wakeDevice();
        return true;
    }

    private @Nullable GunSmithTableRecipe findRecipe(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (this.level == null) {
            return null;
        }
        ItemStack encoded = patternDetails.getDefinition().toStack();
        ResourceLocation workbenchId = TaCZPatternMetadata.getWorkbenchId(encoded);
        if (TaCZPatternMetadata.hasWorkbenchId(encoded)
                && (workbenchId == null || !(getBlockState().getBlock() instanceof MolecularAssemblerBlock block
                        && block.isUniversal()) && !workbenchId.equals(getEffectiveBlockId()))) {
            return null;
        }
        for (GunSmithTableRecipe recipe : this.level.getRecipeManager()
                .getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get())) {
            ItemStack output = recipe.getResultItem(this.level.registryAccess());
            if (isRecipeAllowed(recipe)
                    && (workbenchId == null || TaCZPatternEncodingRecipes.isAllowed(workbenchId,
                            new TaCZPatternEncodingRecipes.Entry(recipe.getId(), recipe)))
                    && AssemblerRecipeMatcher.outputsMatch(output, Arrays.asList(patternDetails.getOutputs()))
                    && AssemblerRecipeMatcher.matches(recipe, inputs)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean isRecipeAllowed(GunSmithTableRecipe recipe) {
        WorkbenchUpgradeKind kind = getAssemblerKind();
        if (kind == null) {
            return getBlockState().getBlock() instanceof MolecularAssemblerBlock block && block.isUniversal();
        }
        ResourceLocation blockId = getEffectiveBlockId();
        if (WorkbenchUpgradeKind.forAssembler(blockId) != kind) {
            return false;
        }
        return TimelessAPI.getCommonBlockIndex(blockId).map(index -> {
            return (index.getFilter() == null || index.getFilter().contains(recipe.getId()))
                    && index.getData().getTabs().stream().anyMatch(tab -> tab.id().equals(recipe.getTab()));
        }).orElse(false);
    }

    private @Nullable WorkbenchUpgradeKind getAssemblerKind() {
        BlockState state = getBlockState();
        return state.getBlock() instanceof MolecularAssemblerBlock block
                ? block.getKind()
                : null;
    }

    private ResourceLocation getEffectiveBlockId() {
        if (this.id != null) {
            return this.id;
        }
        return AETaCZWorkbenchIndex.getDefaultBlockId(getBlockState().getBlock());
    }

    private boolean chargeCraft() {
        var grid = this.mainNode.getGrid();
        if (grid == null) {
            return false;
        }
        if (grid.getEnergyService().extractAEPower(POWER_PER_CRAFT, Actionable.SIMULATE,
                PowerMultiplier.CONFIG) < POWER_PER_CRAFT) {
            return false;
        }
        return grid.getEnergyService().extractAEPower(POWER_PER_CRAFT, Actionable.MODULATE,
                PowerMultiplier.CONFIG) >= POWER_PER_CRAFT;
    }

    private boolean tryReturnOutput(CraftJob job) {
        if (job.internalOutput) {
            var grid = this.mainNode.getGrid();
            if (grid == null) {
                return false;
            }
            long inserted = grid.getStorageService().getInventory().insert(AEItemKey.of(job.output),
                    job.output.getCount(), Actionable.MODULATE, this.actionSource);
            if (inserted <= 0) {
                return false;
            }
            job.output.shrink((int) inserted);
            saveChanges();
            return job.output.isEmpty();
        }
        if (this.level == null || job.returnPos == null || job.returnFace == null) {
            return false;
        }
        if (!this.level.hasChunkAt(job.returnPos)) {
            return false;
        }
        var transfer = InternalInventory.wrapExternal(this.level, job.returnPos, job.returnFace);
        if (transfer == null) {
            return false;
        }
        ItemStack remainder = transfer.addItems(job.output.copy());
        if (ItemStack.matches(remainder, job.output)) {
            return false;
        }
        job.output = remainder;
        saveChanges();
        return job.output.isEmpty();
    }

    private void wakeDevice() {
        this.mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

    private void syncNetworkState() {
        boolean networkPowered = this.mainNode.isPowered();
        boolean networkOnline = this.mainNode.isOnline();
        boolean gridBooted = this.mainNode.hasGridBooted();
        if (networkPowered == this.clientNetworkPowered
                && networkOnline == this.clientNetworkOnline
                && gridBooted == this.clientGridBooted) {
            return;
        }
        this.clientNetworkPowered = networkPowered;
        this.clientNetworkOnline = networkOnline;
        this.clientGridBooted = gridBooted;
        this.markForUpdate();
    }

    private boolean canExposeNodeOnSide(@Nullable Direction dir) {
        return dir != null;
    }

    private boolean canExposeCraftingOnSide(@Nullable Direction dir) {
        return dir != null;
    }

    private boolean canAcceptPlans() {
        return this.jobs.size() < (isExtended() ? 8 : 1)
                && this.mainNode.isActive();
    }

    private static final class CraftJob {
        private ItemStack output;
        private int remainingTicks;
        private final @Nullable BlockPos returnPos;
        private final @Nullable Direction returnFace;
        private final boolean internalOutput;

        private CraftJob(ItemStack output, int remainingTicks, @Nullable BlockPos returnPos,
                @Nullable Direction returnFace, boolean internalOutput) {
            this.output = output;
            this.remainingTicks = remainingTicks;
            this.returnPos = returnPos;
            this.returnFace = returnFace;
            this.internalOutput = internalOutput;
        }
    }
}
