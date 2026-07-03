package de.xcrafttm.opensoundboard.ui;

/**
 * Base class for all UI components. Fully version-neutral: it receives already-extracted
 * primitive input (the base screen unwraps the version-specific event objects and manages
 * focus).
 */
public abstract class Widget {

    public int x;
    public int y;
    public int w;
    public int h;
    public boolean visible = true;
    public boolean active = true;

    /** Whether this widget currently holds keyboard focus (managed by {@link OsbScreen}). */
    protected boolean focused = false;

    public Widget bounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public boolean isFocused() {
        return focused;
    }

    /** Whether this widget wants keyboard focus after being clicked. */
    public boolean focusable() {
        return false;
    }

    public abstract void draw(UiCanvas c);

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public void mouseDragged(double mx, double my, int button) {
    }

    public void mouseReleased(double mx, double my, int button) {
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        return false;
    }

    public boolean charTyped(char ch) {
        return false;
    }

    public void tick() {
    }
}
