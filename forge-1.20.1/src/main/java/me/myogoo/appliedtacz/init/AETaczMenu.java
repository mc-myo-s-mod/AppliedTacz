package me.myogoo.appliedtacz.init;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.menu.AEGunSmithTableMenu;
import me.myogoo.appliedtacz.menu.AEMolecularAssemblerMenu;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class AETaczMenu {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            AppliedTaCZ.MODID);

    public static final RegistryObject<MenuType<AEGunSmithTableMenu>> AE_GUN_SMITH_TABLE = REGISTER
            .register("ae_gun_smith_table_menu", () -> AEGunSmithTableMenu.TYPE);
    public static final RegistryObject<MenuType<AEMolecularAssemblerMenu>> AE_MOLECULAR_ASSEMBLER = REGISTER
            .register("ae_molecular_assembler_menu", () -> AEMolecularAssemblerMenu.TYPE);
    public static final RegistryObject<MenuType<TaCZPatternEncodingMenu>> TACZ_PATTERN_ENCODING_TERMINAL = REGISTER
            .register("tacz_pattern_encoding_terminal", () -> TaCZPatternEncodingMenu.TYPE);

}
