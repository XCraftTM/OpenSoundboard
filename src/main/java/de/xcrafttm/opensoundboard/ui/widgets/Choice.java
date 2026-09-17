package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.tools.Keys;
import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pick one value from a short list. The modern style shows a segmented control when every label
 * fits, otherwise a compact stepper ({@code < value >}). The vanilla style renders a Minecraft
 * cycle button ("Label: Value"); left click goes forward, right click or shift-click goes back.
 */
public class Choice<T> extends Widget {

    private static final int ARROW_W = 14;

    private final List<T> values;
    private final Function<T, Component> labeler;
    private final Consumer<T> onChange;
    private int index;
    private Component label;

    public Choice(List<T> values, T current, Function<T, Component> labeler, Consumer<T> onChange) {
        this.values = List.copyOf(values);
        this.labeler = labeler;
        this.onChange = onChange;
        this.index = Math.max(0, this.values.indexOf(current));
    }

    /** Option name used by the vanilla cycle button ("Label: Value"). */
    public Choice<T> label(Component label) {
        this.label = label;
        return this;
    }

    public T value() {
        return values.get(index);
    }

    public void set(T value) {
        int i = values.indexOf(value);
        if (i >= 0) index = i;
    }

    private void select(int newIndex) {
        int wrapped = Math.floorMod(newIndex, values.size());
        if (wrapped == index) return;
        UiSound.click();
        index = wrapped;
        onChange.accept(values.get(index));
    }

    private boolean segmented() {
        if (values.size() > 4 || w <= 0) return false;
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int segment = w / values.size();
        for (T value : values) {
            if (Math.ceil(font.width(labeler.apply(value).getString()) * UiStyle.fontScale()) > segment - 6) return false;
        }
        return true;
    }

    @Override
    public void draw(UiCanvas c) {
        boolean hover = active && c.hovered(x, y, w, h);
        if (UiStyle.useVanillaComponents()) {
            String sprite = !active ? "widget/button_disabled" : (hover ? "widget/button_highlighted" : "widget/button");
            c.sprite(sprite, x, y, w, h);
            Component valueText = labeler.apply(value());
            Component full = label == null ? valueText : Component.translatable("options.generic_value", label, valueText);
            c.fitText(full.getString(), x + 4, c.centeredTextY(y, h), w - 8,
                    active ? UiStyle.VANILLA_TEXT : UiStyle.VANILLA_TEXT_DISABLED);
            return;
        }

        c.fillRoundRect(x, y, w, h, Theme.field);
        if (segmented()) {
            int n = values.size();
            int segment = w / n;
            for (int i = 0; i < n; i++) {
                int sx = x + i * segment;
                int sw = i == n - 1 ? w - segment * (n - 1) : segment;
                boolean selected = i == index;
                boolean segHover = active && c.hovered(sx, y, sw, h);
                if (selected) {
                    c.fillRoundRect(sx + 1, y + 1, sw - 2, h - 2, active ? Theme.accent : Theme.controlDisabled);
                } else if (segHover) {
                    c.fillRoundRect(sx + 1, y + 1, sw - 2, h - 2, Theme.surfaceHover);
                }
                if (i > 0 && !selected && i - 1 != index) {
                    c.vLine(sx, y + 4, h - 8, Theme.border);
                }
                int color = !active ? Theme.textFaint : (selected ? Theme.onAccent : (segHover ? Theme.text : Theme.textMuted));
                String text = c.trimText(labeler.apply(values.get(i)).getString(), sw - 4);
                c.centeredText(text, sx + sw / 2, c.centeredTextY(y, h), color);
            }
        } else {
            boolean leftHover = active && c.hovered(x, y, ARROW_W, h);
            boolean rightHover = active && c.hovered(x + w - ARROW_W, y, ARROW_W, h);
            if (leftHover) c.fillRoundRect(x + 1, y + 1, ARROW_W - 1, h - 2, Theme.surfaceHover);
            if (rightHover) c.fillRoundRect(x + w - ARROW_W, y + 1, ARROW_W - 1, h - 2, Theme.surfaceHover);
            int arrowColor = !active ? Theme.textFaint : Theme.textMuted;
            c.icon(Icons.CHEVRON_LEFT, x, y, ARROW_W, h, leftHover ? Theme.text : arrowColor);
            c.icon(Icons.CHEVRON_RIGHT, x + w - ARROW_W, y, ARROW_W, h, rightHover ? Theme.text : arrowColor);
            String text = c.trimText(labeler.apply(value()).getString(), w - ARROW_W * 2 - 4);
            c.centeredText(text, x + w / 2, c.centeredTextY(y, h), active ? Theme.text : Theme.textFaint);
        }
        c.roundBorder(x, y, w, h, hover ? Theme.borderStrong : Theme.border);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!active || (button != 0 && button != 1)) return false;
        boolean back = button == 1 || Keys.shiftDown();
        if (UiStyle.useVanillaComponents()) {
            select(index + (back ? -1 : 1));
            return true;
        }
        int n = values.size();
        if (segmented() && button == 0) {
            int segment = w / n;
            select(Math.min(n - 1, (int) ((mx - x) / segment)));
        } else if (mx < x + ARROW_W || back) {
            select(index - 1);
        } else {
            select(index + 1);
        }
        return true;
    }
}
