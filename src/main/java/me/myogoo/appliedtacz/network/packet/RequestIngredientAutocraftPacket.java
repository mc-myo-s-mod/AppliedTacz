package me.myogoo.appliedtacz.network.packet;

import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record RequestIngredientAutocraftPacket(int containerId, ResourceLocation recipeId, int ingredientIndex) {
    public static void encode(RequestIngredientAutocraftPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeResourceLocation(packet.recipeId);
        buf.writeVarInt(packet.ingredientIndex);
    }

    public static RequestIngredientAutocraftPacket decode(FriendlyByteBuf buf) {
        return new RequestIngredientAutocraftPacket(
                buf.readVarInt(),
                buf.readResourceLocation(),
                buf.readVarInt());
    }

    public static void handle(RequestIngredientAutocraftPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
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

            menu.requestIngredientAutocraft(packet.recipeId, packet.ingredientIndex, sender);
        });
        context.setPacketHandled(true);
    }
}
