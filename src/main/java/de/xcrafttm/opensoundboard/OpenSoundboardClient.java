package de.xcrafttm.opensoundboard;

import com.mojang.blaze3d.platform.InputConstants;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.platform.PlatformBootstrap;
import de.xcrafttm.opensoundboard.screens.SoundWheelOverlay;
import de.xcrafttm.opensoundboard.screens.SoundboardScreen;
import de.xcrafttm.opensoundboard.tools.KeybindHandler;
import de.xcrafttm.opensoundboard.tools.McCompat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Client entrypoint: config, per-sound keybind playback (via {@link KeybindHandler}), and the two
 * rebindable GUI keybinds — open soundboard, and hold-to-open sound wheel. The wheel key is polled
 * directly from GLFW so it can be detected while the overlay screen is open.
 */
public class OpenSoundboardClient implements ClientModInitializer {

    public static final String MOD_ID = "opensoundboard";
    public static final Logger LOGGER = LoggerFactory.getLogger("OpenSoundboard");

    public static final File soundDir = new File(FabricLoader.getInstance().getGameDir().toFile(), "opensoundboard");

    private static KeyMapping openKey;
    private static KeyMapping wheelKey;

    @Override
    public void onInitializeClient() {
        PlatformBootstrap.setClient(() -> FabricLoader.getInstance().getConfigDir().toFile());

        if (!soundDir.exists()) {
            soundDir.mkdirs();
        }

        SoundboardConfig.load();

        KeyMapping openKm;
        KeyMapping wheelKm;
        // Keybind category: a String before 1.21.11, a registered Category (Identifier) after.
        //? if >=1.21.11 {
        KeyMapping.Category category = KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "general"));
        openKm = new KeyMapping("key.opensoundboard.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, category);
        wheelKm = new KeyMapping("key.opensoundboard.wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category);
        //?} else {
        /*openKm = new KeyMapping("key.opensoundboard.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.opensoundboard.general");
        wheelKm = new KeyMapping("key.opensoundboard.wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.opensoundboard.general");
        *///?}

        // Fabric renamed KeyBindingHelper -> KeyMappingHelper for the 26.x API.
        //? if >=26 {
        /*openKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(openKm);
        wheelKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(wheelKm);
        *///?} else {
        openKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(openKm);
        wheelKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(wheelKm);
        //?}

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KeybindHandler.tick(client, soundDir);

            while (openKey.consumeClick()) {
                client.setScreen(new SoundboardScreen());
            }

            // Poll the wheel key's bound key directly so we can detect hold while the overlay is open.
            long window = McCompat.windowHandle(client);
            InputConstants.Key bound = InputConstants.getKey(wheelKey.saveString());
            int wheelCode = bound.getValue();
            boolean wheelHeld = wheelCode != GLFW.GLFW_KEY_UNKNOWN
                    && (bound.getType() == InputConstants.Type.MOUSE
                    ? GLFW.glfwGetMouseButton(window, wheelCode) == GLFW.GLFW_PRESS
                    : GLFW.glfwGetKey(window, wheelCode) == GLFW.GLFW_PRESS);

            if (client.screen == null && wheelHeld) {
                client.setScreen(new SoundWheelOverlay());
            } else if (client.screen instanceof SoundWheelOverlay overlay && !wheelHeld) {
                overlay.playHoveredAndClose();
            }
        });

        LOGGER.info("[OpenSoundboard] client initialized");
    }
}
