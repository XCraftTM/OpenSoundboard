package de.xcrafttm.opensoundboard.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Plays the vanilla UI click. Mojang-mapped and identical across the whole version span. */
public final class UiSound {

    private UiSound() {
    }

    public static void click() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        }
    }
}
