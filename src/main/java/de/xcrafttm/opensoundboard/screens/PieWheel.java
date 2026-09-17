package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.TextWrap;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;

import java.util.List;

/**
 * Pie-menu geometry and rendering shared by the sound wheel overlay and the layout editor: a ring
 * of touching slices (slot 0 at the top, clockwise) around a center plate. Selection works by
 * direction, so the whole slice is a target. The ring is rasterized once into horizontal pixel
 * runs because the GUI can only fill rectangles.
 */
final class PieWheel {

    /** What a slice shows. {@code empty} slices are drawn dimmed and cannot be hovered. */
    record Slice(Icons icon, int iconColor, String label, boolean empty, boolean playing) {
        static Slice empty(String label) {
            return new Slice(null, 0, label, true, false);
        }
    }

    private static final int CENTER = -1;
    private static final int SEPARATOR = -2;
    private static final int RIM = -3;

    final int cx;
    final int cy;
    final int outer;
    final int inner;
    final int count;
    private final double step;
    /** Flattened {@code [y, x, length, code]} runs. */
    private final int[] runs;

    private PieWheel(int cx, int cy, int outer, int inner, int count) {
        this.cx = cx;
        this.cy = cy;
        this.outer = outer;
        this.inner = inner;
        this.count = Math.max(1, count);
        this.step = Math.PI * 2 / this.count;
        this.runs = rasterize();
    }

    /** Size the wheel to fit the screen between the reserved top and bottom margins. */
    static PieWheel fit(int screenW, int screenH, int count, int reservedTop, int reservedBottom) {
        int usableH = Math.max(80, screenH - reservedTop - reservedBottom);
        int outer = Math.max(60, Math.min(150, Math.min(screenW, usableH) / 2 - 8));
        // A small center plate leaves most of the radius to the slices and their labels.
        int inner = Math.max(26, Math.round(outer * 0.3f));
        return new PieWheel(screenW / 2, reservedTop + usableH / 2, outer, inner, count);
    }

    /** Slot under the given point, or -1 inside the center plate. */
    int sliceAt(double mx, double my) {
        double dx = mx - cx;
        double dy = my - cy;
        if (Math.hypot(dx, dy) < inner) return -1;
        return indexForAngle(Math.atan2(dy, dx));
    }

    private int indexForAngle(double angle) {
        double t = normalize(angle + Math.PI / 2 + step / 2);
        return Math.min(count - 1, (int) (t / step));
    }

    private static double normalize(double angle) {
        double twoPi = Math.PI * 2;
        angle %= twoPi;
        return angle < 0 ? angle + twoPi : angle;
    }

    private int[] rasterize() {
        int[] buffer = new int[4096];
        int size = 0;
        for (int py = -outer; py < outer; py++) {
            int runStart = 0;
            int runCode = Integer.MIN_VALUE;
            for (int px = -outer; px <= outer; px++) {
                int code = px == outer ? Integer.MIN_VALUE : codeAt(px + 0.5, py + 0.5);
                if (code != runCode) {
                    if (runCode != Integer.MIN_VALUE) {
                        if (size + 4 > buffer.length) buffer = java.util.Arrays.copyOf(buffer, buffer.length * 2);
                        buffer[size++] = py;
                        buffer[size++] = runStart;
                        buffer[size++] = px - runStart;
                        buffer[size++] = runCode;
                    }
                    runStart = px;
                    runCode = code;
                }
            }
        }
        return java.util.Arrays.copyOf(buffer, size);
    }

    /** Region code for a pixel center: slice index, CENTER, SEPARATOR, RIM, or MIN_VALUE (outside). */
    private int codeAt(double x, double y) {
        double d = Math.hypot(x, y);
        if (d > outer) return Integer.MIN_VALUE;
        if (d > outer - 1 || (d >= inner - 1 && d < inner)) return RIM;
        if (d < inner - 1) return CENTER;
        double t = normalize(Math.atan2(y, x) + Math.PI / 2 + step / 2);
        // Only the first pixel after each boundary becomes the separator, so lines stay 1px wide
        // even where a boundary runs exactly between two pixel rows.
        if (count > 1 && (t % step) * d < 1.0) return SEPARATOR;
        return Math.min(count - 1, (int) (t / step));
    }

