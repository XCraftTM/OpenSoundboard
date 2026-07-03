package de.xcrafttm.opensoundboard;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.KeybindHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Client entrypoint.
 *
 * <p>Phase 2 scope: config + per-sound keybind playback + voicechat audio. The per-sound
 * keybinds are polled directly from GLFW by {@link KeybindHandler}, so this class needs no
 * version-specific {@code KeyMapping}/{@code Identifier} code. The GUI keybinds (open board /
 * sound wheel) and their {@code KeyMapping} registration arrive with the custom UI in Phase 4.
 */
public class OpenSoundboardClient implements ClientModInitializer {

    public static final String MOD_ID = "opensoundboard";
    public static final Logger LOGGER = LoggerFactory.getLogger("OpenSoundboard");

    public static final File soundDir = new File(FabricLoader.getInstance().getGameDir().toFile(), "opensoundboard");

    @Override
    public void onInitializeClient() {
        if (!soundDir.exists()) {
            soundDir.mkdirs();
        }

        SoundboardConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> KeybindHandler.tick(client, soundDir));

        LOGGER.info("[OpenSoundboard] client initialized");
    }
}
