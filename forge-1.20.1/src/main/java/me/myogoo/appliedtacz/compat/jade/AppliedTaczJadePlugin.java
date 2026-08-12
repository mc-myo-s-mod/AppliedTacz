package me.myogoo.appliedtacz.compat.jade;

import com.tacz.guns.block.AbstractGunSmithTableBlock;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(AppliedTaCZ.MODID)
public final class AppliedTaczJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(AppliedTaczJadeProvider.INSTANCE, AEGunSmithTableBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // AE2 already registers the ae2:grid_node_state config key. Reuse it, but do not
        // register the same config again on Jade 1.20.1, otherwise Jade aborts loading
        // this plugin with "Duplicate config key: ae2:grid_node_state".
        registration.registerBlockComponent(AppliedTaczJadeProvider.INSTANCE, AbstractGunSmithTableBlock.class);
    }
}
