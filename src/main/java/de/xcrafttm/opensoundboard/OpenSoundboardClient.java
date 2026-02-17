package de.xcrafttm.opensoundboard;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.screens.SoundboardScreen;
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
    public static final File soundDir = new File(FabricLoader.getInstance().getGameDir().toFile(), "opensoundboard");
    private static KeyBinding openSoundboardKey;

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
                KeyBinding.Category.create(Identifier.of("opensoundboard", "general"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSoundboardKey.wasPressed()) {
                client.setScreen(new SoundboardScreen());
            }
            KeybindHandler.tick(client, soundDir);
        });
    }
}
