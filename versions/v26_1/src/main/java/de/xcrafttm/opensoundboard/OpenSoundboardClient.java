package de.xcrafttm.opensoundboard;

import com.mojang.blaze3d.platform.InputConstants;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.screens.SoundboardScreen;
import de.xcrafttm.opensoundboard.screens.SoundWheelOverlay;
import de.xcrafttm.opensoundboard.tools.HoldableKeyBinding;
import de.xcrafttm.opensoundboard.tools.KeybindHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.File;

public class OpenSoundboardClient implements ClientModInitializer {

    public static final String MOD_ID = "opensoundboard";
    public final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
    public static final File soundDir = new File(FabricLoader.getInstance().getGameDir().toFile(), "opensoundboard");
    private static KeyMapping openSoundboardKey;
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

        openSoundboardKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.opensoundboard.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                KEY_CATEGORY
        ));

        wheelKey = (HoldableKeyBinding) KeyMappingHelper.registerKeyMapping(new HoldableKeyBinding(
                "key.opensoundboard.wheel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSoundboardKey.isDown()) {
                client.setScreen(new SoundboardScreen());
            }

            // Use raw GLFW state so we can detect key-held even while a screen is open
            long window = client.getWindow().handle();
            int keyCode = wheelKey.getBoundKeyCode();
            InputConstants.Type keyType = wheelKey.getBoundKeyType();
            boolean wheelHeld = keyCode != GLFW.GLFW_KEY_UNKNOWN && switch (keyType) {
                case MOUSE   -> GLFW.glfwGetMouseButton(window, keyCode) == GLFW.GLFW_PRESS;
                default      -> GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
            };

            // Wheel overlay: open when wheel keybind is held and no screen is open
            if (client.screen == null && wheelHeld) {
                client.setScreen((Screen) new SoundWheelOverlay());
            }

            // Wheel overlay: close when wheel keybind is released
            if (client.screen instanceof SoundWheelOverlay overlay && !wheelHeld) {
                overlay.playHoveredAndClose();
            }

            KeybindHandler.tick(client, soundDir);
        });
    }
}
