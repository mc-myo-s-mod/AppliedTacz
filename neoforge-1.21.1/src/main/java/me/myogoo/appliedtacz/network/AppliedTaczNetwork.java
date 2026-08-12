package me.myogoo.appliedtacz.network;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.network.packet.RequestIngredientAutocraftPacket;
import me.myogoo.appliedtacz.network.packet.RequestIngredientCountsPacket;
import me.myogoo.appliedtacz.network.packet.SyncIngredientCountsPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class AppliedTaczNetwork {
    private AppliedTaczNetwork() {
    }

    public static void init(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(AppliedTaCZ.MODID);
        registrar.playToServer(
                RequestIngredientCountsPacket.TYPE,
                RequestIngredientCountsPacket.STREAM_CODEC,
                RequestIngredientCountsPacket::handle
        );
        registrar.playToServer(
                RequestIngredientAutocraftPacket.TYPE,
                RequestIngredientAutocraftPacket.STREAM_CODEC,
                RequestIngredientAutocraftPacket::handle
        );
        registrar.playToClient(
                SyncIngredientCountsPacket.TYPE,
                SyncIngredientCountsPacket.STREAM_CODEC,
                SyncIngredientCountsPacket::handle
        );
    }
}