    // ---------------------------------------------------------------- rendering

    void render(UiCanvas c, List<Slice> slices, int hovered, String centerTitle, String centerSubtitle) {
        boolean vanilla = UiStyle.useVanillaComponents();
        int[] fills = new int[count];
        for (int i = 0; i < count; i++) {
            Slice slice = i < slices.size() ? slices.get(i) : null;
            boolean empty = slice == null || slice.empty();
            if (vanilla) {
                fills[i] = i == hovered && !empty ? 0xC0A0A0A0 : (empty ? 0x70000000 : (slice.playing() ? 0xB0304830 : 0xB0101010));
            } else {
                fills[i] = i == hovered && !empty ? Theme.accent
                        : empty ? Theme.withAlpha(Theme.panel, 0x90)
                        : slice.playing() ? Theme.accentSoft : Theme.panel;
            }
        }
        int separator = vanilla ? 0xFF000000 : Theme.border;
        int rim = vanilla ? 0xFFA0A0A0 : Theme.borderStrong;
        int center = vanilla ? 0xE0000000 : Theme.opaque(Theme.header);

        for (int i = 0; i < runs.length; i += 4) {
            int code = runs[i + 3];
            int color = code >= 0 ? fills[code] : code == CENTER ? center : code == SEPARATOR ? separator : rim;
            c.fillRect(cx + runs[i + 1], cy + runs[i], runs[i + 2], 1, color);
        }

        for (int i = 0; i < count; i++) {
            Slice slice = i < slices.size() ? slices.get(i) : null;
            if (slice != null) drawLabel(c, i, slice, i == hovered && !slice.empty(), vanilla);
        }
        drawCenter(c, centerTitle, centerSubtitle, vanilla);
    }

    private void drawLabel(UiCanvas c, int index, Slice slice, boolean hovered, boolean vanilla) {
        int[] box = labelBox(index);
        int boxX = cx + box[0];
        int boxY = cy + box[1];
        int boxW = box[2];
        int boxH = box[3];

        int textColor;
        int iconColor = slice.iconColor();
        if (vanilla) {
            textColor = slice.empty() ? UiStyle.VANILLA_TEXT_DISABLED : (slice.playing() ? 0xFFFFFF55 : UiStyle.VANILLA_TEXT);
        } else {
            textColor = hovered ? Theme.onAccent : (slice.empty() ? Theme.textFaint : Theme.text);
            if (hovered) iconColor = Theme.onAccent;
        }

        int lh = c.lineHeight();
        int lineStep = lh + 1;
        Icons icon = slice.icon();
        int iconBlock = icon != null ? icon.height + 3 : 0;
        int maxLines = Math.max(1, (boxH - iconBlock + 1) / lineStep);
        if (icon != null && boxH < iconBlock + lh) {
            // Too flat for an icon above the text: fall back to one inline line.
            String text = c.trimText(slice.label(), boxW - icon.width - 4);
            int w = icon.width + 4 + c.textWidth(text);
            int x = boxX + (boxW - w) / 2;
            int midY = boxY + boxH / 2;
            c.icon(icon, x, midY - icon.height / 2, iconColor);
            c.text(text, x + icon.width + 4, midY - lh / 2 + 1, textColor);
            return;
        }

        List<String> lines = slice.label().isEmpty() ? List.of() : TextWrap.wrap(c.font, slice.label(), boxW, maxLines);
        int blockH = iconBlock + lines.size() * lineStep - (lines.isEmpty() ? 0 : 1);
        int y = boxY + (boxH - blockH) / 2;
        int centerX = boxX + boxW / 2;
        if (icon != null) {
            c.icon(icon, centerX - icon.width / 2, y, iconColor);
            y += iconBlock;
        }
        for (String line : lines) {
            c.centeredText(line, centerX, y + 1, textColor);
            y += lineStep;
        }
    }

    // ---------------------------------------------------------------- label boxes

    /** Margin in pixels between a label box and the slice edges. */
    private static final double LABEL_MARGIN = 5;

    /** Per slice: largest text box inside the slice, as {@code [x, y, w, h]} relative to the center. */
    private int[][] labelBoxes;

