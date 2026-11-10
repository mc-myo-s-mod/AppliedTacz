package me.myogoo.appliedtacz.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public final class AssemblerRecipeMatcher {
    private AssemblerRecipeMatcher() {
    }

    public static boolean outputsMatch(ItemStack expected, Iterable<GenericStack> outputs) {
        if (expected.isEmpty()) {
            return false;
        }

        GenericStack onlyOutput = null;
        for (GenericStack output : outputs) {
            if (onlyOutput != null) {
                return false;
            }
            onlyOutput = output;
        }

        return onlyOutput != null
                && onlyOutput.what() instanceof AEItemKey itemKey
                && onlyOutput.amount() == expected.getCount()
                && itemKey.matches(expected);
    }

    public static boolean matches(GunSmithTableRecipe recipe, KeyCounter[] inputs) {
        return allocate(recipe, inputs);
    }

    public static void consume(KeyCounter[] inputs) {
        for (KeyCounter counter : inputs) {
            counter.clear();
        }
    }

    private static boolean allocate(GunSmithTableRecipe recipe, KeyCounter[] inputs) {
        List<GunSmithTableIngredient> ingredients = recipe.getInputs();
        LinkedHashMap<AEItemKey, Long> available = collectAvailable(inputs);
        if (available == null) {
            return false;
        }

        long requiredTotal = 0;
        for (GunSmithTableIngredient ingredient : ingredients) {
            if (ingredient.getCount() <= 0) {
                return false;
            }
            try {
                requiredTotal = Math.addExact(requiredTotal, ingredient.getCount());
            } catch (ArithmeticException ignored) {
                return false;
            }
        }
        long availableTotal = sumAvailable(available);
        if (availableTotal < 0) {
            return false;
        }
        if (availableTotal != requiredTotal) {
            return false;
        }

        long totalFlow = maxFlow(ingredients, new ArrayList<>(available.keySet()),
                available.values().stream().mapToLong(Long::longValue).toArray());
        if (totalFlow != requiredTotal) {
            return false;
        }
        return true;
    }

    private static LinkedHashMap<AEItemKey, Long> collectAvailable(KeyCounter[] inputs) {
        LinkedHashMap<AEItemKey, Long> available = new LinkedHashMap<>();
        for (KeyCounter counter : inputs) {
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                long amount = entry.getLongValue();
                if (amount < 0 || !(entry.getKey() instanceof AEItemKey key)) {
                    return null;
                }
                if (amount > 0) {
                    try {
                        available.merge(key, amount, (left, right) -> Math.addExact(left, right));
                    } catch (ArithmeticException ignored) {
                        return null;
                    }
                }
            }
        }
        return available;
    }

    private static long sumAvailable(LinkedHashMap<AEItemKey, Long> available) {
        long total = 0;
        for (long amount : available.values()) {
            try {
                total = Math.addExact(total, amount);
            } catch (ArithmeticException ignored) {
                return -1;
            }
        }
        return total;
    }

    private static long maxFlow(List<GunSmithTableIngredient> ingredients, List<AEItemKey> keys,
            long[] available) {
        int source = 0;
        int firstIngredient = 1;
        int firstKey = firstIngredient + ingredients.size();
        int sink = firstKey + keys.size();
        long[][] capacity = new long[sink + 1][sink + 1];

        for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
            capacity[source][firstIngredient + ingredient] = ingredients.get(ingredient).getCount();
            for (int key = 0; key < keys.size(); key++) {
                if (ingredients.get(ingredient).getIngredient().test(keys.get(key).getReadOnlyStack())) {
                    capacity[firstIngredient + ingredient][firstKey + key] = Long.MAX_VALUE / 4;
                }
            }
        }
        for (int key = 0; key < keys.size(); key++) {
            capacity[firstKey + key][sink] = available[key];
        }

        long totalFlow = 0;
        int[] parent = new int[capacity.length];
        while (findPath(capacity, source, sink, parent)) {
            long pathFlow = Long.MAX_VALUE;
            for (int node = sink; node != source; node = parent[node]) {
                pathFlow = Math.min(pathFlow, capacity[parent[node]][node]);
            }
            for (int node = sink; node != source; node = parent[node]) {
                capacity[parent[node]][node] -= pathFlow;
                capacity[node][parent[node]] += pathFlow;
            }
            totalFlow += pathFlow;
        }
        return totalFlow;
    }

    private static boolean findPath(long[][] capacity, int source, int sink, int[] parent) {
        Arrays.fill(parent, -1);
        int[] queue = new int[capacity.length];
        int read = 0;
        int write = 0;
        queue[write++] = source;
        parent[source] = source;
        while (read < write) {
            int node = queue[read++];
            for (int next = 0; next < capacity.length; next++) {
                if (parent[next] == -1 && capacity[node][next] > 0) {
                    parent[next] = node;
                    if (next == sink) {
                        return true;
                    }
                    queue[write++] = next;
                }
            }
        }
        return false;
    }
}
