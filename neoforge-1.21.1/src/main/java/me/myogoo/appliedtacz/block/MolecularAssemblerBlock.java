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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class MolecularAssemblerBlock extends AbstractGunSmithTableBlock {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);

    private static final Set<String> CREATIVE_REMOVALS = new HashSet<>();
    private static boolean removingStructure;

    private final MapCodec<MolecularAssemblerBlock> codec;
    private final @Nullable WorkbenchUpgradeKind kind;
    private final int width;
    private final int depth;
    private final int height;
    private final boolean extended;
    private final VoxelShape upperShape;

    public MolecularAssemblerBlock(@Nullable WorkbenchUpgradeKind kind, int width, int depth, int height, int upperHeight) {
        this(kind, width, depth, height, upperHeight, false);
    }

    public MolecularAssemblerBlock(@Nullable WorkbenchUpgradeKind kind, int width, int depth, int height, int upperHeight,
            boolean extended) {
        super();
        this.kind = kind;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.extended = extended;
        this.upperShape = Block.box(0, 0, 0, 16, upperHeight, 16);
        this.codec = simpleCodec(properties -> new MolecularAssemblerBlock(kind, width, depth, height, upperHeight, extended));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, 0));
    }

    @Override
    protected MapCodec<MolecularAssemblerBlock> codec() {
        return codec;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AEMolecularAssemblerBlockEntity assembler) {
            AEMolecularAssemblerBlockEntity root = assembler.getRootAssembler();
            if (root != null && Platform.hasPermissions(new DimensionalBlockPos(root), player)) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignoredPlayer) -> new AEMolecularAssemblerMenu(containerId, inventory, root),
                        root.getBlockState().getBlock().getName()
                ), buffer -> buffer.writeBlockPos(root.getBlockPos()));
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (!canPlaceFootprint(context, facing)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, 0);
    }

    private boolean canPlaceFootprint(BlockPlaceContext context, Direction facing) {
        Level level = context.getLevel();
        BlockPos root = context.getClickedPos();
        for (int part = 0; part < partCount(); part++) {
            BlockPos pos = getPartPos(root, facing, part);
            if (pos.getY() < level.getMinBuildHeight()
                    || pos.getY() >= level.getMaxBuildHeight()
                    || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }

        Direction facing = state.getValue(FACING);
        ResourceLocation blockId = stack.getItem() instanceof BlockItemDataAccessor accessor
                ? accessor.getBlockId(stack)
                : stack.getItem() instanceof BlockItem blockItem
                        ? AETaCZWorkbenchIds.getDefaultBlockId(blockItem.getBlock())
                        : kind.defaultBlockId();
        for (int part = 0; part < partCount(); part++) {
            BlockPos partPos = getPartPos(pos, facing, part);
            if (part != 0) {
                level.setBlock(partPos, state.setValue(PART, part), Block.UPDATE_ALL);
            }
            syncBlockEntity(level, partPos, isUniversal() ? DefaultAssets.EMPTY_BLOCK_ID : blockId, placer);
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
        if (!state.is(newState.getBlock()) && !level.isClientSide && !removingStructure) {
            BlockPos root = getRootPos(pos, state);
            boolean dropMachine = !CREATIVE_REMOVALS.remove(removalKey(level, root));
            removeStructure(level, root, state, pos, dropMachine);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos root = getRootPos(pos, state);
            CREATIVE_REMOVALS.add(removalKey(level, root));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private void removeStructure(Level level, BlockPos root, BlockState brokenState, BlockPos brokenPos, boolean dropMachine) {
        AEMolecularAssemblerBlockEntity rootEntity = level.getBlockEntity(root) instanceof AEMolecularAssemblerBlockEntity assembler
                ? assembler
                : null;
        AEMolecularAssemblerBlockEntity brokenEntity = level.getBlockEntity(brokenPos) instanceof AEMolecularAssemblerBlockEntity assembler
                ? assembler
                : null;
        if (dropMachine) {
            popResource(level, brokenPos, createDropStack(rootEntity != null ? rootEntity.getId()
                    : brokenEntity != null ? brokenEntity.getId() : null));
        }
        if (rootEntity != null) {
            for (ItemStack stack : rootEntity.collectPendingDrops()) {
                popResource(level, brokenPos, stack);
            }
        }

        removingStructure = true;
        try {
            Direction facing = brokenState.getValue(FACING);
            for (int part = 0; part < partCount(); part++) {
                BlockPos partPos = getPartPos(root, facing, part);
                if (partPos.equals(brokenPos)) {
                    continue;
                }

                BlockState partState = level.getBlockState(partPos);
                if (partState.is(this) && getRootPos(partPos, partState).equals(root)) {
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        } finally {
            removingStructure = false;
        }
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
        BlockPos root = getRootPos(pos, state);
        BlockEntity rootEntity = level.getBlockEntity(root);
        if (rootEntity instanceof AEMolecularAssemblerBlockEntity assembler) {
            return createDropStack(assembler.getId());
        }
        BlockEntity partEntity = level.getBlockEntity(pos);
        return partEntity instanceof AEMolecularAssemblerBlockEntity assembler
                ? createDropStack(assembler.getId())
                : super.getCloneItemStack(state, target, level, pos, player);
    }

    private static String removalKey(Level level, BlockPos root) {
        return level.dimension().location() + "@" + root.asLong();
    }

    public boolean isInternalFace(BlockState state, BlockPos pos, Level level, Direction side) {
        BlockPos root = getRootPos(pos, state);
        BlockPos adjacentPos = pos.relative(side);
        if (!level.hasChunkAt(adjacentPos)) {
            return false;
        }
        BlockState adjacent = level.getBlockState(adjacentPos);
        return adjacent.is(this) && getRootPos(adjacentPos, adjacent).equals(root);
    }

    public boolean isStructureLoadedAndValid(BlockPos root, BlockState rootState, Level level) {
        Direction facing = rootState.getValue(FACING);
        for (int part = 0; part < partCount(); part++) {
            BlockPos partPos = getPartPos(root, facing, part);
            if (!level.hasChunkAt(partPos)) {
                return false;
            }

            BlockState partState = level.getBlockState(partPos);
            if (!partState.is(this)
                    || partState.getValue(FACING) != facing
                    || partState.getValue(PART) != part) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isRoot(BlockState blockState) {
        return isValidPart(blockState) && blockState.getValue(PART) == 0;
    }

    @Override
    public BlockPos getRootPos(BlockPos pos, BlockState blockState) {
        if (!isValidPart(blockState)) {
            return pos;
        }
        Direction facing = blockState.getValue(FACING);
        int part = blockState.getValue(PART);
        int layer = width * depth;
        int y = part / layer;
        int layerPart = part % layer;
        int z = layerPart / width;
        int x = layerPart % width;
        return pos.relative(facing.getClockWise(), -x)
                .below(y)
                .relative(facing.getOpposite(), -z);
    }

    public BlockPos getPartPos(BlockPos root, Direction facing, int part) {
        int layer = width * depth;
        int y = part / layer;
        int layerPart = part % layer;
        int z = layerPart / width;
        int x = layerPart % width;
        return root.relative(facing.getClockWise(), x)
                .above(y)
                .relative(facing.getOpposite(), z);
    }

    public int partCount() {
        return width * depth * height;
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

    public boolean isValidPart(BlockState state) {
        return state.getValue(PART) < partCount();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState blockState) {
        return new AEMolecularAssemblerBlockEntity(pos, blockState);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!isValidPart(state)) {
            return Shapes.empty();
        }
        int y = state.getValue(PART) / (width * depth);
        return y == 0 ? Shapes.block() : upperShape;
    }
}
