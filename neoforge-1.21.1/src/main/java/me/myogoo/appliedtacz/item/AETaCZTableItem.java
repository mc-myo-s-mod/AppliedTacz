package me.myogoo.appliedtacz.item;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.item.GunSmithTableItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class AETaCZTableItem extends GunSmithTableItem {
    private final ResourceLocation defaultBlockId;

    public AETaCZTableItem(Block block, ResourceLocation defaultBlockId) {
        super(block);
        this.defaultBlockId = defaultBlockId;
    }

    @Override
    public ResourceLocation getBlockId(ItemStack stack) {
        ResourceLocation blockId = super.getBlockId(stack);
        if (DefaultAssets.EMPTY_BLOCK_ID.equals(blockId)) {
            return this.defaultBlockId;
        }
        return blockId;
    }
}
