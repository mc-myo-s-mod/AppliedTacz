package me.myogoo.appliedtacz.client;

import me.myogoo.appliedtacz.registry.ModItems;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

public final class AETaCZItemDecorators {
    private static final ResourceLocation INTERFACE_BADGE = ResourceLocation.fromNamespaceAndPath(
            "ae2",
            "textures/part/interface.png"
    );
    private static final int BADGE_SIZE = 8;
    private static final IItemDecorator WORKBENCH_BADGE = new InterfaceBadgeDecorator();

    private AETaCZItemDecorators() {
    }

    public static void register(RegisterItemDecorationsEvent event) {
        event.register(ModItems.GUN_SMITH_TABLE.get(), WORKBENCH_BADGE);
        event.register(ModItems.AMMO_WORKBENCH.get(), WORKBENCH_BADGE);
        event.register(ModItems.ATTACHMENT_TABLE.get(), WORKBENCH_BADGE);
    }

    private static final class InterfaceBadgeDecorator implements IItemDecorator {
        @Override
        public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int xOffset, int yOffset) {
            int badgeX = xOffset + 8;
            int badgeY = yOffset + 8;

            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, ItemRenderer.ITEM_COUNT_BLIT_OFFSET + 10.0F);
            graphics.blit(INTERFACE_BADGE, badgeX, badgeY, BADGE_SIZE, BADGE_SIZE, 0, 0, 16, 16, 16, 16);
            graphics.pose().popPose();
            return false;
        }
    }
}
