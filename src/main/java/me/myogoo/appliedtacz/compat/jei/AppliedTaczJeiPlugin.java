package me.myogoo.appliedtacz.compat.jei;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.registry.ModItems;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public class AppliedTaczJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, "jei_plugin");

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
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.GUN_SMITH_TABLE.get(), tableSubtype);
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.AMMO_WORKBENCH.get(), tableSubtype);
        registration.registerSubtypeInterpreter(VanillaTypes.ITEM_STACK, ModItems.ATTACHMENT_TABLE.get(), tableSubtype);
    }

    private static RecipeType<RecipeHolder<GunSmithTableRecipe>> recipeType(ResourceLocation blockId) {
        return RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(GunMod.MOD_ID,
                "gun_smith_table/" + blockId.toString().replace(':', '_')));
    }

    private static ISubtypeInterpreter<ItemStack> tableSubtype() {
        return new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, UidContext context) {
                return tableSubtypeId(stack);
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                return tableSubtypeId(stack);
            }
        };
    }

    private static String tableSubtypeId(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof IBlock blockItem) {
            return blockItem.getBlockId(stack).toString();
        }
        return "";
    }
}
