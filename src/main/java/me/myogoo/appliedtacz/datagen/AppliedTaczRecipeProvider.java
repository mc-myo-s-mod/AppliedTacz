package me.myogoo.appliedtacz.datagen;

import appeng.api.ids.AEPartIds;
import com.google.common.base.Preconditions;
import com.tacz.guns.api.DefaultAssets;
import me.myogoo.appliedtacz.init.AETaCZBlock;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.PartialNBTIngredient;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Consumer;

public class AppliedTaczRecipeProvider extends RecipeProvider {
    public AppliedTaczRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> consumer) {
        ItemLike storageBus = resolveItem(AEPartIds.STORAGE_BUS);

        // tacz:gun_smith_table has no BlockId NBT — plain item ingredient is fine
        buildWorkbenchUpgradeRecipe(consumer, AETaCZBlock.GUN_SMITH_TABLE.get(),
                Ingredient.of(resolveItem(DefaultAssets.DEFAULT_BLOCK_ID)),
                storageBus, "gun_smith_table");

        // tacz:workbench_a / workbench_c carry a BlockId NBT tag that identifies
        // which workbench type they represent, so we match on that NBT.
        buildWorkbenchUpgradeRecipe(consumer, AETaCZBlock.AMMO_ASSEMBLY_TABLE.get(),
                nbtIngredient(AETaCZWorkbenchIds.WORKBENCH_A_ID, AETaCZWorkbenchIds.AMMO_WORKBENCH_ID),
                storageBus, "ammo_workbench");

        buildWorkbenchUpgradeRecipe(consumer, AETaCZBlock.ATTACHMENT_TABLE.get(),
                nbtIngredient(AETaCZWorkbenchIds.WORKBENCH_C_ID, AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID),
                storageBus, "attachment_workbench");
    }

    /**
     * Creates a Forge {@link NBTIngredient} for a TaCZ workbench item that carries
     * {@code {"BlockId": "<blockId>"}} as its NBT data.
     *
     * @param itemId  the registry id of the workbench item (e.g. {@code tacz:workbench_a})
     * @param blockId the value to write into the {@code BlockId} NBT tag
     */
    private Ingredient nbtIngredient(ResourceLocation itemId, ResourceLocation blockId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        Preconditions.checkState(item != null, "Missing item for id %s", itemId);
        CompoundTag nbt = new CompoundTag();
        nbt.putString("BlockId", blockId.toString());
        return PartialNBTIngredient.of(item, nbt);
    }

    private void buildWorkbenchUpgradeRecipe(Consumer<FinishedRecipe> consumer, ItemLike result,
            Ingredient workbench, ItemLike storageBus, String recipeName) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, result)
                .requires(workbench)
                .requires(storageBus)
                .unlockedBy(getHasName(storageBus), has(storageBus))
                .save(consumer, new ResourceLocation("appliedtacz", recipeName));
    }

    private ItemLike resolveItem(ResourceLocation id) {
        Item item = ForgeRegistries.ITEMS.getValue(id);
        Preconditions.checkState(item != null, "Missing item for id %s", id);
        return item;
    }
}
