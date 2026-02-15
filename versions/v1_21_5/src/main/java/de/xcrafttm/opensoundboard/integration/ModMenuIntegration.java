package de.xcrafttm.opensoundboard.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.xcrafttm.opensoundboard.screens.SoundboardConfigScreen;
import net.minecraft.client.gui.screen.Screen;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::getConfigScreen;
    }

    public static Screen getConfigScreen(Screen parent) {
        // Custom owo-ui config screen.
        // This supports dynamic show/hide of dependent controls (e.g. syncGlobalVolume sliders)
        // without requiring a "refresh" button.
        return new SoundboardConfigScreen(parent);
    }
}
