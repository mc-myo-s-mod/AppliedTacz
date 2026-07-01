package me.myogoo.appliedtacz.util;

import com.tacz.guns.api.DefaultAssets;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public final class AETaCZWorkbenchIds {
    public static final ResourceLocation AMMO_WORKBENCH_ID = ResourceLocation.fromNamespaceAndPath("tacz",
            "ammo_workbench");
    public static final ResourceLocation ATTACHMENT_WORKBENCH_ID = ResourceLocation.fromNamespaceAndPath("tacz",
            "attachment_workbench");
    public static final ResourceLocation WORKBENCH_A_ID = ResourceLocation.fromNamespaceAndPath("tacz", "workbench_a");
    public static final ResourceLocation WORKBENCH_B_ID = ResourceLocation.fromNamespaceAndPath("tacz", "workbench_b");
    public static final ResourceLocation WORKBENCH_C_ID = ResourceLocation.fromNamespaceAndPath("tacz", "workbench_c");

    private AETaCZWorkbenchIds() {
    }

    public static ResourceLocation getMenuBlockId(@Nullable AEGunSmithTableBlockEntity blockEntity) {
        if (blockEntity != null && blockEntity.getId() != null) {
            return blockEntity.getId();
        }
        if (blockEntity != null) {
            return getDefaultBlockId(blockEntity.getBlockState().getBlock());
        }
        return DefaultAssets.DEFAULT_BLOCK_ID;
    }

    public static ResourceLocation getDefaultBlockId(Block block) {
        if (block == ModBlocks.AMMO_ASSEMBLY_TABLE.get()) {
            return AMMO_WORKBENCH_ID;
        }
        if (block == ModBlocks.ATTACHMENT_TABLE.get()) {
            return ATTACHMENT_WORKBENCH_ID;
        }
        return DefaultAssets.DEFAULT_BLOCK_ID;
    }

    public static Block getBlockForBaseWorkbench(ResourceLocation baseWorkbenchId) {
        if (WORKBENCH_A_ID.equals(baseWorkbenchId)) {
            return ModBlocks.AMMO_ASSEMBLY_TABLE.get();
        }
        if (WORKBENCH_C_ID.equals(baseWorkbenchId)) {
            return ModBlocks.ATTACHMENT_TABLE.get();
        }
        return ModBlocks.GUN_SMITH_TABLE.get();
    }
}
