package me.myogoo.appliedtacz.mixin.client;

import appeng.client.gui.me.crafting.CraftAmountScreen;
import me.myogoo.appliedtacz.client.MousePositionRestorer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftAmountScreen.class, remap = false)
public abstract class CraftAmountScreenMixin {
    @Inject(method = "updateBeforeRender", at = @At("HEAD"))
    private void appliedTacz$restoreMousePosition(CallbackInfo ci) {
        MousePositionRestorer.restoreIfPending();
    }

    @Inject(method = "confirm", at = @At("HEAD"))
    private void appliedTacz$rememberMousePositionBeforeConfirm(CallbackInfo ci) {
        MousePositionRestorer.rememberCurrentPositionForReturnToMainMenu();
    }
}
