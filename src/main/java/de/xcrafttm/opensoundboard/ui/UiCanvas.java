package de.xcrafttm.opensoundboard.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Version-neutral drawing surface. All differences across the 26.1 render overhaul
 * ({@code GuiGraphics} -> {@code GuiGraphicsExtractor}, {@code drawString} -> {@code text})
 * are isolated here so the rest of the UI never needs Stonecutter conditionals.
 */
public final class UiCanvas {

    //? if >=26 {
    /*public final GuiGraphicsExtractor g;
    *///?} else {
    public final GuiGraphics g;
    //?}
    public final Font font;
    public final int mouseX;
    public final int mouseY;

    //? if >=26 {
    /*public UiCanvas(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
    *///?} else {
    public UiCanvas(GuiGraphics g, Font font, int mouseX, int mouseY) {
    //?}
        this.g = g;
        this.font = font;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    /** Filled rectangle. {@code fill(x1,y1,x2,y2,argb)} is identical on both draw surfaces. */
    public void fillRect(int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + h, argb);
    }

    /** 1px inner border around the rectangle. */
    public void border(int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y + 1, x + 1, y + h - 1, argb);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
    }

    /** Filled rectangle with slightly rounded corners (default {@link Theme#RADIUS}). */
    public void fillRoundRect(int x, int y, int w, int h, int argb) {
        fillRoundRect(x, y, w, h, Theme.RADIUS, argb);
    }

    public void fillRoundRect(int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            fillRect(x, y, w, h, argb);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, argb);
        for (int i = 0; i < r; i++) {
            int dy = r - 1 - i;
            int inset = r - (int) Math.floor(Math.sqrt((double) (r * r - dy * dy)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, argb);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, argb);
        }
    }

    /** 1px outline matching {@link #fillRoundRect}. */
    public void roundBorder(int x, int y, int w, int h, int argb) {
        roundBorder(x, y, w, h, Theme.RADIUS, argb);
    }

    public void roundBorder(int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            border(x, y, w, h, argb);
            return;
        }
        g.fill(x + r, y, x + w - r, y + 1, argb);
        g.fill(x + r, y + h - 1, x + w - r, y + h, argb);
        g.fill(x, y + r, x + 1, y + h - r, argb);
        g.fill(x + w - 1, y + r, x + w, y + h - r, argb);
        for (int i = 0; i < r; i++) {
            int dy = r - 1 - i;
            int inset = r - (int) Math.floor(Math.sqrt((double) (r * r - dy * dy)));
            g.fill(x + inset, y + i, x + inset + 1, y + i + 1, argb);
            g.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, argb);
            g.fill(x + inset, y + h - 1 - i, x + inset + 1, y + h - i, argb);
            g.fill(x + w - inset - 1, y + h - 1 - i, x + w - inset, y + h - i, argb);
        }
    }

    public void text(String s, int x, int y, int color) {
        float scale = UiStyle.fontScale();
        if (scale != 1F) pushTextPose(x, y, scale);
        //? if >=26 {
        /*g.text(font, s, scale == 1F ? x : 0, scale == 1F ? y : 0, color, false);
        *///?} else {
        g.drawString(font, s, scale == 1F ? x : 0, scale == 1F ? y : 0, color, false);
        //?}
        if (scale != 1F) popTextPose();
    }

    public void centeredText(Component s, int centerX, int y, int color) {
        float scale = UiStyle.fontScale();
        if (scale != 1F) pushTextPose(centerX, y, scale);
        //? if >=26 {
        /*g.centeredText(font, s, scale == 1F ? centerX : 0, scale == 1F ? y : 0, color);
        *///?} else {
        g.drawCenteredString(font, s, scale == 1F ? centerX : 0, scale == 1F ? y : 0, color);
        //?}
        if (scale != 1F) popTextPose();
    }

    private void pushTextPose(int x, int y, float scale) {
        //? if >=1.21.11 {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale);
        //?} else {
        /*g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        *///?}
    }

    private void popTextPose() {
        //? if >=1.21.11 {
        g.pose().popMatrix();
        //?} else {
        /*g.pose().popPose();
        *///?}
    }

    /** Clip subsequent drawing to this rectangle (enableScissor is identical on both surfaces). */
    public void pushScissor(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    public void popScissor() {
        g.disableScissor();
    }

    public int textWidth(String s) {
        return (int) Math.ceil(font.width(s) * UiStyle.fontScale());
    }

    public int lineHeight() {
        return (int) Math.ceil(font.lineHeight * UiStyle.fontScale());
    }

    /**
     * Visual vertical center for text inside a control. Minecraft's line height includes one
     * spacing pixel below the glyphs, so the purely mathematical center appears too high.
     */
    public int centeredTextY(int y, int height) {
        return y + (height - lineHeight()) / 2 + 1;
    }

    /** Truncate text to the configured visual font width and append an ellipsis. */
    public String trimText(String s, int maxWidth) {
        if (textWidth(s) <= maxWidth) return s;
        String ellipsis = "...";
        int rawMax = (int) Math.floor(maxWidth / UiStyle.fontScale());
        int contentWidth = Math.max(0, rawMax - font.width(ellipsis));
        return font.plainSubstrByWidth(s, contentWidth) + ellipsis;
    }

    /** Render a native Minecraft component inside the shared OpenSoundboard layout. */
    public void renderVanilla(AbstractWidget widget) {
        //? if >=26 {
        /*widget.extractRenderState(g, mouseX, mouseY, 0F);
        *///?} else {
        widget.render(g, mouseX, mouseY, 0F);
        //?}
    }

    /** True if the point (mouseX, mouseY) is inside the given rectangle. */
    public boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
