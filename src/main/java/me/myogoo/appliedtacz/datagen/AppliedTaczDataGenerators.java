package me.myogoo.appliedtacz.datagen;

import me.myogoo.appliedtacz.AppliedTaCZ;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AppliedTaCZ.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AppliedTaczDataGenerators {
    private AppliedTaczDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(),
                new AppliedTaczRecipeProvider(event.getGenerator().getPackOutput()));
    }
}
