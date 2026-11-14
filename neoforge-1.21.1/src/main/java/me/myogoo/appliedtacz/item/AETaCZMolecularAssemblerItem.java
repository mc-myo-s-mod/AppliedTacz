package me.myogoo.appliedtacz.item;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import me.myogoo.appliedtacz.client.AssemblerNames;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

public class AETaCZMolecularAssemblerItem extends BlockItem implements BlockItemDataAccessor {
    private final @Nullable ResourceLocation defaultBlockId;

    public AETaCZMolecularAssemblerItem(Block block, @Nullable ResourceLocation defaultBlockId) {
        super(block, new Item.Properties().stacksTo(64));
        this.defaultBlockId = defaultBlockId;
    }

    @Override
    public ResourceLocation getBlockId(ItemStack stack) {
        ResourceLocation blockId = BlockItemDataAccessor.super.getBlockId(stack);
        return DefaultAssets.EMPTY_BLOCK_ID.equals(blockId) && defaultBlockId != null ? defaultBlockId : blockId;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public Component getName(ItemStack stack) {
        return AssemblerNames.title(defaultBlockId == null ? "" : getBlockId(stack).toString(),
                getBlock() instanceof MolecularAssemblerBlock block && block.isExtended());
    }
}
