package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.SliderComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

/**
 * Custom config screen using owo-ui.
 *
 * Key feature: dependent sections (like syncGlobalVolume sliders) rebuild dynamically.
 */
public class SoundboardConfigScreen extends BaseOwoScreen<FlowLayout> {

    private static final int[] SKIP_OPTIONS = {1, 3, 5, 10, 15, 30};
    private static final String[] KEYBIND_MODES = {"play_stop", "pause_resume", "play_restart"};

    private final Screen parent;

    private FlowLayout globalVolumeSection;

    public SoundboardConfigScreen(Screen parent) {
        this.parent = parent;
    }

    private void returnToParentAndApply() {
        if (this.client == null) {
            super.close();
            return;
        }

        // If we came from the soundboard, reopen it to rebuild its UI with the new config
        if (this.parent instanceof SoundboardScreen) {
            this.client.setScreen(new SoundboardScreen());
        } else {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        int columnWidth = Math.min(400, Math.max(320, (int) (this.width * 0.55f)));

        root.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(10));

        root.child(UIComponents.label(Text.translatable("title.opensoundboard.config").formatted(Formatting.BOLD))
                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                .horizontalSizing(Sizing.fixed(columnWidth))
                .margins(Insets.bottom(8)));

        var panel = (FlowLayout) UIContainers.verticalFlow(Sizing.fixed(columnWidth), Sizing.content())
                .gap(6)
                .padding(Insets.of(6))
                .surface(Surface.DARK_PANEL);

        panel.child(toggleRow(
                Text.translatable("option.opensoundboard.playWhileMuted"),
                SoundboardConfig.data.isPlayWhileMuted(),
                v -> {
                    SoundboardConfig.data.setPlayWhileMuted(v);
                    SoundboardConfig.save();
                }
        ));

        panel.child(toggleRow(
                Text.translatable("option.opensoundboard.playLocally"),
                SoundboardConfig.data.isPlayLocally(),
                v -> {
                    SoundboardConfig.data.setPlayLocally(v);
                    SoundboardConfig.save();
                }
        ));

        panel.child(toggleRow(
                Text.translatable("option.opensoundboard.syncAudio"),
                SoundboardConfig.data.isSyncAudio(),
                v -> {
                    SoundboardConfig.data.setSyncAudio(v);
                    SoundboardConfig.save();
                }
        ));

        // sync global volume toggle
        panel.child(toggleRow(
                Text.translatable("option.opensoundboard.syncGlobalVolume"),
                SoundboardConfig.data.isSyncGlobalVolume(),
                v -> {
                    SoundboardConfig.data.setSyncGlobalVolume(v);
                    if (v) {
                        SoundboardConfig.data.setGlobalPlayerVolume(SoundboardConfig.data.getGlobalLocalVolume());
                    }
                    SoundboardConfig.save();
                    rebuildGlobalVolumeSection();
                }
        ));

        globalVolumeSection = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(4);
        panel.child(globalVolumeSection);
        rebuildGlobalVolumeSection();

        panel.child(toggleRow(
                Text.translatable("option.opensoundboard.singleSongAtATime"),
                SoundboardConfig.data.isSingleSongAtATime(),
                v -> {
                    SoundboardConfig.data.setSingleSongAtATime(v);
                    SoundboardConfig.save();
                }
        ));

        panel.child(toggleRow(
                Text.translatable("option.opensoundboard.loopAll"),
                SoundboardConfig.data.isLoopAll(),
                v -> {
                    SoundboardConfig.data.setLoopAll(v);
                    SoundboardConfig.save();
                    SoundboardAudioSystem.setGlobalLooping(v);
                }
        ));

        panel.child(cycleRow(
                Text.translatable("option.opensoundboard.skipAmount"),
                SoundboardConfig.data.getSkipAmountSeconds()
        ));

        panel.child(keybindModeRow());

        root.child(panel);

        var buttons = (FlowLayout) UIContainers.horizontalFlow(Sizing.fixed(columnWidth), Sizing.content())
                .gap(6)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .margins(Insets.top(10));

        buttons.child(UIComponents.button(Text.translatable("gui.done"), b -> returnToParentAndApply())
                .sizing(Sizing.fixed(140), Sizing.content()));

        buttons.child(UIComponents.button(Text.translatable("gui.cancel"), b -> this.client.setScreen(parent))
                .sizing(Sizing.fixed(140), Sizing.content()));

        root.child(buttons);
    }

