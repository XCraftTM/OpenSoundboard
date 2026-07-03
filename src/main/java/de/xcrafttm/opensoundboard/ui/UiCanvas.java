package de.xcrafttm.opensoundboard.ui;

import net.minecraft.client.gui.Font;
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

    public void text(String s, int x, int y, int color) {
        //? if >=26 {
        /*g.text(font, s, x, y, color, false);
        *///?} else {
        g.drawString(font, s, x, y, color, false);
        //?}
    }

    public void centeredText(Component s, int centerX, int y, int color) {
        //? if >=26 {
        /*g.centeredText(font, s, centerX, y, color);
        *///?} else {
        g.drawCenteredString(font, s, centerX, y, color);
        //?}
    }

    /** Clip subsequent drawing to this rectangle (enableScissor is identical on both surfaces). */
    public void pushScissor(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    public void popScissor() {
        g.disableScissor();
    }

    public int textWidth(String s) {
        return font.width(s);
    }

    public int lineHeight() {
        return font.lineHeight;
    }

    /** True if the point (mouseX, mouseY) is inside the given rectangle. */
    public boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
