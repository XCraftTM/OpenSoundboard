package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical scrolling list of {@link Row}s. Clips content with a scissor rectangle, draws a slim
 * indigo scrollbar, and routes clicks/scroll to the visible rows. Screens supply their own rows.
 */
public class ScrollList extends Widget {

    /** A single row. Screens implement drawing and (optionally) click handling. */
    public interface Row {
        int height();

        void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered);

        default boolean click(double mx, double my, int rx, int ry, int rw, int button) {
            return false;
        }

        default String tooltip(double mx, int rx, int rw) {
            return null;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private int scroll = 0;
    private int rowGap = 2;
    private int background = 0x66000000;
    private final Scrollbar scrollbar = new Scrollbar();

    public ScrollList gap(int gap) {
        this.rowGap = gap;
        return this;
    }

    public ScrollList background(int argb) {
        this.background = argb;
        return this;
    }

    public void clearRows() {
        rows.clear();
    }

    public void addRow(Row row) {
        rows.add(row);
    }

    public int rowCount() {
        return rows.size();
    }

    private int contentHeight() {
        int total = 0;
        for (Row r : rows) total += r.height() + rowGap;
        return Math.max(0, total - rowGap);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - h);
    }

    public void scrollToTop() {
        scroll = 0;
    }

    @Override
    public void draw(UiCanvas c) {
        c.fillRoundRect(x, y, w, h, background);
        c.pushScissor(x, y, w, h);
        int ry = y - scroll;
        for (Row r : rows) {
            int rh = r.height();
            if (ry + rh >= y && ry <= y + h) {
                boolean hovered = c.mouseX >= x && c.mouseX < x + w
                        && c.mouseY >= Math.max(y, ry) && c.mouseY < Math.min(y + h, ry + rh);
                r.draw(c, x, ry, w, hovered);
            }
            ry += rh + rowGap;
        }
        c.popScissor();

        scrollbar.draw(c, x, y, w, h, contentHeight(), scroll);
    }

    @Override
    public String tooltipAt(double mx, double my) {
        if (!contains(mx, my)) return null;
        int ry = y - scroll;
        for (Row r : rows) {
            int rh = r.height();
            if (my >= ry && my < ry + rh && my >= y && my < y + h) return r.tooltip(mx, x, w);
            ry += rh + rowGap;
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amount) {
        int ms = maxScroll();
        if (ms <= 0) return false;
        scroll = Math.max(0, Math.min(ms, scroll - (int) (amount * 16)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!contains(mx, my)) return false;
        if (button == 0 && scrollbar.mouseClicked(mx, my, x, y, w, h, contentHeight(), scroll,
                value -> scroll = value)) {
            return true;
        }
        int ry = y - scroll;
        for (Row r : rows) {
            int rh = r.height();
            if (my >= ry && my < ry + rh && my >= y && my < y + h) {
                if (r.click(mx, my, x, ry, w, button)) {
                    de.xcrafttm.opensoundboard.ui.UiSound.click();
                    return true;
                }
                return false;
            }
            ry += rh + rowGap;
        }
        return false;
    }

    @Override
    public void mouseDragged(double mx, double my, int button) {
        if (button == 0) scrollbar.mouseDragged(my, y, h, contentHeight(), value -> scroll = value);
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        scrollbar.mouseReleased();
    }
}
