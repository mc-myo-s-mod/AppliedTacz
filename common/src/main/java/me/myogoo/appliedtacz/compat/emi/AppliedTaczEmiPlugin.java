package me.myogoo.appliedtacz.compat.emi;

import com.tacz.guns.api.item.IBlock;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.Comparison;
import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.menu.TaCZPatternEncodingMenu;

@EmiEntrypoint
public class AppliedTaczEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // EMI skips JEI callbacks for mods with a native EMI plugin, including item subtypes.
        Comparison workbenchComparison = Comparison.compareData(emiStack -> {
            var stack = emiStack.getItemStack();
            return ((IBlock) stack.getItem()).getBlockId(stack);
        });
        for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
            registry.setDefaultComparison(kind.result().asItem(), workbenchComparison);
            registry.setDefaultComparison(kind.assemblerBlock().asItem(), workbenchComparison);
            registry.setDefaultComparison(kind.extendedAssemblerBlock().asItem(), workbenchComparison);
        }
        registry.addRecipeHandler(TaCZPatternEncodingMenu.TYPE, new TaCZPatternEmiHandler());
    }
}
