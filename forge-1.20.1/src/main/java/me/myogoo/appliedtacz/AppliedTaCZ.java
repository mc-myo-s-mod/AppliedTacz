package me.myogoo.appliedtacz;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.client.AppliedTaczClient;
import me.myogoo.appliedtacz.config.AppliedTaczClientConfig;
import me.myogoo.appliedtacz.config.AppliedTaczServerConfig;
import me.myogoo.appliedtacz.gametest.AppliedTaczGameTests;
import me.myogoo.appliedtacz.gametest.AssemblerAccelerationGameTests;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import me.myogoo.appliedtacz.init.AETaCZBlockEntity;
import me.myogoo.appliedtacz.init.AETaCZCreativeTab;
import me.myogoo.appliedtacz.init.AETaczMenu;
import me.myogoo.appliedtacz.network.AppliedTaczNetwork;
import me.myogoo.appliedtacz.gametest.PatternEncodingGameTests;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AppliedTaCZ.MODID)
public class AppliedTaCZ {
    public static final String MODID = "appliedtacz";

    public AppliedTaCZ() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, AppliedTaczServerConfig.SPEC,
                "appliedtacz-server.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AppliedTaczClientConfig.SPEC,
                "appliedtacz-client.toml");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        AppliedTaczNetwork.init();

        AETaCZBlock.BLOCkS.register(modEventBus);
        AETaCZBlock.ITEMS.register(modEventBus);
        AETaczMenu.REGISTER.register(modEventBus);
        AETaCZBlockEntity.REGISTER.register(modEventBus);
        modEventBus.addListener(AETaCZCreativeTab::addToTaczTab);
        modEventBus.addListener(AppliedTaczGameTests::register);
        modEventBus.addListener(AssemblerAccelerationGameTests::register);
        modEventBus.addListener(PatternEncodingGameTests::register);
        modEventBus.addListener(this::commonSetup);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modEventBus.addListener(AppliedTaczClient::init);
            modEventBus.addListener(AppliedTaczClient::registerRenderers);
            modEventBus.addListener(AppliedTaczClient::registerItemDecorations);
            modEventBus.addListener(AppliedTaczClient::registerItemColors);
            modEventBus.addListener(AppliedTaczClient::registerGeometryLoaders);
        });

    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> AETaCZBlock.BLOCkS.getEntries().forEach(entry -> {
            if (entry.get() instanceof MolecularAssemblerBlock block) {
                Upgrades.add(AEItems.SPEED_CARD, block, AEMolecularAssemblerBlockEntity.UPGRADE_SLOTS);
            }
        }));
    }
}
