package me.myogoo.appliedtacz.init;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;

public class AETaCZCreativeTab {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AppliedTaCZ.MODID);
    private static final ResourceKey<CreativeModeTab> TACZ_OTHER_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            ResourceLocation.fromNamespaceAndPath("tacz", "other")
    );

    static {
        REGISTER.register("tacz", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.appliedtacz"))
                .icon(() -> AETaCZBlock.GUN_SMITH_TABLE.get().asItem().getDefaultInstance())
                .displayItems((params, output) -> {
                    addAppliedWorkbenches(output);
                })
                .build());
    }

    public static void addToTaczTab(BuildCreativeModeTabContentsEvent event) {
        if (TACZ_OTHER_TAB.equals(event.getTabKey())) {
            addAppliedWorkbenches(event);
        }
    }

    private static void addAppliedWorkbenches(CreativeModeTab.Output output) {
        AETaCZWorkbenchIndex.entries()
                .forEach(entry -> output.accept(entry.createAppliedStack()));
    }
}
