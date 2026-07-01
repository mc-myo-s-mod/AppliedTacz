package me.myogoo.appliedtacz.network.packet;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RequestIngredientCountsPacket(int containerId, ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<RequestIngredientCountsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, "request_ingredient_counts"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestIngredientCountsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            RequestIngredientCountsPacket::containerId,
            ResourceLocation.STREAM_CODEC,
            RequestIngredientCountsPacket::recipeId,
            RequestIngredientCountsPacket::new
    );

    public static void handle(RequestIngredientCountsPacket packet, IPayloadContext context) {
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

            Int2IntArrayMap counts = menu.getAvailableIngredientCounts(packet.recipeId, sender);
            menu.setWatchedRecipe(packet.recipeId);
            PacketDistributor.sendToPlayer(sender, new SyncIngredientCountsPacket(packet.containerId, packet.recipeId, counts,
                    menu.getCraftableIngredientIndices(packet.recipeId, sender)));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
