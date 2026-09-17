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
 * Push button with an optional pixel icon. Modern kinds: primary (accent fill), secondary
 * (bordered), and ghost (no chrome until hovered). The vanilla style uses Minecraft's button
 * sprites. A button can also be marked "selected" to act as an on/off toggle.
 */
public class Button extends Widget {

    public enum Kind { PRIMARY, SECONDARY, GHOST }

    private Component label;
    private Icons icon;
    private final Consumer<Button> onClick;
    private Kind kind = Kind.SECONDARY;
    private boolean selected = false;
    private boolean danger = false;

    public Button(Component label, Consumer<Button> onClick) {
        this(null, label, onClick);
    }

    public Button(Icons icon, Component label, Consumer<Button> onClick) {
        this.icon = icon;
        this.label = label;
        this.onClick = onClick;
    }

    public Button primary() {
        this.kind = Kind.PRIMARY;
        return this;
    }

    public Button secondary() {
        this.kind = Kind.SECONDARY;
        return this;
    }

    public Button ghost() {
        this.kind = Kind.GHOST;
        return this;
    }

    /** Destructive/stop action: tinted red while hovered in the modern style. */
    public Button danger(boolean danger) {
        this.danger = danger;
        return this;
    }

    public Button setSelected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    public void setIcon(Icons icon) {
        this.icon = icon;
    }

    @Override
    public void draw(UiCanvas c) {
        boolean hover = active && c.hovered(x, y, w, h);
        if (UiStyle.useVanillaComponents()) {
            String sprite = !active ? "widget/button_disabled" : (hover ? "widget/button_highlighted" : "widget/button");
            c.sprite(sprite, x, y, w, h);
            int color = active ? UiStyle.VANILLA_TEXT : UiStyle.VANILLA_TEXT_DISABLED;
            drawContent(c, color, color);
            if (selected) {
                int lineW = Math.max(6, Math.min(w - 8, contentWidth(c)));
                c.fillRect(x + (w - lineW) / 2, y + h - 4, lineW, 1, 0xFFFFFFFF);
            }
            return;
        }

        int textColor;
        int iconColor;
        if (!active) {
            if (kind != Kind.GHOST) c.fillRoundRect(x, y, w, h, Theme.controlDisabled);
            textColor = Theme.textFaint;
            iconColor = Theme.textFaint;
        } else if (selected) {
            c.fillRoundRect(x, y, w, h, hover ? Theme.mix(Theme.accentSoft, Theme.accent, 0.15f) : Theme.accentSoft);
            c.roundBorder(x, y, w, h, Theme.accent);
            textColor = Theme.text;
            iconColor = Theme.accentHover;
        } else if (kind == Kind.PRIMARY) {
            c.fillRoundRect(x, y, w, h, hover ? Theme.accentHover : Theme.accent);
            textColor = Theme.onAccent;
            iconColor = Theme.onAccent;
        } else if (kind == Kind.GHOST) {
            if (hover) c.fillRoundRect(x, y, w, h, Theme.surfaceHover);
            textColor = hover ? (danger ? Theme.DANGER : Theme.text) : Theme.textMuted;
            iconColor = textColor;
        } else {
            c.fillRoundRect(x, y, w, h, hover ? Theme.controlHover : Theme.control);
            c.roundBorder(x, y, w, h, hover ? (danger ? Theme.DANGER : Theme.borderStrong) : Theme.border);
            textColor = Theme.text;
            iconColor = hover && danger ? Theme.DANGER : Theme.text;
        }
        drawContent(c, textColor, iconColor);
    }

    private int contentWidth(UiCanvas c) {
        String text = labelText();
        Icons icon = UiStyle.useVanillaComponents() && !text.isEmpty() ? null : this.icon;
        int width = icon != null ? icon.width : 0;
        if (!text.isEmpty()) width += (icon != null ? 4 : 0) + c.textWidth(text);
        return width;
    }

    private String labelText() {
        return label == null ? "" : label.getString();
    }

    private void drawContent(UiCanvas c, int textColor, int iconColor) {
        String text = labelText();
        Icons icon = UiStyle.useVanillaComponents() && !text.isEmpty() ? null : this.icon;
        int iconW = icon != null ? icon.width + (text.isEmpty() ? 0 : 4) : 0;
        String visible = text.isEmpty() ? "" : c.trimText(text, Math.max(0, w - 8 - iconW));
        int contentW = iconW + (visible.isEmpty() ? 0 : c.textWidth(visible));
        int cx = x + (w - contentW) / 2;
        if (icon != null) {
            c.icon(icon, cx, y + (h - icon.height) / 2, iconColor);
        }
        if (icon == null && !text.isEmpty() && UiStyle.useVanillaComponents()) {
            c.fitText(text, x + 4, c.centeredTextY(y, h), w - 8, textColor);
        } else if (!visible.isEmpty()) {
            c.text(visible, cx + iconW, c.centeredTextY(y, h), textColor);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && active) {
            UiSound.click();
            onClick.accept(this);
            return true;
        }
        return false;
    }
}
