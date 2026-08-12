package me.myogoo.appliedtacz.datagen;

import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class AppliedTaczDataGenerators {
    private AppliedTaczDataGenerators() {
    }

    public static void gatherData(GatherDataEvent event) {
        if (event.includeServer()) {
            var generator = event.getGenerator();
            var registries = event.getLookupProvider();
            var pack = generator.getVanillaPack(true);
            pack.addProvider(output -> new AppliedTaczRecipeProvider(output, registries));
        }
    }
}
