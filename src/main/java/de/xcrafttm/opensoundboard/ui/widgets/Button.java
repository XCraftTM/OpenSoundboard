package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Flat button. Primary = filled indigo; secondary = subtle row fill with a border.
 */
public class Button extends Widget {

    private Component label;
    private final Consumer<Button> onClick;
    private boolean primary = true;
    private net.minecraft.client.gui.components.Button vanilla;
    private int vanillaW = -1;
    private int vanillaH = -1;

    public Button(Component label, Consumer<Button> onClick) {
        this.label = label;
        this.onClick = onClick;
    }

    public Button secondary() {
        this.primary = false;
        return this;
    }

    /** Toggle the filled/accent look at runtime (used as an on/off state indicator). */
    public Button setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }

    public void setLabel(Component label) {
        this.label = label;
    }

    @Override
    public void draw(UiCanvas c) {
        if (UiStyle.useVanillaComponents()) {
            drawVanilla(c);
            return;
        }

        boolean hover = active && c.hovered(x, y, w, h);
        if (primary) {
            c.fillRoundRect(x, y, w, h, active ? (hover ? Theme.ACCENT_HOVER : Theme.ACCENT) : Theme.BTN_DISABLED);
        } else {
            c.fillRoundRect(x, y, w, h, !active ? Theme.BTN_DISABLED : (hover ? Theme.BTN_HOVER : Theme.BTN));
            c.roundBorder(x, y, w, h, hover ? Theme.BORDER_STRONG : Theme.BORDER);
        }
        int textColor = !active ? Theme.TEXT_MUTED : (primary ? Theme.TEXT_ON_ACCENT : Theme.TEXT);
        c.centeredText(visibleLabel(c), x + w / 2, c.centeredTextY(y, h), textColor);
    }

    private void drawVanilla(UiCanvas c) {
        if (vanilla == null || vanillaW != w || vanillaH != h) {
            vanilla = net.minecraft.client.gui.components.Button.builder(Component.empty(), ignored -> {
            }).bounds(x, y, w, h).build();
            vanillaW = w;
            vanillaH = h;
        }
        vanilla.setX(x);
        vanilla.setY(y);
        vanilla.setMessage(Component.empty());
        vanilla.active = active;
        c.renderVanilla(vanilla);
        c.centeredText(visibleLabel(c), x + w / 2, c.centeredTextY(y, h),
                active ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    private Component visibleLabel(UiCanvas c) {
        return Component.literal(c.trimText(label.getString(), Math.max(0, w - 8)));
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
