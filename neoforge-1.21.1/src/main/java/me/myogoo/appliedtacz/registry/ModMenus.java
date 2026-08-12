package me.myogoo.appliedtacz.registry;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, AppliedTaCZ.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AEGunSmithTableMenu>> AE_GUN_SMITH_TABLE =
            MENUS.register("ae_gun_smith_table_menu", () -> AEGunSmithTableMenu.TYPE);

    private ModMenus() {
    }
}
