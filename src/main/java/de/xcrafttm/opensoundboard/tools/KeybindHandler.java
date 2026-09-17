package de.xcrafttm.opensoundboard.tools;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Polls raw key state (see {@link Keys}) for the per-sound keybinds configured by the user and triggers
 * playback. Version differences in key polling are hidden in {@link Keys}.
 */
public final class KeybindHandler {

    private static final Set<String> heldKeybinds = new HashSet<>();

    private KeybindHandler() {
    }

    public static void tick(Minecraft client, File soundDir) {
        if (McCompat.screen(client) != null || client.getWindow() == null) {
            heldKeybinds.clear();
            return;
        }

        for (var entry : SoundboardConfig.sounds().entrySet()) {
            SoundboardConfig.KeyBind keybind = entry.getValue().getKeybind();
            if (keybind == null) continue;

            boolean keyDown = Keys.isKeyDown(keybind.getKeyCode());
            boolean modsMatch = modifiersMatch(keybind.getModifiers());

            String name = entry.getKey();

            if (keyDown && modsMatch) {
                if (!heldKeybinds.contains(name)) {
                    heldKeybinds.add(name);
                    File file = SoundLibrary.find(name);
                    if (file != null) {
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

    private static boolean modifiersMatch(int expected) {
        boolean ctrlExpected = (expected & Keys.MOD_CONTROL) != 0;
        boolean shiftExpected = (expected & Keys.MOD_SHIFT) != 0;
        boolean altExpected = (expected & Keys.MOD_ALT) != 0;
        return ctrlExpected == Keys.controlDown() && shiftExpected == Keys.shiftDown() && altExpected == Keys.altDown();
    }
}
