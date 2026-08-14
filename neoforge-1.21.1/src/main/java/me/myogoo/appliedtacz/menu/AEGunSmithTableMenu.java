package me.myogoo.appliedtacz.menu;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftAmountMenu;
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
import me.myogoo.appliedtacz.mixin.GunSmithTableMenuAccessor;
import me.myogoo.appliedtacz.network.packet.SyncIngredientCountsPacket;
import me.myogoo.appliedtacz.registry.ModMenus;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class AEGunSmithTableMenu extends GunSmithTableMenu {
    private static final String MEGA_CELLS_MOD_ID = "megacells";

    public static final MenuType<AEGunSmithTableMenu> TYPE = IMenuTypeExtension.create((windowId, inv, data) -> {
        BlockPos pos = data.readBlockPos();
        ResourceLocation blockId = data.readResourceLocation();
        BlockEntity blockEntity = inv.player.level().getBlockEntity(pos);
        AEGunSmithTableBlockEntity table = blockEntity instanceof AEGunSmithTableBlockEntity aeTable ? aeTable : null;
        return new AEGunSmithTableMenu(windowId, inv, table, blockId);
    });

    private final Inventory playerInventory;
    private final @Nullable AEGunSmithTableBlockEntity blockEntity;
    private final IActionSource actionSource;
    private final Int2IntArrayMap syncedIngredientCounts = new Int2IntArrayMap();
    private final IntArraySet syncedCraftableIngredients = new IntArraySet();
    private @Nullable IStorageService storageService;
    private @Nullable ResourceLocation syncedRecipeId;
    private @Nullable ResourceLocation watchedRecipeId;
    private Int2IntArrayMap lastSentCounts = new Int2IntArrayMap();
    private IntArraySet lastSentCraftableIngredients = new IntArraySet();
    private long nextIngredientCountUpdateGameTime;

    public AEGunSmithTableMenu(int id, Inventory inventory, @Nullable AEGunSmithTableBlockEntity blockEntity) {
        this(id, inventory, blockEntity, AETaCZWorkbenchIds.getMenuBlockId(blockEntity));
    }

    public AEGunSmithTableMenu(int id, Inventory inventory, @Nullable AEGunSmithTableBlockEntity blockEntity,
            @Nullable ResourceLocation blockId) {
        super(id, inventory, blockId != null ? blockId : AETaCZWorkbenchIds.getMenuBlockId(blockEntity));
        this.playerInventory = inventory;
        this.blockEntity = blockEntity;
        this.actionSource = blockEntity != null
                ? IActionSource.ofPlayer(inventory.player, blockEntity)
                : IActionSource.ofPlayer(inventory.player);
        if (blockEntity != null) {
            this.storageService = blockEntity.getStorageService();
        }
    }

    @Override
    public MenuType<?> getType() {
        return ModMenus.AE_GUN_SMITH_TABLE.get();
    }

    public void setWatchedRecipe(@Nullable ResourceLocation recipeId) {
        if (!Objects.equals(recipeId, watchedRecipeId)) {
            watchedRecipeId = recipeId;
            lastSentCounts.clear();
            lastSentCraftableIngredients.clear();
            nextIngredientCountUpdateGameTime = 0;
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (playerInventory.player.level().isClientSide()) {
            return;
        }
        if (!(playerInventory.player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (watchedRecipeId == null) {
            return;
        }

        long gameTime = serverPlayer.level().getGameTime();
        if (gameTime < nextIngredientCountUpdateGameTime) {
            return;
        }
        scheduleNextIngredientCountUpdate(gameTime);

        Int2IntArrayMap currentCounts = getAvailableIngredientCounts(watchedRecipeId, serverPlayer);
        IntArraySet currentCraftableIngredients = getCraftableIngredientIndices(watchedRecipeId, serverPlayer);
        if (!currentCounts.equals(lastSentCounts) || !currentCraftableIngredients.equals(lastSentCraftableIngredients)) {
            lastSentCounts = new Int2IntArrayMap(currentCounts);
            lastSentCraftableIngredients = new IntArraySet(currentCraftableIngredients);
            PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new SyncIngredientCountsPacket(containerId, watchedRecipeId, currentCounts, currentCraftableIngredients));
        }
    }

    @Override
    public void doCraft(ResourceLocation recipeId, Player player) {
        if (player.level().isClientSide()) {
            return;
        }

        GunSmithTableRecipe recipe = ((GunSmithTableMenuAccessor) this)
                .appliedtacz$getRecipe(recipeId, player.level().getRecipeManager());
        if (recipe == null) {
            return;
        }

        if (!player.isCreative()) {
            IItemHandler playerItems = player.getCapability(Capabilities.ItemHandler.ENTITY, null);
            IStorageService availableStorageService = getStorageService();
            Int2IntArrayMap toExtractSlots = new Int2IntArrayMap();
            Map<AEItemKey, Long> toExtract = new HashMap<>();

            for (GunSmithTableIngredient ingredient : recipe.getInputs()) {
                long foundTotal = 0;
                int needed = ingredient.getCount();

                if (playerItems != null) {
                    foundTotal += reserveFromPlayerInventory(ingredient, playerItems, toExtractSlots, needed);
                }

                if (foundTotal < needed && availableStorageService != null) {
                    if (isMegaCellsLoaded()) {
                        foundTotal += reserveDirectIngredientKeysFromNetwork(ingredient, availableStorageService,
                                toExtract, needed - foundTotal);
                    } else {
                        KeyCounter networkStorage = availableStorageService.getCachedInventory();
                        List<AEItemKey> candidates = findBestMatchingItemStack(ingredient, networkStorage);

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

                            long extractable = availableStorageService.getInventory().extract(
                                    key,
                                    Math.min(remaining, available),
                                    Actionable.SIMULATE,
                                    actionSource
                            );
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

            if (availableStorageService != null && !commitNetworkExtractions(availableStorageService, toExtract)) {
                syncIngredientCountsToPlayer(recipeId, player);
                return;
            }

            if (playerItems != null) {
                for (int slot : toExtractSlots.keySet()) {
                    playerItems.extractItem(slot, toExtractSlots.get(slot), false);
                }
            }
        }

        ItemStack result = recipe.getResultItem(player.registryAccess()).copy();
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
        player.inventoryMenu.broadcastFullState();
        syncIngredientCountsToPlayer(recipeId, player);
        NetworkHandler.sendToClientPlayer(new ServerMessageCraft(containerId), player);
    }

    private void syncIngredientCountsToPlayer(ResourceLocation recipeId, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        Int2IntArrayMap counts = getAvailableIngredientCounts(recipeId, player);
        IntArraySet craftableIngredients = getCraftableIngredientIndices(recipeId, player);
        lastSentCounts = new Int2IntArrayMap(counts);
        lastSentCraftableIngredients = new IntArraySet(craftableIngredients);
        scheduleNextIngredientCountUpdate(player.level().getGameTime());
        PacketDistributor.sendToPlayer(serverPlayer,
                new SyncIngredientCountsPacket(containerId, recipeId, counts, craftableIngredients));
    }

    private void scheduleNextIngredientCountUpdate(long gameTime) {
        nextIngredientCountUpdateGameTime = gameTime
                + AppliedTaczServerConfig.ingredientCountUpdateIntervalTicks();
    }

    public void requestIngredientAutocraft(ResourceLocation recipeId, int ingredientIndex, ServerPlayer player) {
        if (blockEntity == null) {
            return;
        }
        var node = blockEntity.getActionableNode();
        if (node == null || !node.isActive()) {
            return;
        }

        GunSmithTableRecipe recipe = ((GunSmithTableMenuAccessor) this)
                .appliedtacz$getRecipe(recipeId, player.level().getRecipeManager());
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
                MenuLocators.forBlockEntity(blockEntity),
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

    public final @Nullable IActionHost getActionHost() {
        return blockEntity;
    }

    public final IActionSource getActionSource() {
        return actionSource;
    }

    public final Player getPlayer() {
        return playerInventory.player;
    }

    public final Inventory getPlayerInventory() {
        return playerInventory;
    }

    public @Nullable IStorageService getStorageService() {
        if (storageService == null && blockEntity != null) {
            storageService = blockEntity.getStorageService();
        }
        return storageService;
    }

    public List<AEItemKey> findBestMatchingItemStack(GunSmithTableIngredient ingredient, KeyCounter storage) {
        return Arrays.stream(ingredient.getIngredient().getItems())
                .map(AEItemKey::of)
                .filter(Objects::nonNull)
                .flatMap(key -> storage.findFuzzy(key, FuzzyMode.IGNORE_ALL).stream())
                .map(entry -> (AEItemKey) entry.getKey())
                .filter(Objects::nonNull)
                .filter(key -> key.matches(ingredient.getIngredient()))
                .distinct()
                .sorted((left, right) -> Long.compare(storage.get(right), storage.get(left)))
                .toList();
    }

    private List<AEItemKey> getDirectIngredientItemKeys(GunSmithTableIngredient ingredient) {
        return Arrays.stream(ingredient.getIngredient().getItems())
                .map(AEItemKey::of)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private long reserveDirectIngredientKeysFromNetwork(GunSmithTableIngredient ingredient,
            IStorageService storageService, Map<AEItemKey, Long> toExtract, long needed) {
        long foundTotal = 0;

        for (AEItemKey key : getDirectIngredientItemKeys(ingredient)) {
            if (foundTotal >= needed) {
                break;
            }

            long remaining = needed - foundTotal;
            long reserved = toExtract.getOrDefault(key, 0L);
            long totalExtractable = storageService.getInventory().extract(key, reserved + remaining,
                    Actionable.SIMULATE, actionSource);
            long extractable = Math.min(remaining, Math.max(0L, totalExtractable - reserved));
            if (extractable <= 0) {
                continue;
            }

            toExtract.put(key, reserved + extractable);
            foundTotal += extractable;
        }

        return foundTotal;
    }

    private long reserveFromPlayerInventory(GunSmithTableIngredient ingredient, IItemHandler playerItems,
            Int2IntArrayMap toExtractSlots, long needed) {
        long foundTotal = 0;
        for (int slot = 0; slot < playerItems.getSlots(); slot++) {
            if (foundTotal >= needed) {
                break;
            }

            ItemStack stack = playerItems.getStackInSlot(slot);
            if (stack.isEmpty() || !ingredient.getIngredient().test(stack)) {
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
            long actual = storageService.getInventory().extract(entry.getKey(), requested, Actionable.MODULATE, actionSource);
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
            if (entry.getValue() > 0) {
                storageService.getInventory().insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, actionSource);
            }
        }
    }

    public long getAvailableInventoryIngredientCount(GunSmithTableIngredient ingredient) {
        return playerInventory.items.stream()
                .filter(stack -> !stack.isEmpty() && ingredient.getIngredient().test(stack))
                .mapToLong(ItemStack::getCount)
                .sum();
    }

    public long getAvailableNetworkIngredientCount(GunSmithTableIngredient ingredient) {
        IStorageService availableStorageService = getStorageService();
        if (availableStorageService == null) {
            return 0;
        }

        if (isMegaCellsLoaded()) {
            return getDirectlyExtractableIngredientCount(ingredient, availableStorageService);
        }

        return findBestMatchingItemStack(ingredient, availableStorageService.getCachedInventory()).stream()
                .mapToLong(key -> availableStorageService.getCachedInventory().get(key))
                .sum();
    }

    private long getDirectlyExtractableIngredientCount(GunSmithTableIngredient ingredient,
            IStorageService storageService) {
        long count = 0;
        for (AEItemKey key : getDirectIngredientItemKeys(ingredient)) {
            long remaining = Integer.MAX_VALUE - count;
            if (remaining <= 0) {
                break;
            }

            count += storageService.getInventory().extract(key, remaining, Actionable.SIMULATE, actionSource);
        }
        return count;
    }

    public long getAvailableIngredientCount(GunSmithTableIngredient ingredient) {
        return getAvailableInventoryIngredientCount(ingredient) + getAvailableNetworkIngredientCount(ingredient);
    }

    public Int2IntArrayMap getAvailableIngredientCounts(ResourceLocation recipeId, Player player) {
        Int2IntArrayMap counts = new Int2IntArrayMap();
        GunSmithTableRecipe recipe = ((GunSmithTableMenuAccessor) this)
                .appliedtacz$getRecipe(recipeId, player.level().getRecipeManager());
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
        if (blockEntity == null || blockEntity.getActionableNode() == null) {
            return craftableIngredients;
        }
        GunSmithTableRecipe recipe = ((GunSmithTableMenuAccessor) this)
                .appliedtacz$getRecipe(recipeId, player.level().getRecipeManager());
        if (recipe == null) {
            return craftableIngredients;
        }

        ICraftingService craftingService = blockEntity.getActionableNode().getGrid().getCraftingService();
        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        for (int i = 0; i < ingredients.size(); i++) {
            if (findCraftableIngredientKey(ingredients.get(i), craftingService).isPresent()) {
                craftableIngredients.add(i);
            }
        }
        return craftableIngredients;
    }

    public void setSyncedIngredientCounts(ResourceLocation recipeId, Int2IntArrayMap counts, IntSet craftableIngredients) {
        syncedRecipeId = recipeId;
        syncedIngredientCounts.clear();
        syncedIngredientCounts.putAll(counts);
        syncedCraftableIngredients.clear();
        syncedCraftableIngredients.addAll(craftableIngredients);
    }

    public void clearSyncedIngredientCounts() {
        syncedRecipeId = null;
        syncedIngredientCounts.clear();
        syncedCraftableIngredients.clear();
    }

    public boolean hasSyncedIngredientCounts(ResourceLocation recipeId) {
        return recipeId.equals(syncedRecipeId);
    }

    public int getSyncedIngredientCount(int index) {
        return syncedIngredientCounts.get(index);
    }

    public boolean isSyncedIngredientCraftable(int index) {
        return syncedCraftableIngredients.contains(index);
    }

    public @Nullable AEGunSmithTableBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isNetworkPowered() {
        return blockEntity != null && blockEntity.isNetworkPowered();
    }

    public boolean isNetworkOnline() {
        return blockEntity != null && blockEntity.isNetworkOnline();
    }

    public boolean hasBootedGrid() {
        return blockEntity != null && blockEntity.hasBootedGrid();
    }

    private boolean isMegaCellsLoaded() {
        return ModList.get().isLoaded(MEGA_CELLS_MOD_ID);
    }
}
