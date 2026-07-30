package de.xcrafttm.opensoundboard.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every OpenSoundboard screen. All version divergence the UI cares about is
 * isolated here, so subclasses and widgets stay 100% version-neutral:
 *  - render pipeline: {@code render(GuiGraphics)} (1.21.x) vs
 *    {@code extractRenderState(GuiGraphicsExtractor)} (26.x);
 *  - input: primitive args (1.21.1) vs event objects (>=1.21.11).
 */
public abstract class OsbScreen extends Screen {

    private static final int VANILLA_SCREEN_BOX = 0x66000000;

    protected final List<Widget> widgets = new ArrayList<>();
    private Widget focused;
    private Widget dragging;

    protected OsbScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        widgets.clear();
        focused = null;
        dragging = null;
        buildUi();
    }

    /** Create and add widgets. May use {@code this.width}/{@code this.height}. */
    protected abstract void buildUi();

    /** Optional custom drawing behind the widgets (scrim, panels, titles). */
    protected void renderContent(UiCanvas c) {
    }

    /** Whether screen-specific custom backgrounds and panels should be drawn. */
    protected final boolean useCustomBackground() {
        return !UiStyle.useVanillaComponents();
    }

    /** Applies the user-selected width to the layout's original responsive width. */
    protected final int screenBoxWidth(int previousMinimumWidth) {
        int previousWidth = Math.max(previousMinimumWidth, (int) (this.width * 0.6F));
        int availableWidth = Math.max(1, this.width - Theme.PAD * 2);
        return Math.min(availableWidth, Math.round(previousWidth * UiStyle.uiWidthScale()));
    }

    /** Applies the user-selected height to every regular screen box. */
    protected final int screenBoxHeight() {
        int availableHeight = Math.max(1, this.height - Theme.PAD);
        return Math.min(availableHeight, Math.round(this.height * UiStyle.uiHeightScale()));
    }

    /** Draw the shared custom frame or a simple translucent box in vanilla mode. */
    protected final void renderScreenBox(UiCanvas c, int x, int y, int width, int height) {
        if (useCustomBackground()) {
            c.fillRect(0, 0, this.width, this.height, Theme.SCRIM);
            c.fillRoundRect(x, y, width, height, Theme.PANEL);
            c.roundBorder(x, y, width, height, Theme.BORDER);
            c.fillRect(x + Theme.RADIUS, y, width - Theme.RADIUS * 2, 3, Theme.ACCENT);
        } else {
            c.fillRect(x, y, width, height, VANILLA_SCREEN_BOX);
        }
    }

    /** Optional screen-level key handling (e.g. Enter to confirm), before focus routing. */
    protected boolean screenKeyPressed(int key, int scan, int mods) {
        return false;
    }

    /** Optional screen-level click handling, after no widget consumed the click. */
    protected boolean screenMouseClicked(double mx, double my, int button) {
        return false;
    }

    protected <T extends Widget> T add(T widget) {
        widgets.add(widget);
        return widget;
    }

    /** Rebuild the widget list in place (e.g. when a toggle changes what controls are shown). */
    protected void rebuildUi() {
        widgets.clear();
        focused = null;
        dragging = null;
        buildUi();
    }

    protected void setFocused(Widget widget) {
        if (focused == widget) return;
        if (focused != null) focused.setFocused(false);
        focused = widget;
        if (focused != null) focused.setFocused(true);
    }

    private void paint(UiCanvas c) {
        renderContent(c);
        for (Widget w : widgets) {
            if (w.visible) w.draw(c);
        }
        drawTooltip(c);
    }

    private void drawTooltip(UiCanvas c) {
        String tip = null;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w.visible && w.contains(c.mouseX, c.mouseY)) {
                tip = w.tooltipAt(c.mouseX, c.mouseY);
                break;
            }
        }
        if (tip == null) return;
        if (UiStyle.useVanillaComponents()) {
            int tooltipWidth = Math.max(40, Math.min(170, this.width - 20));
            var lines = c.font.split(Component.literal(tip), tooltipWidth);
            //? if >=1.21.11 {
            c.g.setTooltipForNextFrame(lines, c.mouseX, c.mouseY);
            //?} else {
            /*setTooltipForNextRenderPass(lines);
            *///?}
            return;
        }
        String[] lines = tip.split("\n");
        int tw = 0;
        for (String ln : lines) tw = Math.max(tw, c.textWidth(ln));
        int pad = 4;
        int lh = c.lineHeight() + 1;
        int bw = tw + pad * 2;
        int bh = lines.length * lh + pad * 2 - 2;
        int bx = Math.min(c.mouseX + 10, this.width - bw - 2);
        int by = Math.min(Math.max(2, c.mouseY - bh - 4), this.height - bh - 2);
        c.fillRoundRect(bx, by, bw, bh, 0xF01A1A22);
        c.roundBorder(bx, by, bw, bh, Theme.ACCENT);
        for (int i = 0; i < lines.length; i++) c.text(lines[i], bx + pad, by + pad + i * lh, Theme.TEXT);
    }

    // ---- render entrypoint (26.1 render overhaul) --------------------------------------
    //? if >=26 {
    /*@Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        paint(new UiCanvas(g, this.font, mouseX, mouseY));
    }
    *///?} else {
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        paint(new UiCanvas(g, this.font, mouseX, mouseY));
    }
    //?}

    // ---- input (1.21.11 input overhaul: primitives -> event objects) -------------------
    //? if >=1.21.11 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e, boolean doubleClick) {
        if (dispatchClick(e.x(), e.y(), e.button())) return true;
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent e, double dragX, double dragY) {
        if (dragging != null) {
            dragging.mouseDragged(e.x(), e.y(), e.button());
            return true;
        }
        return super.mouseDragged(e, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent e) {
        if (dragging != null) {
            dragging.mouseReleased(e.x(), e.y(), e.button());
            dragging = null;
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (dispatchKey(e.key(), e.scancode(), e.modifiers())) return true;
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        if (dispatchChar((char) e.codepoint())) return true;
        return super.charTyped(e);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (dispatchClick(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (dragging != null) {
            dragging.mouseDragged(mx, my, button);
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null) {
            dragging.mouseReleased(mx, my, button);
            dragging = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (dispatchKey(key, scan, mods)) return true;
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char ch, int mods) {
        if (dispatchChar(ch)) return true;
        return super.charTyped(ch, mods);
    }
    *///?}

    // mouseScrolled has the same signature on every target
    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w.visible && w.contains(mx, my) && w.mouseScrolled(mx, my, vertical)) return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }

    private boolean dispatchClick(double mx, double my, int button) {
        Widget hit = null;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w.visible && w.active && w.contains(mx, my) && w.mouseClicked(mx, my, button)) {
                hit = w;
                break;
            }
        }
        setFocused(hit != null && hit.focusable() ? hit : null);
        if (hit != null) {
            dragging = hit;
            return true;
        }
        return screenMouseClicked(mx, my, button);
    }

    private boolean dispatchKey(int key, int scan, int mods) {
        if (screenKeyPressed(key, scan, mods)) return true;
        return focused != null && focused.keyPressed(key, scan, mods);
    }

    private boolean dispatchChar(char ch) {
        return focused != null && focused.charTyped(ch);
    }

    @Override
    public void tick() {
        super.tick();
        for (Widget w : widgets) w.tick();
    }
}
