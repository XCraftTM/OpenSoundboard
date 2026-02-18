package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.integration.ModMenuIntegration;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SliderComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class SoundboardScreen extends BaseOwoScreen<FlowLayout> {

    private static final int BUTTON_SPACING = 5;
    private static final long DOUBLE_CLICK_WINDOW_MS = 250;

    private TextBoxComponent queryField;

    private FlowLayout resultsList;
    private ScrollContainer<FlowLayout> scrollContainer;

    private LabelComponent detailLabel;
    private SliderComponent detailLocalSlider;
    private SliderComponent detailPlayerSlider;
    private ButtonComponent detailBindBtn;

    private SliderComponent timelineSlider;
    private TextBoxComponent timeField;

    private ButtonComponent stopButton;
    private ButtonComponent backBtn;
    private ButtonComponent pauseBtn;
    private ButtonComponent forwardBtn;
    private ButtonComponent loopBtn;
    private ButtonComponent setStartBtn;

    private File selectedFile = null;
    private boolean isBinding = false;
    /**
     * while binding: last pressed key candidate; we commit it when all keys are released
     */
    private SoundboardConfig.KeyBind pendingKeybind = null;

    private List<File> results = List.of();

    // double click tracking for rows
    private File lastClickedFile = null;
    private long lastClickMs = 0;

    // deferred scroll target – layout isn't ready during build()
    private String pendingScrollTarget = null;

    private static final int FAV_WIDTH = 20;
    private static final int PLAY_WIDTH = 35;

    private int listWidth;

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        // Compute width based on actual screen size
        // Keep a nice centered column like the reference image
        this.listWidth = Math.min(400, Math.max(320, (int) (this.width * 0.55f)));

        rootComponent
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(10));

        // ----- Header -----
        rootComponent.child(
                UIComponents.label(Text.translatable("gui.opensoundboard.title").formatted(Formatting.BOLD))
                        .horizontalTextAlignment(HorizontalAlignment.CENTER)
                        .margins(Insets.bottom(5))
        );

        queryField = UIComponents.textBox(Sizing.fixed(listWidth));
        queryField.setPlaceholder(Text.translatable("gui.opensoundboard.search_hint"));
        queryField.onChanged().subscribe(s -> scanSounds());
        rootComponent.child(queryField.margins(Insets.bottom(5)));

        int gaps = BUTTON_SPACING * 3;
        int buttonW = Math.max(20, (listWidth - gaps) / 4);

        var topButtons = UIContainers.horizontalFlow(Sizing.fixed(listWidth), Sizing.content());
        topButtons.gap(BUTTON_SPACING).margins(Insets.bottom(10));

        var refreshBtn = UIComponents.button(Text.translatable("gui.opensoundboard.refresh"), b -> scanSounds());
        refreshBtn.sizing(Sizing.fixed(buttonW), Sizing.content());
        refreshBtn.tooltip(Text.translatable("tooltip.opensoundboard.refresh"));
        topButtons.child(refreshBtn);

        var folderBtn = UIComponents.button(Text.translatable("gui.opensoundboard.folder"), b -> Util.getOperatingSystem().open(OpenSoundboardClient.soundDir));
        folderBtn.sizing(Sizing.fixed(buttonW), Sizing.content());
        folderBtn.tooltip(Text.translatable("tooltip.opensoundboard.folder"));
        topButtons.child(folderBtn);

        var configBtn = UIComponents.button(Text.translatable("gui.opensoundboard.config"), b -> client.setScreen(new SoundboardConfigScreen(this)));
        configBtn.sizing(Sizing.fixed(buttonW), Sizing.content());
        configBtn.tooltip(Text.translatable("tooltip.opensoundboard.config"));
        topButtons.child(configBtn);

        var youtubeBtn = UIComponents.button(Text.translatable("gui.opensoundboard.youtube"), b -> client.setScreen(new YouTubeScreen(this)));
        youtubeBtn.sizing(Sizing.fixed(buttonW), Sizing.content());
        youtubeBtn.tooltip(Text.translatable("tooltip.opensoundboard.youtube"));
        topButtons.child(youtubeBtn);

        rootComponent.child(topButtons);


        // ----- Results -----
        resultsList = UIContainers.verticalFlow(Sizing.fixed(listWidth), Sizing.content()).gap(2);
        scrollContainer = UIContainers.verticalScroll(Sizing.fixed(listWidth), Sizing.expand(), resultsList);
        // darker translucent list background
        scrollContainer.surface(Surface.flat(0x66000000)).padding(Insets.of(5));
        rootComponent.child(scrollContainer);

        // ----- Details -----
        // Details pane has padding -> children should fill and not assume full listWidth
        var detailsPane = UIContainers.verticalFlow(Sizing.fixed(listWidth), Sizing.content());
        detailsPane.surface(Surface.BLANK).padding(Insets.of(5));

        // Usable inner width inside detailsPane after padding (5 left + 5 right)
        final int detailsInnerWidth = Math.max(0, listWidth - 10);

        detailLabel = UIComponents.label(Text.translatable("gui.opensoundboard.select_hint").formatted(Formatting.GRAY));
        detailLabel.horizontalSizing(Sizing.fill(100));
        detailsPane.child(detailLabel.horizontalTextAlignment(HorizontalAlignment.CENTER).margins(Insets.bottom(5)));

        int gap = 5;
        int inner = detailsInnerWidth;
        int colW = (inner - gap * 2) / 3;

        var row = (FlowLayout) UIContainers.horizontalFlow(Sizing.fixed(inner), Sizing.content())
                .gap(gap)
                .verticalAlignment(VerticalAlignment.CENTER);

        detailLocalSlider = UIComponents.slider(Sizing.fixed(colW));
        detailPlayerSlider = UIComponents.slider(Sizing.fixed(colW));

        // slider labels + volume update handlers
        detailLocalSlider.message(v -> {
            int pct;
            try {
                pct = (int) Math.round(Double.parseDouble(v) * 100d);
            } catch (Exception ignored) {
                pct = 0;
            }
            pct = Math.max(0, Math.min(100, pct));
            if (SoundboardConfig.data.isSyncAudio()) {
                return Text.translatable("gui.opensoundboard.sync_volume", String.valueOf(pct));
            }
            return Text.translatable("gui.opensoundboard.local_volume", String.valueOf(pct));
        });
        detailLocalSlider.onChanged().subscribe(value -> {
            if (SoundboardConfig.data.isSyncAudio()) {
                updateSelectedVolume(value, value);
            } else {
                updateSelectedVolume(value, null);
            }
        });

        detailPlayerSlider.message(v -> {
            int pct;
            try {
                pct = (int) Math.round(Double.parseDouble(v) * 100d);
            } catch (Exception ignored) {
                pct = 0;
            }
            pct = Math.max(0, Math.min(100, pct));
            return Text.translatable("gui.opensoundboard.player_volume", String.valueOf(pct));
        });
        detailPlayerSlider.onChanged().subscribe(value -> updateSelectedVolume(null, value));

        detailBindBtn = (ButtonComponent) UIComponents.button(Text.translatable("gui.opensoundboard.keybind.none"), b -> {
            if (selectedFile != null) {
                isBinding = true;
                b.setMessage(Text.translatable("gui.opensoundboard.keybind.listening").formatted(Formatting.YELLOW));
            }
        }).sizing(Sizing.fixed(colW), Sizing.content());
        detailBindBtn.tooltip(Text.translatable("tooltip.opensoundboard.keybind"));

        row.child(detailLocalSlider);

        if (SoundboardConfig.data.isSyncAudio()) {
            // In sync mode, the local slider represents both volumes and should span the available space
            // (two columns), while keeping the keybind button on the right.
            detailLocalSlider.horizontalSizing(Sizing.fixed(colW * 2 + gap));
            row.child(UIContainers.horizontalFlow(Sizing.fixed(0), Sizing.content()));
        } else {
            row.child(detailPlayerSlider);
        }

        row.child(detailBindBtn);

        detailsPane.child(row.margins(Insets.top(2)));

        // Timeline slider
        timelineSlider = UIComponents.slider(Sizing.fill()).message(v -> {
            var file = selectedFile;
            if (file == null) return Text.literal("0:00.0 / 0:00.0");

            String key = file.getName();
            long durationMs = SoundboardAudioSystem.getDurationMillis(key);
            long passedMs = SoundboardAudioSystem.getTimeMillis(key);

            if (durationMs > 0) {
                // If not currently playing, estimate passed from slider position for a stable tooltip
                if (!SoundboardAudioSystem.isPlaying(key)) {
                    float progress;
                    try {
                        progress = Float.parseFloat(v);
                    } catch (Exception ignored) {
                        progress = 0f;
                    }
                    progress = Math.max(0f, Math.min(1f, progress));
                    passedMs = Math.max(0, Math.min(durationMs, Math.round(progress * durationMs)));
                } else {
                    passedMs = Math.max(0, Math.min(durationMs, passedMs));
                }
                long leftMs = Math.max(0, durationMs - passedMs);
                return Text.literal(GuiTools.formatTimeMillis(passedMs) + " / -" + GuiTools.formatTimeMillis(leftMs));
            }

            // unknown duration
            return Text.literal(GuiTools.formatTimeMillis(Math.max(0, passedMs)) + " / -0:00.0");
        });
        timelineSlider.onChanged().subscribe(value -> {
            var file = selectedFile;
            if (file != null && SoundboardAudioSystem.isPlaying(file.getName())) {
                SoundboardAudioSystem.setCursor(file.getName(), (float) value);
            }
        });
        detailsPane.child(timelineSlider.margins(Insets.top(5)));


        var controlsRow = (FlowLayout) UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .verticalAlignment(VerticalAlignment.CENTER);

        // fixed side columns (~80px) + centered middle column
        var leftControls = (FlowLayout) UIContainers.horizontalFlow(Sizing.fixed(80), Sizing.content())
                .horizontalAlignment(HorizontalAlignment.LEFT);


        var centerControls = (FlowLayout) UIContainers.horizontalFlow(Sizing.expand(), Sizing.content())
                .gap(5)
                .horizontalAlignment(HorizontalAlignment.CENTER);

        var rightControls = (FlowLayout) UIContainers.horizontalFlow(Sizing.fixed(80), Sizing.content())
                .horizontalAlignment(HorizontalAlignment.RIGHT);

        timeField = UIComponents.textBox(Sizing.fill());
        timeField.onChanged().subscribe(text -> {
            if (!timeField.isFocused()) return;
            var file = selectedFile;
            if (file == null) return;
            if (!SoundboardAudioSystem.isPlaying(file.getName())) return;

            long millis = GuiTools.parseTimeMillis(text);
            if (millis < 0) return;

            long duration = SoundboardAudioSystem.getDurationMillis(file.getName());
            if (duration <= 0) return;

            float progress = Math.max(0f, Math.min(1f, millis / (float) duration));
            SoundboardAudioSystem.setCursor(file.getName(), progress);
        });

        stopButton = UIComponents.button(Text.literal("⏹"), b -> {
            if (selectedFile != null)
                SoundboardAudioSystem.stopAll();
        });
        stopButton.tooltip(Text.translatable("gui.opensoundboard.stop_all"));
        stopButton.sizing(Sizing.fixed(30), Sizing.content());

        backBtn = UIComponents.button(Text.literal("⏪"), b -> {
            if (selectedFile != null)
                SoundboardAudioSystem.skip(selectedFile.getName(), -SoundboardConfig.data.getSkipAmountSeconds());
        });
        backBtn.tooltip(Text.translatable("gui.opensoundboard.skip_back"));
        backBtn.sizing(Sizing.fixed(30), Sizing.content());

        pauseBtn = UIComponents.button(Text.literal("⏸"), b -> {
            if (selectedFile == null) return;
            if (SoundboardAudioSystem.isPaused(selectedFile.getName()))
                SoundboardAudioSystem.resume(selectedFile.getName());
            else SoundboardAudioSystem.pause(selectedFile.getName());
        });
        pauseBtn.tooltip(Text.translatable("gui.opensoundboard.pause_resume"));
        pauseBtn.sizing(Sizing.fixed(30), Sizing.content());

        forwardBtn = UIComponents.button(Text.literal("⏩"), b -> {
            if (selectedFile != null)
                SoundboardAudioSystem.skip(selectedFile.getName(), SoundboardConfig.data.getSkipAmountSeconds());
        });
        forwardBtn.tooltip(Text.translatable("gui.opensoundboard.skip_forward"));
        forwardBtn.sizing(Sizing.fixed(30), Sizing.content());

        loopBtn = UIComponents.button(GuiTools.loopLabel(SoundboardConfig.data.isLoopAll()), b -> {
            SoundboardConfig.data.setLoopAll(!SoundboardConfig.data.isLoopAll());
            SoundboardConfig.save();
            SoundboardAudioSystem.setGlobalLooping(SoundboardConfig.data.isLoopAll());
            b.setMessage(GuiTools.loopLabel(SoundboardConfig.data.isLoopAll()));
        });
        loopBtn.tooltip(Text.translatable("gui.opensoundboard.loop"));
        loopBtn.sizing(Sizing.fixed(30), Sizing.content());

        setStartBtn = UIComponents.button(Text.translatable("gui.opensoundboard.set_start"), b -> {
            if (selectedFile == null) return;
            var data = SoundboardConfig.get(selectedFile.getName());
            data.setStartingPoint(Math.max(0f, SoundboardAudioSystem.getProgress(selectedFile.getName())));
            SoundboardConfig.save();
            if (client.player != null) {
                client.player.sendMessage(Text.translatable("message.opensoundboard.start_point_set", selectedFile.getName()), true);
            }
        }).active(selectedFile == null);
        setStartBtn.sizing(Sizing.fixed(80), Sizing.content());
        setStartBtn.tooltip(Text.translatable("tooltip.opensoundboard.set_start"));

        leftControls.child(timeField);
        centerControls.child(stopButton);
        centerControls.child(backBtn);
        centerControls.child(pauseBtn);
        centerControls.child(forwardBtn);
        centerControls.child(loopBtn);
        rightControls.child(setStartBtn);

        controlsRow.child(leftControls);
        controlsRow.child(centerControls);
        controlsRow.child(rightControls);

        detailsPane.child(controlsRow.margins(Insets.top(5)));

        rootComponent.child(detailsPane);

        rootComponent.child(UIComponents.button(Text.translatable("gui.done"), b -> close())
                .sizing(Sizing.fixed(150), Sizing.content())
                .margins(Insets.top(10)));

        scanSounds();

        String activeName = SoundboardAudioSystem.getActiveSoundName();
        if (activeName != null) {
            var file = new File(OpenSoundboardClient.soundDir, activeName);
            if (file.exists()) {
                selectSound(file);
                // Defer scroll to first tick() when layout is ready
                pendingScrollTarget = activeName;
            }
        }

        refreshDetailsUiState();
    }

    private void scrollToSound(String soundFileName) {
        if (soundFileName == null || scrollContainer == null || resultsList == null) return;

        // Find the index of the row matching this sound
        int index = -1;
        var children = resultsList.children();
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);
            if (child instanceof FlowLayout row) {
                String id = row.id();
                if (soundFileName.equals(id)) {
                    index = i;
                    break;
                }
            }
        }

        if (index < 0) return;

        // Best-effort scrolling: center the item in view
        // Owo's ScrollContainer has scrollTo(double) which expects 0..1
        double t = children.size() <= 1 ? 0.0 : (index / (double) (children.size() - 1));
        scrollContainer.scrollTo(Math.max(0.0, Math.min(1.0, t)));
    }

    private void scanSounds() {
        // On each scan, ensure filenames are sanitized and migrate config keys
        sanitizeAndRenameSoundFiles();

        String query = queryField.getText().trim().toLowerCase();
        File[] allFiles = OpenSoundboardClient.soundDir.listFiles((dir, name) -> name.endsWith(".mp3"));
        if (allFiles == null) allFiles = new File[0];

        results = Arrays.stream(allFiles)
                .filter(f -> f.getName().toLowerCase().contains(query))
                .sorted(Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite())
                        .reversed()
                        .thenComparing(File::getName))
                .collect(Collectors.toList());

        resultsList.clearChildren();
        for (File file : results) {
            resultsList.child(buildRow(file));
        }

        if (selectedFile != null && results.stream().noneMatch(f -> Objects.equals(f.getName(), selectedFile.getName()))) {
            selectSound(null);
        }
    }

    private record RowWidgets(ButtonComponent favBtn, LabelComponent nameLabel, ButtonComponent playBtn) {
    }

    private final Map<String, RowWidgets> rowWidgets = new HashMap<>();

    private FlowLayout buildRow(File file) {
        final String key = file.getName();

        var row = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        row.gap(3).padding(Insets.of(1)).verticalAlignment(VerticalAlignment.CENTER).id(key);

        var data = SoundboardConfig.get(key);

        var favBtn = UIComponents.button(GuiTools.favoriteLabel(data.isFavorite()), b -> {
            data.setFavorite(!data.isFavorite());
            SoundboardConfig.save();
            scanSounds();
        });
        favBtn.tooltip(Text.translatable("gui.opensoundboard.favorite"));
        favBtn.sizing(Sizing.fixed(FAV_WIDTH), Sizing.content());

        var nameLabel = UIComponents.label(Text.empty());
        nameLabel.horizontalTextAlignment(HorizontalAlignment.LEFT);


        var playBtn = UIComponents.button(Text.translatable("gui.opensoundboard.play"), b -> {
            togglePlay(file);
            refreshListVisuals();
        });
        playBtn.sizing(Sizing.fixed(PLAY_WIDTH), Sizing.content());

        row.child(playBtn);
        row.child(favBtn);
        row.child(nameLabel);

        rowWidgets.put(key, new RowWidgets(favBtn, nameLabel, playBtn));

        row.mouseDown().subscribe((mouseX, mouseY) -> {
            selectSound(file);

            long now = System.currentTimeMillis();
            boolean isDoubleClick =
                    lastClickedFile != null
                            && key.equals(lastClickedFile.getName())
                            && (now - lastClickMs) <= DOUBLE_CLICK_WINDOW_MS;

            if (isDoubleClick) {
                togglePlay(file);
                lastClickedFile = null;
                lastClickMs = 0;
            } else {
                lastClickedFile = file;
                lastClickMs = now;
            }

            refreshListVisuals();
            return true;
        });

        updateRowVisuals(file, row);
        return row;
    }

    private void togglePlay(File file) {
        String key = file.getName();
        if (SoundboardAudioSystem.isPlaying(key)) {
            SoundboardAudioSystem.stop(key);
        } else {
            var current = SoundboardConfig.get(key);
            SoundboardAudioSystem.playFile(file, current.getLocalVolume(), current.getPlayerVolume());
        }
    }

    private void updateRowVisuals(File file, FlowLayout row) {
        final String key = file.getName();

        boolean isSelected = selectedFile != null && key.equals(selectedFile.getName());
        boolean isPlaying = SoundboardAudioSystem.isPlaying(key);

        row.surface(isSelected ? Surface.flat(0x33FFFFFF) : Surface.BLANK);

        RowWidgets w = rowWidgets.get(key);
        if (w == null) return;

        var data = SoundboardConfig.get(key);
        w.favBtn().setMessage(GuiTools.favoriteLabel(data.isFavorite()));

        // Your old trim math assumed 3 children; still valid as "available space" estimate
        int available = listWidth - FAV_WIDTH - PLAY_WIDTH - 20;
        w.nameLabel().text(Text.literal(
                GuiTools.trimName(client.textRenderer, GuiTools.baseName(file), available)
        ).formatted(isPlaying ? Formatting.YELLOW : Formatting.WHITE));

        w.playBtn().setMessage(isPlaying
                ? Text.translatable("gui.opensoundboard.stop").formatted(Formatting.RED)
                : Text.translatable("gui.opensoundboard.play"));
    }


    private void selectSound(File file) {
        this.selectedFile = file;
        this.isBinding = false;

        if (file == null) {
            detailLabel.text(Text.translatable("gui.opensoundboard.select_hint").formatted(Formatting.GRAY));

            detailLocalSlider.active(false);
            if (detailPlayerSlider != null) detailPlayerSlider.active(false);
            detailBindBtn.active(false);
            detailBindBtn.setMessage(Text.translatable("gui.opensoundboard.keybind.none"));

            timelineSlider.active(false);
            timeField.setEditable(false);
            pauseBtn.active(false);
            setStartBtn.active(false);
            backBtn.active(false);
            forwardBtn.active(false);

            timelineSlider.value(0);
            if (!timeField.isFocused()) timeField.setText("0:00.0");
        } else {
            var data = SoundboardConfig.get(file.getName());

            detailLabel.text(Text.translatable("gui.opensoundboard.settings_for", file.getName()).formatted(Formatting.YELLOW));

            detailLocalSlider.active(true);
            detailLocalSlider.value(data.getLocalVolume());

            if (!SoundboardConfig.data.isSyncAudio() && detailPlayerSlider != null) {
                detailPlayerSlider.active(true);
                detailPlayerSlider.value(data.getPlayerVolume());
            }

            detailBindBtn.active(true);
            updateBindButtonText(data.getKeybind());

            boolean isPlaying = SoundboardAudioSystem.isPlaying(file.getName());
            timelineSlider.active(isPlaying);
            timeField.setEditable(isPlaying);
            pauseBtn.active(isPlaying);
            setStartBtn.active(isPlaying);
            backBtn.active(isPlaying);
            forwardBtn.active(isPlaying);

            if (isPlaying) {
                timelineSlider.value(SoundboardAudioSystem.getProgress(file.getName()));
                if (!timeField.isFocused())
                    timeField.setText(GuiTools.formatTimeMillis(SoundboardAudioSystem.getTimeMillis(file.getName())));
            } else {
                timelineSlider.value(0);
                if (!timeField.isFocused()) timeField.setText("0:00.0");
            }
            pauseBtn.setMessage(SoundboardAudioSystem.isPaused(file.getName()) ? Text.literal("▶") : Text.literal("⏸"));
        }

        refreshListVisuals();
    }

    private void updateSelectedVolume(Double local, Double player) {
        var file = selectedFile;
        if (file == null) return;

        var data = SoundboardConfig.get(file.getName());

        if (SoundboardConfig.data.isSyncAudio()) {
            float vol = (local != null ? local.floatValue() : (player != null ? player.floatValue() : 1f));
            for (var entry : SoundboardConfig.sounds().entrySet()) {
                entry.getValue().setLocalVolume(vol);
                entry.getValue().setPlayerVolume(vol);
                SoundboardAudioSystem.setVolume(entry.getKey(), vol, vol);
            }
            data.setLocalVolume(vol);
            data.setPlayerVolume(vol);
            SoundboardAudioSystem.setVolume(file.getName(), vol, vol);
        } else {
            if (local != null) data.setLocalVolume(local.floatValue());
            if (player != null) data.setPlayerVolume(player.floatValue());
            SoundboardAudioSystem.setVolume(file.getName(), data.getLocalVolume(), data.getPlayerVolume());
        }

        SoundboardConfig.save();
    }

    private void updateBindButtonText(SoundboardConfig.KeyBind keyBind) {
        detailBindBtn.setMessage(GuiTools.keyBindLabel(keyBind));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key();
        int scanCode = input.scancode();
        int modifiers = input.modifiers();

        // enter-to-play-first while searching (legacy)
        if (queryField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (!results.isEmpty()) {
                var file = results.get(0);
                selectSound(file);
                togglePlay(file);
                refreshListVisuals();
                return true;
            }
        }

        if (isBinding && selectedFile != null) {
            var data = SoundboardConfig.get(selectedFile.getName());
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                data.setKeybind(null);
                pendingKeybind = null;
                SoundboardConfig.save();
                updateBindButtonText(data.getKeybind());
                isBinding = false;
                return true;
            } else {
                // Don't finalize immediately; user might be holding modifiers (e.g. CTRL)
                pendingKeybind = new SoundboardConfig.KeyBind(keyCode, scanCode, modifiers);
                // keep listening until all keys are released
                detailBindBtn.setMessage(GuiTools.keyBindLabel(pendingKeybind).copy().formatted(Formatting.YELLOW));
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyInput input) {
        if (isBinding && selectedFile != null) {
            // We only get the current modifier state, so we use 'modifiers == 0' as "all released"
            if (input.modifiers() == 0) {
                var data = SoundboardConfig.get(selectedFile.getName());
                data.setKeybind(pendingKeybind);
                pendingKeybind = null;
                SoundboardConfig.save();
                updateBindButtonText(data.getKeybind());
                isBinding = false;
                return true;
            }
            return true;
        }
        return super.keyReleased(input);
    }

    @Override
    public void tick() {
        super.tick();

        if (pendingScrollTarget != null) {
            scrollToSound(pendingScrollTarget);
            pendingScrollTarget = null;
        }

        refreshDetailsUiState();
        refreshListVisuals();
    }

    private void refreshDetailsUiState() {
        var file = selectedFile;
        if (file == null) {
            timelineSlider.active(false);
            timeField.setEditable(false);
            pauseBtn.active(false);
            setStartBtn.active(false);
            backBtn.active(false);
            forwardBtn.active(false);
            loopBtn.setMessage(GuiTools.loopLabel(SoundboardConfig.data.isLoopAll()));
            return;
        }

        boolean isPlaying = SoundboardAudioSystem.isPlaying(file.getName());
        timelineSlider.active(isPlaying);
        timeField.setEditable(isPlaying);
        pauseBtn.active(isPlaying);
        setStartBtn.active(isPlaying);
        backBtn.active(isPlaying);
        forwardBtn.active(isPlaying);

        if (isPlaying) {
            if (!timelineSlider.isFocused()) {
                float p = SoundboardAudioSystem.getProgress(file.getName());
                if (p >= 0) timelineSlider.value(p);
            }
            if (!timeField.isFocused()) {
                timeField.setText(GuiTools.formatTimeMillis(SoundboardAudioSystem.getTimeMillis(file.getName())));
            }
            pauseBtn.setMessage(SoundboardAudioSystem.isPaused(file.getName()) ? Text.literal("▶") : Text.literal("⏸"));
        } else {
            if (!timelineSlider.isFocused()) timelineSlider.value(0);
            if (!timeField.isFocused()) timeField.setText("0:00.0");
        }

        loopBtn.setMessage(GuiTools.loopLabel(SoundboardConfig.data.isLoopAll()));
    }

    private void refreshListVisuals() {
        for (var child : resultsList.children()) {
            if (!(child instanceof FlowLayout row)) continue;
            String id = row.id();
            if (id == null) continue;
            File match = results.stream().filter(f -> Objects.equals(f.getName(), id)).findFirst().orElse(null);
            if (match != null) updateRowVisuals(match, row);
        }
    }

    /**
     * Renames mp3 files in the sound dir to a sanitized filename.
     * Also migrates the per-song config entry key from the old name to the new name.
     */
    private void sanitizeAndRenameSoundFiles() {
        File[] allFiles = OpenSoundboardClient.soundDir.listFiles((dir, name) -> name.endsWith(".mp3"));
        if (allFiles == null || allFiles.length == 0) return;

        boolean changed = false;

        for (File file : allFiles) {
            String oldName = file.getName();
            String base = oldName.substring(0, oldName.length() - 4);
            String sanitizedBase = de.xcrafttm.opensoundboard.tools.YtDlpManager.sanitizeTrackName(base);
            if (sanitizedBase.isBlank()) sanitizedBase = "track";

            String newName = sanitizedBase + ".mp3";
            if (newName.equals(oldName)) continue;

            File target = new File(OpenSoundboardClient.soundDir, newName);
            // Avoid collisions: "name (2).mp3", "name (3).mp3", ...
            if (target.exists()) {
                int i = 2;
                while (target.exists()) {
                    target = new File(OpenSoundboardClient.soundDir, sanitizedBase + " (" + i + ").mp3");
                    i++;
                }
            }

            // rename on disk
            boolean ok = file.renameTo(target);
            if (!ok) continue;

            // migrate selected file pointer
            if (selectedFile != null && selectedFile.getName().equals(oldName)) {
                selectedFile = target;
            }

            // migrate config entry key (preserve settings)
            if (SoundboardConfig.data != null) {
                var oldData = SoundboardConfig.sounds().remove(oldName);
                if (oldData != null) {
                    SoundboardConfig.sounds().put(target.getName(), oldData);
                    changed = true;
                }
            }
        }

        if (changed) {
            SoundboardConfig.save();
        }
    }
}

