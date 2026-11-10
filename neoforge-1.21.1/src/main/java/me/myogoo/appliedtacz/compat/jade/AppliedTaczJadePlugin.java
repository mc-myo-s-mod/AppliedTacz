package me.myogoo.appliedtacz.compat.jade;

import com.tacz.guns.block.AbstractGunSmithTableBlock;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(AppliedTaCZ.MODID)
public final class AppliedTaczJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(AppliedTaczJadeProvider.INSTANCE, AEGunSmithTableBlockEntity.class);
        registration.registerBlockDataProvider(AssemblerJadeProvider.INSTANCE, AEMolecularAssemblerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // AE2 owns the grid_node_state config; registering it again aborts this plugin.
        registration.registerBlockComponent(AppliedTaczJadeProvider.INSTANCE, AbstractGunSmithTableBlock.class);
        registration.registerBlockComponent(AssemblerJadeProvider.INSTANCE, MolecularAssemblerBlock.class);
    }
}
