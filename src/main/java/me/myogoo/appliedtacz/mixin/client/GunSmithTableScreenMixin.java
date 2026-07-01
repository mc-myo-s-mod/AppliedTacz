package me.myogoo.appliedtacz.mixin.client;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.components.smith.ImageButton;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.network.message.ClientMessageCraft;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import me.myogoo.appliedtacz.client.IngredientCountSyncTarget;
import me.myogoo.appliedtacz.client.MousePositionRestorer;
import me.myogoo.appliedtacz.client.RecipeSelectionRestorer;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.network.packet.RequestIngredientAutocraftPacket;
import me.myogoo.appliedtacz.network.packet.RequestIngredientCountsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
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

@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTableScreenMixin extends AbstractContainerScreen<com.tacz.guns.inventory.GunSmithTableMenu>
        implements IngredientCountSyncTarget {

    private static final int AE2_ICON_BLUE = 0x44B2EB;

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

    @Shadow
    @Final
    private static ResourceLocation TEXTURE;

    @Shadow
    private Int2IntArrayMap playerIngredientCount;

    @Shadow
    @Nullable
    private RecipeHolder<GunSmithTableRecipe> selectedRecipe;

    @Shadow
    private Map<ResourceLocation, List<ResourceLocation>> recipes;

    @Shadow
    private ResourceLocation selectedType;

    @Shadow
    private List<ResourceLocation> selectedRecipeList;

    @Shadow
    private int indexPage;

    private @Nullable ResourceLocation appliedTacz$watchedRecipeId;
    private final Map<ResourceLocation, Int2IntArrayMap> appliedTacz$countsCache = new HashMap<>();

    protected GunSmithTableScreenMixin(com.tacz.guns.inventory.GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void appliedTacz$renderCraftableMarkersOnly(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        if (!(menu instanceof AEGunSmithTableMenu)) {
            return;
        }

        MousePositionRestorer.restoreReturnToMainMenuIfPending();
        appliedTacz$renderCraftableMarkers(graphics);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void appliedTacz$restoreMouseAfterReturningToTable(CallbackInfo ci) {
        if (menu instanceof AEGunSmithTableMenu) {
            MousePositionRestorer.restoreReturnToMainMenuIfPending();
        }
    }

    @Inject(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/client/gui/GunSmithTableScreen;classifyRecipes()V",
                    shift = At.Shift.AFTER))
    private void appliedTacz$restorePendingRecipeSelection(CallbackInfo ci) {
        ResourceLocation recipeId = RecipeSelectionRestorer.getPendingRecipeId();
        if (recipeId == null || Minecraft.getInstance().level == null) {
            return;
        }

        var foundRecipe = Minecraft.getInstance().level.getRecipeManager().byKey(recipeId).orElse(null);
        if (!(foundRecipe instanceof RecipeHolder<?> holder)
                || !(holder.value() instanceof GunSmithTableRecipe recipe)) {
            RecipeSelectionRestorer.forget(recipeId);
            return;
        }

        ResourceLocation group = appliedTacz$findRecipeGroup(recipeId, recipe);
        List<ResourceLocation> recipeList = group != null ? recipes.get(group) : null;
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
        @SuppressWarnings("unchecked")
        RecipeHolder<GunSmithTableRecipe> gunRecipeHolder = (RecipeHolder<GunSmithTableRecipe>) holder;
        this.selectedRecipe = gunRecipeHolder;
        RecipeSelectionRestorer.forget(recipeId);
        appliedTacz$refreshSelectedRecipeCounts(recipeId, gunRecipeHolder);
    }

    private @Nullable ResourceLocation appliedTacz$findRecipeGroup(ResourceLocation recipeId, GunSmithTableRecipe recipe) {
        ResourceLocation recipeGroup = recipe.getResult().getGroup();
        if (recipes.containsKey(recipeGroup)) {
            return recipeGroup;
        }

        for (Map.Entry<ResourceLocation, List<ResourceLocation>> entry : recipes.entrySet()) {
            if (entry.getValue().contains(recipeId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void appliedTacz$refreshSelectedRecipeCounts(ResourceLocation recipeId,
            RecipeHolder<GunSmithTableRecipe> recipe) {
        if (menu instanceof AEGunSmithTableMenu aeMenu) {
            appliedTacz$watchedRecipeId = recipeId;
            PacketDistributor.sendToServer(new RequestIngredientCountsPacket(aeMenu.containerId, recipeId));
            if (aeMenu.hasSyncedIngredientCounts(recipeId)) {
                appliedTacz$applySyncedIngredientCounts(aeMenu, recipe);
                return;
            }
        }

        Int2IntArrayMap cached = appliedTacz$countsCache.get(recipeId);
        if (cached != null) {
            playerIngredientCount = new Int2IntArrayMap(cached);
            return;
        }

        appliedTacz$applyPlayerInventoryIngredientCounts(recipe.value());
    }

    private void appliedTacz$applyPlayerInventoryIngredientCounts(GunSmithTableRecipe recipe) {
        if (Minecraft.getInstance().player == null) {
            playerIngredientCount = new Int2IntArrayMap();
            return;
        }

        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        playerIngredientCount = new Int2IntArrayMap(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            GunSmithTableIngredient ingredient = ingredients.get(i);
            int availableCount = 0;
            for (ItemStack stack : inventory.items) {
                if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {
                    availableCount += stack.getCount();
                }
            }
            playerIngredientCount.put(i, availableCount);
        }
    }

    @Inject(method = "getPlayerIngredientCount", at = @At("HEAD"), cancellable = true)
    private void appliedTacz$getPlayerIngredientCount(@Nullable RecipeHolder<GunSmithTableRecipe> recipe, CallbackInfo ci) {
        if (!(menu instanceof AEGunSmithTableMenu aeMenu)) {
            return;
        }

        ci.cancel();
        if (Minecraft.getInstance().player == null || recipe == null) {
            playerIngredientCount = new Int2IntArrayMap();
            appliedTacz$watchedRecipeId = null;
            return;
        }

        ResourceLocation recipeId = recipe.id();
        if (!recipeId.equals(appliedTacz$watchedRecipeId)) {
            appliedTacz$watchedRecipeId = recipeId;
            PacketDistributor.sendToServer(new RequestIngredientCountsPacket(aeMenu.containerId, recipeId));
        }

        if (aeMenu.hasSyncedIngredientCounts(recipeId)) {
            appliedTacz$applySyncedIngredientCounts(aeMenu, recipe);
            return;
        }

        Int2IntArrayMap cached = appliedTacz$countsCache.get(recipeId);
        if (cached != null) {
            playerIngredientCount = new Int2IntArrayMap(cached);
            return;
        }

        List<GunSmithTableIngredient> ingredients = recipe.value().getInputs();
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        playerIngredientCount = new Int2IntArrayMap(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            GunSmithTableIngredient ingredient = ingredients.get(i);
            int availableCount = 0;
            for (ItemStack stack : inventory.items) {
                if (!stack.isEmpty() && ingredient.getIngredient().test(stack)) {
                    availableCount += stack.getCount();
                }
            }
            playerIngredientCount.put(i, availableCount);
        }
    }

    @Inject(method = "addCraftButton", at = @At("HEAD"), cancellable = true)
    private void appliedTacz$addCraftButton(CallbackInfo ci) {
        if (!(menu instanceof AEGunSmithTableMenu aeMenu)) {
            return;
        }

        ci.cancel();
        addRenderableWidget(new ImageButton(leftPos + 289, topPos + 162, 48, 18, 138, 164, 18, TEXTURE, button -> {
            if (selectedRecipe == null) {
                return;
            }

            boolean creative = Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative();
            if (!creative && aeMenu.hasSyncedIngredientCounts(selectedRecipe.id())) {
                List<GunSmithTableIngredient> inputs = selectedRecipe.value().getInputs();
                for (int i = 0; i < inputs.size(); i++) {
                    if (aeMenu.getSyncedIngredientCount(i) < inputs.get(i).getCount()) {
                        return;
                    }
                }
            } else if (!creative) {
                PacketDistributor.sendToServer(new RequestIngredientCountsPacket(aeMenu.containerId, selectedRecipe.id()));
            }

            PacketDistributor.sendToServer(new ClientMessageCraft(selectedRecipe.id(), menu.containerId));
        }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu instanceof AEGunSmithTableMenu aeMenu && selectedRecipe != null) {
            int ingredientIndex = appliedTacz$getIngredientIndexAt(mouseX, mouseY);
            if (ingredientIndex >= 0) {
                MousePositionRestorer.rememberCurrentPosition();
                RecipeSelectionRestorer.remember(selectedRecipe.id());
                PacketDistributor.sendToServer(new RequestIngredientAutocraftPacket(
                        aeMenu.containerId,
                        selectedRecipe.id(),
                        ingredientIndex));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int appliedTacz$getIngredientIndexAt(double mouseX, double mouseY) {
        if (selectedRecipe == null) {
            return -1;
        }

        int localX = (int) mouseX - leftPos - 254;
        int localY = (int) mouseY - topPos - 62;
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
        return index < selectedRecipe.value().getInputs().size() ? index : -1;
    }

    private void appliedTacz$renderCraftableMarkers(GuiGraphics graphics) {
        if (!(menu instanceof AEGunSmithTableMenu aeMenu) || selectedRecipe == null) {
            return;
        }
        if (!aeMenu.hasSyncedIngredientCounts(selectedRecipe.id())) {
            return;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);
        int size = selectedRecipe.value().getInputs().size();
        for (int index = 0; index < size; index++) {
            if (!aeMenu.isSyncedIngredientCraftable(index)) {
                continue;
            }
            int column = index % 2;
            int row = index / 2;
            int x = leftPos + 254 + 45 * column + 10;
            int y = topPos + 62 + 17 * row + 8;
            graphics.drawString(font, "+", x, y, AE2_ICON_BLUE, true);
        }
        graphics.pose().popPose();
    }

    @Redirect(
            method = "renderIngredient",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"))
    private String appliedTacz$formatIngredientAmount(String format, Object[] args) {
        if (!(menu instanceof AEGunSmithTableMenu)) {
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

    @Override
    public void appliedTacz$applyIngredientCounts() {
        if (!(menu instanceof AEGunSmithTableMenu aeMenu) || selectedRecipe == null) {
            return;
        }
        if (!aeMenu.hasSyncedIngredientCounts(selectedRecipe.id())) {
            return;
        }

        appliedTacz$applySyncedIngredientCounts(aeMenu, selectedRecipe);
        appliedTacz$countsCache.put(selectedRecipe.id(), new Int2IntArrayMap(playerIngredientCount));
    }

    private void appliedTacz$applySyncedIngredientCounts(AEGunSmithTableMenu aeMenu, RecipeHolder<GunSmithTableRecipe> recipe) {
        List<GunSmithTableIngredient> ingredients = recipe.value().getInputs();
        playerIngredientCount = new Int2IntArrayMap(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            playerIngredientCount.put(i, aeMenu.getSyncedIngredientCount(i));
        }
    }
}
