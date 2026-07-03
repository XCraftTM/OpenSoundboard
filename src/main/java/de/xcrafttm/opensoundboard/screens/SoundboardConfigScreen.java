package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.Label;
import de.xcrafttm.opensoundboard.ui.widgets.Slider;
import de.xcrafttm.opensoundboard.ui.widgets.Toggle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Configuration screen: toggles, cycles, and the dynamic global-volume section. */
public class SoundboardConfigScreen extends OsbScreen {

    private static final int[] SKIP = {1, 3, 5, 10, 15, 30};
    private static final String[] KEYBIND_MODES = {"play_stop", "pause_resume", "play_restart"};
    private static final int[] WHEEL_PAGES = {6, 8, 12, 16};

    private final Screen parent;
    private int px;
    private int py;
    private int pw;
    private int ph;

    public SoundboardConfigScreen(Screen parent) {
        super(Component.translatable("title.opensoundboard.config"));
        this.parent = parent;
    }

    @Override
    protected void buildUi() {
        ph = (int) (this.height * 0.9);
        pw = Math.max(380, Math.min(520, (int) (this.width * 0.55)));
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        int cx = px + Theme.PAD;
        int cw = pw - Theme.PAD * 2;
        int[] y = {py + 28};

        toggleRow(cx, cw, y, "option.opensoundboard.playWhileMuted",
                SoundboardConfig.data.isPlayWhileMuted(), v -> { SoundboardConfig.data.setPlayWhileMuted(v); SoundboardConfig.save(); });
        toggleRow(cx, cw, y, "option.opensoundboard.playLocally",
                SoundboardConfig.data.isPlayLocally(), v -> { SoundboardConfig.data.setPlayLocally(v); SoundboardConfig.save(); });
        toggleRow(cx, cw, y, "option.opensoundboard.syncAudio",
                SoundboardConfig.data.isSyncAudio(), v -> { SoundboardConfig.data.setSyncAudio(v); SoundboardConfig.save(); });
        toggleRow(cx, cw, y, "option.opensoundboard.syncGlobalVolume",
                SoundboardConfig.data.isSyncGlobalVolume(), v -> {
                    SoundboardConfig.data.setSyncGlobalVolume(v);
                    if (v) SoundboardConfig.data.setGlobalPlayerVolume(SoundboardConfig.data.getGlobalLocalVolume());
                    SoundboardConfig.save();
                    rebuildUi();
                });

        // Dynamic global-volume section
        if (SoundboardConfig.data.isSyncGlobalVolume()) {
            volumeRow(cx, cw, y, "option.opensoundboard.globalVolume", SoundboardConfig.data.getGlobalLocalVolume(), f -> {
                SoundboardConfig.data.setGlobalLocalVolume(f);
                SoundboardConfig.data.setGlobalPlayerVolume(f);
                SoundboardConfig.save();
            });
        } else {
            volumeRow(cx, cw, y, "option.opensoundboard.globalLocalVolume", SoundboardConfig.data.getGlobalLocalVolume(), f -> {
                SoundboardConfig.data.setGlobalLocalVolume(f);
                SoundboardConfig.save();
            });
            volumeRow(cx, cw, y, "option.opensoundboard.globalPlayerVolume", SoundboardConfig.data.getGlobalPlayerVolume(), f -> {
                SoundboardConfig.data.setGlobalPlayerVolume(f);
                SoundboardConfig.save();
            });
        }

        toggleRow(cx, cw, y, "option.opensoundboard.singleSongAtATime",
                SoundboardConfig.data.isSingleSongAtATime(), v -> { SoundboardConfig.data.setSingleSongAtATime(v); SoundboardConfig.save(); });
        toggleRow(cx, cw, y, "option.opensoundboard.loopAll",
                SoundboardConfig.data.isLoopAll(), v -> { SoundboardConfig.data.setLoopAll(v); SoundboardConfig.save(); SoundboardAudioSystem.setGlobalLooping(v); });

        cycleRow(cx, cw, y, "option.opensoundboard.skipAmount", () -> Component.literal(SoundboardConfig.data.getSkipAmountSeconds() + "s"), () -> {
            int cur = SoundboardConfig.data.getSkipAmountSeconds();
            int i = 0;
            for (int j = 0; j < SKIP.length; j++) if (SKIP[j] == cur) i = (j + 1) % SKIP.length;
            SoundboardConfig.data.setSkipAmountSeconds(SKIP[i]);
            SoundboardConfig.save();
        });
        cycleRow(cx, cw, y, "option.opensoundboard.keybindMode",
                () -> Component.translatable("option.opensoundboard.keybindMode." + SoundboardConfig.data.getKeybindMode()), () -> {
                    String cur = SoundboardConfig.data.getKeybindMode();
                    int i = 0;
                    for (int j = 0; j < KEYBIND_MODES.length; j++) if (KEYBIND_MODES[j].equals(cur)) i = (j + 1) % KEYBIND_MODES.length;
                    SoundboardConfig.data.setKeybindMode(KEYBIND_MODES[i]);
                    SoundboardConfig.save();
                });
        toggleRow(cx, cw, y, "option.opensoundboard.showSubfolders",
                SoundboardConfig.data.isShowSubfolders(), v -> { SoundboardConfig.data.setShowSubfolders(v); SoundboardConfig.save(); });

        y[0] += 6;
        add(new Label(Component.translatable("option.opensoundboard.wheel.header").getString()).color(0xFFF0C044))
                .bounds(cx, y[0], cw, 10);
        y[0] += 16;

        cycleRow(cx, cw, y, "option.opensoundboard.wheelSoundsPerPage", () -> Component.literal(String.valueOf(SoundboardConfig.data.getWheelSoundsPerPage())), () -> {
            int cur = SoundboardConfig.data.getWheelSoundsPerPage();
            int i = 0;
            for (int j = 0; j < WHEEL_PAGES.length; j++) if (WHEEL_PAGES[j] == cur) i = (j + 1) % WHEEL_PAGES.length;
            SoundboardConfig.data.setWheelSoundsPerPage(WHEEL_PAGES[i]);
            SoundboardConfig.save();
        });
        toggleRow(cx, cw, y, "option.opensoundboard.wheelFavoritesOnly",
                SoundboardConfig.data.isWheelFavoritesOnly(), v -> { SoundboardConfig.data.setWheelFavoritesOnly(v); SoundboardConfig.save(); });
        toggleRow(cx, cw, y, "option.opensoundboard.wheelCustomLayout",
                SoundboardConfig.data.isWheelCustomLayout(), v -> { SoundboardConfig.data.setWheelCustomLayout(v); SoundboardConfig.save(); rebuildUi(); });

        Button edit = add(new Button(Component.translatable("gui.opensoundboard.wheel.editor.open"),
                b -> this.minecraft.setScreen(new WheelLayoutEditorScreen(this))).secondary());
        edit.bounds(cx, y[0], cw, 18);
        edit.active = SoundboardConfig.data.isWheelCustomLayout();
        y[0] += 24;

        // Done / Cancel
        int by = py + ph - Theme.PAD - 22;
        int half = (cw - 6) / 2;
        add(new Button(Component.translatable("gui.done"), b -> done())).bounds(cx, by, half, 22);
        add(new Button(Component.translatable("gui.cancel"), b -> this.minecraft.setScreen(parent)).secondary())
                .bounds(cx + half + 6, by, cw - half - 6, 22);
    }

