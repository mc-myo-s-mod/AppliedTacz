package me.myogoo.appliedtacz.registry;

import com.tacz.guns.api.DefaultAssets;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.item.AETaCZTableItem;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AppliedTaCZ.MODID);

    public static final DeferredItem<Item> GUN_SMITH_TABLE = ITEMS.register("gun_smith_table",
            () -> new AETaCZTableItem(ModBlocks.GUN_SMITH_TABLE.get(), DefaultAssets.DEFAULT_BLOCK_ID));
    public static final DeferredItem<Item> ATTACHMENT_TABLE = ITEMS.register("attachment_workbench",
            () -> new AETaCZTableItem(ModBlocks.ATTACHMENT_TABLE.get(), AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID));
    public static final DeferredItem<Item> AMMO_WORKBENCH = ITEMS.register("ammo_workbench",
            () -> new AETaCZTableItem(ModBlocks.AMMO_ASSEMBLY_TABLE.get(), AETaCZWorkbenchIds.AMMO_WORKBENCH_ID));

    private ModItems() {
    }
}
