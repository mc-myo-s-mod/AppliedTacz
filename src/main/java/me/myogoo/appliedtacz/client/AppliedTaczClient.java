package me.myogoo.appliedtacz.client;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import me.myogoo.appliedtacz.client.renderer.AEGunSmithTableRenderer;
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
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.AE_GUN_SMITH_TABLE.get(), AEGunSmithTableRenderer::new);
    }

    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        AETaCZItemDecorators.register(event);
    }
}
