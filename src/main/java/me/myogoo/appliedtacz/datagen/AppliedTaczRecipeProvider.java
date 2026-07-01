package me.myogoo.appliedtacz.datagen;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;

public class AppliedTaczRecipeProvider extends RecipeProvider {
    public AppliedTaczRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    public void buildRecipes(RecipeOutput consumer) {
        buildWorkbenchUpgradeRecipe(consumer, "gun_smith_table", WorkbenchUpgradeKind.GUN_SMITH_TABLE);
        buildWorkbenchUpgradeRecipe(consumer, "ammo_workbench", WorkbenchUpgradeKind.AMMO_WORKBENCH);
        buildWorkbenchUpgradeRecipe(consumer, "attachment_workbench", WorkbenchUpgradeKind.ATTACHMENT_WORKBENCH);
    }

    private void buildWorkbenchUpgradeRecipe(RecipeOutput consumer, String name, WorkbenchUpgradeKind kind) {
        SpecialRecipeBuilder.special(category -> new WorkbenchUpgradeRecipe(category, kind))
                .save(consumer, ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, name));
    }
}
