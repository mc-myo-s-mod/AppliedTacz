package me.myogoo.appliedtacz.block;

import com.mojang.serialization.MapCodec;
import appeng.api.util.DimensionalBlockPos;
import appeng.util.Platform;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class MolecularAssemblerBlock extends AbstractGunSmithTableBlock {
    private static final Set<String> CREATIVE_REMOVALS = new HashSet<>();

    private final MapCodec<MolecularAssemblerBlock> codec;
    private final @Nullable WorkbenchUpgradeKind kind;
    private final boolean extended;

    public MolecularAssemblerBlock(@Nullable WorkbenchUpgradeKind kind) {
        this(kind, false);
    }

    public MolecularAssemblerBlock(@Nullable WorkbenchUpgradeKind kind, boolean extended) {
        this.kind = kind;
        this.extended = extended;
        this.codec = simpleCodec(properties -> new MolecularAssemblerBlock(kind, extended));
    }

    @Override
    protected MapCodec<MolecularAssemblerBlock> codec() {
        return codec;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AEMolecularAssemblerBlockEntity assembler
                && Platform.hasPermissions(new DimensionalBlockPos(assembler), player)) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignoredPlayer) -> new AEMolecularAssemblerMenu(containerId, inventory, assembler),
                    state.getBlock().getName()
            ), buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            ResourceLocation blockId = stack.getItem() instanceof BlockItemDataAccessor accessor
                    ? accessor.getBlockId(stack) : AETaCZWorkbenchIds.getDefaultBlockId(this);
            syncBlockEntity(level, pos, isUniversal() ? DefaultAssets.EMPTY_BLOCK_ID : blockId, placer);
        }
    }

    private static void syncBlockEntity(Level level, BlockPos pos, ResourceLocation blockId,
            @Nullable LivingEntity placer) {
        if (level.getBlockEntity(pos) instanceof AEMolecularAssemblerBlockEntity assembler) {
            assembler.setId(blockId);
            if (placer instanceof Player player) {
                assembler.setOwningPlayer(player);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            boolean dropMachine = !CREATIVE_REMOVALS.remove(removalKey(level, pos));
            AEMolecularAssemblerBlockEntity assembler =
                    level.getBlockEntity(pos) instanceof AEMolecularAssemblerBlockEntity entity ? entity : null;
            if (dropMachine) {
                popResource(level, pos, createDropStack(assembler != null ? assembler.getId() : null));
            }
            if (assembler != null) {
                for (ItemStack stack : assembler.collectPendingDrops()) {
                    popResource(level, pos, stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            CREATIVE_REMOVALS.add(removalKey(level, pos));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private ItemStack createDropStack(@Nullable ResourceLocation blockId) {
        if (!isUniversal() && blockId != null && !DefaultAssets.EMPTY_BLOCK_ID.equals(blockId)) {
            return BlockItemBuilder.create(this).setId(blockId).build();
        }
        return new ItemStack(this);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos,
            Player player) {
        return level.getBlockEntity(pos) instanceof AEMolecularAssemblerBlockEntity assembler
                ? createDropStack(assembler.getId()) : new ItemStack(this);
    }

    private static String removalKey(Level level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    @Override
    public boolean isRoot(BlockState blockState) {
        return true;
    }

    @Override
    public BlockPos getRootPos(BlockPos pos, BlockState blockState) {
        return pos;
    }

    public @Nullable WorkbenchUpgradeKind kind() {
        return kind;
    }

    public boolean isUniversal() {
        return kind == null;
    }

    public boolean isExtended() {
        return extended;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState blockState) {
        return new AEMolecularAssemblerBlockEntity(pos, blockState);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
