package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.screens.WheelLayoutEditorScreen;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.SliderComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Custom config screen using owo-ui.
 *
 * Key feature: dependent sections (like syncGlobalVolume sliders) rebuild dynamically.
 */
public class SoundboardConfigScreen extends BaseOwoScreen<FlowLayout> {

    private static final int[] SKIP_OPTIONS = {1, 3, 5, 10, 15, 30};
    private static final String[] KEYBIND_MODES = {"play_stop", "pause_resume", "play_restart"};
    private static final int[] WHEEL_PAGE_OPTIONS = {6, 8, 12, 16};

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

        root.child(UIComponents.label(Component.translatable("title.opensoundboard.config").withStyle(ChatFormatting .BOLD))
                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                .horizontalSizing(Sizing.fixed(columnWidth))
                .margins(Insets.bottom(8)));

        var panel = (FlowLayout) UIContainers.verticalFlow(Sizing.fixed(columnWidth), Sizing.content())
                .gap(6)
                .padding(Insets.of(6))
                .surface(Surface.DARK_PANEL);

        panel.child(toggleRow(
                Component.translatable("option.opensoundboard.playWhileMuted"),
                SoundboardConfig.data.isPlayWhileMuted(),
                v -> {
                    SoundboardConfig.data.setPlayWhileMuted(v);
                    SoundboardConfig.save();
                },
                Component.translatable("tooltip.opensoundboard.playWhileMuted")
        ));

        panel.child(toggleRow(
                Component.translatable("option.opensoundboard.playLocally"),
                SoundboardConfig.data.isPlayLocally(),
                v -> {
                    SoundboardConfig.data.setPlayLocally(v);
                    SoundboardConfig.save();
                },
                Component.translatable("tooltip.opensoundboard.playLocally")
        ));

        panel.child(toggleRow(
                Component.translatable("option.opensoundboard.syncAudio"),
                SoundboardConfig.data.isSyncAudio(),
                v -> {
                    SoundboardConfig.data.setSyncAudio(v);
                    SoundboardConfig.save();
                },
                Component.translatable("tooltip.opensoundboard.syncAudio")
        ));

        // sync global volume toggle
        panel.child(toggleRow(
                Component.translatable("option.opensoundboard.syncGlobalVolume"),
                SoundboardConfig.data.isSyncGlobalVolume(),
                v -> {
                    SoundboardConfig.data.setSyncGlobalVolume(v);
                    if (v) {
                        SoundboardConfig.data.setGlobalPlayerVolume(SoundboardConfig.data.getGlobalLocalVolume());
                    }
                    SoundboardConfig.save();
                    rebuildGlobalVolumeSection();
                },
                Component.translatable("tooltip.opensoundboard.syncGlobalVolume")
        ));

        globalVolumeSection = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(4);
        panel.child(globalVolumeSection);
        rebuildGlobalVolumeSection();

        panel.child(toggleRow(
                Component.translatable("option.opensoundboard.singleSongAtATime"),
                SoundboardConfig.data.isSingleSongAtATime(),
                v -> {
                    SoundboardConfig.data.setSingleSongAtATime(v);
                    SoundboardConfig.save();
                },
                Component.translatable("tooltip.opensoundboard.singleSongAtATime")
        ));

        panel.child(toggleRow(
                Component.translatable("option.opensoundboard.loopAll"),
                SoundboardConfig.data.isLoopAll(),
                v -> {
                    SoundboardConfig.data.setLoopAll(v);
                    SoundboardConfig.save();
                    SoundboardAudioSystem.setGlobalLooping(v);
                },
                Component.translatable("tooltip.opensoundboard.loopAll")
        ));

        panel.child(cycleRow(
                Component.translatable("option.opensoundboard.skipAmount"),
                SoundboardConfig.data.getSkipAmountSeconds(),
                Component.translatable("tooltip.opensoundboard.skipAmount")
        ));

        panel.child(keybindModeRow());

        panel.child(toggleRow(
                Component.translatable("option.opensoundboard.showSubfolders"),
                SoundboardConfig.data.isShowSubfolders(),
                v -> {
                    SoundboardConfig.data.setShowSubfolders(v);
                    SoundboardConfig.save();
                },
                Component.translatable("tooltip.opensoundboard.showSubfolders")
        ));

        // --- Wheel Overlay section ---
        panel.child(UIComponents.label(Component.translatable("option.opensoundboard.wheel.header").withStyle(ChatFormatting.GOLD))
                .margins(Insets.top(6)));

        panel.child(wheelPageSizeRow());

        panel.child(toggleRow(
                Component.translatable("option.opensoundboard.wheelFavoritesOnly"),
                SoundboardConfig.data.isWheelFavoritesOnly(),
                v -> {
                    SoundboardConfig.data.setWheelFavoritesOnly(v);
                    SoundboardConfig.save();
                },
                Component.translatable("tooltip.opensoundboard.wheelFavoritesOnly")
        ));

        var editLayoutBtn = UIComponents.button(
                Component.translatable("gui.opensoundboard.wheel.editor.open"),
                b -> { if (client != null) client.setScreen(new WheelLayoutEditorScreen(this)); });
        editLayoutBtn.sizing(Sizing.fixed(columnWidth), Sizing.content());
        editLayoutBtn.active(SoundboardConfig.data.isWheelCustomLayout());

