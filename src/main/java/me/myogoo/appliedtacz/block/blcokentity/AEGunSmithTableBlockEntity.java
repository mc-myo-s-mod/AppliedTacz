package me.myogoo.appliedtacz.block.blcokentity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridMultiblock;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.capabilities.Capabilities;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.GunSmithTableBlockC;
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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Set;

public class AEGunSmithTableBlockEntity extends AEBaseBlockEntity
        implements IGridConnectedBlockEntity, MenuProvider {
    private static final String ID_TAG = "BlockId";

    private final IManagedGridNode mainNode = createMainNode()
            .setVisualRepresentation(getBlockState().getBlock())
            .setExposedOnSides(EnumSet.complementOf(EnumSet.of(Direction.UP)))
            .setInWorldNode(true)
            .setTagName("main")
            .setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.MULTIBLOCK)
            .addService(IGridMultiblock.class, this::getMultiblockNodes);

    private @Nullable ResourceLocation id;
    private boolean clientNetworkPowered;
    private boolean clientNetworkOnline;
    private boolean clientGridBooted;

    public AEGunSmithTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(AETaCZBlockEntity.AE_GUN_SMITH_TABLE.get(), pos, blockState);
        updateGridConnectableSides();
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

    private void updateGridConnectableSides() {
        this.mainNode.setExposedOnSides(getGridConnectableSides(BlockOrientation.get(getBlockState())));
    }

    private Iterator<IGridNode> getMultiblockNodes() {
        if (this.level == null) {
            ArrayList<IGridNode> self = new ArrayList<>();
            IGridNode node = this.getGridNode();
            if (node != null) {
                self.add(node);
            }
            return self.iterator();
        }

        ArrayList<IGridNode> nodes = new ArrayList<>();
        for (BlockPos pos : getMultiblockPositions()) {
            if (this.level.getBlockEntity(pos) instanceof AEGunSmithTableBlockEntity table) {
                IGridNode node = table.getGridNode();
                if (node != null) {
                    nodes.add(node);
                }
            }
        }
        return nodes.iterator();
    }

    private Set<BlockPos> getMultiblockPositions() {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof AbstractGunSmithTableBlock block)) {
            return Set.of(this.worldPosition);
        }

        BlockPos rootPos = block.getRootPos(this.worldPosition, state);
        if (state.getBlock() instanceof GunSmithTableBlockC) {
            return Set.of(rootPos, rootPos.above());
        }
        if (rootPos.equals(this.worldPosition)) {
            Direction facing = state.getValue(AbstractGunSmithTableBlock.FACING);
            BlockPos otherPos = rootPos.relative(facing);
            if (this.level != null && this.level.getBlockEntity(otherPos) instanceof AEGunSmithTableBlockEntity) {
                return Set.of(rootPos, otherPos);
            }
        } else {
            return Set.of(rootPos, this.worldPosition);
        }
        return Set.of(this.worldPosition);
    }

    public @Nullable ResourceLocation getId() {
        return this.id;
    }

    @Override
    public IManagedGridNode getMainNode() {
        return this.mainNode;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        Set<Direction> sides = EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.TOP)));
        BlockState state = getBlockState();
        if (state.getBlock() instanceof GunSmithTableBlockC
                && state.getBlock() instanceof AbstractGunSmithTableBlock block
                && block.getRootPos(this.worldPosition, state).equals(this.worldPosition)) {
            sides.add(Direction.UP);
        }
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
        data.writeBoolean(this.id != null);
        if (this.id != null) {
            data.writeResourceLocation(this.id);
        }
        data.writeBoolean(this.getMainNode().isPowered());
        data.writeBoolean(this.getMainNode().isOnline());
        data.writeBoolean(this.getMainNode().hasGridBooted());
    }

    @Override
    protected boolean readFromStream(FriendlyByteBuf data) {
        this.id = data.readBoolean() ? data.readResourceLocation() : null;
        boolean networkPowered = data.readBoolean();
        boolean networkOnline = data.readBoolean();
        boolean gridBooted = data.readBoolean();

        boolean changed = networkPowered != this.clientNetworkPowered
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
    public void onReady() {
        super.onReady();
        updateGridConnectableSides();
        this.mainNode.create(this.level, this.worldPosition);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == Capabilities.IN_WORLD_GRID_NODE_HOST && !Direction.UP.equals(side)) {
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
        boolean networkPowered = this.getMainNode().isPowered();
        boolean networkOnline = this.getMainNode().isOnline();
        boolean gridBooted = this.getMainNode().hasGridBooted();
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

    public boolean isNetworkPowered() {
        return this.level != null && this.level.isClientSide()
                ? this.clientNetworkPowered
                : this.getMainNode().isPowered();
    }

    public boolean isNetworkOnline() {
        return this.level != null && this.level.isClientSide()
                ? this.clientNetworkOnline
                : this.getMainNode().isOnline();
    }

    public boolean hasBootedGrid() {
        return this.level != null && this.level.isClientSide()
                ? this.clientGridBooted
                : this.getMainNode().hasGridBooted();
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(this.worldPosition.offset(-2, 0, -2), this.worldPosition.offset(2, 1, 2));
    }
}
