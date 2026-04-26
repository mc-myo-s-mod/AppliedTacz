package me.myogoo.appliedtacz.init;

import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIndex;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;

public class AETaCZCreativeTab {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AppliedTaCZ.MODID);

    static {
        REGISTER.register("tacz", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.appliedtacz"))
                .icon(() -> AETaCZBlock.GUN_SMITH_TABLE.get().asItem().getDefaultInstance())
                .displayItems((params, output) -> {
                    AETaCZWorkbenchIndex.entries()
                            .forEach(entry -> output.accept(entry.createAppliedStack()));
                })
                .build());
    }
}
