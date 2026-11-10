package me.myogoo.appliedtacz.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.resource.index.CommonBlockIndex;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

public final class AssemblerWorkbenchBadge {
    // Reloaded gunpacks replace their index objects, so obsolete item previews can be collected.
    private static final Map<CommonBlockIndex, ItemStack> WORKBENCH_ITEMS = new WeakHashMap<>();

    private AssemblerWorkbenchBadge() {
    }

    public static boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!(stack.getItem() instanceof BlockItemDataAccessor accessor)) {
            return false;
        }
        var id = accessor.getBlockId(stack);
        var index = TimelessAPI.getCommonBlockIndex(id).orElse(null);
        if (index == null) {
            return false;
        }
        var workbench = WORKBENCH_ITEMS.computeIfAbsent(index,
                key -> BlockItemBuilder.create(key.getBlock()).setId(id).build());
        graphics.pose().pushPose();
        try {
            transformBadge(graphics.pose(), x, y);
            // Keep TaCZ's 3D GUI model and transforms without nesting item decorations.
            graphics.renderItem(workbench, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
        return true;
    }

    static void transformBadge(PoseStack poses, int x, int y) {
        poses.translate(x + 8, y + 8, ItemRenderer.ITEM_COUNT_BLIT_OFFSET + 10);
        poses.scale(0.5F, 0.5F, 1.0F);
    }
}
