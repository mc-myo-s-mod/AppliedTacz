package me.myogoo.appliedtacz.mixin;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageCraft;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import me.myogoo.appliedtacz.client.IngredientCountSyncTarget;
import me.myogoo.appliedtacz.client.MousePositionRestorer;
import me.myogoo.appliedtacz.client.RecipeSelectionRestorer;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.network.AppliedTaczNetwork;
import me.myogoo.appliedtacz.network.packet.RequestIngredientAutocraftPacket;
import me.myogoo.appliedtacz.network.packet.RequestIngredientCountsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Mixin(GunSmithTableScreen.class)
public abstract class GunSmithTableScreenMixin extends AbstractContainerScreen<GunSmithTableMenu>
        implements IngredientCountSyncTarget {

    private static final int CRAFTABLE_MARKER_WHITE = 0xFFFFFF;

    @Unique
    private static final String[] APPLIED_TACZ_AMOUNT_SUFFIXES = new String[] { "", "k", "m", "g", "t", "p", "e" };

    @Unique
    private static final DecimalFormat APPLIED_TACZ_WHOLE_FORMAT = new DecimalFormat("0",
            DecimalFormatSymbols.getInstance(Locale.ROOT));

    @Unique
    private static final DecimalFormat APPLIED_TACZ_SINGLE_DECIMAL_FORMAT = new DecimalFormat("0.#",
            DecimalFormatSymbols.getInstance(Locale.ROOT));

    @Unique
    private static final DecimalFormat APPLIED_TACZ_DOUBLE_DECIMAL_FORMAT = new DecimalFormat("0.##",
            DecimalFormatSymbols.getInstance(Locale.ROOT));

    @Shadow(remap = false)
    private static ResourceLocation TEXTURE;

    @Shadow(remap = false)
    private Int2IntArrayMap playerIngredientCount;

    @Shadow(remap = false)
    private GunSmithTableRecipe selectedRecipe;

    @Shadow(remap = false)
    private Map<ResourceLocation, List<ResourceLocation>> recipes;

    @Shadow(remap = false)
    private ResourceLocation selectedType;

    @Shadow(remap = false)
    private List<ResourceLocation> selectedRecipeList;

    @Shadow(remap = false)
    private int indexPage;

    /**
     * Which recipe we have registered with the server as "currently watching".
     * Sending a new {@link RequestIngredientCountsPacket} only when this changes
     * avoids flooding the server every frame.
     */
    private @Nullable ResourceLocation appliedTacz$watchedRecipeId;

    /**
     * Per-recipe cache of the last ingredient counts received from the server.
     * Prevents a flash of zeros when the player switches recipes: instead of
     * showing player-inventory-only fallback counts while waiting for the first
     * push, we show the last known counts for that recipe.
     */
    private final Map<ResourceLocation, Int2IntArrayMap> appliedTacz$countsCache = new HashMap<>();

    public GunSmithTableScreenMixin(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "render", at = @At("TAIL"), remap = true)
    void appliedTacz$renderCraftableMarkersOnly(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        if (!(this.menu instanceof AEGunSmithTableMenu)) {
            return;
        }

        MousePositionRestorer.restoreReturnToMainMenuIfPending();
        appliedTacz$renderNetworkStatus(graphics, mouseX, mouseY, (AEGunSmithTableMenu) this.menu);
        appliedTacz$renderCraftableMarkers(graphics);
    }

    private void appliedTacz$renderNetworkStatus(GuiGraphics graphics, int mouseX, int mouseY,
            AEGunSmithTableMenu aeMenu) {
        Component status;
        int color;
        if (!aeMenu.isNetworkPowered()) {
            status = Component.translatable("gui.appliedtacz.ae_network.offline");
            color = 0xC05050;
        } else if (!aeMenu.isNetworkOnline()) {
            status = Component.translatable("gui.appliedtacz.ae_network.no_channel");
            color = 0xD4A13A;
        } else if (!aeMenu.hasBootedGrid()) {
            status = Component.translatable("gui.appliedtacz.ae_network.booting");
            color = 0xD4A13A;
        } else {
            status = Component.translatable("gui.appliedtacz.ae_network.connected");
            color = 0x55AA55;
        }

        Component line = Component.translatable("gui.appliedtacz.ae_network",
                status.copy().withStyle(ChatFormatting.WHITE));
        int x = this.leftPos + 6;
        int y = this.topPos + this.imageHeight - this.font.lineHeight - 4;
        graphics.drawString(this.font, line, x, y, color, false);

        int width = this.font.width(line);
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + this.font.lineHeight) {
            graphics.renderTooltip(this.font,
                    Component.translatable("gui.appliedtacz.ae_network.tooltip"),
                    mouseX,
                    mouseY);
        }
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void appliedTacz$restoreMouseAfterReturningToTable(CallbackInfo ci) {
        if (this.menu instanceof AEGunSmithTableMenu) {
            MousePositionRestorer.restoreReturnToMainMenuIfPending();
        }
    }

    @Inject(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/gui/GunSmithTableScreen;classifyRecipes()V",
                    shift = At.Shift.AFTER,
                    remap = false),
            remap = true)
    private void appliedTacz$restorePendingRecipeSelection(CallbackInfo ci) {
        ResourceLocation recipeId = RecipeSelectionRestorer.getPendingRecipeId();
        if (recipeId == null || Minecraft.getInstance().level == null) {
            return;
        }

        Recipe<?> foundRecipe = Minecraft.getInstance().level.getRecipeManager().byKey(recipeId).orElse(null);
        if (!(foundRecipe instanceof GunSmithTableRecipe recipe)) {
            RecipeSelectionRestorer.forget(recipeId);
            return;
        }

        ResourceLocation group = appliedTacz$findRecipeGroup(recipeId, recipe);
        List<ResourceLocation> recipeList = this.recipes.get(group);
        if (group == null || recipeList == null) {
            RecipeSelectionRestorer.forget(recipeId);
            return;
        }

        int recipeIndex = recipeList.indexOf(recipeId);
        if (recipeIndex < 0) {
            RecipeSelectionRestorer.forget(recipeId);
            return;
        }

        this.selectedType = group;
        this.selectedRecipeList = recipeList;
        this.indexPage = recipeIndex / 6;
        this.selectedRecipe = recipe;
        RecipeSelectionRestorer.forget(recipeId);
        appliedTacz$refreshSelectedRecipeCounts(recipeId, recipe);
    }

    private @Nullable ResourceLocation appliedTacz$findRecipeGroup(ResourceLocation recipeId, GunSmithTableRecipe recipe) {
        ResourceLocation recipeGroup = recipe.getResult().getGroup();
        if (this.recipes.containsKey(recipeGroup)) {
            return recipeGroup;
        }

        for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : this.recipes.entrySet()) {
            if (entry.getValue().contains(recipeId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void appliedTacz$refreshSelectedRecipeCounts(ResourceLocation recipeId, GunSmithTableRecipe recipe) {
        if (this.menu instanceof AEGunSmithTableMenu aeMenu) {
            this.appliedTacz$watchedRecipeId = recipeId;
            AppliedTaczNetwork.sendToServer(new RequestIngredientCountsPacket(aeMenu.containerId, recipeId));
            if (aeMenu.hasSyncedIngredientCounts(recipeId)) {
                appliedTacz$applySyncedIngredientCounts(aeMenu, recipe);
                return;
            }
        }

        Int2IntArrayMap cached = this.appliedTacz$countsCache.get(recipeId);
        if (cached != null) {
            this.playerIngredientCount = new Int2IntArrayMap(cached);
            return;
        }

        appliedTacz$applyPlayerInventoryIngredientCounts(recipe);
    }

    private void appliedTacz$applyPlayerInventoryIngredientCounts(GunSmithTableRecipe recipe) {
        if (Minecraft.getInstance().player == null) {
            this.playerIngredientCount = new Int2IntArrayMap();
            return;
        }

        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        this.playerIngredientCount = new Int2IntArrayMap(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            GunSmithTableIngredient ingredient = ingredients.get(i);
            int count = 0;
            for (ItemStack stack : inventory.items) {
                if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {
                    count += stack.getCount();
                }
            }
            this.playerIngredientCount.put(i, count);
        }
    }

    /**
     * Replaces TaCZ's player-inventory-only count lookup with combined
     * player + ME network counts (push model).
     *
     * <p>Priority when displaying counts:
     * <ol>
     *   <li>Server-pushed synced counts (always up-to-date)</li>
     *   <li>Client-side cache from the last push for this recipe (avoids 0 flash on recipe switch)</li>
     *   <li>Player-inventory fallback (first time viewing a recipe before server has responded)</li>
     * </ol>
     *
     * <p>NOTE: we intentionally do NOT clear synced counts when the recipe changes or
     * after a craft.  After a craft, the server sends a {@code SyncIngredientCountsPacket}
     * with fresh counts <em>before</em> it sends {@code ServerMessageCraft}, so by the time
     * TaCZ calls {@code updateIngredientCount()} the synced counts are already correct.
     * Clearing them here would undo that and cause a 0-flash.
     */
    @Inject(method = "getPlayerIngredientCount", at = @At("HEAD"), cancellable = true, remap = false)
    void appliedTacz$getPlayerIngredientCount(GunSmithTableRecipe recipe, CallbackInfo ci) {
        if (!(this.menu instanceof AEGunSmithTableMenu aeMenu)) {
            return;
        }

        ci.cancel();

        if (Minecraft.getInstance().player == null || recipe == null) {
            this.playerIngredientCount = new Int2IntArrayMap();
            return;
        }

        ResourceLocation recipeId = recipe.getId();

        // Notify the server to start watching this recipe (only when it changes)
        if (!recipeId.equals(this.appliedTacz$watchedRecipeId)) {
            this.appliedTacz$watchedRecipeId = recipeId;
            AppliedTaczNetwork.sendToServer(new RequestIngredientCountsPacket(aeMenu.containerId, recipeId));
        }

        // 1. Use server-pushed synced counts if available for this exact recipe
        if (aeMenu.hasSyncedIngredientCounts(recipeId)) {
            appliedTacz$applySyncedIngredientCounts(aeMenu, recipe);
            return;
        }

        // 2. Use the client-side cache to avoid flashing zeros while the first push is in flight
        Int2IntArrayMap cached = appliedTacz$countsCache.get(recipeId);
        if (cached != null) {
            this.playerIngredientCount = new Int2IntArrayMap(cached);
            return;
        }

        // 3. Last resort: count only what is in the player's inventory
        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        this.playerIngredientCount = new Int2IntArrayMap(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            GunSmithTableIngredient ingredient = ingredients.get(i);
            int count = 0;
            for (ItemStack stack : inventory.items) {
                if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {
                    count += stack.getCount();
                }
            }
            this.playerIngredientCount.put(i, count);
        }
    }

    @Inject(method = "addCraftButton", at = @At("HEAD"), cancellable = true, remap = false)
    void appliedTacz$addCraftButton(CallbackInfo ci) {
        if (!(this.menu instanceof AEGunSmithTableMenu aeMenu)) {
            return;
        }

        ci.cancel();
        this.addRenderableWidget(new ImageButton(leftPos + 289, topPos + 162, 48, 18, 138, 164, 18, TEXTURE, b -> {
            if (this.selectedRecipe == null) {
                return;
            }

            boolean isCreative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
            if (!isCreative && aeMenu.hasSyncedIngredientCounts(this.selectedRecipe.getId())) {
                List<GunSmithTableIngredient> inputs = this.selectedRecipe.getInputs();
                for (int i = 0; i < inputs.size(); i++) {
                    if (aeMenu.getSyncedIngredientCount(i) < inputs.get(i).getCount()) {
                        return;
                    }
                }
            } else if (!isCreative) {
                AppliedTaczNetwork.sendToServer(new RequestIngredientCountsPacket(aeMenu.containerId, this.selectedRecipe.getId()));
            }

            NetworkHandler.CHANNEL.sendToServer(new ClientMessageCraft(this.selectedRecipe.getId(), this.menu.containerId));
        }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.menu instanceof AEGunSmithTableMenu aeMenu && this.selectedRecipe != null) {
            int ingredientIndex = appliedTacz$getIngredientIndexAt(mouseX, mouseY);
            if (ingredientIndex >= 0) {
                MousePositionRestorer.rememberCurrentPosition();
                RecipeSelectionRestorer.remember(this.selectedRecipe.getId());
                AppliedTaczNetwork.sendToServer(new RequestIngredientAutocraftPacket(
                        aeMenu.containerId,
                        this.selectedRecipe.getId(),
                        ingredientIndex));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int appliedTacz$getIngredientIndexAt(double mouseX, double mouseY) {
        if (this.selectedRecipe == null) {
            return -1;
        }

        int localX = (int) mouseX - this.leftPos - 254;
        int localY = (int) mouseY - this.topPos - 62;
        if (localX < 0 || localY < 0) {
            return -1;
        }

        int column = localX / 45;
        int row = localY / 17;
        if (column < 0 || column >= 2 || row < 0 || row >= 6) {
            return -1;
        }
        if (localX % 45 >= 45 || localY % 17 >= 17) {
            return -1;
        }

        int index = row * 2 + column;
        return index < this.selectedRecipe.getInputs().size() ? index : -1;
    }

    private void appliedTacz$renderCraftableMarkers(GuiGraphics graphics) {
        if (!(this.menu instanceof AEGunSmithTableMenu aeMenu) || this.selectedRecipe == null) {
            return;
        }
        if (!aeMenu.hasSyncedIngredientCounts(this.selectedRecipe.getId())) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        int size = this.selectedRecipe.getInputs().size();
        for (int index = 0; index < size; index++) {
            if (!aeMenu.isSyncedIngredientCraftable(index)) {
                continue;
            }
            int column = index % 2;
            int row = index / 2;
            int x = this.leftPos + 254 + 45 * column + 10;
            int y = this.topPos + 62 + 17 * row + 8;
            graphics.drawString(this.font, "+", x, y, CRAFTABLE_MARKER_WHITE, true);
        }
        graphics.pose().popPose();
    }

    @Redirect(
            method = "renderIngredient",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"),
            remap = false)
    private String appliedTacz$formatIngredientAmount(String format, Object[] args) {
        if (!(this.menu instanceof AEGunSmithTableMenu)) {
            return String.format(Locale.ROOT, format, args);
        }

        if (args.length == 1 && args[0] instanceof Number needed) {
            return appliedTacz$formatAmount(needed.longValue()) + "/∞";
        }

        if (args.length == 2 && args[0] instanceof Number needed && args[1] instanceof Number available) {
            return appliedTacz$formatAmount(needed.longValue()) + "/" + appliedTacz$formatAmount(available.longValue());
        }

        return String.format(Locale.ROOT, format, args);
    }

    @Unique
    private static String appliedTacz$formatAmount(long amount) {
        if (amount < 1000) {
            return Long.toString(amount);
        }

        int index = 0;
        double value = amount;
        while (value >= 999.95 && index < APPLIED_TACZ_AMOUNT_SUFFIXES.length - 1) {
            value /= 1000.0;
            index++;
        }

        DecimalFormat format = value >= 100
                ? APPLIED_TACZ_WHOLE_FORMAT
                : value >= 10 ? APPLIED_TACZ_SINGLE_DECIMAL_FORMAT : APPLIED_TACZ_DOUBLE_DECIMAL_FORMAT;
        return format.format(value) + APPLIED_TACZ_AMOUNT_SUFFIXES[index];
    }

    /**
     * Called when a {@link me.myogoo.appliedtacz.network.packet.SyncIngredientCountsPacket}
     * arrives from the server.  Applies the fresh counts immediately and updates the
     * per-recipe cache so that future recipe switches don't show a 0-flash.
     */
    @Override
    public void appliedTacz$applyIngredientCounts() {
        if (!(this.menu instanceof AEGunSmithTableMenu aeMenu) || this.selectedRecipe == null) {
            return;
        }
        if (!aeMenu.hasSyncedIngredientCounts(this.selectedRecipe.getId())) {
            return;
        }
        appliedTacz$applySyncedIngredientCounts(aeMenu, this.selectedRecipe);
        // Keep a copy in the cache so switching away and back shows the last known counts
        appliedTacz$countsCache.put(this.selectedRecipe.getId(), new Int2IntArrayMap(this.playerIngredientCount));
    }

    private void appliedTacz$applySyncedIngredientCounts(AEGunSmithTableMenu aeMenu, GunSmithTableRecipe recipe) {
        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        this.playerIngredientCount = new Int2IntArrayMap(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            this.playerIngredientCount.put(i, aeMenu.getSyncedIngredientCount(i));
        }
    }
}
