package de.xcrafttm.opensoundboard.ui;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base class for all OpenSoundboard screens. Isolates the 26.1 render-pipeline change:
 * 1.21.x draws in {@code render(GuiGraphics, ...)}, 26.x in
 * {@code extractRenderState(GuiGraphicsExtractor, ...)}. Subclasses only implement the
 * version-neutral {@link #drawUi(UiCanvas)}.
 */
public abstract class OsbScreen extends Screen {

    protected OsbScreen(Component title) {
        super(title);
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        drawUi(new UiCanvas(g, this.font, mouseX, mouseY));
    }
    *///?} else {
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        drawUi(new UiCanvas(g, this.font, mouseX, mouseY));
    }
    //?}

    /** Draw the screen contents using the version-neutral canvas. */
    protected abstract void drawUi(UiCanvas c);
}
