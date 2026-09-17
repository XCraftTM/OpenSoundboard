package de.xcrafttm.opensoundboard.ui;

import de.xcrafttm.opensoundboard.ui.widgets.Button;
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
 *
 * It also owns the shared screen frame. The modern style draws a centered panel with a title bar;
 * the vanilla style mirrors Minecraft's own option screens: a centered title in a 33px header, a
 * list background between separators, and a footer row of buttons.
 */
public abstract class OsbScreen extends Screen {

    protected final List<Widget> widgets = new ArrayList<>();
    private Widget focused;
    private Widget dragging;

    /** Outer frame (modern panel, or the whole screen in vanilla style). */
    protected int frameX, frameY, frameW, frameH;
    /** Content area inside the frame, below the header and above the footer. */
    protected int bodyX, bodyY, bodyW, bodyH;
    /** Top edge of the footer strip. */
    protected int footerY;
    private int footerH;
    private int headerH;
    private int headerButtons;

    protected OsbScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        Theme.sync();
        rebuildUi();
    }

    /** Create and add widgets. May use {@code this.width}/{@code this.height}. */
    protected abstract void buildUi();

    /** Optional custom drawing behind the widgets (frame, titles, decorations). */
    protected void renderContent(UiCanvas c) {
    }

    protected final boolean vanilla() {
        return UiStyle.useVanillaComponents();
    }

    // ---- frame layout ---------------------------------------------------------------------

    /**
     * Compute the frame and body rectangles.
     *
     * @param minWidth      minimum panel width before the user's width scale is applied
     * @param modernFooterH height of the modern footer strip, or 0 for none
     */
    protected final void layoutFrame(int minWidth, int modernFooterH) {
        layoutFrame(minWidth, modernFooterH, UiStyle.VANILLA_HEADER_H);
    }

    protected final void layoutFrame(int minWidth, int modernFooterH, int vanillaHeaderH) {
        headerButtons = 0;
        int contentW = screenBoxWidth(minWidth);
        if (vanilla()) {
            headerH = vanillaHeaderH;
            footerH = UiStyle.VANILLA_FOOTER_H;
            frameX = 0;
            frameY = 0;
            frameW = this.width;
            frameH = this.height;
            footerY = this.height - footerH;
            bodyW = contentW;
            bodyX = (this.width - bodyW) / 2;
            bodyY = headerH + 6;
            bodyH = footerY - 6 - bodyY;
        } else {
            headerH = Theme.HEADER_H;
            footerH = modernFooterH;
            frameW = contentW;
            frameH = screenBoxHeight();
            frameX = (this.width - frameW) / 2;
            frameY = (this.height - frameH) / 2;
            footerY = frameY + frameH - footerH;
            bodyX = frameX + Theme.PAD;
            bodyW = frameW - Theme.PAD * 2;
            bodyY = frameY + headerH + Theme.PAD;
            bodyH = footerY - Theme.PAD - bodyY;
        }
    }

    /** Applies the user-selected width to the layout's responsive width. */
    protected final int screenBoxWidth(int minimumWidth) {
        int responsive = Math.max(minimumWidth, (int) (this.width * 0.6F));
        int available = Math.max(1, this.width - Theme.PAD * 2);
        return Math.min(available, Math.round(responsive * UiStyle.uiWidthScale()));
    }

    /** Applies the user-selected height to every modern screen panel. */
    protected final int screenBoxHeight() {
        int available = Math.max(1, this.height - Theme.PAD);
        return Math.min(available, Math.round(this.height * UiStyle.uiHeightScale()));
    }

    /** Draw the frame for the current style: panel + title bar, or vanilla header/list/footer. */
    protected final void renderFrame(UiCanvas c, String title) {
        if (vanilla()) {
            c.vanillaListBackground(0, headerH, this.width, footerY - headerH);
            c.vanillaSeparator(true, 0, headerH - 2, this.width);
            c.vanillaSeparator(false, 0, footerY, this.width);
            if (title != null) {
                c.centeredText(title, this.width / 2, (headerH - c.lineHeight()) / 2 + 1, UiStyle.VANILLA_TEXT);
            }
            return;
        }

        c.fillRect(0, 0, this.width, this.height, Theme.scrim);
        c.fillRoundRect(frameX, frameY, frameW, frameH, Theme.panel);
        c.fillRect(frameX + 1, frameY + 1, frameW - 2, headerH - 1, Theme.header);
        c.hLine(frameX + 1, frameY + headerH, frameW - 2, Theme.border);
        if (footerH > 0) {
            c.fillRect(frameX + 1, footerY, frameW - 2, footerH - 1, Theme.header);
            c.hLine(frameX + 1, footerY, frameW - 2, Theme.border);
        }
        c.roundBorder(frameX, frameY, frameW, frameH, Theme.borderStrong);
        if (title != null) {
            int reserved = headerButtons * 18 + Theme.PAD;
            c.text(c.trimText(title, frameW - Theme.PAD * 2 - reserved), frameX + Theme.PAD,
                    c.centeredTextY(frameY, headerH + 1), Theme.text);
        }
    }

    /** Add a small icon button to the right side of the modern title bar (right to left). */
    protected final Button addHeaderButton(Icons icon, String tooltip, Runnable action) {
        int size = 16;
        int x = frameX + frameW - 3 - size - headerButtons * (size + 2);
        int y = frameY + (headerH - size) / 2 + 1;
        headerButtons++;
        Button button = add(new Button(icon, null, b -> action.run()).ghost());
        button.bounds(x, y, size, size).tooltip(tooltip);
        return button;
    }

    /** Lay out buttons in the footer: centered vanilla row, or right-aligned in the modern strip. */
    protected final void layoutFooter(Widget... buttons) {
        int n = buttons.length;
        if (n == 0) return;
        if (vanilla()) {
            int gap = 8;
            int maxRow = Math.min(this.width - 16, n == 1 ? 200 : Math.max(310, bodyW));
            int bw = Math.min(n == 1 ? 200 : 150, (maxRow - gap * (n - 1)) / n);
            int total = bw * n + gap * (n - 1);
            int x = (this.width - total) / 2;
            int y = footerY + (footerH - 20) / 2 + 1;
            for (Widget b : buttons) {
                b.bounds(x, y, bw, 20);
                x += bw + gap;
            }
        } else {
            int h = 16;
            int x = frameX + frameW - Theme.PAD;
            int y = footerY + (footerH - h) / 2;
            for (int i = n - 1; i >= 0; i--) {
                Widget b = buttons[i];
                int bw = b.w > 0 ? b.w : 80;
                x -= bw;
                b.bounds(x, y, bw, h);
                x -= Theme.GAP;
            }
        }
    }

    // ---- widgets --------------------------------------------------------------------------

    /** Optional screen-level key handling (e.g. Enter to confirm), before focus routing. */
    protected boolean screenKeyPressed(int key, int scan, int mods) {
        return false;
    }

    /** Optional screen-level character handling, when no focused widget consumed it. */
    protected boolean screenCharTyped(char ch) {
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
        headerButtons = 0;
        buildUi();
    }

    protected void setFocused(Widget widget) {
        if (focused == widget) return;
        if (focused != null) focused.setFocused(false);
        focused = widget;
        if (focused != null) focused.setFocused(true);
    }

    private void paint(UiCanvas c) {
        Theme.sync();
        renderContent(c);
        for (Widget w : widgets) {
            if (w.visible) w.draw(c);
        }
        drawTooltip(c);
    }

    private void drawTooltip(UiCanvas c) {
        if (dragging != null) return;
        String tip = null;
        for (int i = widgets.size() - 1; i >= 0; i--) {
            Widget w = widgets.get(i);
            if (w.visible && w.contains(c.mouseX, c.mouseY)) {
                tip = w.tooltipAt(c.mouseX, c.mouseY);
                break;
            }
        }
        if (tip == null || tip.isBlank()) return;
        if (vanilla()) {
            int tooltipWidth = Math.max(40, Math.min(200, this.width - 20));
            var lines = c.font.split(Component.literal(tip), tooltipWidth);
            //? if >=1.21.11 {
            c.g.setTooltipForNextFrame(lines, c.mouseX, c.mouseY);
            //?} else {
            /*setTooltipForNextRenderPass(lines);
            *///?}
            return;
        }

        List<String> lines = TextWrap.wrap(c.font, tip, Math.max(60, Math.min(200, this.width - 24)), 0);
        int tw = 0;
        for (String ln : lines) tw = Math.max(tw, c.textWidth(ln));
        int pad = 4;
        int lh = c.lineHeight() + 1;
        int bw = tw + pad * 2;
        int bh = lines.size() * lh + pad * 2 - 1;
        int bx = Math.max(2, Math.min(c.mouseX + 10, this.width - bw - 2));
        int by = c.mouseY - bh - 6;
        if (by < 2) by = Math.min(this.height - bh - 2, c.mouseY + 14);
        c.fillRoundRect(bx, by, bw, bh, Theme.opaque(Theme.header));
        c.roundBorder(bx, by, bw, bh, Theme.borderStrong);
        for (int i = 0; i < lines.size(); i++) {
            c.text(lines.get(i), bx + pad, by + pad + i * lh, i == 0 ? Theme.text : Theme.textMuted);
        }
    }

    // ---- render entrypoint (26.1 render overhaul) --------------------------------------
    //? if >=26 {
    @Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        paint(new UiCanvas(g, this.font, mouseX, mouseY));
    }
    //?} else {
    /*@Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        paint(new UiCanvas(g, this.font, mouseX, mouseY));
    }
    *///?}

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
        //? if >=26.3 {
        if (dispatchKey(e.key(), e.keycode(), e.modifiers())) return true;
        //?} else {
        /*if (dispatchKey(e.key(), e.scancode(), e.modifiers())) return true;
        *///?}
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
        if (focused != null && focused.charTyped(ch)) return true;
        return screenCharTyped(ch);
    }

    @Override
    public void tick() {
        super.tick();
        for (Widget w : widgets) w.tick();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
