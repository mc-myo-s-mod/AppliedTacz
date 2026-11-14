package me.myogoo.appliedtacz.event;

import appeng.core.definitions.AEItems;
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
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.common.crafting.CompoundIngredient;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
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
    private static final String ASSEMBLER_RECIPE_PREFIX = "assembler_upgrade/";
    private static final String EXTENDED_ASSEMBLER_RECIPE_PREFIX = "extended_assembler_upgrade/";
    private static final ResourceLocation AE2_MOLECULAR_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("ae2", "molecular_assembler");
    private static final ResourceLocation AE2_PATTERN_PROVIDER =
            ResourceLocation.fromNamespaceAndPath("ae2", "pattern_provider");

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
            recipes.add(createAssemblerRecipe(entry));
            recipes.add(createExtendedAssemblerRecipe(entry));
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

    private static Recipe<?> createAssemblerRecipe(AETaCZWorkbenchIndex.Entry entry) {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(createWorkbenchIngredient(entry.baseWorkbench(), entry.blockId()));
        ingredients.add(Ingredient.of(BuiltInRegistries.ITEM.get(AE2_MOLECULAR_ASSEMBLER)));
        ingredients.add(Ingredient.of(BuiltInRegistries.ITEM.get(AE2_PATTERN_PROVIDER)));

        return new ShapelessRecipe(
                assemblerRecipeId(entry.blockId()),
                AppliedTaCZ.MODID + ":assembler_upgrade",
                CraftingBookCategory.MISC,
                entry.createAssemblerStack(),
                ingredients
        );
    }

    private static Recipe<?> createExtendedAssemblerRecipe(AETaCZWorkbenchIndex.Entry entry) {
        Ingredient assembler = createWorkbenchIngredient(entry.kind().assemblerBlock().asItem(), entry.blockId());
        NonNullList<Ingredient> ingredients = NonNullList.withSize(9, assembler);
        ingredients.set(4, Ingredient.of(AEItems.CALCULATION_PROCESSOR.asItem()));
        return new ShapedRecipe(extendedAssemblerRecipeId(entry), AppliedTaCZ.MODID + ":extended_assembler_upgrade",
                CraftingBookCategory.MISC, 3, 3, ingredients, entry.createExtendedAssemblerStack());
    }

    private static Ingredient createWorkbenchIngredient(Item baseWorkbench, ResourceLocation blockId) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString(BlockItemDataAccessor.BLOCK_ID, blockId.toString());
        Ingredient bound = PartialNBTIngredient.of(baseWorkbench, nbt);
        ItemStack emptyTagStack = new ItemStack(baseWorkbench);
        emptyTagStack.setTag(new CompoundTag());
        // TaCZ's BlockId accessor creates an empty tag even when reading an unbound default stack.
        return usesDefaultBlockId(baseWorkbench, blockId)
                ? CompoundIngredient.of(StrictNBTIngredient.of(new ItemStack(baseWorkbench)),
                        StrictNBTIngredient.of(emptyTagStack), bound)
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
