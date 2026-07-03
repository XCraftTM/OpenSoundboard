package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.Slider;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import de.xcrafttm.opensoundboard.ui.widgets.Toggle;
import net.minecraft.network.chat.Component;

/**
 * Temporary Phase 3 proof screen exercising the custom widget set (text field, slider, toggle,
 * button) through the version-neutral framework. Removed once the real screens land in Phase 4.
 */
public class DemoScreen extends OsbScreen {

    private static final int PW = 320;
    private static final int PH = 220;
    private int px;
    private int py;
    private boolean localOnly = false;
    private double volume = 0.75;

    public DemoScreen() {
        super(Component.literal("OpenSoundboard"));
    }

    @Override
    protected void buildUi() {
        px = (this.width - PW) / 2;
        py = (this.height - PH) / 2;
        int cx = px + Theme.PAD;
        int cw = PW - Theme.PAD * 2;

        add(new TextField().placeholder("Search sounds").onChange(s -> {
        })).bounds(cx, py + 46, cw, 18);

        add(new Slider(volume, v -> volume = v)
                .readout(v -> Component.literal("Volume " + (int) Math.round(v * 100) + "%")))
                .bounds(cx, py + 72, cw, 18);

        add(new Toggle(localOnly, v -> localOnly = v)).bounds(cx, py + 100, 34, 16);

        int bh = 20;
        add(new Button(Component.literal("Close"), b -> onClose()).secondary())
                .bounds(cx, py + PH - Theme.PAD - bh, cw, bh);
    }

    @Override
    protected void renderContent(UiCanvas c) {
        c.fillRect(0, 0, this.width, this.height, Theme.SCRIM);
        c.fillRect(px, py, PW, PH, Theme.PANEL);
        c.border(px, py, PW, PH, Theme.BORDER);
        c.fillRect(px, py, PW, 3, Theme.ACCENT);
        c.centeredText(Component.literal("OpenSoundboard"), this.width / 2, py + 14, Theme.TEXT);
        c.text("Custom UI - components preview", px + Theme.PAD, py + 30, Theme.TEXT_MUTED);
        c.text("Play locally only", cx() + 42, py + 102, Theme.TEXT);
    }

    private int cx() {
        return px + Theme.PAD;
    }
}
