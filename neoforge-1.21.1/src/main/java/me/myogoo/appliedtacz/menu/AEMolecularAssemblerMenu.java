package me.myogoo.appliedtacz.menu;

import appeng.api.util.DimensionalBlockPos;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.AppEngSlot;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.Platform;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.block.blcokentity.AEMolecularAssemblerBlockEntity;
import me.myogoo.appliedtacz.registry.ModMenus;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
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
    private final IUpgradeInventory upgrades;

    @GuiSync(3)
    public String workbenchId = "";

    public AEMolecularAssemblerMenu(int id, Inventory inventory,
            @Nullable AEMolecularAssemblerBlockEntity blockEntity) {
        super(ModMenus.AE_MOLECULAR_ASSEMBLER.get(), id, inventory, blockEntity);
        this.blockEntity = blockEntity;
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
        this.upgrades = blockEntity == null
                ? UpgradeInventories.forMachine(AEBlocks.MOLECULAR_ASSEMBLER,
                        AEMolecularAssemblerBlockEntity.UPGRADE_SLOTS, () -> {})
                : blockEntity.getUpgrades();
        setupUpgrades(this.upgrades);
        var returnInventory = (blockEntity == null
                ? new PatternProviderReturnInventory(() -> {})
                : blockEntity.getReturnInventory()).createMenuWrapper();
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
        updateState();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            updateState();
            if (!isStillValidRoot(getPlayer())) {
                setValidMenu(false);
            }
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return isValidMenu() && isStillValidRoot(player);
    }

    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    public boolean isExtended() {
        return blockEntity != null && blockEntity.isExtended();
    }

    private void updateState() {
        workbenchId = getWorkbenchId(blockEntity);
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
                && blockEntity.isValidMenuRoot()
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
