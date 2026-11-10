package me.myogoo.appliedtacz.registry;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.AmmoAssemblyTable;
import me.myogoo.appliedtacz.block.AttachmentTable;
import me.myogoo.appliedtacz.block.GunSmithTable;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AppliedTaCZ.MODID);

    public static final DeferredBlock<Block> GUN_SMITH_TABLE = BLOCKS.register("gun_smith_table", GunSmithTable::new);
    public static final DeferredBlock<Block> ATTACHMENT_TABLE = BLOCKS.register("attachment_workbench", AttachmentTable::new);
    public static final DeferredBlock<Block> AMMO_ASSEMBLY_TABLE = BLOCKS.register("ammo_workbench", AmmoAssemblyTable::new);
    public static final DeferredBlock<Block> MOLECULAR_ASSEMBLER_AMMO = BLOCKS.register("molecular_assembler_ammo",
            () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.AMMO_WORKBENCH, 2, 1, 2, 8));
    public static final DeferredBlock<Block> MOLECULAR_ASSEMBLER_ATTACHMENT = BLOCKS.register("molecular_assembler_attachment",
            () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH, 2, 1, 2, 8));
    public static final DeferredBlock<Block> MOLECULAR_ASSEMBLER_GUN = BLOCKS.register("molecular_assembler_gun",
            () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.GUN_SMITH_TABLE, 2, 2, 2, 12));
    public static final DeferredBlock<Block> MOLECULAR_ASSEMBLER_UNIVERSAL = BLOCKS.register("molecular_assembler_universal",
            () -> new MolecularAssemblerBlock(null, 2, 2, 2, 12));
    public static final DeferredBlock<Block> MOLECULAR_ASSEMBLER = BLOCKS.register("molecular_assembler",
            () -> new MolecularAssemblerBlock(null, 1, 1, 1, 16));
    public static final DeferredBlock<Block> TACZ_ASSEMBLER_AMMO = BLOCKS.register("tacz_assembler_ammo",
            () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.AMMO_WORKBENCH, 1, 1, 1, 16));
    public static final DeferredBlock<Block> TACZ_ASSEMBLER_ATTACHMENT = BLOCKS.register("tacz_assembler_attachment",
            () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH, 1, 1, 1, 16));
    public static final DeferredBlock<Block> TACZ_ASSEMBLER_GUN = BLOCKS.register("tacz_assembler_gun",
            () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.GUN_SMITH_TABLE, 1, 1, 1, 16));
    public static final DeferredBlock<Block> TACZ_ASSEMBLER_UNIVERSAL = BLOCKS.register("tacz_assembler_universal",
            () -> new MolecularAssemblerBlock(null, 1, 1, 1, 16));
    public static final DeferredBlock<Block> EXTENDED_TACZ_ASSEMBLER_AMMO = BLOCKS.register("extended_tacz_assembler_ammo",
            () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.AMMO_WORKBENCH, 1, 1, 1, 16, true));
    public static final DeferredBlock<Block> EXTENDED_TACZ_ASSEMBLER_ATTACHMENT = BLOCKS.register("extended_tacz_assembler_attachment",
            () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH, 1, 1, 1, 16, true));
    public static final DeferredBlock<Block> EXTENDED_TACZ_ASSEMBLER_GUN = BLOCKS.register("extended_tacz_assembler_gun",
            () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.GUN_SMITH_TABLE, 1, 1, 1, 16, true));

    private ModBlocks() {
    }
}
