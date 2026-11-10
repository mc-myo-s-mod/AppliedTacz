package me.myogoo.appliedtacz.client;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class AssemblerNames {
    private AssemblerNames() {
    }

    public static Component title(String workbenchId) {
        return title(workbenchId, false);
    }

    public static Component title(String workbenchId, boolean extended) {
        var id = workbenchId.isEmpty() ? null : ResourceLocation.tryParse(workbenchId);
        if (id == null || DefaultAssets.EMPTY_BLOCK_ID.equals(id)) {
            return Component.translatable("block.appliedtacz.tacz_assembler_universal");
        }
        var name = TimelessAPI.getClientBlockIndex(id)
                .map(index -> Component.translatable(index.getName()))
                .orElseGet(() -> Component.translatable("gui.appliedtacz.assembler.workbench_name"));
        return Component.translatable(extended ? "gui.appliedtacz.assembler.extended_title"
                : "gui.appliedtacz.assembler.title", name);
    }
}
