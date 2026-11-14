package me.myogoo.appliedtacz.menu;

import appeng.menu.AEBaseMenu;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import org.jetbrains.annotations.Nullable;

public class AEMolecularAssemblerMenu extends AEBaseMenu {
    public static final MenuType<AEMolecularAssemblerMenu> TYPE = IForgeMenuType.create(
            AEMolecularAssemblerMenu::fromNetwork);

    private final @Nullable AEMolecularAssemblerBlockEntity blockEntity;
    private final IUpgradeInventory upgrades;

    @GuiSync(3)
    public String workbenchId = "";

    public AEMolecularAssemblerMenu(int id, Inventory inventory,
            @Nullable AEMolecularAssemblerBlockEntity blockEntity) {
        super(TYPE, id, inventory, resolveMenuRoot(inventory, blockEntity));
        this.blockEntity = resolveMenuRoot(inventory, blockEntity);
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
        this.upgrades = this.blockEntity == null
                ? UpgradeInventories.forMachine(AEBlocks.MOLECULAR_ASSEMBLER,
                        AEMolecularAssemblerBlockEntity.UPGRADE_SLOTS, () -> {})
                : this.blockEntity.getUpgrades();
        setupUpgrades(this.upgrades);
        var returnInventory = (this.blockEntity == null
                ? new PatternProviderReturnInventory(() -> {})
                : this.blockEntity.getReturnInventory()).createMenuWrapper();
        for (int i = 0; i < PatternProviderReturnInventory.NUMBER_OF_SLOTS; i++) {
            addSlot(new AppEngSlot(returnInventory, i) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // Rejected or excess patterns must not fall through to the network return buffer.
                    return !PatternDetailsHelper.isEncodedPattern(stack) && super.mayPlace(stack);
                }
            }, SlotSemantics.STORAGE);
        }
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

    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    private void updateState() {
        if (this.blockEntity == null || !this.blockEntity.isValidMenuRoot()) {
            this.workbenchId = "";
            return;
        }
        this.workbenchId = this.blockEntity.getMenuWorkbenchId();
    }
}
