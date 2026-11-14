package me.myogoo.appliedtacz.init;

import me.myogoo.appliedtacz.crafting.WorkbenchUpgradeKind;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

public class AETaCZCreativeTab {
    private static final ResourceKey<CreativeModeTab> TACZ_OTHER_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("tacz", "other")
    );

    public static void addToTaczTab(BuildCreativeModeTabContentsEvent event) {
        if (TACZ_OTHER_TAB.equals(event.getTabKey())) {
            addAppliedWorkbenches(event);
        }
    }

    private static void addAppliedWorkbenches(CreativeModeTab.Output output) {
        output.accept(AETaCZBlock.TACZ_PATTERN_ENCODING_TERMINAL.get());
        var entries = AETaCZWorkbenchIndex.entries();
        if (entries.isEmpty()) {
            for (WorkbenchUpgradeKind kind : WorkbenchUpgradeKind.values()) {
                output.accept(kind.result());
                output.accept(kind.assemblerResult());
                output.accept(kind.extendedAssemblerBlock());
            }
            return;
        }
        entries.forEach(entry -> {
            output.accept(entry.createAppliedStack());
            output.accept(entry.createAssemblerStack());
            output.accept(entry.createExtendedAssemblerStack());
        });
    }
}
