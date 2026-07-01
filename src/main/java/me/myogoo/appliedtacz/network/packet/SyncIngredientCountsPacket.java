package me.myogoo.appliedtacz.network.packet;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.client.AppliedTaczClientHooks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncIngredientCountsPacket(int containerId, ResourceLocation recipeId, Int2IntArrayMap counts,
        IntSet craftableIngredients)
        implements CustomPacketPayload {
    public static final Type<SyncIngredientCountsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, "sync_ingredient_counts"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncIngredientCountsPacket> STREAM_CODEC = StreamCodec.ofMember(
            SyncIngredientCountsPacket::write,
            SyncIngredientCountsPacket::decode
    );

    private static SyncIngredientCountsPacket decode(RegistryFriendlyByteBuf buffer) {
        int containerId = buffer.readInt();
        ResourceLocation recipeId = ResourceLocation.STREAM_CODEC.decode(buffer);
        int size = buffer.readInt();
        Int2IntArrayMap counts = new Int2IntArrayMap(size);
        for (int i = 0; i < size; i++) {
            counts.put(i, ByteBufCodecs.INT.decode(buffer).intValue());
        }
        int craftableSize = buffer.readInt();
        IntArraySet craftableIngredients = new IntArraySet(craftableSize);
        for (int i = 0; i < craftableSize; i++) {
            craftableIngredients.add(buffer.readInt());
        }
        return new SyncIngredientCountsPacket(containerId, recipeId, counts, craftableIngredients);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(containerId);
        ResourceLocation.STREAM_CODEC.encode(buffer, recipeId);
        buffer.writeInt(counts.size());
        for (int i = 0; i < counts.size(); i++) {
            ByteBufCodecs.INT.encode(buffer, counts.get(i));
        }
        buffer.writeInt(craftableIngredients.size());
        for (int index : craftableIngredients) {
            buffer.writeInt(index);
        }
    }

    public static void handle(SyncIngredientCountsPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> AppliedTaczClientHooks.handleIngredientCounts(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
