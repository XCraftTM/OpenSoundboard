package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.Label;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollPanel;
import de.xcrafttm.opensoundboard.ui.widgets.Slider;
import de.xcrafttm.opensoundboard.ui.widgets.Toggle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Scrollable configuration screen: toggles, cycles, and the dynamic global-volume section. */
public class SoundboardConfigScreen extends OsbScreen {

    private static final int[] SKIP = {1, 3, 5, 10, 15, 30};
    private static final String[] KEYBIND_MODES = {"play_stop", "pause_resume", "play_restart"};
    private static final int[] WHEEL_PAGES = {6, 8, 12, 16};

    private final Screen parent;
    private ScrollPanel panel;
    private int savedScroll = 0;
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
        pw = Math.max(400, (int) (this.width * 0.7));
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        int cx = px + Theme.PAD;
        int cw = pw - Theme.PAD * 2;
        int doneY = py + ph - Theme.PAD - 22;

        panel = add(new ScrollPanel());
        panel.bounds(cx, py + 26, cw, doneY - 6 - (py + 26));

        int w = cw - 8;
        int[] y = {2};

        toggle(y, w, "option.opensoundboard.playWhileMuted",
                SoundboardConfig.data.isPlayWhileMuted(), v -> { SoundboardConfig.data.setPlayWhileMuted(v); SoundboardConfig.save(); });
        toggle(y, w, "option.opensoundboard.playLocally",
                SoundboardConfig.data.isPlayLocally(), v -> { SoundboardConfig.data.setPlayLocally(v); SoundboardConfig.save(); });
        toggle(y, w, "option.opensoundboard.syncAudio",
                SoundboardConfig.data.isSyncAudio(), v -> { SoundboardConfig.data.setSyncAudio(v); SoundboardConfig.save(); });
        toggle(y, w, "option.opensoundboard.syncGlobalVolume",
                SoundboardConfig.data.isSyncGlobalVolume(), v -> {
                    SoundboardConfig.data.setSyncGlobalVolume(v);
                    if (v) SoundboardConfig.data.setGlobalPlayerVolume(SoundboardConfig.data.getGlobalLocalVolume());
                    SoundboardConfig.save();
                    savedScroll = panel.getScroll();
                    rebuildUi();
                });

        if (SoundboardConfig.data.isSyncGlobalVolume()) {
            volume(y, w, "option.opensoundboard.globalVolume", SoundboardConfig.data.getGlobalLocalVolume(), f -> {
                SoundboardConfig.data.setGlobalLocalVolume(f);
                SoundboardConfig.data.setGlobalPlayerVolume(f);
                SoundboardConfig.save();
            });
        } else {
            volume(y, w, "option.opensoundboard.globalLocalVolume", SoundboardConfig.data.getGlobalLocalVolume(), f -> {
                SoundboardConfig.data.setGlobalLocalVolume(f);
                SoundboardConfig.save();
            });
            volume(y, w, "option.opensoundboard.globalPlayerVolume", SoundboardConfig.data.getGlobalPlayerVolume(), f -> {
                SoundboardConfig.data.setGlobalPlayerVolume(f);
                SoundboardConfig.save();
            });
        }

        toggle(y, w, "option.opensoundboard.singleSongAtATime",
                SoundboardConfig.data.isSingleSongAtATime(), v -> { SoundboardConfig.data.setSingleSongAtATime(v); SoundboardConfig.save(); });
        toggle(y, w, "option.opensoundboard.loopAll",
                SoundboardConfig.data.isLoopAll(), v -> { SoundboardConfig.data.setLoopAll(v); SoundboardConfig.save(); SoundboardAudioSystem.setGlobalLooping(v); });

