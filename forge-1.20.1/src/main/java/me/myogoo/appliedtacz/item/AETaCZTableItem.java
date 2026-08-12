package me.myogoo.appliedtacz.item;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.item.GunSmithTableItem;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class AETaCZTableItem extends GunSmithTableItem {
    public AETaCZTableItem(Block block) {
        super(block);
    }

    @Override
    public ResourceLocation getBlockId(ItemStack stack) {
        ResourceLocation blockId = super.getBlockId(stack);
        if (DefaultAssets.EMPTY_BLOCK_ID.equals(blockId)) {
            return AETaCZWorkbenchIndex.getDefaultBlockId(getBlock());
        }
        return blockId;
    }
}
