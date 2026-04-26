package me.myogoo.appliedtacz.crafting;

import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.GunSmithTableBlockA;
import com.tacz.guns.block.GunSmithTableBlockC;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public enum WorkbenchUpgradeKind {
    GUN_SMITH_TABLE(() -> AETaCZBlock.GUN_SMITH_TABLE.get()),
    AMMO_WORKBENCH(() -> AETaCZBlock.AMMO_ASSEMBLY_TABLE.get()),
    ATTACHMENT_WORKBENCH(() -> AETaCZBlock.ATTACHMENT_TABLE.get());

    private final Supplier<? extends Block> result;

    WorkbenchUpgradeKind(Supplier<? extends Block> result) {
        this.result = result;
    }

    public ItemLike result() {
        return result.get();
    }

    public Block resultBlock() {
        return result.get();
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
        if (appliedWorkbench == AETaCZBlock.ATTACHMENT_TABLE.get()) {
            return ATTACHMENT_WORKBENCH;
        }
        if (appliedWorkbench == AETaCZBlock.GUN_SMITH_TABLE.get()) {
            return GUN_SMITH_TABLE;
        }
        return null;
    }
}
