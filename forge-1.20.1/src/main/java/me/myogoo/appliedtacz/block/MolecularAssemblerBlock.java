package me.myogoo.appliedtacz.block;

import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MolecularAssemblerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
    private static final Set<String> CREATIVE_REMOVALS = new HashSet<>();
    private static boolean removingParts;

    private final @Nullable WorkbenchUpgradeKind kind;
    private final int width;
    private final int depth;
    private final int height;
    private final int topShapeHeight;
    private final boolean extended;

    public MolecularAssemblerBlock(@Nullable WorkbenchUpgradeKind kind, int width, int depth, int height,
            int topShapeHeight) {
        this(kind, width, depth, height, topShapeHeight, false);
    }

    public MolecularAssemblerBlock(@Nullable WorkbenchUpgradeKind kind, int width, int depth, int height,
            int topShapeHeight, boolean extended) {
        super(Properties.of().sound(SoundType.METAL).strength(3.0F, 6.0F).noOcclusion());
        this.kind = kind;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.topShapeHeight = topShapeHeight;
        this.extended = extended;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }

    public @Nullable WorkbenchUpgradeKind getKind() {
        return kind;
    }

    public boolean isUniversal() {
        return kind == null;
    }

    public boolean isExtended() {
        return extended;
    }

    public IntegerProperty partProperty() {
        return PART;
    }

    public int getPart(BlockState state) {
        return state.getValue(PART);
    }

    public boolean isRoot(BlockState state) {
        return getPart(state) == 0;
    }

    public BlockPos getRootPos(BlockPos pos, BlockState state) {
        if (!isValidPart(state)) {
            return pos;
        }
        Direction facing = state.getValue(FACING);
        int part = getPart(state);
        int y = part / (width * depth);
        int layerPart = part % (width * depth);
        int z = layerPart / width;
        int x = layerPart % width;
        return pos.relative(facing.getCounterClockWise(), x).relative(facing, z).below(y);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos root = context.getClickedPos();
        Level level = context.getLevel();
        for (int part = 0; part < partCount(); part++) {
            BlockPos pos = partPos(root, facing, part);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || !level.getBlockState(pos).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(PART, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }

        Direction facing = state.getValue(FACING);
        for (int part = 1; part < partCount(); part++) {
            level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), Block.UPDATE_ALL);
        }

        ResourceLocation blockId = isUniversal()
                ? null
                : stack.getItem() instanceof BlockItemDataAccessor accessor
                        ? accessor.getBlockId(stack)
                        : AETaCZWorkbenchIndex.getDefaultBlockId(this);
        forEachPart(pos, facing, partPos -> {
            BlockEntity blockEntity = level.getBlockEntity(partPos);
            if (blockEntity instanceof AEMolecularAssemblerBlockEntity assembler) {
                assembler.setId(blockId);
                if (placer instanceof Player player) {
                    assembler.setOwner(player);
                }
            }
        });
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos root = getRootPos(pos, state);
            CREATIVE_REMOVALS.add(removalKey(level, root));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !newState.is(this) && !removingParts) {
            BlockPos root = getRootPos(pos, state);
            boolean dropMachine = !CREATIVE_REMOVALS.remove(removalKey(level, root));
            removeStructure(level, root, state, pos, dropMachine);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos,
            Player player) {
        BlockEntity blockEntity = level.getBlockEntity(getRootPos(pos, state));
        if (blockEntity instanceof AEMolecularAssemblerBlockEntity assembler) {
            return assembler.createDropStack();
        }
        if (isUniversal()) {
            return new ItemStack(this);
        }
        return BlockItemBuilder.create(this).setId(AETaCZWorkbenchIndex.getDefaultBlockId(this)).build();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int part = getPart(state);
        if (!isValidPart(state)) {
            return Shapes.empty();
        }
        int y = part / (width * depth);
        return y == height - 1 ? Block.box(0, 0, 0, 16, topShapeHeight, 16) : Block.box(0, 0, 0, 16, 16, 16);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AEMolecularAssemblerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof AEMolecularAssemblerBlockEntity clicked) {
            AEMolecularAssemblerBlockEntity root = clicked.getMenuRootAssembler();
            if (root != null && root.mayOpenMenu(player)) {
                NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                        (id, inventory, menuPlayer) -> new AEMolecularAssemblerMenu(id, inventory, root),
                        root.getDisplayName()), buf -> buf.writeBlockPos(root.getBlockPos()));
            }
        }
        return InteractionResult.SUCCESS;
    }

    public BlockPos partPos(BlockPos root, Direction facing, int part) {
        int y = part / (width * depth);
        int layerPart = part % (width * depth);
        int z = layerPart / width;
        int x = layerPart % width;
        return root.relative(facing.getClockWise(), x).relative(facing.getOpposite(), z).above(y);
    }

    public int partCount() {
        return width * depth * height;
    }

    public boolean isValidPart(BlockState state) {
        return getPart(state) < partCount();
    }

    private void removeOtherParts(Level level, BlockPos root, Direction facing, BlockPos keptPos) {
        removingParts = true;
        try {
            forEachPart(root, facing, partPos -> {
                BlockState partState = level.getBlockState(partPos);
                if (!partPos.equals(keptPos) && partState.is(this) && getRootPos(partPos, partState).equals(root)) {
                    level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            });
        } finally {
            removingParts = false;
        }
    }

    private void removeStructure(Level level, BlockPos root, BlockState brokenState, BlockPos brokenPos,
            boolean dropMachine) {
        AEMolecularAssemblerBlockEntity rootEntity = level.getBlockEntity(root) instanceof AEMolecularAssemblerBlockEntity assembler
                ? assembler
                : null;
        AEMolecularAssemblerBlockEntity brokenEntity = level.getBlockEntity(brokenPos) instanceof AEMolecularAssemblerBlockEntity assembler
                ? assembler
                : null;
        if (dropMachine) {
            ResourceLocation blockId = rootEntity != null ? rootEntity.getId()
                    : brokenEntity != null ? brokenEntity.getId() : null;
            Block.popResource(level, brokenPos, createDropStack(blockId));
        }
        if (rootEntity != null) {
            for (ItemStack stack : rootEntity.collectPendingDrops()) {
                Block.popResource(level, brokenPos, stack);
            }
        }
        removeOtherParts(level, root, brokenState.getValue(FACING), brokenPos);
    }

    private ItemStack createDropStack(@Nullable ResourceLocation blockId) {
        if (isUniversal()) {
            return new ItemStack(this);
        }
        return BlockItemBuilder.create(this)
                .setId(blockId != null ? blockId : AETaCZWorkbenchIndex.getDefaultBlockId(this))
                .build();
    }

    private static String removalKey(Level level, BlockPos root) {
        return level.dimension().location() + "@" + root.asLong();
    }

    private void forEachPart(BlockPos root, Direction facing, java.util.function.Consumer<BlockPos> consumer) {
        for (int part = 0; part < partCount(); part++) {
            consumer.accept(partPos(root, facing, part));
        }
    }

    public static final class Ammo extends MolecularAssemblerBlock {
        public Ammo() {
            super(WorkbenchUpgradeKind.AMMO_WORKBENCH, 2, 1, 2, 8);
        }
    }

    public static final class Attachment extends MolecularAssemblerBlock {
        public Attachment() {
            super(WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH, 2, 1, 2, 8);
        }
    }

    public static final class Gun extends MolecularAssemblerBlock {
        public Gun() {
            super(WorkbenchUpgradeKind.GUN_SMITH_TABLE, 2, 2, 2, 12);
        }
    }

    public static final class Universal extends MolecularAssemblerBlock {
        public Universal() {
            super(null, 2, 2, 2, 12);
        }
    }

    public static final class Compact extends MolecularAssemblerBlock {
        public Compact() {
            super(null, 1, 1, 1, 16);
        }
    }
}
