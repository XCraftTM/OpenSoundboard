package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.TextWrap;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.client.gui.Font;

import java.util.List;

/**
 * Modern settings row: a title with a short description (wrapped to at most two lines) on the
 * left; the control itself is a separate widget placed in the reserved space on the right.
 * Clicking the row can forward to the control (e.g. toggling a checkbox).
 */
public class OptionRow extends Widget {

    public static final int MAX_DESCRIPTION_LINES = 2;
    private static final int PAD_Y = 6;

    private final String title;
    private final String description;
    private final int controlWidth;
    private Runnable onClick;
    private boolean alignTop = false;

    public OptionRow(String title, String description, int controlWidth) {
        this.title = title;
        this.description = description;
        this.controlWidth = controlWidth;
    }

    public OptionRow onClick(Runnable onClick) {
        this.onClick = onClick;
        return this;
    }

    /** Pin the text to the top of the row (for rows with extra controls underneath). */
    public OptionRow alignTop() {
        this.alignTop = true;
        return this;
    }

    /** Width available for the title and description. */
    public static int textWidth(int rowWidth, int controlWidth) {
        return rowWidth - controlWidth - 20;
    }

    /** Height of the text block (title + wrapped description) for layout. */
    public static int textBlockHeight(Font font, int lineHeight, String description, int textWidth) {
        if (description == null || description.isBlank()) return lineHeight;
        int lines = TextWrap.wrap(font, description, textWidth, MAX_DESCRIPTION_LINES).size();
        return lineHeight + 2 + lines * (lineHeight + 1);
    }

    /** Row height that fits the text block and a control of {@code controlHeight}. */
    public static int rowHeight(Font font, int lineHeight, String description, int textWidth, int controlHeight) {
        return Math.max(controlHeight, textBlockHeight(font, lineHeight, description, textWidth)) + PAD_Y * 2;
    }

    @Override
    public void draw(UiCanvas c) {
        boolean hover = active && c.hovered(x, y, w, h);
        if (hover) c.fillRoundRect(x, y, w, h, Theme.surface);

        int textW = textWidth(w, controlWidth);
        int lh = c.lineHeight();
        int blockH = textBlockHeight(c.font, lh, description, textW);
        int ty = alignTop ? y + PAD_Y : y + (h - blockH) / 2 + 1;
        c.text(c.trimText(title, textW), x + 6, ty, active ? Theme.text : Theme.textFaint);
        if (description != null && !description.isBlank()) {
            List<String> lines = TextWrap.wrap(c.font, description, textW, MAX_DESCRIPTION_LINES);
            for (int i = 0; i < lines.size(); i++) {
                c.text(lines.get(i), x + 6, ty + lh + 2 + i * (lh + 1), active ? Theme.textMuted : Theme.textFaint);
            }
        }
    }

    @Override
    public String tooltipAt(double mx, double my) {
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || onClick == null) return false;
        onClick.run();
        return true;
    }
}
