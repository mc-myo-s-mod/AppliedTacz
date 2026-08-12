package me.myogoo.appliedtacz.compat.jei;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public class AppliedTaczJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, "jei_plugin");

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var entry : AETaCZWorkbenchIndex.entries()) {
            registration.addRecipeCatalyst(entry.createAppliedStack(), recipeType(entry.blockId()));
        }
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        var tableSubtype = tableSubtype();
        registration.registerSubtypeInterpreter(AETaCZBlock.GUN_SMITH_TABLE.get().asItem(), tableSubtype);
        registration.registerSubtypeInterpreter(AETaCZBlock.AMMO_ASSEMBLY_TABLE.get().asItem(), tableSubtype);
        registration.registerSubtypeInterpreter(AETaCZBlock.ATTACHMENT_TABLE.get().asItem(), tableSubtype);
    }

    private static RecipeType<GunSmithTableRecipe> recipeType(ResourceLocation blockId) {
        return RecipeType.create(GunMod.MOD_ID,
                "gun_smith_table/" + blockId.toString().replace(':', '_'),
                GunSmithTableRecipe.class);
    }

    private static IIngredientSubtypeInterpreter<ItemStack> tableSubtype() {
        return (stack, context) -> {
            if (stack.getItem() instanceof IBlock blockItem) {
                return blockItem.getBlockId(stack).toString();
            }
            return IIngredientSubtypeInterpreter.NONE;
        };
    }
}
