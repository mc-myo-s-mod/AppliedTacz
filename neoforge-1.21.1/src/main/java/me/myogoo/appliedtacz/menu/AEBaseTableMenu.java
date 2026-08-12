package me.myogoo.appliedtacz.menu;

import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

public abstract class AEBaseTableMenu extends GunSmithTableMenu {
    public AEBaseTableMenu(int id, Inventory inventory, @Nullable ResourceLocation resourceLocation) {
        super(id, inventory, resourceLocation);
    }
}
