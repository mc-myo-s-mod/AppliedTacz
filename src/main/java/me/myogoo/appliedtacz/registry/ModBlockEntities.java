package me.myogoo.appliedtacz.registry;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, AppliedTaCZ.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AEGunSmithTableBlockEntity>> AE_GUN_SMITH_TABLE =
            BLOCK_ENTITIES.register("ae_gun_smith_table", () -> BlockEntityType.Builder.of(
                    AEGunSmithTableBlockEntity::new,
                    ModBlocks.GUN_SMITH_TABLE.get(),
                    ModBlocks.AMMO_ASSEMBLY_TABLE.get(),
                    ModBlocks.ATTACHMENT_TABLE.get()
            ).build(null));

    private ModBlockEntities() {
    }
}
