package me.myogoo.appliedtacz.init;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AETaCZRecipeSerializer {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTER =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, AppliedTaCZ.MODID);

    public static final RegistryObject<RecipeSerializer<WorkbenchUpgradeRecipe>> GUN_SMITH_TABLE_UPGRADE =
            register("gun_smith_table_upgrade", WorkbenchUpgradeKind.GUN_SMITH_TABLE);
    public static final RegistryObject<RecipeSerializer<WorkbenchUpgradeRecipe>> AMMO_WORKBENCH_UPGRADE =
            register("ammo_workbench_upgrade", WorkbenchUpgradeKind.AMMO_WORKBENCH);
    public static final RegistryObject<RecipeSerializer<WorkbenchUpgradeRecipe>> ATTACHMENT_WORKBENCH_UPGRADE =
            register("attachment_workbench_upgrade", WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH);

    private AETaCZRecipeSerializer() {
    }

    private static RegistryObject<RecipeSerializer<WorkbenchUpgradeRecipe>> register(String name,
            WorkbenchUpgradeKind kind) {
        return REGISTER.register(name,
                () -> new SimpleCraftingRecipeSerializer<>((id, category) ->
                        new WorkbenchUpgradeRecipe(id, category, kind)));
    }
}
