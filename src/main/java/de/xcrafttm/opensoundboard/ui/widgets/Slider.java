package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

/** Horizontal 0..1 slider with an optional centered readout label. Click or drag to set. */
public class Slider extends Widget {

    private double value;
    private final Consumer<Double> onChange;
    private Function<Double, Component> readout;

    public Slider(double value, Consumer<Double> onChange) {
        this.value = clamp(value);
        this.onChange = onChange;
    }

    public Slider readout(Function<Double, Component> readout) {
        this.readout = readout;
        return this;
    }

    public double value() {
        return value;
    }

    public void set(double v) {
        this.value = clamp(v);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    @Override
    public void draw(UiCanvas c) {
        c.fillRoundRect(x, y, w, h, Theme.ROW);
        c.roundBorder(x, y, w, h, Theme.BORDER);
        int fillW = (int) Math.round(value * (w - 2));
        c.fillRect(x + 1, y + 1, fillW, h - 2, active ? Theme.ACCENT_MUTED : Theme.ROW);
        int hx = x + (int) Math.round(value * (w - 4));
        c.fillRect(hx, y, 4, h, active ? Theme.ACCENT : 0xFF6A6A76);
        if (readout != null) {
            c.centeredText(readout.apply(value), x + w / 2, y + (h - 8) / 2, Theme.TEXT);
        }
    }

    private void apply(double mx) {
        value = clamp((mx - x) / (double) w);
        onChange.accept(value);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && active) {
            apply(mx);
            return true;
        }
        return false;
    }

    @Override
    public void mouseDragged(double mx, double my, int button) {
        if (active) apply(mx);
    }
}
