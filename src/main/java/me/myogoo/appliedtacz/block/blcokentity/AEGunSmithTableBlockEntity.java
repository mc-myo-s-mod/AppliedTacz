package me.myogoo.appliedtacz.block.blcokentity;

import appeng.api.config.FuzzyMode;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.ISubMenuHost;
import appeng.api.util.AECableType;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.menu.ISubMenu;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.registry.ModBlockEntities;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AEGunSmithTableBlockEntity extends AEBaseBlockEntity implements IInWorldGridNodeHost, IActionHost,
        ISubMenuHost {
    private static final String ID_TAG = "BlockId";
    private static final IGridNodeListener<AEGunSmithTableBlockEntity> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(AEGunSmithTableBlockEntity nodeOwner, IGridNode node) {
            nodeOwner.saveChanges();
        }

        @Override
        public void onStateChanged(AEGunSmithTableBlockEntity nodeOwner, IGridNode node, State reason) {
            nodeOwner.syncNetworkState();
        }
    };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setInWorldNode(true)
            .setTagName("ae_node")
            .setVisualRepresentation(getBlockState().getBlock())
            .setExposedOnSides(java.util.EnumSet.complementOf(java.util.EnumSet.of(Direction.UP)))
            .setIdlePowerUsage(1.0)
            .setFlags(GridFlags.REQUIRE_CHANNEL);

    private @Nullable ResourceLocation id;
    private boolean clientNetworkPowered;
    private boolean clientNetworkOnline;
    private boolean clientGridBooted;

    public AEGunSmithTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.AE_GUN_SMITH_TABLE.get(), pos, blockState);
    }

    public void setId(@Nullable ResourceLocation id) {
        this.id = id;
        saveChanges();
        markForUpdate();
    }

    public @Nullable ResourceLocation getId() {
        return id;
    }

    public void setOwningPlayer(Player player) {
        mainNode.setOwningPlayer(player);
        saveChanges();
    }

    public @Nullable IStorageService getStorageService() {
        var grid = mainNode.getGrid();
        return grid != null ? grid.getStorageService() : null;
    }

    public @Nullable MEStorage getStorage() {
        IStorageService storageService = getStorageService();
        return storageService != null ? storageService.getInventory() : null;
    }

    public @Nullable appeng.api.networking.energy.IEnergySource getEnergySource() {
        var grid = mainNode.getGrid();
        return grid != null ? grid.getEnergyService() : null;
    }

    public List<AEItemKey> findBestMatchingItemStacks(net.minecraft.world.item.crafting.Ingredient ingredient) {
        IStorageService storageService = getStorageService();
        if (storageService == null) {
            return List.of();
        }

        KeyCounter storage = storageService.getCachedInventory();
        List<AEItemKey> matches = new ArrayList<>();
        for (var candidate : ingredient.getItems()) {
            AEItemKey key = AEItemKey.of(candidate);
            if (key == null) {
                continue;
            }

            storage.findFuzzy(key, FuzzyMode.IGNORE_ALL).stream()
                    .map(entry -> (AEItemKey) entry.getKey())
                    .filter(Objects::nonNull)
                    .filter(found -> found.matches(ingredient))
                    .filter(found -> !matches.contains(found))
                    .forEach(matches::add);
        }

        matches.sort((left, right) -> Long.compare(storage.get(right), storage.get(left)));
        return matches;
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return dir == Direction.UP ? null : mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return dir == Direction.UP ? AECableType.NONE : AECableType.SMART;
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        mainNode.loadFromNBT(tag);
        if (tag.contains(ID_TAG, Tag.TAG_STRING)) {
            id = ResourceLocation.tryParse(tag.getString(ID_TAG));
        } else {
            id = AETaCZWorkbenchIds.getDefaultBlockId(getBlockState().getBlock());
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        if (id != null) {
            tag.putString(ID_TAG, id.toString());
        }
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        data.writeBoolean(id != null);
        if (id != null) {
            data.writeResourceLocation(id);
        }
        data.writeBoolean(mainNode.isPowered());
        data.writeBoolean(mainNode.isOnline());
        data.writeBoolean(mainNode.hasGridBooted());
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        ResourceLocation oldId = id;
        id = data.readBoolean() ? data.readResourceLocation() : null;
        boolean networkPowered = data.readBoolean();
        boolean networkOnline = data.readBoolean();
        boolean gridBooted = data.readBoolean();
        boolean changed = !Objects.equals(oldId, id)
                || networkPowered != clientNetworkPowered
                || networkOnline != clientNetworkOnline
                || gridBooted != clientGridBooted;
        clientNetworkPowered = networkPowered;
        clientNetworkOnline = networkOnline;
        clientGridBooted = gridBooted;
        return changed;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, be -> be.mainNode.create(be.getLevel(), be.getBlockPos()));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        mainNode.destroy();
    }

    private void syncNetworkState() {
        boolean networkPowered = mainNode.isPowered();
        boolean networkOnline = mainNode.isOnline();
        boolean gridBooted = mainNode.hasGridBooted();
        if (networkPowered == clientNetworkPowered
                && networkOnline == clientNetworkOnline
                && gridBooted == clientGridBooted) {
            return;
        }

        clientNetworkPowered = networkPowered;
        clientNetworkOnline = networkOnline;
        clientGridBooted = gridBooted;
        markForUpdate();
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignoredPlayer) -> new AEGunSmithTableMenu(containerId, inventory, this),
                    getBlockState().getBlock().getName()
            ), buffer -> {
                buffer.writeBlockPos(this.worldPosition);
                buffer.writeResourceLocation(AETaCZWorkbenchIds.getMenuBlockId(this));
            });
        }
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getBlockState().getBlock());
    }

    public boolean isNetworkPowered() {
        return level != null && level.isClientSide() ? clientNetworkPowered : mainNode.isPowered();
    }

    public boolean isNetworkOnline() {
        return level != null && level.isClientSide() ? clientNetworkOnline : mainNode.isOnline();
    }

    public boolean hasBootedGrid() {
        return level != null && level.isClientSide() ? clientGridBooted : mainNode.hasGridBooted();
    }
}
