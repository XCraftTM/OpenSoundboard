package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.network.chat.Component;

/** One rounded button split into two independently-clickable halves (left {@code ratio} / right). */
public class SplitButton extends Widget {

    private final float ratio;
    private Component left;
    private Component right;
    private final Runnable onLeft;
    private final Runnable onRight;

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
        boolean hl = c.hovered(x, y, split, h);
        boolean hr = c.hovered(x + split, y, w - split, h);
        c.fillRoundRect(x, y, w, h, Theme.BTN);
        if (hl) c.fillRect(x + 1, y + 1, split - 1, h - 2, Theme.BTN_HOVER);
        if (hr) c.fillRect(x + split, y + 1, w - split - 1, h - 2, Theme.BTN_HOVER);
        c.roundBorder(x, y, w, h, Theme.BORDER);
        c.fillRect(x + split, y + 2, 1, h - 4, Theme.BORDER);
        c.centeredText(left, x + split / 2, y + (h - 8) / 2, Theme.TEXT);
        c.centeredText(right, x + split + (w - split) / 2, y + (h - 8) / 2, Theme.TEXT);
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
