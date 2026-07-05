package me.myogoo.appliedtacz.compat.jade;

import appeng.api.integrations.igtooltip.TooltipBuilder;
import appeng.api.integrations.igtooltip.TooltipContext;
import appeng.integration.modules.igtooltip.TooltipIds;
import appeng.integration.modules.igtooltip.blocks.GridNodeStateDataProvider;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum AppliedTaczJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final GridNodeStateDataProvider GRID_NODE_STATE = new GridNodeStateDataProvider();

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!config.get(getUid()) || !(accessor.getBlockEntity() instanceof AEGunSmithTableBlockEntity table)) {
            return;
        }

        BlockHitResult hitResult = accessor.getHitResult();
        GRID_NODE_STATE.buildTooltip(
                table,
                new TooltipContext(accessor.getServerData(), hitResult.getLocation(), accessor.getPlayer()),
                new JadeBackedTooltipBuilder(tooltip)
        );
    }

    @Override
    public ResourceLocation getUid() {
        return TooltipIds.GRID_NODE_STATE;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof AEGunSmithTableBlockEntity table) {
            GRID_NODE_STATE.provideServerData(accessor.getPlayer(), table, data);
        }
    }

    private record JadeBackedTooltipBuilder(ITooltip tooltip) implements TooltipBuilder {
        @Override
        public void addLine(Component component) {
            tooltip.add(component);
        }

        @Override
        public void addLine(Component component, ResourceLocation id) {
            tooltip.add(component, id);
        }
    }
}