        panel.child(toggleRow(
                Component.translatable("option.opensoundboard.wheelCustomLayout"),
                SoundboardConfig.data.isWheelCustomLayout(),
                v -> {
                    SoundboardConfig.data.setWheelCustomLayout(v);
                    SoundboardConfig.save();
                    editLayoutBtn.active(v);
                },
                Component.translatable("tooltip.opensoundboard.wheelCustomLayout")
        ));

        var editLayoutRow = (FlowLayout) UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(6).verticalAlignment(VerticalAlignment.CENTER);
        editLayoutRow.child(editLayoutBtn);
        panel.child(editLayoutRow);

        var scroll = UIContainers.verticalScroll(Sizing.fixed(columnWidth), Sizing.expand(), panel);
        root.child(scroll);

        var buttons = (FlowLayout) UIContainers.horizontalFlow(Sizing.fixed(columnWidth), Sizing.content())
                .gap(6)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .margins(Insets.top(10));

        buttons.child(UIComponents.button(Component.translatable("gui.done"), b -> returnToParentAndApply())
                .sizing(Sizing.fixed(140), Sizing.content()));

        buttons.child(UIComponents.button(Component.translatable("gui.cancel"), b -> this.client.setScreen(parent))
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

            globalVolumeSection.child(sliderRow(Component.translatable("option.opensoundboard.globalVolume"), slider, Component.translatable("tooltip.opensoundboard.globalVolume")));
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

            globalVolumeSection.child(sliderRow(Component.translatable("option.opensoundboard.globalLocalVolume"), localSlider, Component.translatable("tooltip.opensoundboard.globalLocalVolume")));
            globalVolumeSection.child(sliderRow(Component.translatable("option.opensoundboard.globalPlayerVolume"), playerSlider, Component.translatable("tooltip.opensoundboard.globalPlayerVolume")));
        }
    }

    private FlowLayout sliderRow(Component label, SliderComponent slider, Component tooltip) {
        var wrap = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
        var lbl = UIComponents.label(Component.literal(label.getString()).withStyle(ChatFormatting.GRAY));
        lbl.tooltip(tooltip);
        wrap.child(lbl);
        wrap.child(slider);
        return wrap;
    }

    private Component percentLabel(String v) {
        int pct;
        try {
            pct = (int) Math.round(Double.parseDouble(v) * 100d);
        } catch (Exception ignored) {
            pct = 0;
        }
        pct = Math.max(0, Math.min(100, pct));
        return Component.literal(pct + "%");
    }

    private static Component toggleLabel(boolean value) {
        return Component.literal(value ? "ON" : "OFF").withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private interface BoolConsumer {
        void accept(boolean v);
    }

    private FlowLayout cycleRow(Component label, int initialValue, Component tooltip) {
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

        var lbl = UIComponents.label(label);
        lbl.tooltip(tooltip);

        row.child(btn);
        row.child(lbl);

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

        var lbl = UIComponents.label(Component.translatable("option.opensoundboard.keybindMode"));
        lbl.tooltip(Component.translatable("tooltip.opensoundboard.keybindMode"));

        row.child(btn);
        row.child(lbl);

        return row;
    }

    private static Component keybindModeLabel(String mode) {
        return Component.translatable("option.opensoundboard.keybindMode." + mode).withStyle(ChatFormatting.AQUA);
    }

    private static Component skipLabel(int seconds) {
        return Component.literal(seconds + "s").withStyle(ChatFormatting.AQUA);
    }

    private FlowLayout toggleRow(Component label, boolean initial, BoolConsumer onChange, Component tooltip) {
        var row = (FlowLayout) UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(6)
                .verticalAlignment(VerticalAlignment.CENTER);

        var toggle = UIComponents.button(toggleLabel(initial), b -> {
            boolean newValue = !b.getMessage().getString().equalsIgnoreCase("ON");
            b.setMessage(toggleLabel(newValue));
            onChange.accept(newValue);
        });
        toggle.sizing(Sizing.fixed(60), Sizing.content());

        var lbl = UIComponents.label(label);
        lbl.tooltip(tooltip);

        row.child(toggle);
        row.child(lbl);

        return row;
    }

    private FlowLayout wheelPageSizeRow() {
        var row = (FlowLayout) UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .gap(6)
                .verticalAlignment(VerticalAlignment.CENTER);

        var btn = UIComponents.button(Component.literal(SoundboardConfig.data.getWheelSoundsPerPage() + "").withStyle(ChatFormatting.AQUA), b -> {
            int current = SoundboardConfig.data.getWheelSoundsPerPage();
            int nextIndex = 0;
            for (int i = 0; i < WHEEL_PAGE_OPTIONS.length; i++) {
                if (WHEEL_PAGE_OPTIONS[i] == current) {
                    nextIndex = (i + 1) % WHEEL_PAGE_OPTIONS.length;
                    break;
                }
            }
            int next = WHEEL_PAGE_OPTIONS[nextIndex];
            SoundboardConfig.data.setWheelSoundsPerPage(next);
            SoundboardConfig.save();
            b.setMessage(Component.literal(next + "").withStyle(ChatFormatting.AQUA));
        });
        btn.sizing(Sizing.fixed(60), Sizing.content());

        var lbl = UIComponents.label(Component.translatable("option.opensoundboard.wheelSoundsPerPage"));
        lbl.tooltip(Component.translatable("tooltip.opensoundboard.wheelSoundsPerPage"));

        row.child(btn);
        row.child(lbl);
        return row;
    }

    @Override
    public void close() {
        // ESC should behave like Done: apply and return
        returnToParentAndApply();
    }
}
