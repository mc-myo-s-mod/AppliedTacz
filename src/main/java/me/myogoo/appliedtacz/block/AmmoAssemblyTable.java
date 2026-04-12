package me.myogoo.appliedtacz.block;

import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.block.GunSmithTableBlockA;
import me.myogoo.appliedtacz.block.blcokentity.AEGunSmithTableBlockEntity;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public class AmmoAssemblyTable extends GunSmithTableBlockA {
    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand,
            BlockHitResult pHit) {
        BlockPos rootPos = getRootPos(pPos, pState);
        if (!pLevel.isClientSide && pPlayer instanceof ServerPlayer serverPlayer) {
            BlockEntity entity = pLevel.getBlockEntity(rootPos);
            if (entity instanceof AEGunSmithTableBlockEntity table) {
                NetworkHooks.openScreen(serverPlayer, new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return table.getBlockState().getBlock().getName();
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int pContainerId, Inventory pPlayerInventory,
                            Player pPlayer) {
                        return new AEGunSmithTableMenu(pContainerId, pPlayerInventory, table);
                    }
                }, buf -> {
                    buf.writeBlockPos(rootPos);
                    buf.writeResourceLocation(AETaCZWorkbenchIds.getMenuBlockId(table));
                });
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState blockState) {
        return new AEGunSmithTableBlockEntity(pos, blockState);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);

        ResourceLocation blockId = stack.getItem() instanceof BlockItemDataAccessor accessor
                ? accessor.getBlockId(stack)
                : stack.getItem() instanceof BlockItem blockItem
                        ? AETaCZWorkbenchIds.getDefaultBlockId(blockItem.getBlock())
                        : null;

        syncBlockEntity(world, pos, blockId, placer);
    }

    private static void syncBlockEntity(Level world, BlockPos pos, @Nullable ResourceLocation blockId,
            @Nullable LivingEntity placer) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof AEGunSmithTableBlockEntity table)) {
            return;
        }

        if (blockId != null) {
            table.setId(blockId);
        }
        if (placer instanceof Player player) {
            table.setOwner(player);
        }
    }
}
