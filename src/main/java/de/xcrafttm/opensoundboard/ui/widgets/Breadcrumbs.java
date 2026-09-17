package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiSound;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Clickable folder path ("Sounds › Games › Mario"). Every crumb except the last one navigates;
 * when the path is too wide, the middle crumbs collapse into "...".
 */
public class Breadcrumbs extends Widget {

    public record Crumb(String label, Runnable onClick) {
    }

    private static final int SEPARATOR_W = 11;
    private static final String ELLIPSIS = "...";

    private final List<Crumb> crumbs;

    /** Layout of the last draw: visible crumb index (-1 = ellipsis) and its x range. */
    private final List<int[]> hitboxes = new ArrayList<>();

    public Breadcrumbs(List<Crumb> crumbs) {
        this.crumbs = List.copyOf(crumbs);
    }

    private List<Integer> visibleIndices(UiCanvas c) {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < crumbs.size(); i++) all.add(i);
        if (totalWidth(c, all) <= w || crumbs.size() <= 2) return all;

        // Keep the root and as many trailing crumbs as fit, with an ellipsis in between.
        List<Integer> out = new ArrayList<>();
        out.add(0);
        out.add(-1);
        out.add(crumbs.size() - 1);
        for (int i = crumbs.size() - 2; i >= 1; i--) {
            List<Integer> candidate = new ArrayList<>(out);
            candidate.add(2, i);
            if (totalWidth(c, candidate) > w) break;
            out = candidate;
        }
        return out;
    }

    private int labelWidth(UiCanvas c, int index) {
        return c.textWidth(index < 0 ? ELLIPSIS : crumbs.get(index).label());
    }

    private int totalWidth(UiCanvas c, List<Integer> indices) {
        int total = 0;
        for (int i = 0; i < indices.size(); i++) {
            if (i > 0) total += SEPARATOR_W;
            total += labelWidth(c, indices.get(i));
        }
        return total;
    }

    @Override
    public void draw(UiCanvas c) {
        boolean vanilla = UiStyle.useVanillaComponents();
        int textY = c.centeredTextY(y, h);
        int cx = x;
        hitboxes.clear();
        List<Integer> indices = visibleIndices(c);
        for (int i = 0; i < indices.size(); i++) {
            int index = indices.get(i);
            if (i > 0) {
                c.icon(Icons.CHEVRON_RIGHT, cx, y, SEPARATOR_W, h, vanilla ? UiStyle.VANILLA_TEXT_MUTED : Theme.textFaint);
                cx += SEPARATOR_W;
            }
            boolean last = index == crumbs.size() - 1;
            String label = index < 0 ? ELLIPSIS : crumbs.get(index).label();
            int remaining = x + w - cx;
            if (remaining <= 0) break;
            label = c.trimText(label, remaining);
            int lw = c.textWidth(label);
            boolean clickable = index >= 0 && !last;
            boolean hover = clickable && c.hovered(cx - 1, y, lw + 2, h);

            int color;
            if (vanilla) color = last ? UiStyle.VANILLA_TEXT : (hover ? 0xFFFFFF55 : UiStyle.VANILLA_TEXT_MUTED);
            else color = last ? Theme.text : (hover ? Theme.accentHover : Theme.textMuted);
            c.text(label, cx, textY, color);
            if (hover) c.hLine(cx, textY + c.lineHeight(), lw, color);

            if (clickable) hitboxes.add(new int[]{index, cx, cx + lw});
            cx += lw;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (int[] box : hitboxes) {
            if (mx >= box[1] - 1 && mx < box[2] + 1) {
                UiSound.click();
                crumbs.get(box[0]).onClick().run();
                return true;
            }
        }
        return false;
    }
}
