package me.myogoo.appliedtacz;

import appeng.api.AECapabilities;
import me.myogoo.appliedtacz.client.AppliedTaczClient;
import me.myogoo.appliedtacz.config.AppliedTaczServerConfig;
import me.myogoo.appliedtacz.datagen.AppliedTaczDataGenerators;
import me.myogoo.appliedtacz.network.AppliedTaczNetwork;
import me.myogoo.appliedtacz.registry.ModBlockEntities;
import me.myogoo.appliedtacz.registry.ModBlocks;
import me.myogoo.appliedtacz.registry.ModItems;
import me.myogoo.appliedtacz.registry.ModMenus;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(AppliedTaCZ.MODID)
public class AppliedTaCZ {
    public static final String MODID = "appliedtacz";
    private static final ResourceKey<CreativeModeTab> TACZ_OTHER_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("tacz", "other")
    );

    public AppliedTaCZ(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, AppliedTaczServerConfig.SPEC,
                "appliedtacz-server.toml");

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        modEventBus.addListener(this::addCreativeTabItems);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(AppliedTaczNetwork::init);
        modEventBus.addListener(AppliedTaczDataGenerators::gatherData);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(AppliedTaczClient::registerScreens);
            modEventBus.addListener(AppliedTaczClient::registerRenderers);
            modEventBus.addListener(AppliedTaczClient::registerItemDecorations);
        }
    }

    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (CreativeModeTabs.FUNCTIONAL_BLOCKS.equals(event.getTabKey()) || TACZ_OTHER_TAB.equals(event.getTabKey())) {
            addWorkbenchItems(event);
        }
    }

    private static void addWorkbenchItems(BuildCreativeModeTabContentsEvent event) {
        var entries = AETaCZWorkbenchIndex.entries();
        if (entries.isEmpty()) {
            event.accept(ModItems.GUN_SMITH_TABLE.get());
            event.accept(ModItems.ATTACHMENT_TABLE.get());
            event.accept(ModItems.AMMO_WORKBENCH.get());
            return;
        }
        entries.forEach(entry -> event.accept(entry.createAppliedStack()));
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.AE_GUN_SMITH_TABLE.get(),
                (blockEntity, context) -> blockEntity
        );
    }
}