    private void rebuildGlobalVolumeSection() {
        if (globalVolumeSection == null) return;

        globalVolumeSection.clearChildren();

        if (SoundboardConfig.data.isSyncGlobalVolume()) {
            var slider = UIComponents.slider(Sizing.fill(100));
            slider.value(SoundboardConfig.data.getGlobalLocalVolume());
            slider.message(this::percentLabel);
            slider.onChanged().subscribe(v -> {
                float f = (float) v;
                SoundboardConfig.data.setGlobalLocalVolume(f);
                SoundboardConfig.data.setGlobalPlayerVolume(f);
                SoundboardConfig.save();
            });

            globalVolumeSection.child(sliderRow(Text.translatable("option.opensoundboard.globalVolume"), slider));
        } else {
            var localSlider = UIComponents.slider(Sizing.fill(100));
            localSlider.value(SoundboardConfig.data.getGlobalLocalVolume());
            localSlider.message(this::percentLabel);
            localSlider.onChanged().subscribe(v -> {
                SoundboardConfig.data.setGlobalLocalVolume((float) v);
                SoundboardConfig.save();
            });

            var playerSlider = UIComponents.slider(Sizing.fill(100));
            playerSlider.value(SoundboardConfig.data.getGlobalPlayerVolume());
            playerSlider.message(this::percentLabel);
            playerSlider.onChanged().subscribe(v -> {
                SoundboardConfig.data.setGlobalPlayerVolume((float) v);
                SoundboardConfig.save();
            });

            globalVolumeSection.child(sliderRow(Text.translatable("option.opensoundboard.globalLocalVolume"), localSlider));
            globalVolumeSection.child(sliderRow(Text.translatable("option.opensoundboard.globalPlayerVolume"), playerSlider));
        }
    }

    private FlowLayout sliderRow(Text label, SliderComponent slider) {
        var wrap = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
        wrap.child(UIComponents.label(Text.literal(label.getString()).formatted(Formatting.GRAY)));
        wrap.child(slider);
        return wrap;
    }

    private Text percentLabel(String v) {
        int pct;
        try {
            pct = (int) Math.round(Double.parseDouble(v) * 100d);
        } catch (Exception ignored) {
            pct = 0;
        }
        pct = Math.max(0, Math.min(100, pct));
        return Text.literal(pct + "%");
    }

    private static Text toggleLabel(boolean value) {
        return Text.literal(value ? "ON" : "OFF").formatted(value ? Formatting.GREEN : Formatting.RED);
    }

    private interface BoolConsumer {
        void accept(boolean v);
    }

    private FlowLayout cycleRow(Text label, int initialValue) {
        var row = (FlowLayout) UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(6)
                .verticalAlignment(VerticalAlignment.CENTER);

        var btn = UIComponents.button(skipLabel(initialValue), b -> {
            int current = SoundboardConfig.data.getSkipAmountSeconds();
            int nextIndex = 0;
            for (int i = 0; i < SKIP_OPTIONS.length; i++) {
                if (SKIP_OPTIONS[i] == current) {
                    nextIndex = (i + 1) % SKIP_OPTIONS.length;
                    break;
                }
            }
            int next = SKIP_OPTIONS[nextIndex];
            SoundboardConfig.data.setSkipAmountSeconds(next);
            SoundboardConfig.save();
            b.setMessage(skipLabel(next));
        });
        btn.sizing(Sizing.fixed(60), Sizing.content());

        row.child(btn);
        row.child(UIComponents.label(label));

        return row;
    }

    private FlowLayout keybindModeRow() {
        var row = (FlowLayout) UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(6)
                .verticalAlignment(VerticalAlignment.CENTER);

        var btn = UIComponents.button(keybindModeLabel(SoundboardConfig.data.getKeybindMode()), b -> {
            String current = SoundboardConfig.data.getKeybindMode();
            int nextIndex = 0;
            for (int i = 0; i < KEYBIND_MODES.length; i++) {
                if (KEYBIND_MODES[i].equals(current)) {
                    nextIndex = (i + 1) % KEYBIND_MODES.length;
                    break;
                }
            }
            String next = KEYBIND_MODES[nextIndex];
            SoundboardConfig.data.setKeybindMode(next);
            SoundboardConfig.save();
            b.setMessage(keybindModeLabel(next));
        });
        btn.sizing(Sizing.fixed(90), Sizing.content());

        row.child(btn);
        row.child(UIComponents.label(Text.translatable("option.opensoundboard.keybindMode")));

        return row;
    }

    private static Text keybindModeLabel(String mode) {
        return Text.translatable("option.opensoundboard.keybindMode." + mode).formatted(Formatting.AQUA);
    }

    private static Text skipLabel(int seconds) {
        return Text.literal(seconds + "s").formatted(Formatting.AQUA);
    }

    private FlowLayout toggleRow(Text label, boolean initial, BoolConsumer onChange) {
        var row = (FlowLayout) UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(6)
                .verticalAlignment(VerticalAlignment.CENTER);

        var toggle = UIComponents.button(toggleLabel(initial), b -> {
            boolean newValue = !b.getMessage().getString().equalsIgnoreCase("ON");
            b.setMessage(toggleLabel(newValue));
            onChange.accept(newValue);
        });
        toggle.sizing(Sizing.fixed(60), Sizing.content());

        row.child(toggle);
        row.child(UIComponents.label(label));

        return row;
    }

    @Override
    public void close() {
        // ESC should behave like Done: apply and return
        returnToParentAndApply();
    }
}
