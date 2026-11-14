package me.myogoo.appliedtacz.client;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AssemblerNames {
    private AssemblerNames() {
    }

    public static Component title(String workbenchId, boolean extended) {
        var id = workbenchId.isEmpty() ? null : ResourceLocation.tryParse(workbenchId);
        if (id == null || DefaultAssets.EMPTY_BLOCK_ID.equals(id)) {
            return Component.translatable("block.appliedtacz.tacz_assembler_universal");
        }
        return Component.translatable(extended ? "gui.appliedtacz.assembler.extended_title"
                : "gui.appliedtacz.assembler.title", assemblerWorkbenchName(id));
    }

    public static Component workbenchName(String workbenchId) {
        var id = ResourceLocation.tryParse(workbenchId);
        return id == null ? Component.translatable("gui.appliedtacz.assembler.workbench_name")
                : workbenchName(id);
    }

    private static Component workbenchName(ResourceLocation id) {
        return TimelessAPI.getClientBlockIndex(id)
                .map(index -> Component.translatable(index.getName()))
                .orElseGet(() -> Component.translatable("gui.appliedtacz.assembler.workbench_name"));
    }

    private static Component assemblerWorkbenchName(ResourceLocation id) {
        String name = workbenchName(id).getString().trim();
        if (name.startsWith("ME ")) {
            name = name.substring(3).trim();
        }
        if (name.startsWith("TaCZ ")) {
            name = name.substring(5).trim();
        }
        if (name.regionMatches(true, Math.max(0, name.length() - 6), " Table", 0, 6)) {
            name = name.substring(0, name.length() - 6).trim();
        }
        if (name.endsWith(" 작업대")) {
            name = name.substring(0, name.length() - 4).trim();
        }
        return Component.literal(name);
    }
}
