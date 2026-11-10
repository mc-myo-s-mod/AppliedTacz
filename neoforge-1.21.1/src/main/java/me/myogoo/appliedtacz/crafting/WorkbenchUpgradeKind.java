package me.myogoo.appliedtacz.crafting;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.GunSmithTableBlockA;
import com.tacz.guns.block.GunSmithTableBlockC;
import me.myogoo.appliedtacz.registry.ModBlocks;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

public enum WorkbenchUpgradeKind {
    GUN_SMITH_TABLE(
            () -> ModBlocks.GUN_SMITH_TABLE.get(),
            () -> ModBlocks.TACZ_ASSEMBLER_GUN.get(),
            DefaultAssets.DEFAULT_BLOCK_ID,
            Set.of(DefaultAssets.DEFAULT_BLOCK_ID, AETaCZWorkbenchIds.WORKBENCH_B_ID)
    ),
    AMMO_WORKBENCH(
            () -> ModBlocks.AMMO_ASSEMBLY_TABLE.get(),
            () -> ModBlocks.TACZ_ASSEMBLER_AMMO.get(),
            AETaCZWorkbenchIds.AMMO_WORKBENCH_ID,
            Set.of(AETaCZWorkbenchIds.WORKBENCH_A_ID)
    ),
    ATTACHMENT_WORKBENCH(
            () -> ModBlocks.ATTACHMENT_TABLE.get(),
            () -> ModBlocks.TACZ_ASSEMBLER_ATTACHMENT.get(),
            AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID,
            Set.of(AETaCZWorkbenchIds.WORKBENCH_C_ID)
    );

    private final Supplier<? extends Block> result;
    private final Supplier<? extends Block> assemblerResult;
    private final ResourceLocation defaultBlockId;
    private final Set<ResourceLocation> baseWorkbenchIds;

    WorkbenchUpgradeKind(Supplier<? extends Block> result, Supplier<? extends Block> assemblerResult,
            ResourceLocation defaultBlockId,
            Set<ResourceLocation> baseWorkbenchIds) {
        this.result = result;
        this.assemblerResult = assemblerResult;
        this.defaultBlockId = defaultBlockId;
        this.baseWorkbenchIds = baseWorkbenchIds;
    }

    public ItemLike result() {
        return result.get();
    }

    public Block resultBlock() {
        return result.get();
    }

    public ItemLike assemblerResult() {
        return assemblerResult.get();
    }

    public Block assemblerBlock() {
        return assemblerResult.get();
    }

    public ResourceLocation defaultBlockId() {
        return defaultBlockId;
    }

    public static @Nullable WorkbenchUpgradeKind forAssembler(ResourceLocation workbenchId) {
        for (WorkbenchUpgradeKind kind : values()) {
            if (kind.defaultBlockId.equals(workbenchId)) {
                return kind;
            }
        }
        return null;
    }

    public static Block assemblerBlockFor(ResourceLocation workbenchId) {
        WorkbenchUpgradeKind kind = forAssembler(workbenchId);
        return kind == null ? ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get() : kind.assemblerBlock();
    }

    public boolean acceptsBaseWorkbench(ResourceLocation baseWorkbenchId) {
        return baseWorkbenchIds.contains(baseWorkbenchId);
    }

    public static @Nullable WorkbenchUpgradeKind fromBaseWorkbench(Block baseWorkbench) {
        if (baseWorkbench instanceof GunSmithTableBlockA) {
            return AMMO_WORKBENCH;
        }
        if (baseWorkbench instanceof GunSmithTableBlockC) {
            return ATTACHMENT_WORKBENCH;
        }
        if (baseWorkbench instanceof AbstractGunSmithTableBlock) {
            return GUN_SMITH_TABLE;
        }
        return null;
    }

    public static @Nullable WorkbenchUpgradeKind fromAppliedWorkbench(Block appliedWorkbench) {
        if (appliedWorkbench == ModBlocks.AMMO_ASSEMBLY_TABLE.get()) {
            return AMMO_WORKBENCH;
        }
        if (appliedWorkbench == ModBlocks.MOLECULAR_ASSEMBLER_AMMO.get()
                || appliedWorkbench == ModBlocks.TACZ_ASSEMBLER_AMMO.get()
                || appliedWorkbench == ModBlocks.EXTENDED_TACZ_ASSEMBLER_AMMO.get()) {
            return AMMO_WORKBENCH;
        }
        if (appliedWorkbench == ModBlocks.ATTACHMENT_TABLE.get()) {
            return ATTACHMENT_WORKBENCH;
        }
        if (appliedWorkbench == ModBlocks.MOLECULAR_ASSEMBLER_ATTACHMENT.get()
                || appliedWorkbench == ModBlocks.TACZ_ASSEMBLER_ATTACHMENT.get()
                || appliedWorkbench == ModBlocks.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get()) {
            return ATTACHMENT_WORKBENCH;
        }
        if (appliedWorkbench == ModBlocks.GUN_SMITH_TABLE.get()) {
            return GUN_SMITH_TABLE;
        }
        if (appliedWorkbench == ModBlocks.MOLECULAR_ASSEMBLER_GUN.get()
                || appliedWorkbench == ModBlocks.TACZ_ASSEMBLER_GUN.get()
                || appliedWorkbench == ModBlocks.EXTENDED_TACZ_ASSEMBLER_GUN.get()) {
            return GUN_SMITH_TABLE;
        }
        return null;
    }
}
