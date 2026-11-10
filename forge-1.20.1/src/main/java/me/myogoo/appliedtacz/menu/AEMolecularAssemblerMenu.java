package me.myogoo.appliedtacz.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.InaccessibleSlot;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import org.jetbrains.annotations.Nullable;

public class AEMolecularAssemblerMenu extends AEBaseMenu {
    public static final MenuType<AEMolecularAssemblerMenu> TYPE = IForgeMenuType.create(
            AEMolecularAssemblerMenu::fromNetwork);

    private final @Nullable AEMolecularAssemblerBlockEntity blockEntity;
    private final AppEngInternalInventory previewInventory = new AppEngInternalInventory(1);
    private final Slot previewSlot;

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
        super(TYPE, id, inventory, resolveMenuRoot(inventory, blockEntity));
        this.blockEntity = resolveMenuRoot(inventory, blockEntity);
        this.previewSlot = addSlot(new PreviewSlot(this.previewInventory, 0), SlotSemantics.MACHINE_OUTPUT);
        InternalInventory patterns = this.blockEntity == null
                ? new AppEngInternalInventory(AEMolecularAssemblerBlockEntity.PATTERN_SLOTS)
                : this.blockEntity.getPatternInventory();
        for (int i = 0; i < AEMolecularAssemblerBlockEntity.PATTERN_SLOTS; i++) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.ENCODED_PATTERN, patterns, i) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return AEItems.PROCESSING_PATTERN.isSameAs(stack) && super.mayPlace(stack);
                }
            }.setStackLimit(1), SlotSemantics.ENCODED_PATTERN);
        }
        setupUpgrades(this.blockEntity == null
                ? UpgradeInventories.forMachine(AEBlocks.MOLECULAR_ASSEMBLER,
                        AEMolecularAssemblerBlockEntity.UPGRADE_SLOTS, () -> {})
                : this.blockEntity.getUpgrades());
        createPlayerInventorySlots(inventory);
        if (isServerSide()) {
            updateState();
        }
    }

    private static AEMolecularAssemblerMenu fromNetwork(int windowId, Inventory inv, FriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        AEMolecularAssemblerBlockEntity blockEntity = inv.player.level().getBlockEntity(pos)
                instanceof AEMolecularAssemblerBlockEntity assembler ? assembler : null;
        return new AEMolecularAssemblerMenu(windowId, inv, blockEntity);
    }

    private static @Nullable AEMolecularAssemblerBlockEntity resolveMenuRoot(Inventory inventory,
            @Nullable AEMolecularAssemblerBlockEntity blockEntity) {
        if (blockEntity == null) {
            return null;
        }
        return inventory.player.level().isClientSide() ? blockEntity : blockEntity.getMenuRootAssembler();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            if (!stillValid(getPlayer())) {
                setValidMenu(false);
                return;
            }
            updateState();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        if (!super.stillValid(player) || this.blockEntity == null) {
            return false;
        }
        BlockPos pos = this.blockEntity.getBlockPos();
        return player.level() == this.blockEntity.getLevel()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0
                && (player.level().isClientSide() || this.blockEntity.mayOpenMenu(player));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        return idx == this.previewSlot.index ? ItemStack.EMPTY : super.quickMoveStack(player, idx);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId == this.previewSlot.index) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot != this.previewSlot && super.canDragTo(slot);
    }

    public ItemStack getPendingOutput() {
        return this.previewInventory.getStackInSlot(0).copy();
    }

    public boolean isExtended() {
        return this.blockEntity != null
                && this.blockEntity.getBlockState().getBlock() instanceof MolecularAssemblerBlock block
                && block.isExtended();
    }

    private void updateState() {
        if (this.blockEntity == null || !this.blockEntity.isValidMenuRoot()) {
            this.networkPowered = false;
            this.networkOnline = false;
            this.gridBooted = false;
            this.crafting = false;
            this.workbenchId = "";
            this.previewInventory.setItemDirect(0, ItemStack.EMPTY);
            return;
        }
        this.networkPowered = this.blockEntity.isNetworkPowered();
        this.networkOnline = this.blockEntity.isNetworkOnline();
        this.gridBooted = this.blockEntity.hasBootedGrid();
        this.crafting = this.blockEntity.isCrafting();
        this.workbenchId = this.blockEntity.getMenuWorkbenchId();
        this.previewInventory.setItemDirect(0, this.blockEntity.getPendingOutput());
    }

    private static final class PreviewSlot extends InaccessibleSlot {
        private PreviewSlot(AppEngInternalInventory inventory, int invSlot) {
            super(inventory, invSlot);
            setNotDraggable();
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }
    }
}
