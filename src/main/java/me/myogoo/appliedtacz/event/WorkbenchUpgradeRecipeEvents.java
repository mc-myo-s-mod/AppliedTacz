package me.myogoo.appliedtacz.event;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = AppliedTaCZ.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorkbenchUpgradeRecipeEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TagKey<Item> INTERFACE_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("ae2", "interface"));
    private static final String DYNAMIC_RECIPE_PREFIX = "workbench_upgrade/";

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
        List<Recipe<?>> recipes = new ArrayList<>();
        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            if (!isAppliedTaczWorkbenchUpgradeRecipe(recipe.getId())) {
                recipes.add(recipe);
            }
        }

        List<Recipe<?>> generatedRecipes = createDynamicRecipes();
        recipes.addAll(generatedRecipes);
        recipeManager.replaceRecipes(recipes);
        LOGGER.info("Registered {} dynamic AppliedTaCZ workbench upgrade recipes", generatedRecipes.size());
    }

    private static List<Recipe<?>> createDynamicRecipes() {
        List<Recipe<?>> recipes = new ArrayList<>();

        for (var entry : AETaCZWorkbenchIndex.entries()) {
            recipes.add(createRecipe(entry));
        }

        return recipes;
    }

    private static Recipe<?> createRecipe(AETaCZWorkbenchIndex.Entry entry) {
        Item baseWorkbench = entry.baseWorkbench();
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(createWorkbenchIngredient(baseWorkbench, entry.blockId()));
        ingredients.add(Ingredient.of(INTERFACE_TAG));

        return new ShapelessRecipe(
                recipeId(entry.kind(), entry.blockId()),
                AppliedTaCZ.MODID + ":workbench_upgrade",
                CraftingBookCategory.MISC,
                createResult(entry.kind(), entry.blockId()),
                ingredients
        );
    }

    private static Ingredient createWorkbenchIngredient(Item baseWorkbench, ResourceLocation blockId) {
        if (usesDefaultBlockId(baseWorkbench, blockId)) {
            return Ingredient.of(baseWorkbench);
        }

        CompoundTag nbt = new CompoundTag();
        nbt.putString(BlockItemDataAccessor.BLOCK_ID, blockId.toString());
        return PartialNBTIngredient.of(baseWorkbench, nbt);
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
        return AppliedTaCZ.MODID.equals(id.getNamespace())
                && id.getPath().startsWith(DYNAMIC_RECIPE_PREFIX);
    }

    private static ResourceLocation recipeId(WorkbenchUpgradeKind kind, ResourceLocation blockId) {
        return id(DYNAMIC_RECIPE_PREFIX
                + BuiltInRegistries.BLOCK.getKey(kind.resultBlock()).getPath()
                + "/"
                + blockId.getNamespace()
                + "/"
                + blockId.getPath());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, path);
    }
}
