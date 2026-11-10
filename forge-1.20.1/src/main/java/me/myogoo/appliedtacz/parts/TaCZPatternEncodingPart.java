package me.myogoo.appliedtacz.parts;

import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.core.definitions.AEItems;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.Platform;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.AEItemDefinitionFilter;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class TaCZPatternEncodingPart extends AbstractTerminalPart {
    @PartModels
    public static final ResourceLocation MODEL_OFF = ResourceLocation.fromNamespaceAndPath(
            AppliedTaCZ.MODID, "part/tacz_pattern_encoding_terminal_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = ResourceLocation.fromNamespaceAndPath(
            AppliedTaCZ.MODID, "part/tacz_pattern_encoding_terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private final AppEngInternalInventory blankPatterns = new AppEngInternalInventory(this, 1, 64,
            new AEItemDefinitionFilter(AEItems.BLANK_PATTERN));
    private final AppEngInternalInventory encodedPatterns = new AppEngInternalInventory(this, 1, 1);

    public TaCZPatternEncodingPart(IPartItem<?> partItem) {
        super(partItem);
    }

    public InternalInventory getBlankPatternInventory() {
        return blankPatterns;
    }

    public InternalInventory getEncodedPatternInventory() {
        return encodedPatterns;
    }

    public boolean mayOpen(Player player) {
        var host = getHost();
        if (host == null || !host.isInWorld() || host.getPart(getSide()) != this) {
            return false;
        }
        var location = host.getLocation();
        var pos = location.getPos();
        return location.getLevel() == player.level() && player.level().hasChunkAt(pos)
                && player.level().getBlockEntity(pos) == host.getBlockEntity()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64
                && (player.level().isClientSide() || Platform.hasPermissions(location, player));
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return TaCZPatternEncodingMenu.TYPE;
    }

    @Override
    public IPartModel getStaticModels() {
        return selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public void readFromNBT(CompoundTag data) {
        super.readFromNBT(data);
        blankPatterns.clear();
        encodedPatterns.clear();
        blankPatterns.readFromNBT(data, "BlankPatterns");
        encodedPatterns.readFromNBT(data, "EncodedPatterns");
    }

    @Override
    public void writeToNBT(CompoundTag data) {
        super.writeToNBT(data);
        blankPatterns.writeToNBT(data, "BlankPatterns");
        encodedPatterns.writeToNBT(data, "EncodedPatterns");
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (ItemStack stack : blankPatterns) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
        for (ItemStack stack : encodedPatterns) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        blankPatterns.clear();
        encodedPatterns.clear();
    }
}
