package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * On/off control. Modern style: a square checkbox with a pixel check mark. Vanilla style: a
 * Minecraft checkbox, or — when a label is set — an option button reading "Label: ON/OFF".
 */
public class Toggle extends Widget {

    private boolean value;
    private final Consumer<Boolean> onChange;
    private Component label;

    public Toggle(boolean value, Consumer<Boolean> onChange) {
        this.value = value;
        this.onChange = onChange;
    }

    /** Label used by the vanilla option-button rendering. */
    public Toggle label(Component label) {
        this.label = label;
        return this;
    }

    public boolean value() {
        return value;
    }

    public void set(boolean v) {
        this.value = v;
    }

    public void toggle() {
        if (!active) return;
        UiSound.click();
        value = !value;
        onChange.accept(value);
    }

    @Override
    public void draw(UiCanvas c) {
        boolean hover = active && c.hovered(x, y, w, h);
        if (UiStyle.useVanillaComponents()) {
            if (label != null) {
                String sprite = !active ? "widget/button_disabled" : (hover ? "widget/button_highlighted" : "widget/button");
                c.sprite(sprite, x, y, w, h);
                Component state = Component.translatable(value ? "options.on" : "options.off");
                String text = Component.translatable("options.generic_value", label, state).getString();
                c.fitText(text, x + 4, c.centeredTextY(y, h), w - 8,
                        active ? UiStyle.VANILLA_TEXT : UiStyle.VANILLA_TEXT_DISABLED);
            } else {
                String sprite = "widget/checkbox" + (value ? "_selected" : "") + (hover ? "_highlighted" : "");
                c.sprite(sprite, x, y, w, h);
            }
            return;
        }

        if (value) {
            c.fillRoundRect(x, y, w, h, !active ? Theme.controlDisabled : (hover ? Theme.accentHover : Theme.accent));
            c.icon(Icons.CHECK, x, y, w, h, active ? Theme.onAccent : Theme.textFaint);
        } else {
            c.fillRoundRect(x, y, w, h, Theme.field);
            c.roundBorder(x, y, w, h, !active ? Theme.border : (hover ? Theme.accent : Theme.borderStrong));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && active) {
            toggle();
            return true;
        }
        return false;
    }
}
