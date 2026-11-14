package me.myogoo.appliedtacz.compat.emi;

import appeng.integration.modules.itemlists.TransferHelper;
import com.tacz.guns.compat.emi.TaczEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

final class EmiEncodingSupport {
    static final int BLUE_SLOT_HIGHLIGHT_COLOR = TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR;

    private EmiEncodingSupport() {
    }

    static ResourceLocation recipeId(EmiRecipe recipe) {
        // TaCZ's native EMI display ID is synthetic and has no vanilla backing recipe.
        return recipe instanceof TaczEmiRecipe taczRecipe && taczRecipe.isGunSmithTableRecipe()
                ? taczRecipe.getSelectionRecipeId() : null;
    }

    static List<Component> encodingTooltip(boolean hasCraftable) {
        return TransferHelper.createEncodingTooltip(hasCraftable, false);
    }
}
