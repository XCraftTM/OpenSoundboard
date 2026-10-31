package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Horizontal slider with two handles selecting a range inside 0..1: drag the left handle for the
 * low end and the right one for the high end (they cannot cross). A mark shows the middle, e.g.
 * "unchanged" when the scale runs from "less" on the left to "more" on the right. Optional
 * snapping to steps, a centered readout, and a commit callback once a drag ends.
 */
public class RangeSlider extends Widget {

    private static final int VANILLA_HANDLE_W = 8;
    private static final int LOW = 0;
    private static final int HIGH = 1;

    private double low;
    private double high;
    private final BiConsumer<Double, Double> onChange;
    private BiConsumer<Double, Double> onCommit;
    private BiFunction<Double, Double, Component> readout;
    private int steps = 0;
    /** The handle being dragged (LOW/HIGH), or -1. */
    private int dragging = -1;

    public RangeSlider(double low, double high, BiConsumer<Double, Double> onChange) {
        this.low = clamp(Math.min(low, high));
        this.high = clamp(Math.max(low, high));
        this.onChange = onChange;
    }

    public RangeSlider readout(BiFunction<Double, Double, Component> readout) {
        this.readout = readout;
        return this;
    }

    /** Snap both handles to {@code steps} equal intervals. */
    public RangeSlider steps(int steps) {
        this.steps = Math.max(0, steps);
        return this;
    }

    /** Called with the final range when the user lets go of a handle. */
    public RangeSlider onCommit(BiConsumer<Double, Double> onCommit) {
        this.onCommit = onCommit;
        return this;
    }

    public double low() {
        return low;
    }

    public double high() {
        return high;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    // ---------------------------------------------------------------- geometry

    private boolean vanilla() {
        return UiStyle.useVanillaComponents();
    }

    /** Screen x of a value's position on the track. */
    private double xOf(double value) {
        if (vanilla()) return x + VANILLA_HANDLE_W / 2.0 + value * (w - VANILLA_HANDLE_W);
        return x + 1 + value * (w - 2);
    }

    private double valueAt(double mx) {
        double raw = vanilla()
                ? (mx - (x + VANILLA_HANDLE_W / 2.0)) / (w - VANILLA_HANDLE_W)
                : (mx - x - 1) / (double) (w - 2);
        double v = clamp(raw);
        return steps > 0 ? Math.round(v * steps) / (double) steps : v;
    }

    /** The handle a press at {@code mx} grabs: the nearer one, or by side when they overlap. */
    private int handleAt(double mx) {
        double lowX = xOf(low);
        double highX = xOf(high);
        if (mx <= lowX) return LOW;
        if (mx >= highX) return HIGH;
        double toLow = mx - lowX;
        double toHigh = highX - mx;
        return toLow <= toHigh ? LOW : HIGH;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void draw(UiCanvas c) {
        boolean hover = active && (dragging >= 0 || c.hovered(x, y, w, h));
        int hot = dragging >= 0 ? dragging : (hover ? handleAt(c.mouseX) : -1);
        int lowX = (int) Math.round(xOf(low));
        int highX = (int) Math.round(xOf(high));
        int centerX = (int) Math.round(xOf(0.5));

        if (vanilla()) {
            c.sprite(hover ? "widget/slider_highlighted" : "widget/slider", x, y, w, h);
            c.fillRect(centerX, y + 2, 1, h - 4, active ? 0x80FFFFFF : 0x40FFFFFF);
            if (active) {
                if (highX > lowX) c.fillRect(lowX, y + 2, highX - lowX, h - 4, 0x40FFFFFF);
                drawVanillaHandle(c, lowX, hot == LOW);
                drawVanillaHandle(c, highX, hot == HIGH);
            }
            if (readout != null) {
                c.fitText(readout.apply(low, high).getString(), x + 4, c.centeredTextY(y, h), w - 8,
                        active ? UiStyle.VANILLA_TEXT : UiStyle.VANILLA_TEXT_DISABLED);
            }
            return;
        }

        c.fillRoundRect(x, y, w, h, Theme.field);
        if (active && highX > lowX) c.fillRect(lowX, y + 1, highX - lowX, h - 2, Theme.accentSoft);
        c.fillRect(centerX, y + 1, 1, h - 2, Theme.borderStrong);
        c.roundBorder(x, y, w, h, hover ? Theme.borderStrong : Theme.border);
        if (active) {
            drawHandle(c, lowX, hot == LOW);
            drawHandle(c, highX, hot == HIGH);
        }
        if (readout != null) {
            String text = c.trimText(readout.apply(low, high).getString(), Math.max(0, w - 8));
            c.centeredText(text, x + w / 2, c.centeredTextY(y, h), active ? Theme.text : Theme.textFaint);
        }
    }

    private void drawHandle(UiCanvas c, int centerX, boolean hot) {
        int handleW = hot ? 3 : 2;
        int hx = Math.max(x + 1, Math.min(x + w - 1 - handleW, centerX - handleW / 2));
        c.fillRect(hx, y + 1, handleW, h - 2, hot ? Theme.accentHover : Theme.accent);
    }

    private void drawVanillaHandle(UiCanvas c, int centerX, boolean hot) {
        int hx = centerX - VANILLA_HANDLE_W / 2;
        c.sprite(hot ? "widget/slider_handle_highlighted" : "widget/slider_handle", hx, y, VANILLA_HANDLE_W, h);
    }

    // ---------------------------------------------------------------- input

    private void apply(double mx) {
        double v = valueAt(mx);
        double nextLow = low;
        double nextHigh = high;
        if (dragging == LOW) nextLow = Math.min(v, high);
        else nextHigh = Math.max(v, low);
        if (nextLow != low || nextHigh != high) {
            low = nextLow;
            high = nextHigh;
            onChange.accept(low, high);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !active) return false;
        dragging = handleAt(mx);
        apply(mx);
        return true;
    }

    @Override
    public void mouseDragged(double mx, double my, int button) {
        if (active && dragging >= 0) apply(mx);
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        if (dragging < 0) return;
        dragging = -1;
        if (onCommit != null) onCommit.accept(low, high);
    }
}
