package me.myogoo.appliedtacz.datagen;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;

import java.util.concurrent.CompletableFuture;

public class AppliedTaczRecipeProvider extends RecipeProvider {
    public AppliedTaczRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    public void buildRecipes(RecipeOutput consumer) {
        // Workbench upgrade recipes are generated dynamically from TaCZ's loaded block index.
        // Static JSON cannot cover custom gun-pack workbench ids.
    }
}
