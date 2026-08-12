package me.myogoo.appliedtacz.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class MousePositionRestorer {
    private static final int SCREEN_TRANSITION_RESTORE_FRAMES = 3;
    private static final int RETURN_TO_MAIN_MENU_RESTORE_FRAMES = 8;

    private static double pendingX;
    private static double pendingY;
    private static int pendingRestores;

    private static double returnX;
    private static double returnY;
    private static int returnRestores;

    private MousePositionRestorer() {
    }

    public static void rememberCurrentPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        pendingX = minecraft.mouseHandler.xpos();
        pendingY = minecraft.mouseHandler.ypos();
        pendingRestores = SCREEN_TRANSITION_RESTORE_FRAMES;
        rememberCurrentPositionForReturnToMainMenu(minecraft);
    }

    public static void rememberCurrentPositionForReturnToMainMenu() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        rememberCurrentPositionForReturnToMainMenu(minecraft);
    }

    private static void rememberCurrentPositionForReturnToMainMenu(Minecraft minecraft) {
        returnX = minecraft.mouseHandler.xpos();
        returnY = minecraft.mouseHandler.ypos();
        returnRestores = RETURN_TO_MAIN_MENU_RESTORE_FRAMES;
    }

    public static void restoreIfPending() {
        if (pendingRestores <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            pendingRestores = 0;
            return;
        }

        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), pendingX, pendingY);
        pendingRestores--;
    }

    public static void restoreReturnToMainMenuIfPending() {
        if (returnRestores <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            returnRestores = 0;
            return;
        }

        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), returnX, returnY);
        returnRestores--;
    }
}
