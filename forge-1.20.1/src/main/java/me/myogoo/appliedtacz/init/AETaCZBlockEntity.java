package me.myogoo.appliedtacz.init;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AETaCZBlockEntity {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, AppliedTaCZ.MODID);

    public static final RegistryObject<BlockEntityType<AEGunSmithTableBlockEntity>> AE_GUN_SMITH_TABLE = REGISTER
            .register("ae_gun_smith_table",
                    () -> BlockEntityType.Builder.of(AEGunSmithTableBlockEntity::new,
                            AETaCZBlock.GUN_SMITH_TABLE.get(),
                            AETaCZBlock.AMMO_ASSEMBLY_TABLE.get(),
                            AETaCZBlock.ATTACHMENT_TABLE.get()).build(null));
}
