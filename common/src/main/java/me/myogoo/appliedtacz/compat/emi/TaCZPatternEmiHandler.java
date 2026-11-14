package me.myogoo.appliedtacz.compat.emi;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.Widget;
import me.myogoo.appliedtacz.crafting.TaCZPatternEncodingRecipes;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class TaCZPatternEmiHandler implements EmiRecipeHandler<TaCZPatternEncodingMenu> {
    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<TaCZPatternEncodingMenu> screen) {
        // Encoding only selects a recipe; it must not advertise immediate crafting.
        return new EmiPlayerInventory(List.of());
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return workbenchId(recipe) != null && EmiEncodingSupport.recipeId(recipe) != null;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<TaCZPatternEncodingMenu> context) {
        var workbench = workbenchId(recipe);
        var id = EmiEncodingSupport.recipeId(recipe);
        return context.getType() == EmiCraftContext.Type.FILL_BUTTON && workbench != null && id != null
                && context.getScreenHandler().canSelectRecipe(workbench.toString(), id.toString());
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<TaCZPatternEncodingMenu> context) {
        if (!canCraft(recipe, context)) {
            return false;
        }
        context.getScreenHandler().selectRecipe(workbenchId(recipe).toString(),
                EmiEncodingSupport.recipeId(recipe).toString());
        return true;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe,
                                                  EmiCraftContext<TaCZPatternEncodingMenu> context) {
        if (!canCraft(recipe, context)) {
            return List.of();
        }
        var craftables = craftableKeys(context.getScreenHandler());
        boolean hasCraftable = recipe.getInputs().stream().anyMatch(input -> isCraftable(craftables, input));
        return EmiEncodingSupport.encodingTooltip(hasCraftable).stream()
                .map(component -> ClientTooltipComponent.create(component.getVisualOrderText())).toList();
    }

    @Override
    public void render(EmiRecipe recipe, EmiCraftContext<TaCZPatternEncodingMenu> context,
                       List<Widget> widgets, GuiGraphics graphics) {
        var craftables = craftableKeys(context.getScreenHandler());
        for (var widget : widgets) {
            if (widget instanceof SlotWidget slot && slot.getRecipe() == null
                    && isCraftable(craftables, slot.getStack())) {
                var bounds = slot.getBounds();
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 400);
                graphics.fill(bounds.x() + 1, bounds.y() + 1, bounds.right() - 1, bounds.bottom() - 1,
                        EmiEncodingSupport.BLUE_SLOT_HIGHLIGHT_COLOR);
                graphics.pose().popPose();
            }
        }
    }

    private static ResourceLocation workbenchId(EmiRecipe recipe) {
        // Match TaCZ's category construction, never reverse its lossy ':' -> '_' mapping.
        var category = recipe.getCategory().getId();
        return TaCZPatternEncodingRecipes.getWorkbenches().stream()
                .filter(id -> category.getNamespace().equals("tacz")
                        && category.getPath().equals("gun_smith_table/" + id.toString().replace(':', '_')))
                .findFirst().orElse(null);
    }

    private static Set<AEKey> craftableKeys(TaCZPatternEncodingMenu menu) {
        var repo = menu.getClientRepo();
        return repo == null ? Set.of() : repo.getAllEntries().stream()
                .filter(GridInventoryEntry::isCraftable)
                .map(GridInventoryEntry::getWhat).collect(Collectors.toSet());
    }

    private static boolean isCraftable(Set<AEKey> craftables, EmiIngredient ingredient) {
        return ingredient.getEmiStacks().stream().anyMatch(stack -> {
            var key = AEItemKey.of(stack.getItemStack());
            return key != null && craftables.contains(key);
        });
    }
}
