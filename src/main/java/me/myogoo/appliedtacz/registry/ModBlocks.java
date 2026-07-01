package me.myogoo.appliedtacz.registry;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.AmmoAssemblyTable;
import me.myogoo.appliedtacz.block.AttachmentTable;
import me.myogoo.appliedtacz.block.GunSmithTable;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AppliedTaCZ.MODID);

    public static final DeferredBlock<Block> GUN_SMITH_TABLE = BLOCKS.register("gun_smith_table", GunSmithTable::new);
    public static final DeferredBlock<Block> ATTACHMENT_TABLE = BLOCKS.register("attachment_workbench", AttachmentTable::new);
    public static final DeferredBlock<Block> AMMO_ASSEMBLY_TABLE = BLOCKS.register("ammo_workbench", AmmoAssemblyTable::new);

    private ModBlocks() {
    }
}
