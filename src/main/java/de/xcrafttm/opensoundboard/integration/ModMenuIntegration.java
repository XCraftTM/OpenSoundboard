package de.xcrafttm.opensoundboard.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import de.xcrafttm.opensoundboard.screens.SoundboardConfigScreen;

/** Exposes the OpenSoundboard configuration screen through the optional Mod Menu mod. */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SoundboardConfigScreen::new;
    }
}
