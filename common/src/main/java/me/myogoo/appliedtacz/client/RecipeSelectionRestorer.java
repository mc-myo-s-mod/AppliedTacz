package me.myogoo.appliedtacz.client;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class RecipeSelectionRestorer {
    private static @Nullable ResourceLocation pendingRecipeId;

    private RecipeSelectionRestorer() {
    }

    public static void remember(@Nullable ResourceLocation recipeId) {
        pendingRecipeId = recipeId;
    }

    public static @Nullable ResourceLocation getPendingRecipeId() {
        return pendingRecipeId;
    }

    public static void forget(@Nullable ResourceLocation recipeId) {
        if (recipeId == null || Objects.equals(pendingRecipeId, recipeId)) {
            pendingRecipeId = null;
        }
    }
}
