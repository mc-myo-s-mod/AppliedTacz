package me.myogoo.appliedtacz.registry;

import com.tacz.guns.api.DefaultAssets;
import appeng.api.parts.PartModels;
import appeng.items.parts.PartItem;
import appeng.items.parts.PartModelsHelper;
import me.myogoo.appliedtacz.item.AETaCZMolecularAssemblerItem;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.item.AETaCZTableItem;
import me.myogoo.appliedtacz.parts.TaCZPatternEncodingPart;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedTaCZ.MODID);

    static {
        PartModels.registerModels(PartModelsHelper.createModels(TaCZPatternEncodingPart.class));
    }

    public static final DeferredItem<PartItem<TaCZPatternEncodingPart>> TACZ_PATTERN_ENCODING_TERMINAL =
            ITEMS.register("tacz_pattern_encoding_terminal", () -> new PartItem<>(
                    new Item.Properties().stacksTo(1), TaCZPatternEncodingPart.class, TaCZPatternEncodingPart::new));

    public static final DeferredItem<Item> GUN_SMITH_TABLE = ITEMS.register("gun_smith_table",
            () -> new AETaCZTableItem(ModBlocks.GUN_SMITH_TABLE.get(), DefaultAssets.DEFAULT_BLOCK_ID));
    public static final DeferredItem<Item> ATTACHMENT_TABLE = ITEMS.register("attachment_workbench",
            () -> new AETaCZTableItem(ModBlocks.ATTACHMENT_TABLE.get(), AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID));
    public static final DeferredItem<Item> AMMO_WORKBENCH = ITEMS.register("ammo_workbench",
            () -> new AETaCZTableItem(ModBlocks.AMMO_ASSEMBLY_TABLE.get(), AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));
    public static final DeferredItem<Item> MOLECULAR_ASSEMBLER = ITEMS.register("molecular_assembler",
            () -> new BlockItem(ModBlocks.MOLECULAR_ASSEMBLER.get(), new Item.Properties().stacksTo(64)));
    public static final DeferredItem<Item> TACZ_ASSEMBLER_GUN = ITEMS.register("tacz_assembler_gun",
            () -> new AETaCZMolecularAssemblerItem(ModBlocks.TACZ_ASSEMBLER_GUN.get(), DefaultAssets.DEFAULT_BLOCK_ID));
    public static final DeferredItem<Item> TACZ_ASSEMBLER_ATTACHMENT = ITEMS.register("tacz_assembler_attachment",
            () -> new AETaCZMolecularAssemblerItem(ModBlocks.TACZ_ASSEMBLER_ATTACHMENT.get(), AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID));
    public static final DeferredItem<Item> TACZ_ASSEMBLER_AMMO = ITEMS.register("tacz_assembler_ammo",
            () -> new AETaCZMolecularAssemblerItem(ModBlocks.TACZ_ASSEMBLER_AMMO.get(), AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));
    public static final DeferredItem<Item> TACZ_ASSEMBLER_UNIVERSAL = ITEMS.register("tacz_assembler_universal",
            () -> new AETaCZMolecularAssemblerItem(ModBlocks.TACZ_ASSEMBLER_UNIVERSAL.get(), null));
    public static final DeferredItem<Item> EXTENDED_TACZ_ASSEMBLER_GUN = ITEMS.register("extended_tacz_assembler_gun",
            () -> new AETaCZMolecularAssemblerItem(ModBlocks.EXTENDED_TACZ_ASSEMBLER_GUN.get(), DefaultAssets.DEFAULT_BLOCK_ID));
    public static final DeferredItem<Item> EXTENDED_TACZ_ASSEMBLER_ATTACHMENT = ITEMS.register("extended_tacz_assembler_attachment",
            () -> new AETaCZMolecularAssemblerItem(ModBlocks.EXTENDED_TACZ_ASSEMBLER_ATTACHMENT.get(), AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID));
    public static final DeferredItem<Item> EXTENDED_TACZ_ASSEMBLER_AMMO = ITEMS.register("extended_tacz_assembler_ammo",
            () -> new AETaCZMolecularAssemblerItem(ModBlocks.EXTENDED_TACZ_ASSEMBLER_AMMO.get(), AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));

    private ModItems() {
    }
}
