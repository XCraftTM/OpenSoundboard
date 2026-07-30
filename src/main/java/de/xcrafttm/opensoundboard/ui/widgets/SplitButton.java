package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

/** One rounded button split into two independently-clickable halves (left {@code ratio} / right). */
public class SplitButton extends Widget {

    private final float ratio;
    private Component left;
    private Component right;
    private final Runnable onLeft;
    private final Runnable onRight;
    private net.minecraft.client.gui.components.Button vanillaLeft;
    private net.minecraft.client.gui.components.Button vanillaRight;
    private int vanillaSplit = -1;
    private int vanillaW = -1;
    private int vanillaH = -1;

    public SplitButton(float ratio, Component left, Runnable onLeft, Component right, Runnable onRight) {
        this.ratio = ratio;
        this.left = left;
        this.onLeft = onLeft;
        this.right = right;
        this.onRight = onRight;
    }

    public void setLeft(Component left) {
        this.left = left;
    }

    public void setRight(Component right) {
        this.right = right;
    }

    @Override
    public void draw(UiCanvas c) {
        int split = (int) (w * ratio);
        if (UiStyle.useVanillaComponents()) {
            drawVanilla(c, split);
            return;
        }

        boolean hl = c.hovered(x, y, split, h);
        boolean hr = c.hovered(x + split, y, w - split, h);
        c.fillRoundRect(x, y, w, h, Theme.BTN);
        if (hl) c.fillRect(x + 1, y + 1, split - 1, h - 2, Theme.BTN_HOVER);
        if (hr) c.fillRect(x + split, y + 1, w - split - 1, h - 2, Theme.BTN_HOVER);
        c.roundBorder(x, y, w, h, Theme.BORDER);
        c.fillRect(x + split, y + 2, 1, h - 4, Theme.BORDER);
        int textY = c.centeredTextY(y, h);
        c.centeredText(visible(c, left, split), x + split / 2, textY, Theme.TEXT);
        c.centeredText(visible(c, right, w - split), x + split + (w - split) / 2, textY, Theme.TEXT);
    }

    private void drawVanilla(UiCanvas c, int split) {
        if (vanillaLeft == null || vanillaSplit != split || vanillaW != w || vanillaH != h) {
            vanillaLeft = net.minecraft.client.gui.components.Button.builder(Component.empty(), ignored -> {
            }).bounds(x, y, split, h).build();
            vanillaRight = net.minecraft.client.gui.components.Button.builder(Component.empty(), ignored -> {
            }).bounds(x + split, y, w - split, h).build();
            vanillaSplit = split;
            vanillaW = w;
            vanillaH = h;
        }
        vanillaLeft.setX(x);
        vanillaLeft.setY(y);
        vanillaLeft.setMessage(Component.empty());
        vanillaLeft.active = active;
        vanillaRight.setX(x + split);
        vanillaRight.setY(y);
        vanillaRight.setMessage(Component.empty());
        vanillaRight.active = active;
        c.renderVanilla(vanillaLeft);
        c.renderVanilla(vanillaRight);
        int textColor = active ? 0xFFFFFFFF : 0xFFA0A0A0;
        int textY = c.centeredTextY(y, h);
        c.centeredText(visible(c, left, split), x + split / 2, textY, textColor);
        c.centeredText(visible(c, right, w - split), x + split + (w - split) / 2, textY, textColor);
    }

    private static Component visible(UiCanvas c, Component label, int width) {
        return Component.literal(c.trimText(label.getString(), Math.max(0, width - 8)));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !active) return false;
        UiSound.click();
        if (mx < x + w * ratio) onLeft.run();
        else onRight.run();
        return true;
    }
}
