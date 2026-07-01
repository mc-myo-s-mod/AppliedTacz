package me.myogoo.appliedtacz;

import appeng.api.AECapabilities;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import me.myogoo.appliedtacz.client.AppliedTaczClient;
import me.myogoo.appliedtacz.datagen.AppliedTaczDataGenerators;
import me.myogoo.appliedtacz.network.AppliedTaczNetwork;
import me.myogoo.appliedtacz.registry.ModBlockEntities;
import me.myogoo.appliedtacz.registry.ModBlocks;
import me.myogoo.appliedtacz.registry.ModItems;
import me.myogoo.appliedtacz.registry.ModMenus;
import me.myogoo.appliedtacz.registry.ModRecipeSerializers;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

import java.util.Comparator;

@Mod(AppliedTaCZ.MODID)
public class AppliedTaCZ {
    public static final String MODID = "appliedtacz";

    public AppliedTaCZ(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);

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
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            var entries = TimelessAPI.getAllCommonBlockIndex().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                    .toList();
            if (entries.isEmpty()) {
                event.accept(ModItems.GUN_SMITH_TABLE.get());
                event.accept(ModItems.ATTACHMENT_TABLE.get());
                event.accept(ModItems.AMMO_WORKBENCH.get());
                return;
            }
            entries.forEach(entry -> event.accept(BlockItemBuilder
                    .create(AETaCZWorkbenchIds.getBlockForBaseWorkbench(entry.getValue().getPojo().getId()))
                    .setId(entry.getKey())
                    .build()));
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.AE_GUN_SMITH_TABLE.get(),
                (blockEntity, context) -> blockEntity
        );
    }
}
