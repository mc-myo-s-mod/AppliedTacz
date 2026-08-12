package me.myogoo.appliedtacz.network.packet;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import me.myogoo.appliedtacz.client.AppliedTaczClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SyncIngredientCountsPacket(int containerId, ResourceLocation recipeId, Int2IntArrayMap counts,
        IntSet craftableIngredients) {
    public static void encode(SyncIngredientCountsPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.containerId);
        buf.writeResourceLocation(packet.recipeId);
        buf.writeVarInt(packet.counts.size());
        for (int i = 0; i < packet.counts.size(); i++) {
            buf.writeVarInt(packet.counts.get(i));
        }
        buf.writeVarInt(packet.craftableIngredients.size());
        for (int index : packet.craftableIngredients) {
            buf.writeVarInt(index);
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
        int craftableSize = buf.readVarInt();
        IntArraySet craftableIngredients = new IntArraySet(craftableSize);
        for (int i = 0; i < craftableSize; i++) {
            craftableIngredients.add(buf.readVarInt());
        }
        return new SyncIngredientCountsPacket(containerId, recipeId, counts, craftableIngredients);
    }

    public static void handle(SyncIngredientCountsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> AppliedTaczClientHooks.handleIngredientCounts(packet)));
        context.setPacketHandled(true);
    }
}
