package me.myogoo.appliedtacz.block;

import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.block.GunSmithTableBlockB;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class GunSmithTable extends GunSmithTableBlockB {
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos rootPos = getRootPos(pos, state);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity entity = level.getBlockEntity(rootPos);
            if (entity instanceof AEGunSmithTableBlockEntity table) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignoredPlayer) -> new AEGunSmithTableMenu(containerId, inventory, table),
                        Component.translatable("block.appliedtacz.gun_smith_table")
                ), buffer -> {
                    buffer.writeBlockPos(rootPos);
                    buffer.writeResourceLocation(AETaCZWorkbenchIds.getMenuBlockId(table));
                });
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState blockState) {
        return new AEGunSmithTableBlockEntity(pos, blockState);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);

        BlockPos rootPos = getRootPos(pos, state);
        ResourceLocation blockId = stack.getItem() instanceof BlockItemDataAccessor accessor
                ? accessor.getBlockId(stack)
                : stack.getItem() instanceof BlockItem blockItem
                        ? AETaCZWorkbenchIds.getDefaultBlockId(blockItem.getBlock())
                        : null;

        syncBlockEntity(world, rootPos, blockId, placer);
        syncBlockEntity(world, rootPos.relative(state.getValue(FACING)), blockId, placer);
    }

    private static void syncBlockEntity(Level world, BlockPos pos, @Nullable ResourceLocation blockId,
            @Nullable LivingEntity placer) {
        if (world.getBlockEntity(pos) instanceof AEGunSmithTableBlockEntity table && blockId != null) {
            table.setId(blockId);
            if (placer instanceof Player player) {
                table.setOwningPlayer(player);
            }
        }
    }
}