        cycle(y, w, "option.opensoundboard.skipAmount", () -> Component.literal(SoundboardConfig.data.getSkipAmountSeconds() + "s"), () -> {
            int cur = SoundboardConfig.data.getSkipAmountSeconds();
            int i = 0;
            for (int j = 0; j < SKIP.length; j++) if (SKIP[j] == cur) i = (j + 1) % SKIP.length;
            SoundboardConfig.data.setSkipAmountSeconds(SKIP[i]);
            SoundboardConfig.save();
        });
        cycle(y, w, "option.opensoundboard.keybindMode",
                () -> Component.translatable("option.opensoundboard.keybindMode." + SoundboardConfig.data.getKeybindMode()), () -> {
                    String cur = SoundboardConfig.data.getKeybindMode();
                    int i = 0;
                    for (int j = 0; j < KEYBIND_MODES.length; j++) if (KEYBIND_MODES[j].equals(cur)) i = (j + 1) % KEYBIND_MODES.length;
                    SoundboardConfig.data.setKeybindMode(KEYBIND_MODES[i]);
                    SoundboardConfig.save();
                });
        toggle(y, w, "option.opensoundboard.showSubfolders",
                SoundboardConfig.data.isShowSubfolders(), v -> { SoundboardConfig.data.setShowSubfolders(v); SoundboardConfig.save(); });

        header(y, w, "option.opensoundboard.wheel.header");
        cycle(y, w, "option.opensoundboard.wheelSoundsPerPage", () -> Component.literal(String.valueOf(SoundboardConfig.data.getWheelSoundsPerPage())), () -> {
            int cur = SoundboardConfig.data.getWheelSoundsPerPage();
            int i = 0;
            for (int j = 0; j < WHEEL_PAGES.length; j++) if (WHEEL_PAGES[j] == cur) i = (j + 1) % WHEEL_PAGES.length;
            SoundboardConfig.data.setWheelSoundsPerPage(WHEEL_PAGES[i]);
            SoundboardConfig.save();
        });
        toggle(y, w, "option.opensoundboard.wheelFavoritesOnly",
                SoundboardConfig.data.isWheelFavoritesOnly(), v -> { SoundboardConfig.data.setWheelFavoritesOnly(v); SoundboardConfig.save(); });
        toggle(y, w, "option.opensoundboard.wheelCustomLayout",
                SoundboardConfig.data.isWheelCustomLayout(), v -> { SoundboardConfig.data.setWheelCustomLayout(v); SoundboardConfig.save(); savedScroll = panel.getScroll(); rebuildUi(); });

        Button edit = panel.addChild(new Button(Component.translatable("gui.opensoundboard.wheel.editor.open"),
                b -> this.minecraft.setScreen(new WheelLayoutEditorScreen(this))).secondary(), 0, y[0], w, 18);
        edit.active = SoundboardConfig.data.isWheelCustomLayout();
        y[0] += 24;

        int half = (cw - 6) / 2;
        add(new Button(Component.translatable("gui.done"), b -> done())).bounds(cx, doneY, half, 22);
        add(new Button(Component.translatable("gui.cancel"), b -> this.minecraft.setScreen(parent)).secondary())
                .bounds(cx + half + 6, doneY, cw - half - 6, 22);

        panel.setScroll(savedScroll);
    }

    private void toggle(int[] y, int w, String key, boolean value, Consumer<Boolean> onChange) {
        panel.addChild(new Toggle(value, onChange), 0, y[0], 30, 16);
        panel.addChild(new Label(Component.translatable(key).getString()), 38, y[0] + 4, w - 38, 10);
        y[0] += 24;
    }

    private void cycle(int[] y, int w, String key, Supplier<Component> value, Runnable onCycle) {
        panel.addChild(new Button(value.get(), btn -> {
            onCycle.run();
            btn.setLabel(value.get());
        }).secondary(), 0, y[0], 70, 16);
        panel.addChild(new Label(Component.translatable(key).getString()), 78, y[0] + 4, w - 78, 10);
        y[0] += 24;
    }

    private void volume(int[] y, int w, String key, float value, Consumer<Float> onChange) {
        panel.addChild(new Label(Component.translatable(key).getString()).color(Theme.TEXT_MUTED), 0, y[0], w, 10);
        y[0] += 12;
        panel.addChild(new Slider(value, v -> onChange.accept(v.floatValue()))
                .readout(v -> Component.literal(Math.round(v * 100) + "%")), 0, y[0], w, 16);
        y[0] += 22;
    }

    private void header(int[] y, int w, String key) {
        y[0] += 6;
        panel.addChild(new Label(Component.translatable(key).getString()).color(0xFFF0C044), 0, y[0], w, 10);
        y[0] += 16;
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
