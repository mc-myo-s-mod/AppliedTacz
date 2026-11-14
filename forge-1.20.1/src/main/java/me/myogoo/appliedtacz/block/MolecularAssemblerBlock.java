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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MolecularAssemblerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final Set<String> CREATIVE_REMOVALS = new HashSet<>();

    private final @Nullable WorkbenchUpgradeKind kind;
    private final boolean extended;

    public MolecularAssemblerBlock(@Nullable WorkbenchUpgradeKind kind) {
        this(kind, false);
    }

    public MolecularAssemblerBlock(@Nullable WorkbenchUpgradeKind kind, boolean extended) {
        super(Properties.of().sound(SoundType.METAL).strength(3.0F, 6.0F).noOcclusion());
        this.kind = kind;
        this.extended = extended;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }

        ResourceLocation blockId = isUniversal()
                ? null
                : stack.getItem() instanceof BlockItemDataAccessor accessor
                        ? accessor.getBlockId(stack)
                        : AETaCZWorkbenchIndex.getDefaultBlockId(this);
        if (level.getBlockEntity(pos) instanceof AEMolecularAssemblerBlockEntity assembler) {
            assembler.setId(blockId);
            if (placer instanceof Player player) {
                assembler.setOwner(player);
            }
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            CREATIVE_REMOVALS.add(removalKey(level, pos));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !newState.is(this)) {
            boolean dropMachine = !CREATIVE_REMOVALS.remove(removalKey(level, pos));
            if (level.getBlockEntity(pos) instanceof AEMolecularAssemblerBlockEntity assembler) {
                if (dropMachine) {
                    Block.popResource(level, pos, assembler.createDropStack());
                }
                for (ItemStack stack : assembler.collectPendingDrops()) {
                    Block.popResource(level, pos, stack);
                }
            } else if (dropMachine) {
                Block.popResource(level, pos, createDropStack(null));
            }
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
        BlockEntity blockEntity = level.getBlockEntity(pos);
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
            if (clicked.mayOpenMenu(player)) {
                NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                        (id, inventory, menuPlayer) -> new AEMolecularAssemblerMenu(id, inventory, clicked),
                        clicked.getDisplayName()), buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.SUCCESS;
    }

    private ItemStack createDropStack(@Nullable ResourceLocation blockId) {
        if (isUniversal()) {
            return new ItemStack(this);
        }
        return BlockItemBuilder.create(this)
                .setId(blockId != null ? blockId : AETaCZWorkbenchIndex.getDefaultBlockId(this))
                .build();
    }

    private static String removalKey(Level level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }
}
