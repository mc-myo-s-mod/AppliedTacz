package me.myogoo.appliedtacz.client;

import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.network.packet.SyncIngredientCountsPacket;
import net.minecraft.client.Minecraft;

public final class AppliedTaczClientHooks {
    private AppliedTaczClientHooks() {
    }

    public static void handleIngredientCounts(SyncIngredientCountsPacket packet) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.containerMenu.containerId != packet.containerId()) {
            return;
        }
        if (!(minecraft.player.containerMenu instanceof AEGunSmithTableMenu menu)) {
            return;
        }

        menu.setSyncedIngredientCounts(packet.recipeId(), packet.counts(), packet.craftableIngredients());
        if (minecraft.screen instanceof IngredientCountSyncTarget target) {
            target.appliedTacz$applyIngredientCounts();
        }
    }
}
