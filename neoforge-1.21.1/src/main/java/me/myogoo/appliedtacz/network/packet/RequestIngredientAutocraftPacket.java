package me.myogoo.appliedtacz.network.packet;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestIngredientAutocraftPacket(int containerId, ResourceLocation recipeId,
        int ingredientIndex) implements CustomPacketPayload {
    public static final Type<RequestIngredientAutocraftPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, "request_ingredient_autocraft"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestIngredientAutocraftPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    RequestIngredientAutocraftPacket::containerId,
                    ResourceLocation.STREAM_CODEC,
                    RequestIngredientAutocraftPacket::recipeId,
                    ByteBufCodecs.INT,
                    RequestIngredientAutocraftPacket::ingredientIndex,
                    RequestIngredientAutocraftPacket::new
            );

    public static void handle(RequestIngredientAutocraftPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) {
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
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
