package me.myogoo.appliedtacz.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AppliedTaczClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue RENDER_WORKBENCH;
    private static final ModConfigSpec.BooleanValue ROTATE_WORKBENCH;
    private static final ModConfigSpec.BooleanValue COMPACT_LAYOUT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("assembler");
        RENDER_WORKBENCH = builder
                .comment("Render the internal TaCZ workbench and its orbital effects in compact assemblers.",
                        "The frame, glass and online prismatic lights remain visible when disabled.")
                .define("renderWorkbench", true);
        ROTATE_WORKBENCH = builder
                .comment("Rotate the internal TaCZ workbench around its X, Y and Z axes.",
                        "When disabled, the workbench keeps its default block-facing orientation.",
                        "Orbital effects and prismatic lights continue animating. Ignored when renderWorkbench is false.")
                .define("rotateWorkbench", true);
        builder.pop();

        builder.push("patternTerminal");
        COMPACT_LAYOUT = builder
                .comment("Use nine ME inventory columns instead of thirteen in the TaCZ pattern terminal.",
                        "The window width and recipe encoding area stay unchanged.")
                .define("compactLayout", true);
        builder.pop();

        SPEC = builder.build();
    }

    private AppliedTaczClientConfig() {
    }

    public static boolean renderWorkbench() {
        return RENDER_WORKBENCH.get();
    }

    public static boolean rotateWorkbench() {
        return ROTATE_WORKBENCH.get();
    }

    public static boolean compactPatternTerminal() {
        return COMPACT_LAYOUT.get();
    }

    public static void setCompactPatternTerminal(boolean compact) {
        COMPACT_LAYOUT.set(compact);
        COMPACT_LAYOUT.save();
    }
}
