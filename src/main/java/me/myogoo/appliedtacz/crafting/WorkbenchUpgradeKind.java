package me.myogoo.appliedtacz.crafting;

import com.tacz.guns.api.DefaultAssets;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import me.myogoo.appliedtacz.init.AETaCZRecipeSerializer;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Supplier;

public enum WorkbenchUpgradeKind {
    GUN_SMITH_TABLE(
            () -> AETaCZBlock.GUN_SMITH_TABLE.get(),
            DefaultAssets.DEFAULT_BLOCK_ID,
            DefaultAssets.DEFAULT_BLOCK_ID,
            Set.of(DefaultAssets.DEFAULT_BLOCK_ID, AETaCZWorkbenchIds.WORKBENCH_B_ID)
    ),
    AMMO_WORKBENCH(
            () -> AETaCZBlock.AMMO_ASSEMBLY_TABLE.get(),
            AETaCZWorkbenchIds.AMMO_WORKBENCH_ID,
            AETaCZWorkbenchIds.WORKBENCH_A_ID,
            Set.of(AETaCZWorkbenchIds.WORKBENCH_A_ID)
    ),
    ATTACHMENT_WORKBENCH(
            () -> AETaCZBlock.ATTACHMENT_TABLE.get(),
            AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID,
            AETaCZWorkbenchIds.WORKBENCH_C_ID,
            Set.of(AETaCZWorkbenchIds.WORKBENCH_C_ID)
    );

    private final Supplier<? extends ItemLike> result;
    private final ResourceLocation defaultBlockId;
    private final ResourceLocation defaultBaseWorkbenchId;
    private final Set<ResourceLocation> baseWorkbenchIds;

    WorkbenchUpgradeKind(Supplier<? extends ItemLike> result, ResourceLocation defaultBlockId,
            ResourceLocation defaultBaseWorkbenchId,
            Set<ResourceLocation> baseWorkbenchIds) {
        this.result = result;
        this.defaultBlockId = defaultBlockId;
        this.defaultBaseWorkbenchId = defaultBaseWorkbenchId;
        this.baseWorkbenchIds = baseWorkbenchIds;
    }

    public ItemLike result() {
        return result.get();
    }

    public ResourceLocation defaultBlockId() {
        return defaultBlockId;
    }

    public ResourceLocation defaultBaseWorkbenchId() {
        return defaultBaseWorkbenchId;
    }

    public boolean acceptsBaseWorkbench(ResourceLocation baseWorkbenchId) {
        return baseWorkbenchIds.contains(baseWorkbenchId);
    }

    public RecipeSerializer<?> serializer() {
        return switch (this) {
            case GUN_SMITH_TABLE -> AETaCZRecipeSerializer.GUN_SMITH_TABLE_UPGRADE.get();
            case AMMO_WORKBENCH -> AETaCZRecipeSerializer.AMMO_WORKBENCH_UPGRADE.get();
            case ATTACHMENT_WORKBENCH -> AETaCZRecipeSerializer.ATTACHMENT_WORKBENCH_UPGRADE.get();
        };
    }

    public static @Nullable WorkbenchUpgradeKind fromBaseWorkbench(ResourceLocation baseWorkbenchId) {
        for (WorkbenchUpgradeKind kind : values()) {
            if (kind.acceptsBaseWorkbench(baseWorkbenchId)) {
                return kind;
            }
        }
        return null;
    }
}
