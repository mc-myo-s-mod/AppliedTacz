package me.myogoo.appliedtacz.menu;

import appeng.api.util.DimensionalBlockPos;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.Platform;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.registry.ModMenus;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import org.jetbrains.annotations.Nullable;

public class AEMolecularAssemblerMenu extends AEBaseMenu {
    public static final MenuType<AEMolecularAssemblerMenu> TYPE = IMenuTypeExtension.create((windowId, inv, data) -> {
        BlockPos rootPos = data.readBlockPos();
        BlockEntity blockEntity = inv.player.level().getBlockEntity(rootPos);
        if (blockEntity instanceof AEMolecularAssemblerBlockEntity assembler) {
            return new AEMolecularAssemblerMenu(windowId, inv, assembler);
        }
        return new AEMolecularAssemblerMenu(windowId, inv, null);
    });

    private final @Nullable AEMolecularAssemblerBlockEntity blockEntity;
    private final Container preview = new SimpleContainer(1);

    @GuiSync(0)
    public boolean networkPowered;
    @GuiSync(1)
    public boolean networkOnline;
    @GuiSync(2)
    public boolean gridBooted;
    @GuiSync(3)
    public String workbenchId = "";
    @GuiSync(4)
    public boolean crafting;

    public AEMolecularAssemblerMenu(int id, Inventory inventory,
            @Nullable AEMolecularAssemblerBlockEntity blockEntity) {
        super(ModMenus.AE_MOLECULAR_ASSEMBLER.get(), id, inventory, blockEntity);
        this.blockEntity = blockEntity;
        addSlot(new Slot(preview, 0, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public ItemStack remove(int amount) {
                return ItemStack.EMPTY;
            }
        }, SlotSemantics.MACHINE_OUTPUT);
        InternalInventory patterns = blockEntity == null
                ? new AppEngInternalInventory(AEMolecularAssemblerBlockEntity.PATTERN_SLOTS)
                : blockEntity.getPatternInventory();
        for (int i = 0; i < AEMolecularAssemblerBlockEntity.PATTERN_SLOTS; i++) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.ENCODED_PATTERN, patterns, i) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(AEItems.PROCESSING_PATTERN.asItem()) && super.mayPlace(stack);
                }
            }.setStackLimit(1), SlotSemantics.ENCODED_PATTERN);
        }
        setupUpgrades(blockEntity == null
                ? UpgradeInventories.forMachine(AEBlocks.MOLECULAR_ASSEMBLER,
                        AEMolecularAssemblerBlockEntity.UPGRADE_SLOTS, () -> {})
                : blockEntity.getUpgrades());
        createPlayerInventorySlots(inventory);
        updateState();
        updatePreview();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            updateState();
            if (!isStillValidRoot(getPlayer())) {
                setValidMenu(false);
            } else {
                updatePreview();
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return isValidMenu() && isStillValidRoot(player);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId != 0) {
            super.clicked(slotId, button, clickType, player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return index == 0 ? ItemStack.EMPTY : super.quickMoveStack(player, index);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.index != 0 && super.canDragTo(slot);
    }

    public ItemStack getPendingOutput() {
        return preview.getItem(0).copy();
    }

    public boolean isExtended() {
        return blockEntity != null && blockEntity.isExtended();
    }

    private void updateState() {
        networkPowered = blockEntity != null && blockEntity.isNetworkPowered();
        networkOnline = blockEntity != null && blockEntity.isNetworkOnline();
        gridBooted = blockEntity != null && blockEntity.hasBootedGrid();
        crafting = blockEntity != null && blockEntity.isCrafting();
        workbenchId = getWorkbenchId(blockEntity);
    }

    private void updatePreview() {
        ItemStack stack = blockEntity != null ? blockEntity.getPendingOutputPreview() : ItemStack.EMPTY;
        if (!ItemStack.matches(stack, preview.getItem(0))) {
            preview.setItem(0, stack);
        }
    }

    private boolean isStillValidRoot(Player player) {
        if (blockEntity == null || blockEntity.isRemoved() || blockEntity.getLevel() != player.level()) {
            return false;
        }
        Level level = blockEntity.getLevel();
        BlockPos pos = blockEntity.getBlockPos();
        return level != null
                && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) == blockEntity
                && blockEntity.getRootAssembler() == blockEntity
                && Platform.hasPermissions(new DimensionalBlockPos(blockEntity), player)
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    private static String getWorkbenchId(@Nullable AEMolecularAssemblerBlockEntity blockEntity) {
        if (blockEntity == null) {
            return "";
        }
        BlockState state = blockEntity.getBlockState();
        if (state.getBlock() instanceof MolecularAssemblerBlock block && block.isUniversal()) {
            return "";
        }
        return AETaCZWorkbenchIds.getMenuBlockId(blockEntity).toString();
    }
}
