package de.xcrafttm.opensoundboard.tools;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Polls raw GLFW state for the per-sound keybinds configured by the user and triggers
 * playback. Fully Mojang-mapped; the one window-handle rename is hidden in {@link McCompat}.
 */
public final class KeybindHandler {

    private static final Set<String> heldKeybinds = new HashSet<>();

    private KeybindHandler() {
    }

    public static void tick(Minecraft client, File soundDir) {
        if (client.screen != null || client.getWindow() == null) {
            heldKeybinds.clear();
            return;
        }

        long window = McCompat.windowHandle(client);

        for (var entry : SoundboardConfig.sounds().entrySet()) {
            SoundboardConfig.KeyBind keybind = entry.getValue().getKeybind();
            if (keybind == null) continue;

            boolean keyDown = GLFW.glfwGetKey(window, keybind.getKeyCode()) == GLFW.GLFW_PRESS;
            boolean modsMatch = modifiersMatch(window, keybind.getModifiers());

            String name = entry.getKey();

            if (keyDown && modsMatch) {
                if (!heldKeybinds.contains(name)) {
                    heldKeybinds.add(name);
                    File file = new File(soundDir, name);
                    if (file.exists()) {
                        SoundboardConfig.SoundData data = entry.getValue();
                        String mode = SoundboardConfig.data.getKeybindMode();
                        boolean isPlaying = SoundboardAudioSystem.isPlaying(name);

                        switch (mode) {
                            case "pause_resume" -> {
                                if (isPlaying) {
                                    if (SoundboardAudioSystem.isPaused(name)) {
                                        SoundboardAudioSystem.resume(name);
                                    } else {
                                        SoundboardAudioSystem.pause(name);
                                    }
                                } else {
                                    SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                                }
                            }
                            case "play_restart" -> {
                                if (isPlaying) {
                                    SoundboardAudioSystem.stop(name);
                                }
                                SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                            }
                            default -> { // "play_stop"
                                if (isPlaying) {
                                    SoundboardAudioSystem.stop(name);
                                } else {
                                    SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
                                }
                            }
                        }
                    }
                }
            } else {
                heldKeybinds.remove(name);
            }
        }
    }

    private static boolean modifiersMatch(long window, int expected) {
        boolean ctrlExpected = (expected & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shiftExpected = (expected & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean altExpected = (expected & GLFW.GLFW_MOD_ALT) != 0;

        boolean ctrlDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shiftDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean altDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

        return ctrlExpected == ctrlDown && shiftExpected == shiftDown && altExpected == altDown;
    }
}
