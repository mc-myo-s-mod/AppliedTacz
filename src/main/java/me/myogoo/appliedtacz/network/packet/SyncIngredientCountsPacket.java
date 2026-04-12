package me.myogoo.appliedtacz.network.packet;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import me.myogoo.appliedtacz.client.AppliedTaczClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncIngredientCountsPacket(int containerId, ResourceLocation recipeId, Int2IntArrayMap counts) {
    public static void encode(SyncIngredientCountsPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeResourceLocation(packet.recipeId);
        buf.writeVarInt(packet.counts.size());
        for (int i = 0; i < packet.counts.size(); i++) {
            buf.writeVarInt(packet.counts.get(i));
        }
    }

    public static SyncIngredientCountsPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        ResourceLocation recipeId = buf.readResourceLocation();
        int size = buf.readVarInt();
        Int2IntArrayMap counts = new Int2IntArrayMap(size);
        for (int i = 0; i < size; i++) {
            counts.put(i, buf.readVarInt());
        }
        return new SyncIngredientCountsPacket(containerId, recipeId, counts);
    }

    public static void handle(SyncIngredientCountsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AppliedTaczClientHooks.handleIngredientCounts(packet)));
        context.setPacketHandled(true);
    }
}
