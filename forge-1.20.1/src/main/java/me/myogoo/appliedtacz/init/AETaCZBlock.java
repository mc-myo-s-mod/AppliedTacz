package me.myogoo.appliedtacz.init;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.AmmoAssemblyTable;
import me.myogoo.appliedtacz.block.AttachmentTable;
import me.myogoo.appliedtacz.block.GunSmithTable;
import me.myogoo.appliedtacz.block.MolecularAssemblerBlock;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.item.AETaCZAssemblerItem;
import me.myogoo.appliedtacz.item.AETaCZTableItem;
import me.myogoo.appliedtacz.parts.TaCZPatternEncodingPart;
import appeng.api.parts.PartModels;
import appeng.items.parts.PartItem;
import appeng.items.parts.PartModelsHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class AETaCZBlock {
    public static final DeferredRegister<Block> BLOCkS = DeferredRegister.create(ForgeRegistries.BLOCKS, AppliedTaCZ.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AppliedTaCZ.MODID);

    static {
        PartModels.registerModels(PartModelsHelper.createModels(TaCZPatternEncodingPart.class));
    }

    public static final RegistryObject<PartItem<TaCZPatternEncodingPart>> TACZ_PATTERN_ENCODING_TERMINAL =
            ITEMS.register("tacz_pattern_encoding_terminal", () -> new PartItem<>(
                    new Item.Properties().stacksTo(1), TaCZPatternEncodingPart.class, TaCZPatternEncodingPart::new));

    public static final RegistryObject<GunSmithTable> GUN_SMITH_TABLE = registerTableBlock("gun_smith_table",
            GunSmithTable::new);
    public static final RegistryObject<AttachmentTable> ATTACHMENT_TABLE = registerTableBlock("attachment_workbench",
            AttachmentTable::new);
    public static final RegistryObject<AmmoAssemblyTable> AMMO_ASSEMBLY_TABLE = registerTableBlock("ammo_workbench",
            AmmoAssemblyTable::new);
    public static final RegistryObject<MolecularAssemblerBlock> MOLECULAR_ASSEMBLER_AMMO =
            registerAssemblerBlock("molecular_assembler_ammo", MolecularAssemblerBlock.Ammo::new);
    public static final RegistryObject<MolecularAssemblerBlock> MOLECULAR_ASSEMBLER_ATTACHMENT =
            registerAssemblerBlock("molecular_assembler_attachment", MolecularAssemblerBlock.Attachment::new);
    public static final RegistryObject<MolecularAssemblerBlock> MOLECULAR_ASSEMBLER_GUN =
            registerAssemblerBlock("molecular_assembler_gun", MolecularAssemblerBlock.Gun::new);
    public static final RegistryObject<MolecularAssemblerBlock> MOLECULAR_ASSEMBLER_UNIVERSAL =
            registerPlainAssemblerBlock("molecular_assembler_universal", MolecularAssemblerBlock.Universal::new);
    public static final RegistryObject<MolecularAssemblerBlock> MOLECULAR_ASSEMBLER =
            registerPlainAssemblerBlock("molecular_assembler", MolecularAssemblerBlock.Compact::new);
    public static final RegistryObject<MolecularAssemblerBlock> TACZ_ASSEMBLER_AMMO =
            registerAssemblerBlock("tacz_assembler_ammo",
                    () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.AMMO_WORKBENCH, 1, 1, 1, 16));
    public static final RegistryObject<MolecularAssemblerBlock> TACZ_ASSEMBLER_ATTACHMENT =
            registerAssemblerBlock("tacz_assembler_attachment",
                    () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH, 1, 1, 1, 16));
    public static final RegistryObject<MolecularAssemblerBlock> TACZ_ASSEMBLER_GUN =
            registerAssemblerBlock("tacz_assembler_gun",
                    () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.GUN_SMITH_TABLE, 1, 1, 1, 16));
    public static final RegistryObject<MolecularAssemblerBlock> TACZ_ASSEMBLER_UNIVERSAL =
            registerPlainAssemblerBlock("tacz_assembler_universal",
                    () -> new MolecularAssemblerBlock(null, 1, 1, 1, 16));
    public static final RegistryObject<MolecularAssemblerBlock> EXTENDED_TACZ_ASSEMBLER_AMMO =
            registerAssemblerBlock("extended_tacz_assembler_ammo",
                    () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.AMMO_WORKBENCH, 1, 1, 1, 16, true));
    public static final RegistryObject<MolecularAssemblerBlock> EXTENDED_TACZ_ASSEMBLER_ATTACHMENT =
            registerAssemblerBlock("extended_tacz_assembler_attachment",
                    () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH, 1, 1, 1, 16, true));
    public static final RegistryObject<MolecularAssemblerBlock> EXTENDED_TACZ_ASSEMBLER_GUN =
            registerAssemblerBlock("extended_tacz_assembler_gun",
                    () -> new MolecularAssemblerBlock(WorkbenchUpgradeKind.GUN_SMITH_TABLE, 1, 1, 1, 16, true));

    private static <T extends Block> RegistryObject<T> registerTableBlock(String name, Supplier<T> supplier) {
        var block = BLOCkS.register(name, supplier);
        ITEMS.register(name, () -> new AETaCZTableItem(block.get()));
        return block;
    }

    private static <T extends MolecularAssemblerBlock> RegistryObject<T> registerAssemblerBlock(String name,
            Supplier<T> supplier) {
        var block = BLOCkS.register(name, supplier);
        ITEMS.register(name, () -> new AETaCZAssemblerItem(block.get()));
        return block;
    }

    private static <T extends MolecularAssemblerBlock> RegistryObject<T> registerPlainAssemblerBlock(String name,
            Supplier<T> supplier) {
        var block = BLOCkS.register(name, supplier);
        ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().stacksTo(1)));
        return block;
    }
}
