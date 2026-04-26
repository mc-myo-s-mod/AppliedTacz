package me.myogoo.appliedtacz.util;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.resource.index.CommonBlockIndex;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AETaCZWorkbenchIndex {
    private AETaCZWorkbenchIndex() {
    }

    public static List<Entry> entries() {
        return TimelessAPI.getAllCommonBlockIndex().stream()
                .map(AETaCZWorkbenchIndex::createEntry)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(entry -> entry.blockId().toString()))
                .toList();
    }

    public static ResourceLocation getMenuBlockId(@Nullable AEGunSmithTableBlockEntity blockEntity) {
        if (blockEntity != null && blockEntity.getId() != null
                && !DefaultAssets.EMPTY_BLOCK_ID.equals(blockEntity.getId())) {
            return blockEntity.getId();
        }
        if (blockEntity != null) {
            return getDefaultBlockId(blockEntity.getBlockState().getBlock());
        }
        return getDefaultBlockId(null);
    }

    public static ResourceLocation getDefaultBlockId(@Nullable Block appliedWorkbench) {
        WorkbenchUpgradeKind kind = appliedWorkbench == null
                ? WorkbenchUpgradeKind.GUN_SMITH_TABLE
                : WorkbenchUpgradeKind.fromAppliedWorkbench(appliedWorkbench);
        if (kind == null) {
            return DefaultAssets.EMPTY_BLOCK_ID;
        }
        return entries().stream()
                .filter(entry -> entry.kind() == kind)
                .map(Entry::blockId)
                .findFirst()
                .orElse(DefaultAssets.EMPTY_BLOCK_ID);
    }

    private static Optional<Entry> createEntry(Map.Entry<ResourceLocation, CommonBlockIndex> commonEntry) {
        CommonBlockIndex index = commonEntry.getValue();
        BlockItem baseWorkbench = index.getBlock();
        WorkbenchUpgradeKind kind = WorkbenchUpgradeKind.fromBaseWorkbench(baseWorkbench.getBlock());
        if (kind == null) {
            return Optional.empty();
        }
        return Optional.of(new Entry(commonEntry.getKey(), baseWorkbench, kind));
    }

    public record Entry(ResourceLocation blockId, BlockItem baseWorkbench, WorkbenchUpgradeKind kind) {
        public ItemStack createAppliedStack() {
            return BlockItemBuilder.create(kind.result())
                    .setId(blockId)
                    .build();
        }
    }
}
