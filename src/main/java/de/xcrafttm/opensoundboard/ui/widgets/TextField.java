package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.tools.Keys;
import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.function.Consumer;

/**
 * Single-line editable text field with a caret, selection (drag or shift+arrows), horizontal
 * scroll, and clipboard support (Ctrl+A/C/X/V). Focus is managed by {@link de.xcrafttm.opensoundboard.ui.OsbScreen}.
 * Uses only Mojang-mapped names that are identical across the whole span, so no conditionals.
 */
public class TextField extends Widget {

    private final StringBuilder text = new StringBuilder();
    private int cursor = 0;
    private int selAnchor = 0;
    private String placeholder = "";
    private int maxLength = 256;
    private Consumer<String> onChange;
    private int blink = 0;
    private int scrollPx = 0;
    private Icons icon;
    private boolean invalid = false;

    public TextField placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /** Small pixel icon drawn at the left edge (e.g. a magnifier for search fields). */
    public TextField icon(Icons icon) {
        this.icon = icon;
        return this;
    }

    /** Marks the current content as invalid (red outline in the modern style). */
    public void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    public TextField maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public TextField onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    public String getText() {
        return text.toString();
    }

    public void setText(String s) {
        text.setLength(0);
        text.append(s == null ? "" : s);
        if (text.length() > maxLength) text.setLength(maxLength);
        cursor = selAnchor = text.length();
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void setFocused(boolean f) {
        super.setFocused(f);
        if (!f) selAnchor = cursor;
    }

    @Override
    public void tick() {
        blink++;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private int selStart() {
        return Math.min(cursor, selAnchor);
    }

    private int selEnd() {
        return Math.max(cursor, selAnchor);
    }

    private boolean hasSelection() {
        return cursor != selAnchor;
    }

    @Override
    public void draw(UiCanvas c) {
        boolean vanilla = UiStyle.useVanillaComponents();
        if (vanilla) {
            c.sprite(focused ? "widget/text_field_highlighted" : "widget/text_field", x, y, w, h);
        } else {
            boolean hover = active && c.hovered(x, y, w, h);
            c.fillRoundRect(x, y, w, h, Theme.field);
            int outline = invalid ? Theme.DANGER : (focused ? Theme.accent : (hover ? Theme.borderStrong : Theme.border));
            c.roundBorder(x, y, w, h, outline);
        }
        if (icon != null) {
            c.icon(icon, x + 5, y + (h - icon.height) / 2,
                    vanilla ? UiStyle.VANILLA_HINT : (focused ? Theme.textMuted : Theme.textFaint));
        }
        drawContents(c, vanilla);
    }

    private int textLeft() {
        return x + (icon != null ? icon.width + 9 : 5);
    }

    private void drawContents(UiCanvas c, boolean vanilla) {
        String s = text.toString();
        int tx = textLeft();
        int textHeight = c.lineHeight();
        int ty = y + (h - textHeight) / 2 + 1;
        int innerW = x + w - 5 - tx;

        int cursorX = scaledWidth(s.substring(0, cursor));
        if (cursorX - scrollPx > innerW) scrollPx = cursorX - innerW;
        if (cursorX - scrollPx < 0) scrollPx = cursorX;
        int fullW = scaledWidth(s);
        if (fullW - scrollPx < innerW) scrollPx = Math.max(0, fullW - innerW);

        int textColor = vanilla ? (active ? 0xFFE0E0E0 : 0xFF707070) : (active ? Theme.text : Theme.textFaint);
        int hintColor = vanilla ? UiStyle.VANILLA_HINT : Theme.textFaint;

        c.pushScissor(x + 1, y + 1, w - 2, h - 2);
        if (s.isEmpty() && !focused) {
            c.text(c.trimText(placeholder, innerW), tx, ty, hintColor);
        } else {
            if (hasSelection()) {
                int a = scaledWidth(s.substring(0, selStart())) - scrollPx;
                int b = scaledWidth(s.substring(0, selEnd())) - scrollPx;
                c.fillRect(tx + a, ty - 1, b - a, textHeight, vanilla ? 0xFF0000FF : Theme.selection);
            }
            c.text(s, tx - scrollPx, ty, textColor);
            if (focused && (blink / 6) % 2 == 0) {
                c.fillRect(tx + cursorX - scrollPx, ty - 1, 1, textHeight, vanilla ? 0xFFD0D0D0 : Theme.accent);
            }
        }
        c.popScissor();
    }

    private int indexAtX(double mx) {
        String s = text.toString();
        int rel = (int) mx - textLeft() + scrollPx;
        if (rel <= 0) return 0;
        for (int i = 1; i <= s.length(); i++) {
            int wPrev = scaledWidth(s.substring(0, i - 1));
            int wCur = scaledWidth(s.substring(0, i));
            if (wCur >= rel) {
                return (rel - wPrev < wCur - rel) ? i - 1 : i;
            }
        }
        return s.length();
    }

    private static int scaledWidth(String value) {
        return (int) Math.ceil(font().width(value) * UiStyle.fontScale());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !active) return false;
        cursor = selAnchor = indexAtX(mx);
        blink = 0;
        return true;
    }

    @Override
    public void mouseDragged(double mx, double my, int button) {
        if (button == 0 && active) cursor = indexAtX(mx);
    }

    @Override
    public boolean charTyped(char ch) {
        if (!focused || ch < 32 || ch == 127) return false;
        if (hasSelection()) deleteSelection();
        if (text.length() >= maxLength) return true;
        text.insert(cursor, ch);
        cursor = selAnchor = cursor + 1;
        fireChange();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!focused) return false;
        boolean ctrl = (mods & Keys.MOD_CONTROL) != 0;
        boolean shift = (mods & Keys.MOD_SHIFT) != 0;

        if (ctrl) {
            switch (key) {
                case Keys.A -> {
                    selAnchor = 0;
                    cursor = text.length();
                    return true;
                }
                case Keys.C -> {
                    if (hasSelection()) clipboard().setClipboard(text.substring(selStart(), selEnd()));
                    return true;
                }
                case Keys.X -> {
                    if (hasSelection()) {
                        clipboard().setClipboard(text.substring(selStart(), selEnd()));
                        deleteSelection();
                        fireChange();
                    }
                    return true;
                }
                case Keys.V -> {
                    paste(clipboard().getClipboard());
                    return true;
                }
                default -> {
                }
            }
        }

        switch (key) {
            case Keys.BACKSPACE -> {
                if (hasSelection()) deleteSelection();
                else if (cursor > 0) {
                    text.deleteCharAt(cursor - 1);
                    cursor--;
                }
                selAnchor = cursor;
                fireChange();
                return true;
            }
            case Keys.DELETE -> {
                if (hasSelection()) deleteSelection();
                else if (cursor < text.length()) text.deleteCharAt(cursor);
                selAnchor = cursor;
                fireChange();
                return true;
            }
            case Keys.LEFT -> {
                if (cursor > 0) cursor--;
                if (!shift) selAnchor = cursor;
                blink = 0;
                return true;
            }
            case Keys.RIGHT -> {
                if (cursor < text.length()) cursor++;
                if (!shift) selAnchor = cursor;
                blink = 0;
                return true;
            }
            case Keys.HOME -> {
                cursor = 0;
                if (!shift) selAnchor = cursor;
                return true;
            }
            case Keys.END -> {
                cursor = text.length();
                if (!shift) selAnchor = cursor;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void deleteSelection() {
        int a = selStart();
        int b = selEnd();
        text.delete(a, b);
        cursor = selAnchor = a;
    }

    private void paste(String clip) {
        if (clip == null || clip.isEmpty()) return;
        clip = clip.replaceAll("[\\r\\n\\t]", " ");
        if (hasSelection()) deleteSelection();
        int space = maxLength - text.length();
        if (space <= 0) return;
        if (clip.length() > space) clip = clip.substring(0, space);
        text.insert(cursor, clip);
        cursor = selAnchor = cursor + clip.length();
        fireChange();
    }

    private static net.minecraft.client.KeyboardHandler clipboard() {
        return Minecraft.getInstance().keyboardHandler;
    }

    private void fireChange() {
        blink = 0;
        if (onChange != null) onChange.accept(text.toString());
    }
}
