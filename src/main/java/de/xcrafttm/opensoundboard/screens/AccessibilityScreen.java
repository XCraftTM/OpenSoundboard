package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.Label;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Accessibility and sizing options shared by every OpenSoundboard screen. */
public final class AccessibilityScreen extends OsbScreen {

    private static final float[] WIDTHS = {0.6f, 0.7f, 0.8f, 0.9f, 1.0f};
    private static final float[] HEIGHTS = {0.7f, 0.8f, 0.9f, 1.0f};
    private static final float[] FONT_SIZES = {0.75f, 0.85f, 1.0f, 1.15f, 1.25f};
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW_STEP = 28;

    private final Screen parent;
    private int px;
    private int py;
    private int pw;
    private int ph;

    public AccessibilityScreen(Screen parent) {
        super(Component.translatable("title.opensoundboard.accessibility"));
        this.parent = parent;
    }

    @Override
    protected void buildUi() {
        ph = screenBoxHeight();
        pw = screenBoxWidth(400);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        int cx = px + Theme.PAD;
        int cw = pw - Theme.PAD * 2;
        int y = py + 34;

        y = option(y, cx, cw, "option.opensoundboard.uiWidth",
                SoundboardConfig.data.getUiWidthScale(), WIDTHS, SoundboardConfig.data::setUiWidthScale);
        y = option(y, cx, cw, "option.opensoundboard.uiHeight",
                SoundboardConfig.data.getUiHeightScale(), HEIGHTS, SoundboardConfig.data::setUiHeightScale);
        y = option(y, cx, cw, "option.opensoundboard.fontScale",
                SoundboardConfig.data.getFontScale(), FONT_SIZES, SoundboardConfig.data::setFontScale);

        add(new Button(Component.translatable("gui.opensoundboard.accessibility.reset"), b -> reset()).secondary())
                .bounds(cx, y + 6, cw, CONTROL_HEIGHT)
                .tooltip(Component.translatable("tooltip.opensoundboard.accessibility.reset").getString());

        add(new Button(Component.literal("✕"), b -> close()).secondary())
                .bounds(px + pw - 22, py + 3, 18, 16)
                .tooltip(Component.translatable("gui.done").getString());
    }

    private int option(int y, int x, int width, String key, float value,
                       float[] values, Consumer<Float> setter) {
        String tooltip = Component.translatable(key.replace("option.", "tooltip.")).getString();
        Label label = add(new Label(Component.translatable(key).getString()));
        label.tooltip(tooltip);
        label.bounds(x, y + 4, width - 98, CONTROL_HEIGHT - 4);
        add(new Button(percent(value), b -> {
            setter.accept(next(values, value));
            SoundboardConfig.save();
            rebuildUi();
        }).secondary()).bounds(x + width - 90, y, 90, CONTROL_HEIGHT).tooltip(tooltip);
        return y + ROW_STEP;
    }

    private static Component percent(float value) {
        return Component.literal(Math.round(value * 100) + "%");
    }

    private static float next(float[] values, float current) {
        int closest = 0;
        for (int i = 1; i < values.length; i++) {
            if (Math.abs(values[i] - current) < Math.abs(values[closest] - current)) closest = i;
        }
        return values[(closest + 1) % values.length];
    }

    private void reset() {
        SoundboardConfig.data.setUiWidthScale(SoundboardConfig.DEFAULT_UI_WIDTH_SCALE);
        SoundboardConfig.data.setUiHeightScale(SoundboardConfig.DEFAULT_UI_HEIGHT_SCALE);
        SoundboardConfig.data.setFontScale(SoundboardConfig.DEFAULT_FONT_SCALE);
        SoundboardConfig.save();
        rebuildUi();
    }

    private void close() {
        McCompat.setScreen(this.minecraft, parent);
    }

    @Override
    public void onClose() {
        close();
    }

    @Override
    protected void renderContent(UiCanvas c) {
        renderScreenBox(c, px, py, pw, ph);
        c.centeredText(Component.translatable("title.opensoundboard.accessibility"),
                px + pw / 2, py + 12, Theme.TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
