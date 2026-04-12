package me.myogoo.appliedtacz.init;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import me.myogoo.appliedtacz.AppliedTaCZ;
import me.myogoo.appliedtacz.item.AETaCZTableItem;
import me.myogoo.appliedtacz.util.AETaCZWorkbenchIds;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;

import java.util.Comparator;

public class AETaCZCreativeTab {
    public static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AppliedTaCZ.MODID);

    static {
        REGISTER.register("tacz", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.appliedtacz"))
                .icon(() -> AETaCZBlock.GUN_SMITH_TABLE.get().asItem().getDefaultInstance())
                .displayItems((params, output) -> {
                    TimelessAPI.getAllCommonBlockIndex().stream()
                            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                            .forEach(entry -> output.accept(BlockItemBuilder
                                    .create(AETaCZWorkbenchIds
                                            .getBlockForBaseWorkbench(entry.getValue().getPojo().getId()))
                                    .setId(entry.getKey())
                                    .build()));
                })
                .build());
    }
}
