package me.myogoo.appliedtacz.menu;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.helpers.PlayerSource;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageCraft;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.init.AETaczMenu;
import me.myogoo.appliedtacz.mixin.GunSmithTableMenuAccessor;
import me.myogoo.appliedtacz.network.AppliedTaczNetwork;
import me.myogoo.appliedtacz.network.packet.SyncIngredientCountsPacket;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AEGunSmithTableMenu extends GunSmithTableMenu {
    public static final MenuType<AEGunSmithTableMenu> TYPE = IForgeMenuType.create((windowId, inv, data) -> {
        BlockPos pos = data.readBlockPos();
        ResourceLocation blockId = data.readResourceLocation();
        AEGunSmithTableBlockEntity be = inv.player.level().getBlockEntity(pos) instanceof AEGunSmithTableBlockEntity table
                ? table
                : null;
        return new AEGunSmithTableMenu(windowId, inv, be, blockId);
    });

    private final Inventory playerInventory;
    private final @Nullable AEGunSmithTableBlockEntity blockEntity;
    private final IActionSource mySrc;
    private final Int2IntArrayMap syncedIngredientCounts = new Int2IntArrayMap();
    private @Nullable IStorageService storageService;
    private @Nullable ResourceLocation syncedRecipeId;

    // Server-side: which recipe the client is currently viewing
    private @Nullable ResourceLocation watchedRecipeId = null;
    // Server-side: last counts sent to client, used to detect changes
    private Int2IntArrayMap lastSentCounts = new Int2IntArrayMap();

    public AEGunSmithTableMenu(int id, Inventory inventory, @Nullable AEGunSmithTableBlockEntity blockEntity) {
        this(id, inventory, blockEntity, AETaCZWorkbenchIds.getMenuBlockId(blockEntity));
    }

    public AEGunSmithTableMenu(int id, Inventory inventory, @Nullable AEGunSmithTableBlockEntity blockEntity,
            @Nullable ResourceLocation blockId) {
        super(id, inventory, blockId);
        this.blockEntity = blockEntity;
        this.playerInventory = inventory;
        this.mySrc = blockEntity != null ? new PlayerSource(getplayer(), blockEntity) : new PlayerSource(getplayer());
        if (blockEntity == null) {
            return;
        }

        var node = blockEntity.getActionableNode();
        if (node == null || node.getGrid() == null) {
            return;
        }
        this.storageService = node.getGrid().getService(IStorageService.class);
    }

    @Override
    public MenuType<?> getType() {
        return AETaczMenu.AE_GUN_SMITH_TABLE.get();
    }

    /**
     * Called by the client-side packet to start watching a recipe.
     * The server will push ingredient counts whenever they change.
     */
    public void setWatchedRecipe(@Nullable ResourceLocation recipeId) {
        if (!Objects.equals(recipeId, this.watchedRecipeId)) {
            this.watchedRecipeId = recipeId;
            this.lastSentCounts.clear();
        }
    }

    /**
     * Every server tick: if a recipe is being watched, push updated counts to the client
     * whenever the ME network contents change (AE2-style push model).
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isClientSide() || this.watchedRecipeId == null) {
            return;
        }

        Player player = getplayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Int2IntArrayMap currentCounts = getAvailableIngredientCounts(this.watchedRecipeId, player);
        if (!currentCounts.equals(this.lastSentCounts)) {
            this.lastSentCounts = currentCounts;
            AppliedTaczNetwork.sendToPlayer(
                    new SyncIngredientCountsPacket(this.containerId, this.watchedRecipeId, currentCounts),
                    serverPlayer);
        }
    }

    public void doCraft(ResourceLocation recipeId, Player player) {
        if (isClientSide()) {
            return;
        }

        GunSmithTableRecipe recipe = ((GunSmithTableMenuAccessor) this).callGetRecipe(recipeId,
                player.level().getRecipeManager());
        if (recipe == null) {
            return;
        }

        if (!player.isCreative()) {
            IItemHandler playerItems = player.getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .resolve()
                    .orElse(null);
            IStorageService storageService = getStorageService();
            Int2IntArrayMap toExtractSlots = new Int2IntArrayMap();
            Map<AEItemKey, Long> toExtract = new HashMap<>();

            for (GunSmithTableIngredient gunIngredient : recipe.getInputs()) {
                long foundTotal = 0;
                int needed = gunIngredient.getCount();

                if (playerItems != null) {
                    foundTotal += reserveFromPlayerInventory(gunIngredient, playerItems, toExtractSlots, needed);
                }

                if (foundTotal < needed && storageService != null) {
                    KeyCounter networkStorage = storageService.getCachedInventory();
                    List<AEItemKey> candidates = findBestMatchingItemStack(gunIngredient, networkStorage);

                    for (AEItemKey key : candidates) {
                        if (foundTotal >= needed) {
                            break;
                        }

                        long remaining = needed - foundTotal;
                        long reserved = toExtract.getOrDefault(key, 0L);
                        long available = Math.max(0L, networkStorage.get(key) - reserved);
                        if (available <= 0) {
                            continue;
                        }

                        long extractable = storageService.getInventory().extract(key, Math.min(remaining, available),
                                Actionable.SIMULATE, mySrc);
                        if (extractable <= 0) {
                            continue;
                        }

                        toExtract.put(key, reserved + extractable);
                        foundTotal += extractable;
                    }
                }

                if (foundTotal < needed) {
                    syncIngredientCountsToPlayer(recipeId, player);
                    return;
                }
            }

            if (storageService != null && !commitNetworkExtractions(storageService, toExtract)) {
                syncIngredientCountsToPlayer(recipeId, player);
                return;
            }

            if (playerItems != null) {
                for (int slot : toExtractSlots.keySet()) {
                    playerItems.extractItem(slot, toExtractSlots.get(slot), false);
                }
            }
        }

        ItemStack result = recipe.getResultItem(player.level().registryAccess()).copy();
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
        player.inventoryMenu.broadcastFullState();
        syncIngredientCountsToPlayer(recipeId, player);
        NetworkHandler.sendToClientPlayer(new ServerMessageCraft(this.containerId), player);
    }

    private void syncIngredientCountsToPlayer(ResourceLocation recipeId, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        AppliedTaczNetwork.sendToPlayer(
                new SyncIngredientCountsPacket(this.containerId, recipeId, getAvailableIngredientCounts(recipeId, player)),
                serverPlayer);
    }

    private boolean isClientSide() {
        return getplayer().getCommandSenderWorld().isClientSide();
    }

    public final @Nullable IActionHost getActionHost() {
        return this.blockEntity;
    }

    public final IActionSource getActionSource() {
        return this.mySrc;
    }

    public final Player getplayer() {
        return getPlayerInventory().player;
    }

    public final Inventory getPlayerInventory() {
        return this.playerInventory;
    }

    public @Nullable IStorageService getStorageService() {
        if (this.storageService == null && this.blockEntity != null) {
            var node = this.blockEntity.getActionableNode();
            if (node != null && node.getGrid() != null) {
                this.storageService = node.getGrid().getService(IStorageService.class);
            }
        }
        return this.storageService;
    }

    public List<AEItemKey> findBestMatchingItemStack(GunSmithTableIngredient gunIngredient, KeyCounter storage) {
        var ingredient = gunIngredient.getIngredient();
        return Arrays.stream(ingredient.getItems())
                .map(AEItemKey::of)
                .filter(Objects::nonNull)
                .flatMap(key -> storage.findFuzzy(key, FuzzyMode.IGNORE_ALL).stream())
                .filter(entry -> ((AEItemKey) entry.getKey()).matches(ingredient))
                .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                .map(entry -> (AEItemKey) entry.getKey())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private long reserveFromPlayerInventory(GunSmithTableIngredient gunIngredient, IItemHandler playerItems,
            Int2IntArrayMap toExtractSlots, long needed) {
        long foundTotal = 0;

        for (int slot = 0; slot < playerItems.getSlots(); slot++) {
            if (foundTotal >= needed) {
                break;
            }

            ItemStack stack = playerItems.getStackInSlot(slot);
            if (stack.isEmpty() || !gunIngredient.getIngredient().test(stack)) {
                continue;
            }

            int reserved = toExtractSlots.get(slot);
            int available = Math.max(0, stack.getCount() - reserved);
            if (available <= 0) {
                continue;
            }

            int extractable = (int) Math.min(needed - foundTotal, available);
            if (extractable <= 0) {
                continue;
            }

            toExtractSlots.put(slot, reserved + extractable);
            foundTotal += extractable;
        }

        return foundTotal;
    }

    private boolean commitNetworkExtractions(IStorageService storageService, Map<AEItemKey, Long> toExtract) {
        if (toExtract.isEmpty()) {
            return true;
        }

        Map<AEItemKey, Long> extracted = new HashMap<>();
        for (Map.Entry<AEItemKey, Long> entry : toExtract.entrySet()) {
            long requested = entry.getValue();
            long actual = storageService.getInventory().extract(entry.getKey(), requested, Actionable.MODULATE, mySrc);
            if (actual != requested) {
                if (actual > 0) {
                    extracted.put(entry.getKey(), actual);
                }
                rollbackNetworkExtractions(storageService, extracted);
                return false;
            }

            extracted.put(entry.getKey(), actual);
        }

        return true;
    }

    private void rollbackNetworkExtractions(IStorageService storageService, Map<AEItemKey, Long> extracted) {
        for (Map.Entry<AEItemKey, Long> entry : extracted.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }

            storageService.getInventory().insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, mySrc);
        }
    }

    public long getAvailableInventoryIngredientCount(GunSmithTableIngredient gunIngredient) {
        return this.playerInventory.items.stream()
                .filter(stack -> !stack.isEmpty() && gunIngredient.getIngredient().test(stack))
                .mapToLong(ItemStack::getCount)
                .sum();
    }

    public long getAvailableNetworkIngredientCount(GunSmithTableIngredient gunIngredient) {
        IStorageService storageService = getStorageService();
        if (storageService == null) {
            return 0;
        }

        return findBestMatchingItemStack(gunIngredient, storageService.getCachedInventory()).stream()
                .mapToLong(key -> storageService.getCachedInventory().get(key))
                .sum();
    }

    public long getAvailableIngredientCount(GunSmithTableIngredient gunIngredient) {
        return getAvailableInventoryIngredientCount(gunIngredient) + getAvailableNetworkIngredientCount(gunIngredient);
    }

    public Int2IntArrayMap getAvailableIngredientCounts(ResourceLocation recipeId, Player player) {
        Int2IntArrayMap counts = new Int2IntArrayMap();
        GunSmithTableRecipe recipe = ((GunSmithTableMenuAccessor) this).callGetRecipe(recipeId,
                player.level().getRecipeManager());
        if (recipe == null) {
            return counts;
        }

        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        for (int i = 0; i < ingredients.size(); i++) {
            counts.put(i, (int) Math.min(Integer.MAX_VALUE, getAvailableIngredientCount(ingredients.get(i))));
        }
        return counts;
    }

    public void setSyncedIngredientCounts(ResourceLocation recipeId, Int2IntArrayMap counts) {
        this.syncedRecipeId = recipeId;
        this.syncedIngredientCounts.clear();
        this.syncedIngredientCounts.putAll(counts);
    }

    public void clearSyncedIngredientCounts() {
        this.syncedRecipeId = null;
        this.syncedIngredientCounts.clear();
    }

    public boolean hasSyncedIngredientCounts(ResourceLocation recipeId) {
        return recipeId.equals(this.syncedRecipeId);
    }

    public int getSyncedIngredientCount(int index) {
        return this.syncedIngredientCounts.get(index);
    }

    public @Nullable AEGunSmithTableBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public boolean isNetworkPowered() {
        return this.blockEntity != null && this.blockEntity.isNetworkPowered();
    }

    public boolean isNetworkOnline() {
        return this.blockEntity != null && this.blockEntity.isNetworkOnline();
    }

    public boolean hasBootedGrid() {
        return this.blockEntity != null && this.blockEntity.hasBootedGrid();
    }
}
