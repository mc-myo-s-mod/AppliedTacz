package me.myogoo.appliedtacz.client;

import appeng.api.util.AEColor;
import appeng.client.gui.style.StyleManager;
import appeng.client.render.StaticItemColor;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.client.model.ExtendedAssemblerModelLoader;
import me.myogoo.appliedtacz.client.renderer.AEGunSmithTableRenderer;
import me.myogoo.appliedtacz.client.renderer.AEMolecularAssemblerRenderer;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;
import me.myogoo.appliedtacz.client.TaCZPatternEncodingScreen;
import me.myogoo.appliedtacz.registry.ModBlockEntities;
import me.myogoo.appliedtacz.registry.ModItems;
import me.myogoo.appliedtacz.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
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

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        var color = new StaticItemColor(AEColor.TRANSPARENT);
        event.register((stack, tintIndex) -> FastColor.ARGB32.opaque(color.getColor(stack, tintIndex)),
                ModItems.TACZ_PATTERN_ENCODING_TERMINAL.get());
    }

    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, "extended_assembler"),
                new ExtendedAssemblerModelLoader());
    }
}
