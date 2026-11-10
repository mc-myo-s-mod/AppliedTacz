package me.myogoo.appliedtacz.init;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
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
    public static final RegistryObject<BlockEntityType<AEMolecularAssemblerBlockEntity>> AE_MOLECULAR_ASSEMBLER =
            REGISTER.register("ae_molecular_assembler",
                    () -> BlockEntityType.Builder.of(AEMolecularAssemblerBlockEntity::new,
                            AETaCZBlock.MOLECULAR_ASSEMBLER_AMMO.get(),
                            AETaCZBlock.MOLECULAR_ASSEMBLER_ATTACHMENT.get(),
                            AETaCZBlock.MOLECULAR_ASSEMBLER_GUN.get(),
                            AETaCZBlock.MOLECULAR_ASSEMBLER_UNIVERSAL.get(),
                            AETaCZBlock.TACZ_ASSEMBLER_AMMO.get(),
                            AETaCZBlock.TACZ_ASSEMBLER_ATTACHMENT.get(),
                            AETaCZBlock.TACZ_ASSEMBLER_GUN.get(),
                            AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get(),
                            AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_AMMO.get(),
                            AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get(),
                            AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_GUN.get(),
                            AETaCZBlock.MOLECULAR_ASSEMBLER.get()).build(null));
}