    private int[] labelBox(int index) {
        if (labelBoxes == null) labelBoxes = new int[count][];
        if (labelBoxes[index] == null) labelBoxes[index] = computeLabelBox(index);
        return labelBoxes[index];
    }

    /**
     * Search along the slice's middle direction for the axis-aligned box with the most usable text
     * area, favoring width (capped so very wide single-line boxes don't win over everything else).
     */
    private int[] computeLabelBox(int index) {
        double mid = -Math.PI / 2 + index * step;
        double cos = Math.cos(mid);
        double sin = Math.sin(mid);
        int ring = outer - inner;
        int[] best = null;
        long bestScore = -1;
        for (double frac = 0.30; frac <= 0.75; frac += 0.05) {
            double r = inner + ring * frac;
            double centerX = cos * r;
            double centerY = sin * r;
            for (int h = Math.max(10, (int) (ring * 1.4)); h >= 10; h -= 3) {
                int lo = 0;
                int hi = outer * 2;
                while (lo < hi) {
                    int m = (lo + hi + 1) / 2;
                    if (boxFits(index, centerX, centerY, m, h)) lo = m;
                    else hi = m - 1;
                }
                if (lo < 20) continue;
                // Width counts twice: a few wide lines read better than a tall column of single words.
                long capped = Math.min(lo, 120);
                long score = capped * capped * Math.min(h, 100);
                if (score > bestScore) {
                    bestScore = score;
                    best = new int[]{(int) Math.round(centerX - lo / 2.0), (int) Math.round(centerY - h / 2.0), lo, h};
                }
            }
        }
        if (best == null) {
            double r = inner + ring * 0.5;
            best = new int[]{(int) Math.round(cos * r) - 15, (int) Math.round(sin * r) - 5, 30, 10};
        }
        return best;
    }

    private boolean boxFits(int index, double centerX, double centerY, int w, int h) {
        double left = centerX - w / 2.0;
        double top = centerY - h / 2.0;
        int stepsX = Math.max(1, w / 4);
        int stepsY = Math.max(1, h / 4);
        for (int i = 0; i <= stepsX; i++) {
            double x = left + w * (double) i / stepsX;
            if (!insideSlice(index, x, top) || !insideSlice(index, x, top + h)) return false;
        }
        for (int i = 1; i < stepsY; i++) {
            double y = top + h * (double) i / stepsY;
            if (!insideSlice(index, left, y) || !insideSlice(index, left + w, y)) return false;
        }
        return true;
    }

    private boolean insideSlice(int index, double x, double y) {
        double d = Math.hypot(x, y);
        if (d < inner + LABEL_MARGIN || d > outer - LABEL_MARGIN) return false;
        if (count == 1) return true;
        double offset = Math.atan2(y, x) - (-Math.PI / 2 + index * step);
        offset = Math.atan2(Math.sin(offset), Math.cos(offset));
        double half = step / 2;
        if (Math.abs(offset) >= half) return false;
        // Perpendicular distance to the nearer slice edge.
        return d * Math.sin(half - Math.abs(offset)) >= LABEL_MARGIN || half - Math.abs(offset) >= Math.PI / 2;
    }

    private void drawCenter(UiCanvas c, String title, String subtitle, boolean vanilla) {
        int maxW = (int) (inner * 1.6);
        int lh = c.lineHeight();
        List<String> lines = title == null || title.isEmpty() ? List.of() : TextWrap.wrap(c.font, title, maxW, 3);
        boolean hasSubtitle = subtitle != null && !subtitle.isEmpty();
        int blockH = lines.size() * (lh + 1) + (hasSubtitle ? lh + 3 : 0);
        int y = cy - blockH / 2 + 1;
        int titleColor = vanilla ? UiStyle.VANILLA_TEXT : Theme.text;
        for (String line : lines) {
            c.centeredText(line, cx, y, titleColor);
            y += lh + 1;
        }
        if (hasSubtitle) {
            y += 2;
            c.centeredText(c.trimText(subtitle, maxW), cx, y, vanilla ? UiStyle.VANILLA_TEXT_MUTED : Theme.textFaint);
        }
    }
}
