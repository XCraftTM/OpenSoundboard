package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Horizontal 0..1 slider with an optional centered readout. Click or drag to set; optional
 * snapping to steps and a commit callback fired once the drag ends.
 */
public class Slider extends Widget {

    private static final int VANILLA_HANDLE_W = 8;

    private double value;
    private final Consumer<Double> onChange;
    private Consumer<Double> onCommit;
    private Function<Double, Component> readout;
    private int steps = 0;
    private boolean dragging = false;

    public Slider(double value, Consumer<Double> onChange) {
        this.value = clamp(value);
        this.onChange = onChange;
    }

    public Slider readout(Function<Double, Component> readout) {
        this.readout = readout;
        return this;
    }

    /** Snap to {@code steps} equal intervals (e.g. 8 steps = 0, 0.125, ... 1). */
    public Slider steps(int steps) {
        this.steps = Math.max(0, steps);
        return this;
    }

    /** Called with the final value when the user releases the slider. */
    public Slider onCommit(Consumer<Double> onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    public double value() {
        return value;
    }

    public void set(double v) {
        if (!dragging) this.value = clamp(v);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    @Override
    public void draw(UiCanvas c) {
        boolean hover = active && (dragging || c.hovered(x, y, w, h));
        if (UiStyle.useVanillaComponents()) {
            c.sprite(hover ? "widget/slider_highlighted" : "widget/slider", x, y, w, h);
            int hx = x + (int) Math.round(value * (w - VANILLA_HANDLE_W));
            c.sprite(hover ? "widget/slider_handle_highlighted" : "widget/slider_handle", hx, y, VANILLA_HANDLE_W, h);
            if (readout != null) {
                c.fitText(readout.apply(value).getString(), x + 4, c.centeredTextY(y, h), w - 8,
                        active ? UiStyle.VANILLA_TEXT : UiStyle.VANILLA_TEXT_DISABLED);
            }
            return;
        }

        c.fillRoundRect(x, y, w, h, Theme.field);
        int inner = w - 2;
        int fillW = (int) Math.round(value * inner);
        if (fillW > 0) {
            c.fillRect(x + 1, y + 1, fillW, h - 2, active ? Theme.accentSoft : Theme.controlDisabled);
        }
        c.roundBorder(x, y, w, h, hover ? Theme.borderStrong : Theme.border);
        if (active) {
            int handleW = hover ? 3 : 2;
            int hx = x + 1 + Math.min(inner - handleW, Math.max(0, fillW - handleW / 2 - 1));
            c.fillRect(hx, y + 1, handleW, h - 2, hover ? Theme.accentHover : Theme.accent);
        }
        if (readout != null) {
            c.centeredText(visibleReadout(c), x + w / 2, c.centeredTextY(y, h), active ? Theme.text : Theme.textFaint);
        }
    }

    private String visibleReadout(UiCanvas c) {
        return c.trimText(readout.apply(value).getString(), Math.max(0, w - 8));
    }

    private void apply(double mx) {
        double raw = UiStyle.useVanillaComponents()
                ? (mx - (x + VANILLA_HANDLE_W / 2.0)) / (double) (w - VANILLA_HANDLE_W)
                : (mx - x) / (double) w;
        double next = clamp(raw);
        if (steps > 0) next = Math.round(next * steps) / (double) steps;
        if (next != value) {
            value = next;
            onChange.accept(value);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && active) {
            dragging = true;
            apply(mx);
            return true;
        }
        return false;
    }

    @Override
    public void mouseDragged(double mx, double my, int button) {
        if (active && dragging) apply(mx);
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        if (!dragging) return;
        dragging = false;
        if (onCommit != null) onCommit.accept(value);
    }
}
