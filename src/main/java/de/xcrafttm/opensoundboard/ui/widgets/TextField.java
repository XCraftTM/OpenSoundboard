package de.xcrafttm.opensoundboard.ui.widgets;

import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.Widget;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

/** Single-line editable text field with a blinking caret. Focus is managed by the screen. */
public class TextField extends Widget {

    private final StringBuilder text = new StringBuilder();
    private int cursor = 0;
    private String placeholder = "";
    private int maxLength = 256;
    private Consumer<String> onChange;
    private int blink = 0;

    public TextField placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
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
        cursor = text.length();
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void tick() {
        blink++;
    }

    @Override
    public void draw(UiCanvas c) {
        c.fillRect(x, y, w, h, 0xFF0F0F14);
        c.border(x, y, w, h, focused ? Theme.ACCENT : Theme.BORDER);
        int tx = x + 5;
        int ty = y + (h - 8) / 2;
        if (text.length() == 0 && !focused) {
            c.text(placeholder, tx, ty, Theme.TEXT_MUTED);
        } else {
            String s = text.toString();
            c.text(s, tx, ty, Theme.TEXT);
            if (focused && (blink / 6) % 2 == 0) {
                int cx = tx + c.textWidth(s.substring(0, cursor));
                c.fillRect(cx, ty - 1, 1, 10, Theme.TEXT);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && active) {
            cursor = text.length();
            blink = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char ch) {
        if (!focused || text.length() >= maxLength) return false;
        if (ch < 32 || ch == 127) return false;
        text.insert(cursor, ch);
        cursor++;
        fireChange();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!focused) return false;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor > 0) {
                    text.deleteCharAt(cursor - 1);
                    cursor--;
                    fireChange();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor < text.length()) {
                    text.deleteCharAt(cursor);
                    fireChange();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (cursor > 0) cursor--;
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (cursor < text.length()) cursor++;
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                cursor = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                cursor = text.length();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void fireChange() {
        blink = 0;
        if (onChange != null) onChange.accept(text.toString());
    }
}
