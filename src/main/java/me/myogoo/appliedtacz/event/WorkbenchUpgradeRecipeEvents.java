package me.myogoo.appliedtacz.event;

import com.mojang.logging.LogUtils;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Mod.EventBusSubscriber(modid = AppliedTaCZ.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WorkbenchUpgradeRecipeEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation STORAGE_BUS_ID = ResourceLocation.fromNamespaceAndPath("ae2", "storage_bus");
    private static final String DYNAMIC_RECIPE_PREFIX = "workbench_upgrade/";
    private static final Set<ResourceLocation> STATIC_UPGRADE_RECIPE_IDS = Set.of(
            id("gun_smith_table"),
            id("ammo_workbench"),
            id("attachment_workbench")
    );

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
        Item storageBus = BuiltInRegistries.ITEM.get(STORAGE_BUS_ID);
        if (storageBus == Items.AIR) {
            LOGGER.warn("Skipped AppliedTaCZ workbench upgrade recipe generation because {} is missing",
                    STORAGE_BUS_ID);
            return List.of();
        }

        List<Recipe<?>> recipes = new ArrayList<>();
        Set<ResourceLocation> generatedBlockIds = new HashSet<>();

        var blockEntries = TimelessAPI.getAllCommonBlockIndex().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .toList();

        for (var entry : blockEntries) {
            ResourceLocation blockId = entry.getKey();
            ResourceLocation baseWorkbenchId = entry.getValue().getPojo().getId();
            WorkbenchUpgradeKind kind = WorkbenchUpgradeKind.fromBaseWorkbench(baseWorkbenchId);
            if (kind == null) {
                continue;
            }

            Recipe<?> recipe = createRecipe(kind, baseWorkbenchId, blockId, storageBus);
            if (recipe != null) {
                recipes.add(recipe);
                generatedBlockIds.add(blockId);
            }
        }

        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            if (generatedBlockIds.add(kind.defaultBlockId())) {
                Recipe<?> recipe = createRecipe(kind, kind.defaultBaseWorkbenchId(), kind.defaultBlockId(), storageBus);
                if (recipe != null) {
                    recipes.add(recipe);
                }
            }
        }

        return recipes;
    }

    private static Recipe<?> createRecipe(WorkbenchUpgradeKind kind, ResourceLocation baseWorkbenchId,
            ResourceLocation blockId, Item storageBus) {
        Item baseWorkbench = BuiltInRegistries.ITEM.get(baseWorkbenchId);
        if (baseWorkbench == Items.AIR) {
            LOGGER.warn("Skipped AppliedTaCZ workbench upgrade recipe for {} because base item {} is missing",
                    blockId, baseWorkbenchId);
            return null;
        }

        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(createWorkbenchIngredient(baseWorkbench, baseWorkbenchId, blockId));
        ingredients.add(Ingredient.of(storageBus));

        return new ShapelessRecipe(
                recipeId(kind, blockId),
                AppliedTaCZ.MODID + ":workbench_upgrade",
                CraftingBookCategory.MISC,
                createResult(kind, blockId),
                ingredients
        );
    }

    private static Ingredient createWorkbenchIngredient(Item baseWorkbench, ResourceLocation baseWorkbenchId,
            ResourceLocation blockId) {
        if (DefaultAssets.DEFAULT_BLOCK_ID.equals(baseWorkbenchId)
                && DefaultAssets.DEFAULT_BLOCK_ID.equals(blockId)) {
            return Ingredient.of(baseWorkbench);
        }

        CompoundTag nbt = new CompoundTag();
        nbt.putString(BlockItemDataAccessor.BLOCK_ID, blockId.toString());
        return PartialNBTIngredient.of(baseWorkbench, nbt);
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
                && (STATIC_UPGRADE_RECIPE_IDS.contains(id) || id.getPath().startsWith(DYNAMIC_RECIPE_PREFIX));
    }

    private static ResourceLocation recipeId(WorkbenchUpgradeKind kind, ResourceLocation blockId) {
        return id(DYNAMIC_RECIPE_PREFIX
                + kind.name().toLowerCase(Locale.ROOT)
                + "/"
                + blockId.getNamespace()
                + "/"
                + blockId.getPath());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AppliedTaCZ.MODID, path);
    }
}
