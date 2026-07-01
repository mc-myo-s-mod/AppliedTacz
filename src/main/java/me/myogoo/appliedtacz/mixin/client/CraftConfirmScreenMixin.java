package me.myogoo.appliedtacz.mixin.client;

import appeng.client.gui.me.crafting.CraftConfirmScreen;
import me.myogoo.appliedtacz.client.MousePositionRestorer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CraftConfirmScreen.class, remap = false)
public abstract class CraftConfirmScreenMixin {
    @Inject(method = "updateBeforeRender", at = @At("HEAD"))
    private void appliedTacz$restoreMousePosition(CallbackInfo ci) {
        MousePositionRestorer.restoreIfPending();
    }

    @Inject(method = "start", at = @At("HEAD"))
    private void appliedTacz$rememberMousePositionBeforeStart(CallbackInfo ci) {
        MousePositionRestorer.rememberCurrentPositionForReturnToMainMenu();
    }
}
