package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;

import java.util.function.IntConsumer;

/** Shared clickable and draggable vertical scrollbar used by lists and panels. */
final class Scrollbar {

    private static final int MODERN_WIDTH = 3;
    private static final int VANILLA_WIDTH = 6;
    private static final int HIT_WIDTH = 8;
    private static final int MIN_THUMB_HEIGHT = 20;

    private boolean dragging;
    private int grabOffset;

    /** Horizontal space the scrollbar takes when content overflows (0 otherwise). */
    static int reservedWidth(int height, int contentHeight) {
        if (maxScroll(height, contentHeight) <= 0) return 0;
        return (UiStyle.useVanillaComponents() ? VANILLA_WIDTH : MODERN_WIDTH) + 3;
    }

    void draw(UiCanvas c, int x, int y, int width, int height, int contentHeight, int scroll) {
        int maxScroll = maxScroll(height, contentHeight);
        if (maxScroll <= 0) return;

        int thumbHeight = thumbHeight(height, contentHeight);
        int thumbY = thumbY(y, height, thumbHeight, maxScroll, scroll);
        boolean hovered = dragging || c.hovered(x + width - HIT_WIDTH, y, HIT_WIDTH, height);

        if (UiStyle.useVanillaComponents()) {
            int barX = x + width - VANILLA_WIDTH;
            c.sprite("widget/scroller_background", barX, y, VANILLA_WIDTH, height);
            c.sprite("widget/scroller", barX, thumbY, VANILLA_WIDTH, thumbHeight);
        } else {
            int barX = x + width - MODERN_WIDTH - 1;
            c.fillRect(barX, thumbY, MODERN_WIDTH, thumbHeight, hovered ? Theme.accent : Theme.borderStrong);
        }
    }

    boolean mouseClicked(double mx, double my, int x, int y, int width, int height,
                         int contentHeight, int scroll, IntConsumer updateScroll) {
        int maxScroll = maxScroll(height, contentHeight);
        if (maxScroll <= 0 || mx < x + width - HIT_WIDTH || mx >= x + width
                || my < y || my >= y + height) {
            return false;
        }

        int thumbHeight = thumbHeight(height, contentHeight);
        int currentThumbY = thumbY(y, height, thumbHeight, maxScroll, scroll);
        if (my >= currentThumbY && my < currentThumbY + thumbHeight) {
            grabOffset = (int) my - currentThumbY;
        } else {
            grabOffset = thumbHeight / 2;
            updateFromMouse(my, y, height, thumbHeight, maxScroll, updateScroll);
        }
        dragging = true;
        return true;
    }

    boolean mouseDragged(double my, int y, int height, int contentHeight, IntConsumer updateScroll) {
        if (!dragging) return false;
        int maxScroll = maxScroll(height, contentHeight);
        if (maxScroll > 0) {
            updateFromMouse(my, y, height, thumbHeight(height, contentHeight), maxScroll, updateScroll);
        }
        return true;
    }

    void mouseReleased() {
        dragging = false;
    }

    private void updateFromMouse(double my, int y, int height, int thumbHeight,
                                 int maxScroll, IntConsumer updateScroll) {
        int travel = height - thumbHeight;
        if (travel <= 0) {
            updateScroll.accept(0);
            return;
        }
        int thumbTop = Math.max(y, Math.min(y + travel, (int) Math.round(my) - grabOffset));
        updateScroll.accept((int) Math.round((thumbTop - y) * maxScroll / (double) travel));
    }

    private static int maxScroll(int height, int contentHeight) {
        return Math.max(0, contentHeight - height);
    }

    private static int thumbHeight(int height, int contentHeight) {
        if (contentHeight <= 0) return height;
        return Math.min(height, Math.max(MIN_THUMB_HEIGHT, (int) ((long) height * height / contentHeight)));
    }

    private static int thumbY(int y, int height, int thumbHeight, int maxScroll, int scroll) {
        int clampedScroll = Math.max(0, Math.min(maxScroll, scroll));
        return y + (int) ((long) (height - thumbHeight) * clampedScroll / maxScroll);
    }
}
