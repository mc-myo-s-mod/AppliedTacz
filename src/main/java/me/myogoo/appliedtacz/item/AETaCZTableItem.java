package me.myogoo.appliedtacz.item;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.item.GunSmithTableItem;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components,
            TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, components, tooltipFlag);
        components.add(Component.translatable("tooltip.appliedtacz.ae_support").withStyle(ChatFormatting.AQUA));
    }
}
