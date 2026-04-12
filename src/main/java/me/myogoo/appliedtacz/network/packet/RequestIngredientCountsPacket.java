package me.myogoo.appliedtacz.network.packet;

import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent by the client when the player selects a recipe.
 * The server registers this recipe as "watched" and will push
 * ingredient counts via broadcastChanges() whenever they change.
 */
public record RequestIngredientCountsPacket(int containerId, ResourceLocation recipeId) {
    public static void encode(RequestIngredientCountsPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeResourceLocation(packet.recipeId);
    }

    public static RequestIngredientCountsPacket decode(FriendlyByteBuf buf) {
        return new RequestIngredientCountsPacket(buf.readVarInt(), buf.readResourceLocation());
    }

    public static void handle(RequestIngredientCountsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                return;
            }
            if (sender.containerMenu.containerId != packet.containerId) {
                return;
            }
            if (!(sender.containerMenu instanceof AEGunSmithTableMenu menu)) {
                return;
            }
            // Register the recipe to watch; broadcastChanges() will push counts automatically
            menu.setWatchedRecipe(packet.recipeId);
        });
        context.setPacketHandled(true);
    }
}
