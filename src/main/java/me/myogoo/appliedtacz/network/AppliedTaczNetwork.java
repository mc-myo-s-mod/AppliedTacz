package me.myogoo.appliedtacz.network;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.network.packet.RequestIngredientAutocraftPacket;
import me.myogoo.appliedtacz.network.packet.RequestIngredientCountsPacket;
import me.myogoo.appliedtacz.network.packet.SyncIngredientCountsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class AppliedTaczNetwork {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean initialized;

    private AppliedTaczNetwork() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        int id = 0;
        CHANNEL.registerMessage(id++, RequestIngredientCountsPacket.class,
                RequestIngredientCountsPacket::encode,
                RequestIngredientCountsPacket::decode,
                RequestIngredientCountsPacket::handle);
        CHANNEL.registerMessage(id++, RequestIngredientAutocraftPacket.class,
                RequestIngredientAutocraftPacket::encode,
                RequestIngredientAutocraftPacket::decode,
                RequestIngredientAutocraftPacket::handle);
        CHANNEL.registerMessage(id, SyncIngredientCountsPacket.class,
                SyncIngredientCountsPacket::encode,
                SyncIngredientCountsPacket::decode,
                SyncIngredientCountsPacket::handle);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
