package me.myogoo.appliedtacz.compat.emi;

import appeng.integration.modules.jeirei.TransferHelper;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

final class EmiEncodingSupport {
    static final int BLUE_SLOT_HIGHLIGHT_COLOR = TransferHelper.BLUE_SLOT_HIGHLIGHT_COLOR;

    private EmiEncodingSupport() {
    }

    static ResourceLocation recipeId(EmiRecipe recipe) {
        // JEMI uses a synthetic display ID, but preserves the real backing recipe.
        var backing = recipe.getBackingRecipe();
        return backing instanceof GunSmithTableRecipe gunRecipe ? gunRecipe.getId() : null;
    }

    static List<Component> encodingTooltip(boolean hasCraftable) {
        return TransferHelper.createEncodingTooltip(hasCraftable);
    }
}
