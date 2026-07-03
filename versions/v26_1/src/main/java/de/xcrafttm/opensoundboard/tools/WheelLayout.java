package de.xcrafttm.opensoundboard.tools;

/**
 * Shared radial-wheel button positioning algorithm used by both
 * {@code SoundWheelOverlay} and {@code WheelLayoutEditorScreen}.
 *
 * Buttons are arranged as two vertical arcs (left + right) with a top and
 * bottom cap, forming an oval ring around a centre indicator.
 */
public final class WheelLayout {

    private WheelLayout() {}

    // ----------------------------------------------------------------
    // Tuning constants – change here to affect both screens at once
    // ----------------------------------------------------------------
    public static final int    ROW_GAP          = 16;
    public static final int    INNER_GAP_PADDING = 16;
    public static final double BULGE_FACTOR      = 0.40;
    public static final int    MARGIN            = 10;

    // ----------------------------------------------------------------

    /**
     * Returns the {@code [x, y]} top-left corner for a button at {@code index}
     * out of {@code total} slots, centred at {@code (cx, cy)}.
     *
     * @param index    slot index (0-based)
     * @param total    total number of slots
     * @param cx       screen centre X
     * @param cy       screen centre Y
     * @param buttonW  button width
     * @param buttonH  button height
     * @param centerW  width of the centre indicator button
     * @param screenW  screen width  (used to cap horizontal radius)
     * @param screenH  screen height (used to cap vertical radius)
     */
    public static int[] buttonPos(
            int index, int total,
            int cx, int cy,
            int buttonW, int buttonH,
            int centerW,
            int screenW, int screenH
    ) {
        if (total <= 1) return new int[]{ cx - buttonW / 2, cy - buttonH / 2 };

        int innerGapX  = (centerW / 2) + INNER_GAP_PADDING;
        int radiusYCap = Math.max(60, (screenH / 2) - (buttonH / 2) - MARGIN);
        int radiusXCap = Math.max(60, (screenW / 2) - (buttonW / 2) - MARGIN);

        int remaining  = total - 2;
        int leftCount  = remaining / 2;
        int rightCount = remaining - leftCount;

        int stepY        = buttonH + ROW_GAP;
        int maxSideCount = Math.max(leftCount, rightCount);
        int neededRadiusY = maxSideCount <= 0 ? 40 : ((maxSideCount + 1) * stepY) / 2;
        int usedRadiusY   = Math.min(Math.max(40, neededRadiusY), Math.max(40, radiusYCap));

        int topY    = cy - usedRadiusY;
        int bottomY = cy + usedRadiusY;

        // Top cap
        if (index == 0) return new int[]{ cx - buttonW / 2, topY - buttonH / 2 };

        // Bottom cap
        if (index == total - 1) return new int[]{ cx - buttonW / 2, bottomY - buttonH / 2 };

        // Side arcs
        int k          = index - 1;
        boolean rightSide = (k % 2 == 0);
        int localIndex = k / 2;
        int localCount = rightSide ? rightCount : leftCount;
        if (localCount <= 0) localCount = 1;

        double frac = (localIndex + 1.0) / (localCount + 1.0);
        int y = (int) Math.round(topY + frac * (bottomY - topY));

        double dy = y - cy;
        double t  = 1.0 - (dy * dy) / ((double) usedRadiusY * usedRadiusY);
        if (t < 0) t = 0;

        int minX   = innerGapX + buttonW / 2;
        int bulge  = (int) Math.round(usedRadiusY * BULGE_FACTOR);
        int rxWant = minX + bulge;
        int rx     = Math.min(rxWant, Math.max(minX, radiusXCap));

        int xFromCenter = (int) Math.round(Math.sqrt(t) * rx);
        if (xFromCenter < minX) xFromCenter = minX;

        int bx = rightSide ? (cx + xFromCenter) : (cx - xFromCenter);
        return new int[]{ bx - buttonW / 2, y - buttonH / 2 };
    }
}

