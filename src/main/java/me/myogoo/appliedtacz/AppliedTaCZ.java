package me.myogoo.appliedtacz;

import me.myogoo.appliedtacz.init.AETaCZBlock;
import me.myogoo.appliedtacz.init.AETaCZBlockEntity;
import me.myogoo.appliedtacz.init.AETaCZCreativeTab;
import me.myogoo.appliedtacz.init.AETaCZRecipeSerializer;
import me.myogoo.appliedtacz.init.AETaczMenu;
import me.myogoo.appliedtacz.network.AppliedTaczNetwork;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AppliedTaCZ.MODID)
public class AppliedTaCZ {
    public static final String MODID = "appliedtacz";

    public AppliedTaCZ() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        AppliedTaczNetwork.init();

        AETaCZBlock.BLOCkS.register(modEventBus);
        AETaCZBlock.ITEMS.register(modEventBus);
        AETaczMenu.REGISTER.register(modEventBus);
        AETaCZBlockEntity.REGISTER.register(modEventBus);
        AETaCZCreativeTab.REGISTER.register(modEventBus);
        AETaCZRecipeSerializer.REGISTER.register(modEventBus);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modEventBus.addListener(me.myogoo.appliedtacz.client.AppliedTaczClient::init);
            modEventBus.addListener(me.myogoo.appliedtacz.client.AppliedTaczClient::registerRenderers);
            modEventBus.addListener(me.myogoo.appliedtacz.client.AppliedTaczClient::registerItemDecorations);
        });

    }
}
