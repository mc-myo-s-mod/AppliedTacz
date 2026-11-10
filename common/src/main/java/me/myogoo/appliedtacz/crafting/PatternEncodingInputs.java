package me.myogoo.appliedtacz.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PatternEncodingInputs {
    private PatternEncodingInputs() {
    }

    public static List<AEItemKey> choices(GunSmithTableRecipe recipe, int index) {
        if (index < 0 || index >= recipe.getInputs().size()) {
            return List.of();
        }
        var ingredient = recipe.getInputs().get(index).getIngredient();
        List<AEItemKey> choices = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            AEItemKey key = AEItemKey.of(stack);
            if (key != null && ingredient.test(stack) && !choices.contains(key)) {
                choices.add(key);
            }
        }
        return choices;
    }

    public static boolean isEncodable(GunSmithTableRecipe recipe) {
        if (recipe.getInputs().isEmpty() || recipe.getInputs().size() > AEProcessingPattern.MAX_INPUT_SLOTS) {
            return false;
        }
        for (int i = 0; i < recipe.getInputs().size(); i++) {
            if (recipe.getInputs().get(i).getCount() <= 0 || choices(recipe, i).isEmpty()) {
                return false;
            }
        }
        return !recipe.getOutput().isEmpty() && recipe.getOutput().getCount() > 0;
    }

    public static List<GenericStack> validatedInputs(GunSmithTableRecipe recipe, List<AEItemKey> selected) {
        if (recipe.getInputs().isEmpty() || recipe.getInputs().size() > AEProcessingPattern.MAX_INPUT_SLOTS
                || selected.size() != recipe.getInputs().size() || recipe.getOutput().isEmpty()) {
            return List.of();
        }
        List<GenericStack> inputs = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            AEItemKey key = selected.get(i);
            var ingredient = recipe.getInputs().get(i);
            if (key == null || ingredient.getCount() <= 0
                    || !ingredient.getIngredient().test(key.getReadOnlyStack())) {
                return List.of();
            }
            inputs.add(new GenericStack(key, ingredient.getCount()));
        }
        return inputs;
    }

    public static List<AEItemKey> restoredChoices(GunSmithTableRecipe recipe, List<GenericStack> sparseInputs) {
        if (!isEncodable(recipe) || sparseInputs.size() > AEProcessingPattern.MAX_INPUT_SLOTS) {
            return List.of();
        }
        var inputs = sparseInputs.stream().filter(java.util.Objects::nonNull).toList();
        // shortcut: merged or split external inputs are rejected; upgrade when aggregated pattern import is requested.
        if (inputs.size() != recipe.getInputs().size()) {
            return List.of();
        }
        List<AEItemKey> choices = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            if (!inputMatches(recipe, i, inputs.get(i))) {
                choices.clear();
                break;
            }
            choices.add((AEItemKey) inputs.get(i).what());
        }
        if (!choices.isEmpty()) {
            return choices;
        }
        int[] assignedIngredients = new int[inputs.size()];
        Arrays.fill(assignedIngredients, -1);
        for (int ingredient = 0; ingredient < inputs.size(); ingredient++) {
            if (!assignInput(recipe, ingredient, inputs, assignedIngredients, new boolean[inputs.size()])) {
                return List.of();
            }
        }
        AEItemKey[] selected = new AEItemKey[inputs.size()];
        for (int input = 0; input < inputs.size(); input++) {
            selected[assignedIngredients[input]] = (AEItemKey) inputs.get(input).what();
        }
        return Arrays.asList(selected);
    }

    private static boolean assignInput(GunSmithTableRecipe recipe, int ingredient, List<GenericStack> inputs,
                                      int[] assignedIngredients, boolean[] visited) {
        for (int input = 0; input < inputs.size(); input++) {
            if (!visited[input] && inputMatches(recipe, ingredient, inputs.get(input))) {
                visited[input] = true;
                if (assignedIngredients[input] < 0
                        || assignInput(recipe, assignedIngredients[input], inputs, assignedIngredients, visited)) {
                    assignedIngredients[input] = ingredient;
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inputMatches(GunSmithTableRecipe recipe, int index, GenericStack input) {
        var ingredient = recipe.getInputs().get(index);
        return input.what() instanceof AEItemKey key && input.amount() == ingredient.getCount()
                && ingredient.getIngredient().test(key.getReadOnlyStack());
    }
}
