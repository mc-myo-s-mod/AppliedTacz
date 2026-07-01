package me.myogoo.appliedtacz.registry;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, AppliedTaCZ.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<WorkbenchUpgradeRecipe>>
            GUN_SMITH_TABLE_UPGRADE = register("gun_smith_table_upgrade", WorkbenchUpgradeKind.GUN_SMITH_TABLE);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<WorkbenchUpgradeRecipe>>
            AMMO_WORKBENCH_UPGRADE = register("ammo_workbench_upgrade", WorkbenchUpgradeKind.AMMO_WORKBENCH);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<WorkbenchUpgradeRecipe>>
            ATTACHMENT_WORKBENCH_UPGRADE = register("attachment_workbench_upgrade",
                    WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH);

    private ModRecipeSerializers() {
    }

    private static DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<WorkbenchUpgradeRecipe>> register(
            String name, WorkbenchUpgradeKind kind) {
        return RECIPE_SERIALIZERS.register(name,
                () -> new SimpleCraftingRecipeSerializer<>(category -> new WorkbenchUpgradeRecipe(category, kind)));
    }
}
