package me.myogoo.appliedtacz.compat.jade;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.client.AssemblerNames;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.Identifiers;
import snownee.jade.api.config.IPluginConfig;

public enum AssemblerJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = new ResourceLocation(AppliedTaCZ.MODID, "assembler_status");
    private static final String WORKBENCH = "appliedtaczWorkbench";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof AEMolecularAssemblerBlockEntity)) {
            return;
        }
        CompoundTag data = accessor.getServerData();
        if (!data.contains(WORKBENCH)) {
            return;
        }
        tooltip.remove(Identifiers.CORE_OBJECT_NAME);
        tooltip.add(0, AssemblerNames.title(data.getString(WORKBENCH),
                accessor.getBlock() instanceof MolecularAssemblerBlock block && block.isExtended()),
                Identifiers.CORE_OBJECT_NAME);
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof AEMolecularAssemblerBlockEntity assembler) {
            data.putString(WORKBENCH, assembler.getMenuWorkbenchId());
        }
    }
}
