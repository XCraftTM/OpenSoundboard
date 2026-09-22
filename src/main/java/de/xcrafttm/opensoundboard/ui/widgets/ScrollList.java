package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical scrolling list of {@link Row}s. Clips content with a scissor rectangle, draws a
 * scrollbar, and routes clicks/scroll to the visible rows. Screens supply their own rows.
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
    private int rowGap = 1;
    private boolean framed = true;
    private String emptyText = null;
    private final Scrollbar scrollbar = new Scrollbar();

    public ScrollList gap(int gap) {
        this.rowGap = gap;
        return this;
    }

    /** Whether the modern style draws a recessed field behind the rows (default true). */
    public ScrollList framed(boolean framed) {
        this.framed = framed;
        return this;
    }

    /** Message shown centered while the list has no rows. */
    public ScrollList emptyText(String emptyText) {
        this.emptyText = emptyText;
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
        return Math.max(0, total - rowGap) + inset() * 2;
    }

    private int inset() {
        return framed && !UiStyle.useVanillaComponents() ? 2 : 0;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - h);
    }

    public void scrollToTop() {
        scroll = 0;
    }

    public void scrollToBottom() {
        scroll = maxScroll();
    }

    public boolean isAtBottom() {
        return scroll >= maxScroll() - 2;
    }

    private int rowWidth() {
        return w - inset() * 2 - Scrollbar.reservedWidth(h, contentHeight());
    }

    @Override
    public void draw(UiCanvas c) {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        if (framed && !UiStyle.useVanillaComponents()) {
            c.fillRoundRect(x, y, w, h, Theme.field);
            c.roundBorder(x, y, w, h, Theme.border);
        }

        int rx = x + inset();
        int rw = rowWidth();
        // The modern frame's 1px border stays outside the clip; vanilla rows use the full width so
        // the selection outline is not cut off.
        if (UiStyle.useVanillaComponents()) c.pushScissor(x, y, w, h);
        else c.pushScissor(x + 1, y + 1, w - 2, h - 2);
        int ry = y + inset() - scroll;
        for (Row r : rows) {
            int rh = r.height();
            if (ry + rh >= y && ry <= y + h) {
                boolean hovered = c.mouseX >= rx && c.mouseX < rx + rw
                        && c.mouseY >= Math.max(y, ry) && c.mouseY < Math.min(y + h, ry + rh);
                r.draw(c, rx, ry, rw, hovered);
            }
            ry += rh + rowGap;
        }
        if (rows.isEmpty() && emptyText != null) {
            int color = UiStyle.useVanillaComponents() ? UiStyle.VANILLA_TEXT_MUTED : Theme.textMuted;
            c.centeredText(c.trimText(emptyText, w - 12), x + w / 2, y + h / 2 - c.lineHeight() / 2, color);
        }
        c.popScissor();

        scrollbar.draw(c, x, y, w, h, contentHeight(), scroll);
    }

    /**
     * Shared row chrome: the modern style uses a soft fill plus an accent bar for the selected
     * row; the vanilla style uses Minecraft's list selection outline.
     */
    public static void drawRowBackground(UiCanvas c, int rx, int ry, int rw, int rh, boolean selected, boolean hovered) {
        if (UiStyle.useVanillaComponents()) {
            if (selected) {
                c.fillRect(rx, ry, rw, rh, 0xFFFFFFFF);
                c.fillRect(rx + 1, ry + 1, rw - 2, rh - 2, 0xFF000000);
            } else if (hovered) {
                c.fillRect(rx, ry, rw, rh, 0x28FFFFFF);
            }
            return;
        }
        if (selected) {
            c.fillRect(rx, ry, rw, rh, Theme.surfaceHover);
            c.fillRect(rx, ry, 2, rh, Theme.accent);
        } else if (hovered) {
            c.fillRect(rx, ry, rw, rh, Theme.surface);
        }
    }

    @Override
    public String tooltipAt(double mx, double my) {
        if (!contains(mx, my)) return null;
        int rx = x + inset();
        int rw = rowWidth();
        if (mx >= rx + rw) return null;
        int ry = y + inset() - scroll;
        for (Row r : rows) {
            int rh = r.height();
            if (my >= ry && my < ry + rh) return r.tooltip(mx, rx, rw);
            ry += rh + rowGap;
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
        if (!contains(mx, my)) return false;
        if (button == 0 && scrollbar.mouseClicked(mx, my, x, y, w, h, contentHeight(), scroll,
                value -> scroll = value)) {
            return true;
        }
        int rx = x + inset();
        int rw = rowWidth();
        int ry = y + inset() - scroll;
        for (Row r : rows) {
            int rh = r.height();
            if (my >= ry && my < ry + rh) {
                if (r.click(mx, my, rx, ry, rw, button)) {
                    UiSound.click();
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