    private void toggleRow(int cx, int cw, int[] y, String key, boolean value, Consumer<Boolean> onChange) {
        add(new Toggle(value, onChange)).bounds(cx, y[0], 30, 16);
        add(new Label(Component.translatable(key).getString())).bounds(cx + 38, y[0] + 4, cw - 38, 10);
        y[0] += 24;
    }

    private void cycleRow(int cx, int cw, int[] y, String key, java.util.function.Supplier<Component> value, Runnable onCycle) {
        Button b = add(new Button(value.get(), btn -> {
            onCycle.run();
            btn.setLabel(value.get());
        }).secondary());
        b.bounds(cx, y[0], 70, 16);
        add(new Label(Component.translatable(key).getString())).bounds(cx + 78, y[0] + 4, cw - 78, 10);
        y[0] += 24;
    }

    private void volumeRow(int cx, int cw, int[] y, String key, float value, Consumer<Float> onChange) {
        add(new Label(Component.translatable(key).getString()).color(Theme.TEXT_MUTED)).bounds(cx, y[0], cw, 10);
        y[0] += 12;
        add(new Slider(value, v -> onChange.accept(v.floatValue()))
                .readout(v -> Component.literal(Math.round(v * 100) + "%"))).bounds(cx, y[0], cw, 16);
        y[0] += 22;
    }

    private void done() {
        if (parent instanceof SoundboardScreen) this.minecraft.setScreen(new SoundboardScreen());
        else this.minecraft.setScreen(parent);
    }

    @Override
    protected void renderContent(UiCanvas c) {
        c.fillRect(0, 0, this.width, this.height, Theme.SCRIM);
        c.fillRoundRect(px, py, pw, ph, Theme.PANEL);
        c.roundBorder(px, py, pw, ph, Theme.BORDER);
        c.fillRect(px + Theme.RADIUS, py, pw - Theme.RADIUS * 2, 3, Theme.ACCENT);
        c.centeredText(Component.translatable("title.opensoundboard.config"), px + pw / 2, py + 12, Theme.TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
