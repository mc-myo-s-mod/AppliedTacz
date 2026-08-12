package me.myogoo.appliedtacz.client;

import com.tacz.guns.client.gui.GunSmithTableScreen;
import me.myogoo.appliedtacz.init.AETaCZBlockEntity;
import me.myogoo.appliedtacz.init.AETaczMenu;
import me.myogoo.appliedtacz.client.renderer.AEGunSmithTableRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class AppliedTaczClient {
    public static void init(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(AETaczMenu.AE_GUN_SMITH_TABLE.get(), GunSmithTableScreen::new));
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        BlockEntityRenderers.register(AETaCZBlockEntity.AE_GUN_SMITH_TABLE.get(), AEGunSmithTableRenderer::new);
    }

    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        AETaCZItemDecorators.register(event);
    }
}
