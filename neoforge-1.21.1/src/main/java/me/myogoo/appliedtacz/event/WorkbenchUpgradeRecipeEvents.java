package me.myogoo.appliedtacz.event;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IBlock;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = AppliedTaCZ.MODID)
public final class WorkbenchUpgradeRecipeEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TagKey<Item> INTERFACE_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("ae2", "interface"));
    private static final String DYNAMIC_RECIPE_PREFIX = "workbench_upgrade/";
    private static final String ASSEMBLER_RECIPE_PREFIX = "assembler_upgrade/";
    private static final String EXTENDED_ASSEMBLER_RECIPE_PREFIX = "extended_assembler_upgrade/";

    private WorkbenchUpgradeRecipeEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        replaceWorkbenchUpgradeRecipes(server);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        replaceWorkbenchUpgradeRecipes(event.getServer());
    }

    private static void replaceWorkbenchUpgradeRecipes(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        for (RecipeHolder<?> recipe : recipeManager.getRecipes()) {
            if (!isAppliedTaczWorkbenchUpgradeRecipe(recipe.id())) {
                recipes.add(recipe);
            }
        }

        List<RecipeHolder<?>> generatedRecipes = createDynamicRecipes();
        recipes.addAll(generatedRecipes);
        recipeManager.replaceRecipes(recipes);
        LOGGER.info("Registered {} dynamic AppliedTaCZ workbench upgrade recipes", generatedRecipes.size());
    }

    private static List<RecipeHolder<?>> createDynamicRecipes() {
        List<RecipeHolder<?>> recipes = new ArrayList<>();

        for (var entry : AETaCZWorkbenchIndex.entries()) {
            recipes.add(createRecipe(entry));
            recipes.add(createAssemblerRecipe(entry));
            recipes.add(createExtendedAssemblerRecipe(entry));
        }

        return recipes;
    }

    private static RecipeHolder<?> createRecipe(AETaCZWorkbenchIndex.Entry entry) {
        Item baseWorkbench = entry.baseWorkbench();
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(createWorkbenchIngredient(baseWorkbench, entry.blockId()));
        ingredients.add(Ingredient.of(INTERFACE_TAG));

        ResourceLocation id = recipeId(entry.kind(), entry.blockId());
        ShapelessRecipe recipe = new ShapelessRecipe(
                AppliedTaCZ.MODID + ":workbench_upgrade",
                CraftingBookCategory.MISC,
                createResult(entry.kind(), entry.blockId()),
                ingredients
        );
        return new RecipeHolder<>(id, recipe);
    }

    private static RecipeHolder<?> createAssemblerRecipe(AETaCZWorkbenchIndex.Entry entry) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(createWorkbenchIngredient(entry.baseWorkbench(), entry.blockId()));
        ingredients.add(Ingredient.of(AEBlocks.MOLECULAR_ASSEMBLER.asItem()));
        ingredients.add(Ingredient.of(AEBlocks.PATTERN_PROVIDER.asItem()));

        ResourceLocation id = assemblerRecipeId(entry.blockId());
        ShapelessRecipe recipe = new ShapelessRecipe(
                AppliedTaCZ.MODID + ":assembler_upgrade",
                CraftingBookCategory.MISC,
                entry.createAssemblerStack(),
                ingredients
        );
        return new RecipeHolder<>(id, recipe);
    }

    private static RecipeHolder<?> createExtendedAssemblerRecipe(AETaCZWorkbenchIndex.Entry entry) {
        Ingredient assembler = createWorkbenchIngredient(entry.kind().assemblerBlock().asItem(), entry.blockId());
        NonNullList<Ingredient> ingredients = NonNullList.withSize(9, assembler);
        ingredients.set(4, Ingredient.of(AEItems.CALCULATION_PROCESSOR.asItem()));
        var pattern = new ShapedRecipePattern(3, 3, ingredients, Optional.empty());
        return new RecipeHolder<>(extendedAssemblerRecipeId(entry),
                new ShapedRecipe(AppliedTaCZ.MODID + ":extended_assembler_upgrade",
                        CraftingBookCategory.MISC, pattern, entry.createExtendedAssemblerStack()));
    }

    private static Ingredient createWorkbenchIngredient(Item baseWorkbench, ResourceLocation blockId) {
        ItemStack stack = new ItemStack(baseWorkbench);
        if (stack.getItem() instanceof IBlock blockItem) {
            blockItem.setBlockId(stack, blockId);
        }
        Ingredient bound = DataComponentIngredient.of(false, stack);
        return usesDefaultBlockId(baseWorkbench, blockId)
                ? CompoundIngredient.of(DataComponentIngredient.of(true, new ItemStack(baseWorkbench)), bound)
                : bound;
    }

    private static boolean usesDefaultBlockId(Item baseWorkbench, ResourceLocation blockId) {
        if (baseWorkbench instanceof IBlock blockItem) {
            return blockId.equals(blockItem.getBlockId(new ItemStack(baseWorkbench)));
        }
        return true;
    }

    private static ItemStack createResult(WorkbenchUpgradeKind kind, ResourceLocation blockId) {
        ItemStack result = new ItemStack(kind.result());
        if (result.getItem() instanceof IBlock blockItem) {
            blockItem.setBlockId(result, blockId);
        }
        return result;
    }

    private static boolean isAppliedTaczWorkbenchUpgradeRecipe(ResourceLocation id) {
        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            if (id.equals(BuiltInRegistries.BLOCK.getKey(kind.extendedAssemblerBlock()))) {
                return true;
            }
        }
        return AppliedTaCZ.MODID.equals(id.getNamespace())
                && (id.getPath().startsWith(DYNAMIC_RECIPE_PREFIX)
                || id.getPath().startsWith(ASSEMBLER_RECIPE_PREFIX)
                || id.getPath().startsWith(EXTENDED_ASSEMBLER_RECIPE_PREFIX));
    }

    private static ResourceLocation recipeId(WorkbenchUpgradeKind kind, ResourceLocation blockId) {
        return id(DYNAMIC_RECIPE_PREFIX
                + BuiltInRegistries.BLOCK.getKey(kind.resultBlock()).getPath()
                + "/"
                + blockId.getNamespace()
                + "/"
                + blockId.getPath());
    }

    private static ResourceLocation assemblerRecipeId(ResourceLocation blockId) {
        return id(ASSEMBLER_RECIPE_PREFIX
                + BuiltInRegistries.BLOCK.getKey(WorkbenchUpgradeKind.assemblerBlockFor(blockId)).getPath()
                + "/"
                + blockId.getNamespace()
                + "/"
                + blockId.getPath());
    }

    private static ResourceLocation extendedAssemblerRecipeId(AETaCZWorkbenchIndex.Entry entry) {
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(entry.kind().extendedAssemblerBlock());
        return entry.blockId().equals(entry.kind().defaultBlockId()) ? block
                : id(EXTENDED_ASSEMBLER_RECIPE_PREFIX + block.getPath() + "/"
                        + entry.blockId().getNamespace() + "/" + entry.blockId().getPath());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, path);
    }
}
