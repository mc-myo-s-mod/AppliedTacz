package me.myogoo.appliedtacz.client;

import appeng.client.gui.style.StyleManager;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import me.myogoo.appliedtacz.client.renderer.AEGunSmithTableRenderer;
import me.myogoo.appliedtacz.client.renderer.AEMolecularAssemblerRenderer;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;
import me.myogoo.appliedtacz.client.TaCZPatternEncodingScreen;
import me.myogoo.appliedtacz.registry.ModBlockEntities;
import me.myogoo.appliedtacz.registry.ModMenus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class AppliedTaczClient {
    private AppliedTaczClient() {
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.AE_GUN_SMITH_TABLE.get(), GunSmithTableScreen::new);
        event.<AEMolecularAssemblerMenu, AEMolecularAssemblerScreen>register(
                ModMenus.AE_MOLECULAR_ASSEMBLER.get(), (menu, inventory, title) ->
                        new AEMolecularAssemblerScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc("/screens/appliedtacz_molecular_assembler.json")));
        event.<TaCZPatternEncodingMenu, TaCZPatternEncodingScreen>register(
                TaCZPatternEncodingMenu.TYPE, (menu, inventory, title) ->
                        new TaCZPatternEncodingScreen(menu, inventory, title,
                                StyleManager.loadStyleDoc("/screens/appliedtacz_pattern_encoding_terminal.json")));
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.AE_GUN_SMITH_TABLE.get(), AEGunSmithTableRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.AE_MOLECULAR_ASSEMBLER.get(), AEMolecularAssemblerRenderer::new);
    }

    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        AETaCZItemDecorators.register(event);
    }
}
