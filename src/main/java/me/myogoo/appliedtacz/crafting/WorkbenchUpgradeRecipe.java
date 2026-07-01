package me.myogoo.appliedtacz.crafting;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class WorkbenchUpgradeRecipe extends CustomRecipe {
    private static final ResourceLocation STORAGE_BUS_ID = ResourceLocation.fromNamespaceAndPath("ae2", "storage_bus");

    private final WorkbenchUpgradeKind kind;

    public WorkbenchUpgradeRecipe(CraftingBookCategory category, WorkbenchUpgradeKind kind) {
        super(category);
        this.kind = kind;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findMatch(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider provider) {
        Match match = findMatch(input);
        if (match == null) {
            return ItemStack.EMPTY;
        }
        return createResult(match.blockId());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider provider) {
        return createResult(kind.defaultBlockId());
    }

    @Override
    public boolean isIncomplete() {
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return kind.serializer();
    }

    private ItemStack createResult(ResourceLocation blockId) {
        ItemStack result = new ItemStack(kind.result());
        if (result.getItem() instanceof IBlock blockItem) {
            blockItem.setBlockId(result, blockId);
        }
        return result;
    }

    private @Nullable Match findMatch(CraftingInput input) {
        Match table = null;
        boolean hasStorageBus = false;

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (isStorageBus(stack)) {
                if (hasStorageBus) {
                    return null;
                }
                hasStorageBus = true;
                continue;
            }

            Match match = getTableMatch(stack);
            if (match == null || table != null) {
                return null;
            }
            table = match;
        }

        return hasStorageBus ? table : null;
    }

    private boolean isStorageBus(ItemStack stack) {
        return STORAGE_BUS_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    private @Nullable Match getTableMatch(ItemStack stack) {
        ResourceLocation baseWorkbenchId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!kind.acceptsBaseWorkbench(baseWorkbenchId)) {
            return null;
        }

        ResourceLocation blockId = getBlockId(stack, baseWorkbenchId);
        if (!isKnownWorkbench(blockId, baseWorkbenchId)) {
            return null;
        }
        return new Match(blockId);
    }

    private ResourceLocation getBlockId(ItemStack stack, ResourceLocation baseWorkbenchId) {
        if (stack.getItem() instanceof BlockItemDataAccessor accessor) {
            ResourceLocation blockId = accessor.getBlockId(stack);
            if (!DefaultAssets.EMPTY_BLOCK_ID.equals(blockId)) {
                return blockId;
            }
        }
        return getDefaultBlockId(baseWorkbenchId);
    }

    private ResourceLocation getDefaultBlockId(ResourceLocation baseWorkbenchId) {
        if (AETaCZWorkbenchIds.WORKBENCH_A_ID.equals(baseWorkbenchId)) {
            return AETaCZWorkbenchIds.AMMO_WORKBENCH_ID;
        }
        if (AETaCZWorkbenchIds.WORKBENCH_C_ID.equals(baseWorkbenchId)) {
            return AETaCZWorkbenchIds.ATTACHMENT_WORKBENCH_ID;
        }
        return DefaultAssets.DEFAULT_BLOCK_ID;
    }

    private boolean isKnownWorkbench(ResourceLocation blockId, ResourceLocation baseWorkbenchId) {
        return TimelessAPI.getCommonBlockIndex(blockId)
                .map(index -> index.getPojo().getId())
                .filter(baseWorkbenchId::equals)
                .filter(kind::acceptsBaseWorkbench)
                .isPresent();
    }

    private record Match(ResourceLocation blockId) {
    }
}
