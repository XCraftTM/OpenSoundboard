package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import de.xcrafttm.opensoundboard.ui.widgets.Slider;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import de.xcrafttm.opensoundboard.ui.widgets.Toggle;
import net.minecraft.network.chat.Component;

/**
 * Temporary Phase 3 proof screen exercising the full widget set at real-screen proportions
 * (~90% of screen height). Removed once the real screens land in Phase 4.
 */
public class DemoScreen extends OsbScreen {

    private int px;
    private int py;
    private int pw;
    private int ph;
    private boolean localOnly = false;
    private double volume = 0.75;

    public DemoScreen() {
        super(Component.literal("OpenSoundboard"));
    }

    @Override
    protected void buildUi() {
        ph = (int) (this.height * 0.9);
        pw = Math.max(360, Math.min(560, (int) (this.width * 0.7)));
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        int cx = px + Theme.PAD;
        int cw = pw - Theme.PAD * 2;

        int searchY = py + 32;
        add(new TextField().placeholder("Search sounds").onChange(s -> {
        })).bounds(cx, searchY, cw, 20);

        int closeY = py + ph - Theme.PAD - 22;
        int toggleY = closeY - 6 - 16;
        int sliderY = toggleY - 6 - 20;
        int listY = searchY + 26;
        int listH = sliderY - 8 - listY;

        ScrollList list = add(new ScrollList().gap(2));
        list.bounds(cx, listY, cw, listH);
        for (int i = 0; i < 20; i++) {
            final int idx = i;
            list.addRow(new ScrollList.Row() {
                @Override
                public int height() {
                    return 18;
                }

                @Override
                public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                    boolean fav = idx % 4 == 0;
                    if (hovered) c.fillRoundRect(rx, ry, rw, 18, Theme.ROW);
                    c.text(fav ? "★" : "☆", rx + 5, ry + 5, fav ? 0xFFF5C542 : Theme.TEXT_MUTED);
                    c.text("sound_" + String.format("%02d", idx + 1) + ".mp3", rx + 19, ry + 5,
                            fav ? 0xFF8B85F0 : Theme.TEXT);
                    int pill = 34;
                    c.fillRoundRect(rx + rw - pill - 6, ry + 2, pill, 14, Theme.ACCENT);
                    c.centeredText(Component.literal("Play"), rx + rw - pill / 2 - 6, ry + 5, Theme.TEXT_ON_ACCENT);
                }
            });
        }

        add(new Slider(volume, v -> volume = v)
                .readout(v -> Component.literal("Volume " + (int) Math.round(v * 100) + "%")))
                .bounds(cx, sliderY, cw, 20);

        add(new Toggle(localOnly, v -> localOnly = v)).bounds(cx, toggleY, 34, 16);

        add(new Button(Component.literal("Close"), b -> onClose()).secondary())
                .bounds(cx, closeY, cw, 22);
    }

    @Override
    protected void renderContent(UiCanvas c) {
        c.fillRect(0, 0, this.width, this.height, Theme.SCRIM);
        c.fillRoundRect(px, py, pw, ph, Theme.PANEL);
        c.roundBorder(px, py, pw, ph, Theme.BORDER);
        c.fillRect(px + Theme.RADIUS, py, pw - Theme.RADIUS * 2, 3, Theme.ACCENT);
        c.centeredText(Component.literal("OpenSoundboard"), px + pw / 2, py + 12, Theme.TEXT);
        c.text("Play locally only", px + Theme.PAD + 42, py + ph - Theme.PAD - 22 - 6 - 16 + 4, Theme.TEXT);
    }
}
