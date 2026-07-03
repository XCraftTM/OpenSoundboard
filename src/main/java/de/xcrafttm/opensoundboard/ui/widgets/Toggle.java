package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.Widget;

import java.util.function.Consumer;

/** Flat on/off switch. Indigo track when on, sliding knob. */
public class Toggle extends Widget {

    private boolean value;
    private final Consumer<Boolean> onChange;

    public Toggle(boolean value, Consumer<Boolean> onChange) {
        this.value = value;
        this.onChange = onChange;
    }

    public boolean value() {
        return value;
    }

    public void set(boolean v) {
        this.value = v;
    }

    @Override
    public void draw(UiCanvas c) {
        boolean hover = c.hovered(x, y, w, h);
        c.fillRoundRect(x, y, w, h, value ? Theme.ACCENT : 0xFF3A3A44);
        int knobW = w / 2 - 3;
        int kx = value ? x + w - knobW - 2 : x + 2;
        c.fillRoundRect(kx, y + 2, knobW, h - 4, hover ? 0xFFFFFFFF : 0xFFE4E4EA);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && active) {
            value = !value;
            onChange.accept(value);
            return true;
        }
        return false;
    }
}
