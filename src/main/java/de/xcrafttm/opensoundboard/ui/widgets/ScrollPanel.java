package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable container that hosts child widgets laid out with content-relative coordinates.
 * Clips to its bounds, draws an indigo scrollbar, and routes input (click/drag/scroll) to the
 * visible children — a mini version of the screen's dispatch, used for long option lists.
 */
public class ScrollPanel extends Widget {

    private final List<Widget> children = new ArrayList<>();
    private final List<int[]> rel = new ArrayList<>(); // relX, relY, w, h
    private int scroll = 0;
    private int contentHeight = 0;
    private Widget dragging;

    public <T extends Widget> T addChild(T child, int relX, int relY, int cw, int ch) {
        rel.add(new int[]{relX, relY, cw, ch});
        children.add(child);
        contentHeight = Math.max(contentHeight, relY + ch);
        return child;
    }

    public void clear() {
        children.clear();
        rel.clear();
        contentHeight = 0;
        scroll = 0;
        dragging = null;
    }

    public int contentHeight() {
        return contentHeight;
    }

    public int getScroll() {
        return scroll;
    }

    public void setScroll(int s) {
        scroll = Math.max(0, s);
    }

    private void layout() {
        for (int i = 0; i < children.size(); i++) {
            int[] r = rel.get(i);
            Widget c = children.get(i);
            c.x = x + r[0];
            c.y = y + r[1] - scroll;
            c.w = r[2];
            c.h = r[3];
        }
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - h);
    }

    @Override
    public void draw(UiCanvas c) {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        layout();
        c.pushScissor(x, y, w, h);
        for (Widget ch : children) {
            if (ch.visible && ch.y + ch.h >= y && ch.y <= y + h) ch.draw(c);
        }
        c.popScissor();

        int ms = maxScroll();
        if (ms > 0) {
            int barH = Math.max(20, (int) ((long) h * h / contentHeight));
            int barY = y + (int) ((long) (h - barH) * scroll / ms);
            c.fillRect(x + w - 3, y, 3, h, 0x22FFFFFF);
            c.fillRect(x + w - 3, barY, 3, barH, Theme.ACCENT);
        }
    }

    @Override
    public String tooltipAt(double mx, double my) {
        if (my < y || my >= y + h) return null;
        layout();
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget ch = children.get(i);
            if (ch.visible && ch.contains(mx, my)) return ch.tooltipAt(mx, my);
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int ms = maxScroll();
        if (ms <= 0) return false;
        scroll = Math.max(0, Math.min(ms, scroll - (int) (amount * 18)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (my < y || my >= y + h) return false;
        layout();
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget ch = children.get(i);
            if (ch.visible && ch.active && ch.contains(mx, my) && ch.mouseClicked(mx, my, button)) {
                dragging = ch;
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseDragged(double mx, double my, int button) {
        if (dragging != null) dragging.mouseDragged(mx, my, button);
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging.mouseReleased(mx, my, button);
            dragging = null;
        }
    }

    @Override
    public void tick() {
        for (Widget ch : children) ch.tick();
    }
}
