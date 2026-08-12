package me.myogoo.appliedtacz.init;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AETaczMenu {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            AppliedTaCZ.MODID);

    public static final RegistryObject<MenuType<AEGunSmithTableMenu>> AE_GUN_SMITH_TABLE = REGISTER
            .register("ae_gun_smith_table_menu", () -> AEGunSmithTableMenu.TYPE);

}
