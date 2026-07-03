package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import net.minecraft.network.chat.Component;

/**
 * Temporary Phase 3 proof screen: renders a themed panel through the version-neutral
 * {@link UiCanvas} to validate the render abstraction across the 1.21.x / 26.x boundary.
 */
public class DemoScreen extends OsbScreen {

    public DemoScreen() {
        super(Component.literal("OpenSoundboard"));
    }

    @Override
    protected void drawUi(UiCanvas c) {
        int pw = 320;
        int ph = 200;
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        c.fillRect(0, 0, this.width, this.height, Theme.SCRIM);

        c.fillRect(px, py, pw, ph, Theme.PANEL);
        c.border(px, py, pw, ph, Theme.BORDER);
        c.fillRect(px, py, pw, 3, Theme.ACCENT);

        c.centeredText(Component.literal("OpenSoundboard"), this.width / 2, py + 16, Theme.TEXT);
        c.text("Custom UI - Phase 3 foundation", px + Theme.PAD, py + 38, Theme.TEXT_MUTED);

        int bx = px + Theme.PAD;
        int bw = pw - Theme.PAD * 2;
        int bh = 20;
        int by = py + ph - Theme.PAD - bh;
        boolean hover = c.hovered(bx, by, bw, bh);
        c.fillRect(bx, by, bw, bh, hover ? Theme.ACCENT_HOVER : Theme.ACCENT);
        c.centeredText(Component.literal("Deep indigo accent"), px + pw / 2, by + (bh - 8) / 2, Theme.TEXT_ON_ACCENT);
    }
}
