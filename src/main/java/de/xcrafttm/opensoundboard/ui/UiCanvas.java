package de.xcrafttm.opensoundboard.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
//? if >=26 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}

import java.util.HashMap;
import java.util.Map;

/**
 * Version-neutral drawing surface. All differences across the 26.1 render overhaul
 * ({@code GuiGraphics} -> {@code GuiGraphicsExtractor}, {@code drawString} -> {@code text}) and the
 * 1.21.x sprite pipeline changes are isolated here so the rest of the UI never needs Stonecutter
 * conditionals.
 */
public final class UiCanvas {

    //? if >=26 {
    public final GuiGraphicsExtractor g;
    //?} else {
    /*public final GuiGraphics g;
    *///?}
    public final Font font;
    public final int mouseX;
    public final int mouseY;

    //? if >=26 {
    public UiCanvas(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
    //?} else {
    /*public UiCanvas(GuiGraphics g, Font font, int mouseX, int mouseY) {
    *///?}
        this.g = g;
        this.font = font;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    // ---- shapes ---------------------------------------------------------------------------

    /** Filled rectangle. {@code fill(x1,y1,x2,y2,argb)} is identical on both draw surfaces. */
    public void fillRect(int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, argb);
    }

    public void hLine(int x, int y, int w, int argb) {
        fillRect(x, y, w, 1, argb);
    }

    public void vLine(int x, int y, int h, int argb) {
        fillRect(x, y, 1, h, argb);
    }

    /** 1px inner border around the rectangle. */
    public void border(int x, int y, int w, int h, int argb) {
        fillRect(x, y, w, 1, argb);
        fillRect(x, y + h - 1, w, 1, argb);
        fillRect(x, y + 1, 1, h - 2, argb);
        fillRect(x + w - 1, y + 1, 1, h - 2, argb);
    }

    /** Filled rectangle using the theme's corner radius. */
    public void fillRoundRect(int x, int y, int w, int h, int argb) {
        fillRoundRect(x, y, w, h, Theme.radius, argb);
    }

