package me.myogoo.appliedtacz.item;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import me.myogoo.appliedtacz.client.AssemblerNames;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class AETaCZAssemblerItem extends BlockItem implements BlockItemDataAccessor {
    public AETaCZAssemblerItem(Block block) {
        super(block, new Item.Properties().stacksTo(64));
    }

    @Override
    public ResourceLocation getBlockId(ItemStack stack) {
        ResourceLocation blockId = BlockItemDataAccessor.super.getBlockId(stack);
        if (DefaultAssets.EMPTY_BLOCK_ID.equals(blockId)) {
            return AETaCZWorkbenchIndex.getDefaultBlockId(getBlock());
        }
        return blockId;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public Component getName(ItemStack stack) {
        return AssemblerNames.title(getBlockId(stack).toString(),
                getBlock() instanceof MolecularAssemblerBlock block && block.isExtended());
    }
}
