package me.myogoo.appliedtacz.block.blcokentity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.storage.ISubMenuHost;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.menu.ISubMenu;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import me.myogoo.appliedtacz.init.AETaCZBlockEntity;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public class AEGunSmithTableBlockEntity extends AEBaseBlockEntity
        implements IGridConnectedBlockEntity, MenuProvider, ISubMenuHost {
    private static final String ID_TAG = "BlockId";

    private final IManagedGridNode mainNode = createMainNode()
            .setVisualRepresentation(getBlockState().getBlock())
            .setExposedOnSides(EnumSet.complementOf(EnumSet.of(Direction.UP)))
            .setInWorldNode(true)
            .setTagName("main")
            .setFlags(GridFlags.REQUIRE_CHANNEL);

    private @Nullable ResourceLocation id;
    private boolean clientNetworkPowered;
    private boolean clientNetworkOnline;
    private boolean clientGridBooted;

    public AEGunSmithTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(AETaCZBlockEntity.AE_GUN_SMITH_TABLE.get(), pos, blockState);
    }

    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, BlockEntityNodeListener.INSTANCE);
    }

    public void setId(@Nullable ResourceLocation id) {
        this.id = id;
        if (this.level != null) {
            saveChanges();
            markForUpdate();
        }
    }

    public @Nullable ResourceLocation getId() {
        return this.id;
    }

    @Override
    public IManagedGridNode getMainNode() {
        return getEffectiveMainNode();
    }

    @Override
    public @Nullable IGridNode getGridNode() {
        return getEffectiveMainNode().getNode();
    }

    @Override
    public @Nullable IGridNode getGridNode(@Nullable Direction dir) {
        return canExposeNodeOnSide(dir) ? getEffectiveMainNode().getNode() : null;
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return getEffectiveMainNode().getNode();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        Set<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(orientation.getSide(RelativeSide.TOP));
        return sides;
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        this.mainNode.loadFromNBT(tag);

        if (tag.contains(ID_TAG, Tag.TAG_STRING)) {
            this.id = ResourceLocation.tryParse(tag.getString(ID_TAG));
        } else {
            this.id = AETaCZWorkbenchIndex.getDefaultBlockId(getBlockState().getBlock());
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        this.mainNode.saveToNBT(tag);
        if (this.id != null) {
            tag.putString(ID_TAG, this.id.toString());
        }
    }

    @Override
    protected void writeToStream(FriendlyByteBuf data) {
        IManagedGridNode effectiveNode = getEffectiveMainNode();
        data.writeBoolean(this.id != null);
        if (this.id != null) {
            data.writeResourceLocation(this.id);
        }
        data.writeBoolean(effectiveNode.isPowered());
        data.writeBoolean(effectiveNode.isOnline());
        data.writeBoolean(effectiveNode.hasGridBooted());
    }

    @Override
    protected boolean readFromStream(FriendlyByteBuf data) {
        ResourceLocation oldId = this.id;
        this.id = data.readBoolean() ? data.readResourceLocation() : null;
        boolean networkPowered = data.readBoolean();
        boolean networkOnline = data.readBoolean();
        boolean gridBooted = data.readBoolean();

        boolean changed = !Objects.equals(oldId, this.id)
                || networkPowered != this.clientNetworkPowered
                || networkOnline != this.clientNetworkOnline
                || gridBooted != this.clientGridBooted;
        this.clientNetworkPowered = networkPowered;
        this.clientNetworkOnline = networkOnline;
        this.clientGridBooted = gridBooted;
        return changed;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (isRootPart()) {
            this.mainNode.destroy();
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (isRootPart()) {
            this.mainNode.destroy();
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (isRootPart()) {
            scheduleInit();
        }
    }

    @Override
    public void onReady() {
        super.onReady();
        if (isRootPart()) {
            this.mainNode.create(this.level, this.worldPosition);
        }
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == Capabilities.IN_WORLD_GRID_NODE_HOST && canExposeNodeOnSide(side)) {
            return LazyOptional.of(() -> this).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public void onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State reason) {
        syncNetworkState();
    }

    private void syncNetworkState() {
        IManagedGridNode effectiveNode = getEffectiveMainNode();
        boolean networkPowered = effectiveNode.isPowered();
        boolean networkOnline = effectiveNode.isOnline();
        boolean gridBooted = effectiveNode.hasGridBooted();
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

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AEGunSmithTableMenu(id, inventory, this);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, this, buf -> {
                buf.writeBlockPos(this.worldPosition);
                buf.writeResourceLocation(AETaCZWorkbenchIndex.getMenuBlockId(this));
            });
        }
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getBlockState().getBlock());
    }

    public boolean isNetworkPowered() {
        return this.level != null && this.level.isClientSide()
                ? this.clientNetworkPowered
                : getEffectiveMainNode().isPowered();
    }

    public boolean isNetworkOnline() {
        return this.level != null && this.level.isClientSide()
                ? this.clientNetworkOnline
                : getEffectiveMainNode().isOnline();
    }

    public boolean hasBootedGrid() {
        return this.level != null && this.level.isClientSide()
                ? this.clientGridBooted
                : getEffectiveMainNode().hasGridBooted();
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition.offset(-2, 0, -2), this.worldPosition.offset(2, 1, 2));
    }

    private boolean isRootPart() {
        BlockState state = getBlockState();
        return !(state.getBlock() instanceof AbstractGunSmithTableBlock tableBlock)
                || tableBlock.isRoot(state);
    }

    private boolean canExposeNodeOnSide(@Nullable Direction dir) {
        return dir != null && dir != Direction.UP && !isInternalMultiblockFace(dir);
    }

    private boolean isInternalMultiblockFace(Direction dir) {
        Level level = getLevel();
        BlockState state = getBlockState();
        if (level == null || !(state.getBlock() instanceof AbstractGunSmithTableBlock tableBlock)) {
            return false;
        }

        BlockPos adjacentPos = getBlockPos().relative(dir);
        BlockState adjacentState = level.getBlockState(adjacentPos);
        if (!(adjacentState.getBlock() instanceof AbstractGunSmithTableBlock adjacentTableBlock)) {
            return false;
        }

        BlockPos rootPos = tableBlock.getRootPos(getBlockPos(), state);
        BlockPos adjacentRootPos = adjacentTableBlock.getRootPos(adjacentPos, adjacentState);
        return rootPos.equals(adjacentRootPos);
    }

    private IManagedGridNode getEffectiveMainNode() {
        AEGunSmithTableBlockEntity rootTable = getRootTable();
        return rootTable != null ? rootTable.mainNode : mainNode;
    }

    private @Nullable AEGunSmithTableBlockEntity getRootTable() {
        if (isRootPart()) {
            return this;
        }

        Level level = getLevel();
        BlockState state = getBlockState();
        if (level == null || !(state.getBlock() instanceof AbstractGunSmithTableBlock tableBlock)) {
            return null;
        }

        BlockPos rootPos = tableBlock.getRootPos(getBlockPos(), state);
        BlockEntity blockEntity = level.getBlockEntity(rootPos);
        return blockEntity instanceof AEGunSmithTableBlockEntity table ? table : null;
    }
}