    public void fillRoundRect(int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            fillRect(x, y, w, h, argb);
            return;
        }
        fillRect(x, y + r, w, h - r * 2, argb);
        for (int i = 0; i < r; i++) {
            int inset = r - i;
            fillRect(x + inset, y + i, w - inset * 2, 1, argb);
            fillRect(x + inset, y + h - 1 - i, w - inset * 2, 1, argb);
        }
    }

    /** 1px outline matching {@link #fillRoundRect}. */
    public void roundBorder(int x, int y, int w, int h, int argb) {
        roundBorder(x, y, w, h, Theme.radius, argb);
    }

    public void roundBorder(int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            border(x, y, w, h, argb);
            return;
        }
        fillRect(x + r, y, w - r * 2, 1, argb);
        fillRect(x + r, y + h - 1, w - r * 2, 1, argb);
        fillRect(x, y + r, 1, h - r * 2, argb);
        fillRect(x + w - 1, y + r, 1, h - r * 2, argb);
        for (int i = 1; i < r; i++) {
            int inset = r - i;
            fillRect(x + inset, y + i, 1, 1, argb);
            fillRect(x + w - 1 - inset, y + i, 1, 1, argb);
            fillRect(x + inset, y + h - 1 - i, 1, 1, argb);
            fillRect(x + w - 1 - inset, y + h - 1 - i, 1, 1, argb);
        }
    }

    // ---- text -----------------------------------------------------------------------------

    /** Draw text; vanilla style gets Minecraft's drop shadow, the modern style stays flat. */
    public void text(String s, int x, int y, int color) {
        text(s, x, y, color, UiStyle.useVanillaComponents());
    }

    public void text(String s, int x, int y, int color, boolean shadow) {
        float scale = UiStyle.fontScale();
        if (scale != 1F) pushTextPose(x, y, scale);
        int dx = scale == 1F ? x : 0;
        int dy = scale == 1F ? y : 0;
        //? if >=26 {
        g.text(font, s, dx, dy, color, shadow);
        //?} else {
        /*g.drawString(font, s, dx, dy, color, shadow);
        *///?}
        if (scale != 1F) popTextPose();
    }

    public void centeredText(String s, int centerX, int y, int color) {
        text(s, centerX - textWidth(s) / 2, y, color);
    }

    public void centeredText(Component s, int centerX, int y, int color) {
        centeredText(s.getString(), centerX, y, color);
    }

    public void rightText(String s, int rightX, int y, int color) {
        text(s, rightX - textWidth(s), y, color);
    }

    /**
     * Centered text inside a fixed width. If it does not fit, it slowly scrolls back and forth
     * inside the box, the same way Minecraft's own buttons handle long labels.
     */
    public void fitText(String s, int x, int y, int w, int color) {
        int tw = textWidth(s);
        if (tw <= w) {
            text(s, x + (w - tw) / 2, y, color);
            return;
        }
        int overflow = tw - w;
        double seconds = System.currentTimeMillis() / 1000.0;
        double period = Math.max(overflow * 0.5, 3.0);
        double t = Math.sin(Math.PI / 2 * Math.cos(Math.PI * 2 * seconds / period)) / 2 + 0.5;
        int offset = (int) Math.round(t * overflow);
        pushScissor(x, y - 1, w, lineHeight() + 2);
        text(s, x - offset, y, color);
        popScissor();
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

    // ---- icons ----------------------------------------------------------------------------

    public void icon(Icons icon, int x, int y, int color) {
        if (UiStyle.useVanillaComponents()) icon.draw(this, x + 1, y + 1, shadowColor(color));
        icon.draw(this, x, y, color);
    }

    /** Draw an icon centered inside the given box. */
    public void icon(Icons icon, int x, int y, int w, int h, int color) {
        icon(icon, x + (w - icon.width) / 2, y + (h - icon.height) / 2, color);
    }

    private static int shadowColor(int color) {
        return (color & 0xFF000000) | ((color & 0xFCFCFC) >> 2);
    }

    // ---- vanilla textures -----------------------------------------------------------------

    //? if >=1.21.11 {
    private static final Map<String, net.minecraft.resources.Identifier> IDS = new HashMap<>();

    private static net.minecraft.resources.Identifier id(String path) {
        return IDS.computeIfAbsent(path, net.minecraft.resources.Identifier::withDefaultNamespace);
    }
    //?} else {
    /*private static final Map<String, net.minecraft.resources.ResourceLocation> IDS = new HashMap<>();

    private static net.minecraft.resources.ResourceLocation id(String path) {
        return IDS.computeIfAbsent(path, net.minecraft.resources.ResourceLocation::withDefaultNamespace);
    }
    *///?}

    /** Draw a nine-sliced GUI sprite such as {@code widget/button} from the vanilla atlas. */
    public void sprite(String path, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        //? if >=1.21.11 {
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id(path), x, y, w, h);
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        g.blitSprite(id(path), x, y, w, h);
        *///?}
    }

    private static boolean inWorld() {
        return Minecraft.getInstance().level != null;
    }

    /** The darker tiled background Minecraft uses behind scrolling lists. */
    public void vanillaListBackground(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        String path = inWorld() ? "textures/gui/inworld_menu_list_background.png" : "textures/gui/menu_list_background.png";
        //? if >=26 {
        net.minecraft.client.gui.screens.Screen.extractMenuBackgroundTexture(g, id(path), x, y, 0F, 0F, w, h);
        //?} else if >=1.21.11 {
        /*net.minecraft.client.gui.screens.Screen.renderMenuBackgroundTexture(g, id(path), x, y, 0F, 0F, w, h);
        *///?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        net.minecraft.client.gui.screens.Screen.renderMenuBackgroundTexture(g, id(path), x, y, 0F, 0F, w, h);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        *///?}
    }

    /** The 2px header/footer separator lines drawn above and below vanilla lists. */
    public void vanillaSeparator(boolean header, int x, int y, int w) {
        if (w <= 0) return;
        String path = "textures/gui/" + (inWorld() ? "inworld_" : "") + (header ? "header" : "footer") + "_separator.png";
        //? if >=1.21.11 {
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id(path), x, y, 0F, 0F, w, 2, 32, 2);
        //?} else {
        /*com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(id(path), x, y, 0F, 0F, w, 2, 32, 2);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        *///?}
    }

    // ---- clipping / hit testing -----------------------------------------------------------

    /** Clip subsequent drawing to this rectangle (enableScissor is identical on both surfaces). */
    public void pushScissor(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    public void popScissor() {
        g.disableScissor();
    }

    /** True if the point (mouseX, mouseY) is inside the given rectangle. */
    public boolean hovered(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }
}
