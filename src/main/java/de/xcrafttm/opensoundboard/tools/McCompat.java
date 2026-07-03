package de.xcrafttm.opensoundboard.tools;

import net.minecraft.client.Minecraft;

/**
 * Small cross-version helpers that hide Mojang-mapping renames, so the rest of the code
 * stays free of Stonecutter conditionals.
 */
public final class McCompat {

    private McCompat() {
    }

    /** GLFW window handle. {@code Window.getWindow()} was renamed to {@code handle()} after 1.21.1. */
    public static long windowHandle(Minecraft client) {
        //? if >=1.21.2 {
        return client.getWindow().handle();
        //?} else {
        /*return client.getWindow().getWindow();
        *///?}
    }

    /** Open a folder in the OS file explorer. Util moved from net.minecraft.Util to net.minecraft.util.Util. */
    public static void openFolder(java.io.File folder) {
        //? if >=1.21.2 {
        net.minecraft.util.Util.getPlatform().openFile(folder);
        //?} else {
        /*net.minecraft.Util.getPlatform().openFile(folder);
        *///?}
    }
}
