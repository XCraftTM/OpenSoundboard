package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.Widget;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** A row of square color swatches. The swatch matching the current color gets a ring. */
public class Swatches extends Widget {

    private final List<Theme.Swatch> swatches;
    private final IntSupplier current;
    private final IntConsumer onPick;
    private IntFunction<String> names = i -> null;

    public Swatches(List<Theme.Swatch> swatches, IntSupplier current, IntConsumer onPick) {
        this.swatches = swatches;
        this.current = current;
        this.onPick = onPick;
    }

    /** Tooltip text per swatch index. */
    public Swatches names(IntFunction<String> names) {
        this.names = names;
        return this;
    }

    private int size() {
        return h;
    }

    private int gap() {
        int n = swatches.size();
        if (n <= 1) return 0;
        return Math.max(2, (w - size() * n) / (n - 1));
    }

    private int indexAt(double mx, double my) {
        if (my < y || my >= y + h) return -1;
        int step = size() + gap();
        int i = (int) ((mx - x) / step);
        if (i < 0 || i >= swatches.size()) return -1;
        return mx - (x + i * step) < size() ? i : -1;
    }

    @Override
    public void draw(UiCanvas c) {
        int selectedRgb = current.getAsInt() & 0xFFFFFF;
        int step = size() + gap();
        for (int i = 0; i < swatches.size(); i++) {
            int sx = x + i * step;
            int rgb = swatches.get(i).rgb();
            boolean selected = rgb == selectedRgb;
            boolean hover = c.hovered(sx, y, size(), size());
            c.fillRoundRect(sx, y, size(), size(), Theme.opaque(rgb));
            if (selected) {
                c.roundBorder(sx - 2, y - 2, size() + 4, size() + 4, Theme.text);
            } else if (hover) {
                c.roundBorder(sx - 2, y - 2, size() + 4, size() + 4, Theme.textFaint);
            }
        }
    }

    @Override
    public String tooltipAt(double mx, double my) {
        int i = indexAt(mx, my);
        return i < 0 ? null : names.apply(i);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !active) return false;
        int i = indexAt(mx, my);
        if (i < 0) return false;
        UiSound.click();
        onPick.accept(swatches.get(i).rgb());
        return true;
    }
}
