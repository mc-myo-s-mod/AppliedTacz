package me.myogoo.appliedtacz.mixin;

import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = GunSmithTableMenu.class, remap = false)
public interface GunSmithTableMenuAccessor {
    @Invoker("getRecipe")
    GunSmithTableRecipe appliedtacz$getRecipe(ResourceLocation id, RecipeManager manager);
}
