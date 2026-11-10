package me.myogoo.appliedtacz.compat.jei;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.integration.modules.jeirei.TransferHelper;
import appeng.menu.me.common.GridInventoryEntry;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class TaCZPatternTransferHandler
        implements IRecipeTransferHandler<TaCZPatternEncodingMenu, GunSmithTableRecipe> {
    private final RecipeType<GunSmithTableRecipe> recipeType;
    private final IRecipeTransferHandlerHelper helper;
    private final ResourceLocation workbenchId;

    TaCZPatternTransferHandler(ResourceLocation workbenchId, RecipeType<GunSmithTableRecipe> recipeType,
                              IRecipeTransferHandlerHelper helper) {
        this.workbenchId = workbenchId;
        this.recipeType = recipeType;
        this.helper = helper;
    }

    @Override
    public Class<TaCZPatternEncodingMenu> getContainerClass() {
        return TaCZPatternEncodingMenu.class;
    }

    @Override
    public Optional<MenuType<TaCZPatternEncodingMenu>> getMenuType() {
        return Optional.of(TaCZPatternEncodingMenu.TYPE);
    }

    @Override
    public RecipeType<GunSmithTableRecipe> getRecipeType() {
        return recipeType;
    }

    @Override
    public IRecipeTransferError transferRecipe(TaCZPatternEncodingMenu menu, GunSmithTableRecipe recipe,
                                              IRecipeSlotsView slots, Player player,
                                              boolean maxTransfer, boolean doTransfer) {
        // JEI runs on the client; selectRecipe rechecks menu validity on the server.
        if (!menu.canSelectRecipe(workbenchId.toString(), recipe.getId().toString())) {
            return helper.createInternalError();
        }
        if (doTransfer) {
            menu.selectRecipe(workbenchId.toString(), recipe.getId().toString());
            return null;
        }
        var repo = menu.getClientRepo();
        var craftableKeys = repo == null ? Set.<AEKey>of() : repo.getAllEntries().stream()
                .filter(GridInventoryEntry::isCraftable)
                .map(GridInventoryEntry::getWhat).collect(Collectors.toSet());
        var craftableSlots = slots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                .filter(slot -> slot.getIngredients(VanillaTypes.ITEM_STACK)
                        .anyMatch(stack -> {
                            var key = AEItemKey.of(stack);
                            return key != null && craftableKeys.contains(key);
                        }))
                .toList();
        return new EncodingDisplay(craftableSlots);
    }

    private record EncodingDisplay(List<IRecipeSlotView> craftableSlots) implements IRecipeTransferError {
        @Override
        public Type getType() {
            return Type.COSMETIC;
        }

        @Override
        public int getButtonHighlightColor() {
            return 0;
        }

        @Override
        public void showError(GuiGraphics graphics, int mouseX, int mouseY, IRecipeSlotsView slots,
                              int recipeX, int recipeY) {
            graphics.pose().pushPose();
            graphics.pose().translate(recipeX, recipeY, 0);
            for (var slot : craftableSlots) {
                slot.drawHighlight(graphics, TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR);
            }
            graphics.pose().popPose();
            graphics.renderComponentTooltip(Minecraft.getInstance().font,
                    TransferHelper.createEncodingTooltip(!craftableSlots.isEmpty()), mouseX, mouseY);
        }
    }
}
