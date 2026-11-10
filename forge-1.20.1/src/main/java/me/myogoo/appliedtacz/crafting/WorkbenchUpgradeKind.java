package me.myogoo.appliedtacz.crafting;

import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.GunSmithTableBlockA;
import com.tacz.guns.block.GunSmithTableBlockC;
import com.tacz.guns.api.DefaultAssets;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public enum WorkbenchUpgradeKind {
    GUN_SMITH_TABLE(
            () -> AETaCZBlock.GUN_SMITH_TABLE.get(),
            () -> AETaCZBlock.TACZ_ASSEMBLER_GUN.get(),
            DefaultAssets.DEFAULT_BLOCK_ID
    ),
    AMMO_WORKBENCH(
            () -> AETaCZBlock.AMMO_ASSEMBLY_TABLE.get(),
            () -> AETaCZBlock.TACZ_ASSEMBLER_AMMO.get(),
            ResourceLocation.fromNamespaceAndPath("tacz", "ammo_workbench")
    ),
    ATTACHMENT_WORKBENCH(
            () -> AETaCZBlock.ATTACHMENT_TABLE.get(),
            () -> AETaCZBlock.TACZ_ASSEMBLER_ATTACHMENT.get(),
            ResourceLocation.fromNamespaceAndPath("tacz", "attachment_workbench")
    );

    private final Supplier<? extends Block> result;
    private final Supplier<? extends Block> assemblerResult;
    private final ResourceLocation defaultBlockId;

    WorkbenchUpgradeKind(Supplier<? extends Block> result, Supplier<? extends Block> assemblerResult,
            ResourceLocation defaultBlockId) {
        this.result = result;
        this.assemblerResult = assemblerResult;
        this.defaultBlockId = defaultBlockId;
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
        return kind == null ? AETaCZBlock.TACZ_ASSEMBLER_UNIVERSAL.get() : kind.assemblerBlock();
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
        if (appliedWorkbench == AETaCZBlock.AMMO_ASSEMBLY_TABLE.get()) {
            return AMMO_WORKBENCH;
        }
        if (appliedWorkbench == AETaCZBlock.MOLECULAR_ASSEMBLER_AMMO.get()
                || appliedWorkbench == AETaCZBlock.TACZ_ASSEMBLER_AMMO.get()
                || appliedWorkbench == AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_AMMO.get()) {
            return AMMO_WORKBENCH;
        }
        if (appliedWorkbench == AETaCZBlock.ATTACHMENT_TABLE.get()) {
            return ATTACHMENT_WORKBENCH;
        }
        if (appliedWorkbench == AETaCZBlock.MOLECULAR_ASSEMBLER_ATTACHMENT.get()
                || appliedWorkbench == AETaCZBlock.TACZ_ASSEMBLER_ATTACHMENT.get()
                || appliedWorkbench == AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get()) {
            return ATTACHMENT_WORKBENCH;
        }
        if (appliedWorkbench == AETaCZBlock.GUN_SMITH_TABLE.get()) {
            return GUN_SMITH_TABLE;
        }
        if (appliedWorkbench == AETaCZBlock.MOLECULAR_ASSEMBLER_GUN.get()
                || appliedWorkbench == AETaCZBlock.TACZ_ASSEMBLER_GUN.get()
                || appliedWorkbench == AETaCZBlock.EXTENDED_TACZ_ASSEMBLER_GUN.get()) {
            return GUN_SMITH_TABLE;
        }
        return null;
    }
}
