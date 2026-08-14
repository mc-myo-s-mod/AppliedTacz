package me.myogoo.appliedtacz.menu;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingService;
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
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.config.AppliedTaczServerConfig;
import me.myogoo.appliedtacz.init.AETaczMenu;
import me.myogoo.appliedtacz.mixin.GunSmithTableMenuAccessor;
import me.myogoo.appliedtacz.network.AppliedTaczNetwork;
import me.myogoo.appliedtacz.network.packet.SyncIngredientCountsPacket;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AEGunSmithTableMenu extends GunSmithTableMenu {
    private static final String MEGA_CELLS_MOD_ID = "megacells";

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
    private final IntArraySet syncedCraftableIngredients = new IntArraySet();
    private @Nullable IStorageService storageService;
    private @Nullable ResourceLocation syncedRecipeId;

    // Server-side: which recipe the client is currently viewing
    private @Nullable ResourceLocation watchedRecipeId = null;
    // Server-side: last counts sent to client, used to detect changes
    private Int2IntArrayMap lastSentCounts = new Int2IntArrayMap();
    private IntArraySet lastSentCraftableIngredients = new IntArraySet();
    private long nextIngredientCountUpdateGameTime;

    public AEGunSmithTableMenu(int id, Inventory inventory, @Nullable AEGunSmithTableBlockEntity blockEntity) {
        this(id, inventory, blockEntity, AETaCZWorkbenchIndex.getMenuBlockId(blockEntity));
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
            this.lastSentCraftableIngredients.clear();
            this.nextIngredientCountUpdateGameTime = 0;
        }
    }

    /**
     * Every server tick: if a recipe is being watched, push updated counts to the client
     * whenever the ME network contents change (AE2-style push model).
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isClientSide()) {
            return;
        }

        Player player = getplayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (this.watchedRecipeId == null) {
            return;
        }

        long gameTime = player.level().getGameTime();
        if (gameTime < this.nextIngredientCountUpdateGameTime) {
            return;
        }
        scheduleNextIngredientCountUpdate(gameTime);

        Int2IntArrayMap currentCounts = getAvailableIngredientCounts(this.watchedRecipeId, player);
        IntArraySet currentCraftableIngredients = getCraftableIngredientIndices(this.watchedRecipeId, player);
        if (!currentCounts.equals(this.lastSentCounts) || !currentCraftableIngredients.equals(this.lastSentCraftableIngredients)) {
            this.lastSentCounts = new Int2IntArrayMap(currentCounts);
            this.lastSentCraftableIngredients = new IntArraySet(currentCraftableIngredients);
            AppliedTaczNetwork.sendToPlayer(
                    new SyncIngredientCountsPacket(this.containerId, this.watchedRecipeId, currentCounts,
                            currentCraftableIngredients),
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
                    if (isMegaCellsLoaded()) {
                        foundTotal += reserveDirectIngredientKeysFromNetwork(gunIngredient, storageService, toExtract,
                                needed - foundTotal);
                    } else {
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

        IntArraySet craftableIngredients = getCraftableIngredientIndices(recipeId, player);
        this.lastSentCounts = new Int2IntArrayMap(getAvailableIngredientCounts(recipeId, player));
        this.lastSentCraftableIngredients = new IntArraySet(craftableIngredients);
        scheduleNextIngredientCountUpdate(player.level().getGameTime());
        AppliedTaczNetwork.sendToPlayer(
                new SyncIngredientCountsPacket(this.containerId, recipeId, this.lastSentCounts, craftableIngredients),
                serverPlayer);
    }

    private void scheduleNextIngredientCountUpdate(long gameTime) {
        this.nextIngredientCountUpdateGameTime = gameTime
                + AppliedTaczServerConfig.ingredientCountUpdateIntervalTicks();
    }

    public void requestIngredientAutocraft(ResourceLocation recipeId, int ingredientIndex, ServerPlayer player) {
        if (this.blockEntity == null) {
            return;
        }
        var node = this.blockEntity.getActionableNode();
        if (node == null || !node.isActive()) {
            return;
        }

        GunSmithTableRecipe recipe = ((GunSmithTableMenuAccessor) this).callGetRecipe(recipeId,
                player.level().getRecipeManager());
        if (recipe == null || ingredientIndex < 0 || ingredientIndex >= recipe.getInputs().size()) {
            return;
        }

        GunSmithTableIngredient ingredient = recipe.getInputs().get(ingredientIndex);
        long missing = ingredient.getCount() - getAvailableIngredientCount(ingredient);
        long craftAmount = missing > 0 ? missing : ingredient.getCount();

        ICraftingService craftingService = node.getGrid().getCraftingService();
        Optional<AEItemKey> craftableKey = findCraftableIngredientKey(ingredient, craftingService);
        if (craftableKey.isEmpty()) {
            return;
        }

        CraftAmountMenu.open(player,
                MenuLocators.forBlockEntity(this.blockEntity),
                craftableKey.get(),
                (int) Math.min(Integer.MAX_VALUE, craftAmount));
    }

    private Optional<AEItemKey> findCraftableIngredientKey(GunSmithTableIngredient ingredient,
            ICraftingService craftingService) {
        return Arrays.stream(ingredient.getIngredient().getItems())
                .map(AEItemKey::of)
                .filter(Objects::nonNull)
                .map(key -> craftingService.getFuzzyCraftable(
                        key,
                        candidate -> candidate instanceof AEItemKey itemKey
                                && itemKey.matches(ingredient.getIngredient())))
                .filter(AEItemKey.class::isInstance)
                .map(AEItemKey.class::cast)
                .findFirst();
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

    private List<AEItemKey> getDirectIngredientItemKeys(GunSmithTableIngredient gunIngredient) {
        return Arrays.stream(gunIngredient.getIngredient().getItems())
                .map(AEItemKey::of)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private long reserveDirectIngredientKeysFromNetwork(GunSmithTableIngredient gunIngredient,
            IStorageService storageService, Map<AEItemKey, Long> toExtract, long needed) {
        long foundTotal = 0;

        for (AEItemKey key : getDirectIngredientItemKeys(gunIngredient)) {
            if (foundTotal >= needed) {
                break;
            }

            long remaining = needed - foundTotal;
            long reserved = toExtract.getOrDefault(key, 0L);
            long totalExtractable = storageService.getInventory().extract(key, reserved + remaining,
                    Actionable.SIMULATE, mySrc);
            long extractable = Math.min(remaining, Math.max(0L, totalExtractable - reserved));
            if (extractable <= 0) {
                continue;
            }

            toExtract.put(key, reserved + extractable);
            foundTotal += extractable;
        }

        return foundTotal;
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

        if (isMegaCellsLoaded()) {
            return getDirectlyExtractableIngredientCount(gunIngredient, storageService);
        }

        return findBestMatchingItemStack(gunIngredient, storageService.getCachedInventory()).stream()
                .mapToLong(key -> storageService.getCachedInventory().get(key))
                .sum();
    }

    private long getDirectlyExtractableIngredientCount(GunSmithTableIngredient gunIngredient,
            IStorageService storageService) {
        long count = 0;
        for (AEItemKey key : getDirectIngredientItemKeys(gunIngredient)) {
            long remaining = Integer.MAX_VALUE - count;
            if (remaining <= 0) {
                break;
            }

            count += storageService.getInventory().extract(key, remaining, Actionable.SIMULATE, mySrc);
        }
        return count;
    }

    private boolean isMegaCellsLoaded() {
        return ModList.get().isLoaded(MEGA_CELLS_MOD_ID);
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

    public IntArraySet getCraftableIngredientIndices(ResourceLocation recipeId, Player player) {
        IntArraySet craftableIngredients = new IntArraySet();
        if (this.blockEntity == null || this.blockEntity.getActionableNode() == null) {
            return craftableIngredients;
        }
        GunSmithTableRecipe recipe = ((GunSmithTableMenuAccessor) this).callGetRecipe(recipeId,
                player.level().getRecipeManager());
        if (recipe == null) {
            return craftableIngredients;
        }

        ICraftingService craftingService = this.blockEntity.getActionableNode().getGrid().getCraftingService();
        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        for (int i = 0; i < ingredients.size(); i++) {
            if (findCraftableIngredientKey(ingredients.get(i), craftingService).isPresent()) {
                craftableIngredients.add(i);
            }
        }
        return craftableIngredients;
    }

    public void setSyncedIngredientCounts(ResourceLocation recipeId, Int2IntArrayMap counts, IntSet craftableIngredients) {
        this.syncedRecipeId = recipeId;
        this.syncedIngredientCounts.clear();
        this.syncedIngredientCounts.putAll(counts);
        this.syncedCraftableIngredients.clear();
        this.syncedCraftableIngredients.addAll(craftableIngredients);
    }

    public void clearSyncedIngredientCounts() {
        this.syncedRecipeId = null;
        this.syncedIngredientCounts.clear();
        this.syncedCraftableIngredients.clear();
    }

    public boolean hasSyncedIngredientCounts(ResourceLocation recipeId) {
        return recipeId.equals(this.syncedRecipeId);
    }

    public int getSyncedIngredientCount(int index) {
        return this.syncedIngredientCounts.get(index);
    }

    public boolean isSyncedIngredientCraftable(int index) {
        return this.syncedCraftableIngredients.contains(index);
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
