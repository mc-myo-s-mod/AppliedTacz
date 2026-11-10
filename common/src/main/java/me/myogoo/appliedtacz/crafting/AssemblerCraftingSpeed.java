package me.myogoo.appliedtacz.crafting;

public final class AssemblerCraftingSpeed {
    private AssemblerCraftingSpeed() {
    }

    public static int craftTicks(boolean extended, int accelerationCards) {
        // AE2 / ExtendedAE progress per tick toward 100, at full power.
        int progress = switch (accelerationCards) {
            case 1 -> 13;
            case 2 -> 17;
            case 3 -> 20;
            case 4 -> 25;
            case 5 -> 50;
            default -> 10;
        };
        if (extended) {
            progress *= 2;
        }
        return (100 + progress - 1) / progress;
    }
}
