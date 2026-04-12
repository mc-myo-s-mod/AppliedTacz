package me.myogoo.appliedtacz.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;

import me.myogoo.appliedtacz.init.AETaCZBlock;

public final class AETaCZItemDecorators {
    private static final ResourceLocation STORAGE_BUS_BADGE = ResourceLocation.fromNamespaceAndPath("ae2",
            "textures/part/storage_bus.png");
    private static final int BADGE_SIZE = 7;
    private static final IItemDecorator WORKBENCH_BADGE = new StorageBusBadgeDecorator();

    private AETaCZItemDecorators() {
    }

    public static void register(RegisterItemDecorationsEvent event) {
        event.register(AETaCZBlock.GUN_SMITH_TABLE.get(), WORKBENCH_BADGE);
        event.register(AETaCZBlock.AMMO_ASSEMBLY_TABLE.get(), WORKBENCH_BADGE);
        event.register(AETaCZBlock.ATTACHMENT_TABLE.get(), WORKBENCH_BADGE);
    }

    private static final class StorageBusBadgeDecorator implements IItemDecorator {
        @Override
        public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int xOffset, int yOffset) {
            int badgeX = xOffset + 9;
            int badgeY = yOffset + 9;

            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, ItemRenderer.ITEM_COUNT_BLIT_OFFSET + 10.0F);
            graphics.blit(STORAGE_BUS_BADGE, badgeX, badgeY, BADGE_SIZE, BADGE_SIZE, 0, 0, 16, 16, 16, 16);
            graphics.pose().popPose();
            return false;
        }
    }
}
