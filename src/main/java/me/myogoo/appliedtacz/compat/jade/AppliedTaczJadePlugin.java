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
        registration.addConfig(AppliedTaczJadeProvider.INSTANCE.getUid(), true);
        registration.registerBlockComponent(AppliedTaczJadeProvider.INSTANCE, AbstractGunSmithTableBlock.class);
    }
}
