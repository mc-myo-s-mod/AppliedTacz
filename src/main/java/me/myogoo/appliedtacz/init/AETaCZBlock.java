package me.myogoo.appliedtacz.init;

import com.tacz.guns.api.DefaultAssets;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.block.AmmoAssemblyTable;
import me.myogoo.appliedtacz.block.AttachmentTable;
import me.myogoo.appliedtacz.block.GunSmithTable;
import me.myogoo.appliedtacz.item.AETaCZTableItem;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class AETaCZBlock {
    public static final DeferredRegister<Block> BLOCkS = DeferredRegister.create(ForgeRegistries.BLOCKS, AppliedTaCZ.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AppliedTaCZ.MODID);

    public static final RegistryObject<GunSmithTable> GUN_SMITH_TABLE = registerTableBlock("gun_smith_table",
            GunSmithTable::new, DefaultAssets.DEFAULT_BLOCK_ID);
    public static final RegistryObject<AttachmentTable> ATTACHMENT_TABLE = registerTableBlock("attachment_workbench",
            AttachmentTable::new, AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID);
    public static final RegistryObject<AmmoAssemblyTable> AMMO_ASSEMBLY_TABLE = registerTableBlock("ammo_workbench",
            AmmoAssemblyTable::new, AETaCZWorkbenchIds.AMMO_WORKBENCH_ID);

    private static <T extends Block> RegistryObject<T> registerTableBlock(String name, Supplier<T> supplier,
            ResourceLocation defaultBlockId) {
        var block = BLOCkS.register(name, supplier);
        ITEMS.register(name, () -> new AETaCZTableItem(block.get(), defaultBlockId));
        return block;
    }
}
