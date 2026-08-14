package me.myogoo.appliedtacz.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class AppliedTaczServerConfig {
    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue INGREDIENT_COUNT_UPDATE_INTERVAL_TICKS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("gunSmithTable");
        INGREDIENT_COUNT_UPDATE_INTERVAL_TICKS = builder
                .comment(
                        "Server tick interval for recalculating watched gun smith table ingredient counts.",
                        "Lower values update faster but use more server CPU. Valid range: 1-20 ticks."
                )
                .defineInRange("ingredientCountUpdateIntervalTicks", 10, 1, 20);
        builder.pop();

        SPEC = builder.build();
    }

    private AppliedTaczServerConfig() {
    }

    public static int ingredientCountUpdateIntervalTicks() {
        return INGREDIENT_COUNT_UPDATE_INTERVAL_TICKS.get();
    }
}
