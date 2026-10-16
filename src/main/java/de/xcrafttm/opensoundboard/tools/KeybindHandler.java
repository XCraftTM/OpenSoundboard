package de.xcrafttm.opensoundboard.tools;

import com.mojang.blaze3d.platform.InputConstants;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Polls raw key state (see {@link Keys}) for the per-sound keybinds configured by the user and triggers
 * playback. Version differences in key polling are hidden in {@link Keys}.
 */
public final class KeybindHandler {

    private static final Set<String> heldKeybinds = new HashSet<>();

    private KeybindHandler() {
    }

    public static void tick(Minecraft client) {
        if (McCompat.screen(client) != null || client.getWindow() == null) {
            heldKeybinds.clear();
            return;
        }

        for (Map.Entry<String, SoundboardConfig.SoundData> entry : SoundboardConfig.sounds().entrySet()) {
            SoundboardConfig.KeyBind keybind = entry.getValue().getKeybind();
            if (keybind == null) continue;

            InputConstants.Key key = Keys.boundKey(keybind);
            String name = entry.getKey();
            if (key == InputConstants.UNKNOWN || !Keys.isDown(key) || !modifiersMatch(keybind)) {
                heldKeybinds.remove(name);
                continue;
            }
            if (!heldKeybinds.add(name)) continue; // still held from the last press

            File file = SoundLibrary.find(name);
            if (file != null) SoundboardAudioSystem.trigger(file);
        }
    }

    private static boolean modifiersMatch(SoundboardConfig.KeyBind bind) {
        boolean ctrl = bind.isLegacy() ? (bind.getModifiers() & Keys.MOD_CONTROL) != 0 : bind.isCtrl();
        boolean shift = bind.isLegacy() ? (bind.getModifiers() & Keys.MOD_SHIFT) != 0 : bind.isShift();
        boolean alt = bind.isLegacy() ? (bind.getModifiers() & Keys.MOD_ALT) != 0 : bind.isAlt();
        return ctrl == Keys.controlDown() && shift == Keys.shiftDown() && alt == Keys.altDown();
    }
}
