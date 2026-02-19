package de.xcrafttm.opensoundboard;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.screens.SoundboardScreen;
import de.xcrafttm.opensoundboard.screens.SoundWheelOverlay;
import de.xcrafttm.opensoundboard.tools.HoldableKeyBinding;
import de.xcrafttm.opensoundboard.tools.KeybindHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.File;

public class OpenSoundboardClient implements ClientModInitializer {

    public static final String MOD_ID = "opensoundboard";
    public final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(Identifier.of(MOD_ID, "general"));
    public static final File soundDir = new File(FabricLoader.getInstance().getGameDir().toFile(), "opensoundboard");
    private static KeyBinding openSoundboardKey;
    private static HoldableKeyBinding wheelKey;

    public static HoldableKeyBinding getWheelKey() {
        return wheelKey;
    }

    @Override
    public void onInitializeClient() {
        if (!soundDir.exists()) {
            soundDir.mkdirs();
        }

        SoundboardConfig.load();

        openSoundboardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.opensoundboard.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                KEY_CATEGORY
        ));

        wheelKey = (HoldableKeyBinding) KeyBindingHelper.registerKeyBinding(new HoldableKeyBinding(
                "key.opensoundboard.wheel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSoundboardKey.wasPressed()) {
                client.setScreen(new SoundboardScreen());
            }

            // Use raw GLFW state so we can detect key-held even while a screen is open
            long window = client.getWindow().getHandle();
            int keyCode = wheelKey.getBoundKeyCode();
            boolean wheelHeld = keyCode != GLFW.GLFW_KEY_UNKNOWN
                    && GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;

            // Wheel overlay: open when wheel keybind is held and no screen is open
            if (client.currentScreen == null && wheelHeld) {
                client.setScreen(new SoundWheelOverlay());
            }

            // Wheel overlay: close when wheel keybind is released
            if (client.currentScreen instanceof SoundWheelOverlay overlay && !wheelHeld) {
                overlay.playHoveredAndClose();
            }

            KeybindHandler.tick(client, soundDir);
        });
    }
}
