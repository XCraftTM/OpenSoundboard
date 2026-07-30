package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;

/** Horizontal 0..1 slider with an optional centered readout label. Click or drag to set. */
public class Slider extends Widget {

    private double value;
    private final Consumer<Double> onChange;
    private Function<Double, Component> readout;
    private VanillaSlider vanilla;
    private int vanillaW = -1;
    private int vanillaH = -1;

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
        if (UiStyle.useVanillaComponents()) {
            drawVanilla(c);
            return;
        }

        c.fillRoundRect(x, y, w, h, Theme.ROW);
        c.roundBorder(x, y, w, h, Theme.BORDER);
        int fillW = (int) Math.round(value * (w - 2));
        c.fillRect(x + 1, y + 1, fillW, h - 2, active ? Theme.ACCENT_MUTED : Theme.ROW);
        int hx = x + (int) Math.round(value * (w - 4));
        c.fillRect(hx, y, 4, h, active ? Theme.ACCENT : 0xFF6A6A76);
        if (readout != null) {
            c.centeredText(visibleReadout(c), x + w / 2, c.centeredTextY(y, h), Theme.TEXT);
        }
    }

    private void drawVanilla(UiCanvas c) {
        if (vanilla == null || vanillaW != w || vanillaH != h) {
            vanilla = new VanillaSlider(x, y, w, h);
            vanillaW = w;
            vanillaH = h;
        }
        vanilla.setX(x);
        vanilla.setY(y);
        vanilla.active = active;
        vanilla.sync(value);
        c.renderVanilla(vanilla);
        if (readout != null) {
            c.centeredText(visibleReadout(c), x + w / 2, c.centeredTextY(y, h),
                    active ? 0xFFFFFFFF : 0xFFA0A0A0);
        }
    }

    private Component visibleReadout(UiCanvas c) {
        Component valueLabel = readout.apply(value);
        return Component.literal(c.trimText(valueLabel.getString(), Math.max(0, w - 8)));
    }

    private final class VanillaSlider extends net.minecraft.client.gui.components.AbstractSliderButton {

        private VanillaSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), Slider.this.value);
            updateMessage();
        }

        private void sync(double newValue) {
            this.value = newValue;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.empty());
        }

        @Override
        protected void applyValue() {
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
