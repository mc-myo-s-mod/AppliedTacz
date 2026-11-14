package me.myogoo.appliedtacz.client;

import appeng.api.util.AEColor;
import appeng.client.gui.style.StyleManager;
import appeng.client.render.StaticItemColor;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import me.myogoo.appliedtacz.client.model.ExtendedAssemblerModelLoader;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import me.myogoo.appliedtacz.init.AETaCZBlockEntity;
import me.myogoo.appliedtacz.init.AETaczMenu;
import me.myogoo.appliedtacz.client.renderer.AEGunSmithTableRenderer;
import me.myogoo.appliedtacz.client.renderer.AEMolecularAssemblerRenderer;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;
import me.myogoo.appliedtacz.client.TaCZPatternEncodingScreen;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class AppliedTaczClient {
    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(AETaCZBlock.MOLECULAR_ASSEMBLER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AETaCZBlock.TACZ_ASSEMBLER_AMMO.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AETaCZBlock.TACZ_ASSEMBLER_ATTACHMENT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AETaCZBlock.TACZ_ASSEMBLER_GUN.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_AMMO.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_GUN.get(), RenderType.cutout());
            MenuScreens.register(AETaczMenu.AE_GUN_SMITH_TABLE.get(), GunSmithTableScreen::new);
            MenuScreens.<AEMolecularAssemblerMenu, AEMolecularAssemblerScreen>register(
                    AETaczMenu.AE_MOLECULAR_ASSEMBLER.get(), (menu, inventory, title) ->
                            new AEMolecularAssemblerScreen(menu, inventory, title,
                                    StyleManager.loadStyleDoc("/screens/appliedtacz_molecular_assembler.json")));
            MenuScreens.<TaCZPatternEncodingMenu, TaCZPatternEncodingScreen>register(
                    TaCZPatternEncodingMenu.TYPE, (menu, inventory, title) ->
                            new TaCZPatternEncodingScreen(menu, inventory, title,
                                    StyleManager.loadStyleDoc("/screens/appliedtacz_pattern_encoding_terminal.json")));
        });
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        BlockEntityRenderers.register(AETaCZBlockEntity.AE_GUN_SMITH_TABLE.get(), AEGunSmithTableRenderer::new);
        BlockEntityRenderers.register(AETaCZBlockEntity.AE_MOLECULAR_ASSEMBLER.get(), AEMolecularAssemblerRenderer::new);
    }

    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        AETaCZItemDecorators.register(event);
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(new StaticItemColor(AEColor.TRANSPARENT), AETaCZBlock.TACZ_PATTERN_ENCODING_TERMINAL.get());
    }

    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("extended_assembler", new ExtendedAssemblerModelLoader());
    }
}
