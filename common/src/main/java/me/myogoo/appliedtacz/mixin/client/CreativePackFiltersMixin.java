package me.myogoo.appliedtacz.mixin.client;

import me.myogoo.appliedtacz.AppliedTaCZ;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.client.gui.components.CreativePackFilters", remap = false)
public abstract class CreativePackFiltersMixin {
    @Inject(method = "getContentId(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void appliedtacz$groupOwnItems(ItemStack stack,
            CallbackInfoReturnable<ResourceLocation> callback) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (AppliedTaCZ.MODID.equals(itemId.getNamespace())) {
            // Keep the source workbench ID intact; only creative-sidebar grouping uses the item ID.
            callback.setReturnValue(itemId);
        }
    }
}
