package me.myogoo.appliedtacz.crafting;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.init.ModRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class TaCZPatternEncodingRecipes {
    private TaCZPatternEncodingRecipes() {
    }

    public record Entry(ResourceLocation id, GunSmithTableRecipe recipe) {
    }

    public static List<ResourceLocation> getWorkbenches() {
        return TimelessAPI.getAllCommonBlockIndex().stream()
                .filter(entry -> entry.getValue().getData().getTabs() != null
                        && !entry.getValue().getData().getTabs().isEmpty())
                .map(java.util.Map.Entry::getKey)
                .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
    }

    public static List<Entry> getRecipes(Level level, ResourceLocation workbenchId) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipe.GUN_SMITH_TABLE_CRAFTING.get()).stream()
                .map(holder -> new Entry(holder.id(), holder.value()))
                .filter(entry -> isAllowed(workbenchId, entry))
                .sorted(Comparator.comparing(entry -> entry.id().toString())).toList();
    }

    public static @Nullable Entry findRecipe(Level level, ResourceLocation id) {
        if (id == null) {
            return null;
        }
        var recipe = level.getRecipeManager().byKey(id).orElse(null);
        return recipe != null && recipe.value() instanceof GunSmithTableRecipe gunRecipe
                ? new Entry(id, gunRecipe) : null;
    }

    public static boolean isAllowed(ResourceLocation workbenchId, Entry entry) {
        return workbenchId != null && TimelessAPI.getCommonBlockIndex(workbenchId)
                .filter(index -> index.getFilter() == null || index.getFilter().contains(entry.id()))
                .filter(index -> index.getData().getTabs() != null && index.getData().getTabs().stream()
                        .anyMatch(tab -> tab.id().equals(entry.recipe().getTab())))
                .isPresent();
    }

    public static ItemStack encode(Level level, GunSmithTableRecipe recipe, List<AEItemKey> choices) {
        List<GenericStack> inputs = PatternEncodingInputs.validatedInputs(recipe, choices);
        ItemStack output = recipe.getResultItem(level.registryAccess());
        if (inputs.isEmpty() || output.isEmpty() || output.getCount() <= 0) {
            return ItemStack.EMPTY;
        }
        GenericStack result = new GenericStack(AEItemKey.of(output), output.getCount());
        return PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(result));
    }
}
