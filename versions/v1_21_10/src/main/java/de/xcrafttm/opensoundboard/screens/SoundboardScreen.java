package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SliderComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
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

    private static final int BUTTON_SPACING    = 5;
    private static final long DOUBLE_CLICK_WINDOW_MS = 250;
    private static final int FAV_WIDTH  = 20;
    private static final int PLAY_WIDTH = 35;

    // UI fields
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

    // State
    private File selectedFile = null;
    private boolean isBinding = false;
    private SoundboardConfig.KeyBind pendingKeybind = null;
    private List<File> results = List.of();
    private File lastClickedFile = null;
    private long lastClickMs = 0;
    private String pendingScrollTarget = null;
    private static File currentFolder = null;
    private double savedScrollPos = 0.0;
    private int listWidth;

    private record RowWidgets(ButtonComponent favBtn, LabelComponent nameLabel, ButtonComponent playBtn) {}
    private final Map<String, RowWidgets> rowWidgets = new HashMap<>();

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        if (currentFolder == null)
            currentFolder = SoundboardConfig.resolveLastOpenedFolder(OpenSoundboardClient.soundDir);

        listWidth = Math.min(400, Math.max(320, (int) (this.width * 0.55f)));

        root.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(10));

        root.child(Components.label(Text.translatable("gui.opensoundboard.title").formatted(Formatting.BOLD))
                .horizontalTextAlignment(HorizontalAlignment.CENTER)
                .margins(Insets.bottom(5)));

        queryField = Components.textBox(Sizing.fixed(listWidth));
        queryField.setPlaceholder(Text.translatable("gui.opensoundboard.search_hint"));
        queryField.onChanged().subscribe(s -> scanSounds());
        root.child(queryField.margins(Insets.bottom(5)));

        int buttonW = Math.max(20, (listWidth - BUTTON_SPACING * 5) / 6);
        var topButtons = (FlowLayout) Containers.horizontalFlow(Sizing.fixed(listWidth), Sizing.content())
                .gap(BUTTON_SPACING).margins(Insets.bottom(10));

        topButtons.child(buildToolbarButton("gui.opensoundboard.refresh",  "tooltip.opensoundboard.refresh",  buttonW, b -> scanSounds()));
        topButtons.child(buildToolbarButton("gui.opensoundboard.folder",   "tooltip.opensoundboard.folder",   buttonW, b -> Util.getOperatingSystem().open(OpenSoundboardClient.soundDir)));
        topButtons.child(buildToolbarButton("gui.opensoundboard.config",   "tooltip.opensoundboard.config",   buttonW, b -> client.setScreen(new SoundboardConfigScreen(this))));
        topButtons.child(buildToolbarButton("gui.opensoundboard.youtube",  "tooltip.opensoundboard.youtube",  buttonW, b -> client.setScreen(new YouTubeScreen(this))));

        var sortModeBtn = Components.button(sortModeLabel(), b -> {
            String[] modes = {"name", "date", "length"};
            String cur = SoundboardConfig.data.getSortMode();
            int next = 0;
            for (int i = 0; i < modes.length; i++) if (modes[i].equals(cur)) { next = (i + 1) % modes.length; break; }
            SoundboardConfig.data.setSortMode(modes[next]);
            SoundboardConfig.save();
            b.setMessage(sortModeLabel());
            scanSounds();
        });
        sortModeBtn.sizing(Sizing.fixed(buttonW), Sizing.content());
        sortModeBtn.tooltip(Text.translatable("tooltip.opensoundboard.sortMode"));
        topButtons.child(sortModeBtn);

        var sortDirBtn = Components.button(sortDirLabel(), b -> {
            SoundboardConfig.data.setSortAscending(!SoundboardConfig.data.isSortAscending());
            SoundboardConfig.save();
            b.setMessage(sortDirLabel());
            scanSounds();
        });
        sortDirBtn.sizing(Sizing.fixed(buttonW), Sizing.content());
        sortDirBtn.tooltip(Text.translatable("tooltip.opensoundboard.sortDir"));
        topButtons.child(sortDirBtn);

        root.child(topButtons);

        resultsList = Containers.verticalFlow(Sizing.fixed(listWidth), Sizing.content()).gap(2);
        scrollContainer = Containers.verticalScroll(Sizing.fixed(listWidth), Sizing.expand(), resultsList);
        scrollContainer.surface(Surface.flat(0x66000000)).padding(Insets.of(5));
        root.child(scrollContainer);

        buildDetailsPane(root);

        root.child(Components.button(Text.translatable("gui.done"), b -> close())
                .sizing(Sizing.fixed(150), Sizing.content())
                .margins(Insets.top(10)));

        scanSounds();

        String activeName = SoundboardAudioSystem.getActiveSoundName();
        if (activeName != null) {
            var file = new File(OpenSoundboardClient.soundDir, activeName);
            if (file.exists()) { selectSound(file); pendingScrollTarget = activeName; }
        }
        refreshDetailsUiState();
    }

    private void buildDetailsPane(FlowLayout root) {
        var pane = Containers.verticalFlow(Sizing.fixed(listWidth), Sizing.content());
        pane.surface(Surface.BLANK).padding(Insets.of(5));

        int innerW = Math.max(0, listWidth - 10);
        int gap = 5, colW = (innerW - gap * 2) / 3;

        detailLabel = Components.label(Text.translatable("gui.opensoundboard.select_hint").formatted(Formatting.GRAY));
        detailLabel.horizontalSizing(Sizing.fill(100));
        pane.child(detailLabel.horizontalTextAlignment(HorizontalAlignment.CENTER).margins(Insets.bottom(5)));

        var sliderRow = (FlowLayout) Containers.horizontalFlow(Sizing.fixed(innerW), Sizing.content())
                .gap(gap).verticalAlignment(VerticalAlignment.CENTER);

        detailLocalSlider  = Components.slider(Sizing.fixed(colW));
        detailPlayerSlider = Components.slider(Sizing.fixed(colW));

        detailLocalSlider.message(v -> {
            int pct = pctFromSlider(v);
            return SoundboardConfig.data.isSyncAudio()
                    ? Text.translatable("gui.opensoundboard.sync_volume",  String.valueOf(pct))
                    : Text.translatable("gui.opensoundboard.local_volume", String.valueOf(pct));
        });
        detailLocalSlider.onChanged().subscribe(value ->
                updateSelectedVolume(value, SoundboardConfig.data.isSyncAudio() ? value : null));
        detailPlayerSlider.message(v ->
                Text.translatable("gui.opensoundboard.player_volume", String.valueOf(pctFromSlider(v))));
        detailPlayerSlider.onChanged().subscribe(value -> updateSelectedVolume(null, value));

        detailBindBtn = (ButtonComponent) Components.button(
                Text.translatable("gui.opensoundboard.keybind.none"), b -> {
                    if (selectedFile != null) {
                        isBinding = true;
                        b.setMessage(Text.translatable("gui.opensoundboard.keybind.listening").formatted(Formatting.YELLOW));
                    }
                }).sizing(Sizing.fixed(colW), Sizing.content());
        detailBindBtn.tooltip(Text.translatable("tooltip.opensoundboard.keybind"));

        sliderRow.child(detailLocalSlider);
        if (SoundboardConfig.data.isSyncAudio()) {
            detailLocalSlider.horizontalSizing(Sizing.fixed(colW * 2 + gap));
            sliderRow.child(Containers.horizontalFlow(Sizing.fixed(0), Sizing.content()));
        } else {
            sliderRow.child(detailPlayerSlider);
        }
        sliderRow.child(detailBindBtn);
        pane.child(sliderRow.margins(Insets.top(2)));

        timelineSlider = Components.slider(Sizing.fill()).message(v -> {
            var file = selectedFile;
            if (file == null) return Text.literal("0:00.0 / 0:00.0");
            String key = file.getName();
            long durationMs = SoundboardAudioSystem.getDurationMillis(key);
            long passedMs   = SoundboardAudioSystem.getTimeMillis(key);
            if (durationMs > 0) {
                if (!SoundboardAudioSystem.isPlaying(key)) {
                    float p = 0;
                    try { p = Float.parseFloat(v); } catch (Exception ignored) {}
                    p = Math.max(0f, Math.min(1f, p));
                    passedMs = Math.max(0, Math.min(durationMs, Math.round(p * durationMs)));
                } else {
                    passedMs = Math.max(0, Math.min(durationMs, passedMs));
                }
                return Text.literal(GuiTools.formatTimeMillis(passedMs) + " / -" + GuiTools.formatTimeMillis(Math.max(0, durationMs - passedMs)));
            }
            return Text.literal(GuiTools.formatTimeMillis(Math.max(0, passedMs)) + " / -0:00.0");
        });
        timelineSlider.onChanged().subscribe(value -> {
            var file = selectedFile;
            if (file != null && SoundboardAudioSystem.isPlaying(file.getName()))
                SoundboardAudioSystem.setCursor(file.getName(), (float) value);
        });
        pane.child(timelineSlider.margins(Insets.top(5)));

        var controlsRow = (FlowLayout) Containers.horizontalFlow(Sizing.fill(100), Sizing.content())
                .verticalAlignment(VerticalAlignment.CENTER);
        var leftCtrl   = (FlowLayout) Containers.horizontalFlow(Sizing.fixed(80), Sizing.content()).horizontalAlignment(HorizontalAlignment.LEFT);
        var centerCtrl = (FlowLayout) Containers.horizontalFlow(Sizing.expand(), Sizing.content()).gap(5).horizontalAlignment(HorizontalAlignment.CENTER);
        var rightCtrl  = (FlowLayout) Containers.horizontalFlow(Sizing.fixed(80), Sizing.content()).horizontalAlignment(HorizontalAlignment.RIGHT);

        timeField = Components.textBox(Sizing.fill());
        timeField.onChanged().subscribe(text -> {
            if (!timeField.isFocused() || selectedFile == null) return;
            if (!SoundboardAudioSystem.isPlaying(selectedFile.getName())) return;
            long millis   = GuiTools.parseTimeMillis(text);
            long duration = SoundboardAudioSystem.getDurationMillis(selectedFile.getName());
            if (millis < 0 || duration <= 0) return;
            SoundboardAudioSystem.setCursor(selectedFile.getName(), Math.max(0f, Math.min(1f, millis / (float) duration)));
        });

        stopButton = buildControlButton("⏹", "gui.opensoundboard.stop_all",     b -> SoundboardAudioSystem.stopAll());
        backBtn    = buildControlButton("⏪", "gui.opensoundboard.skip_back",    b -> { if (selectedFile != null) SoundboardAudioSystem.skip(selectedFile.getName(), -SoundboardConfig.data.getSkipAmountSeconds()); });
        pauseBtn   = buildControlButton("⏸", "gui.opensoundboard.pause_resume", b -> {
            if (selectedFile == null) return;
            if (SoundboardAudioSystem.isPaused(selectedFile.getName())) SoundboardAudioSystem.resume(selectedFile.getName());
            else SoundboardAudioSystem.pause(selectedFile.getName());
        });
        forwardBtn = buildControlButton("⏩", "gui.opensoundboard.skip_forward", b -> { if (selectedFile != null) SoundboardAudioSystem.skip(selectedFile.getName(), SoundboardConfig.data.getSkipAmountSeconds()); });
        loopBtn = Components.button(GuiTools.loopLabel(SoundboardConfig.data.isLoopAll()), b -> {
            SoundboardConfig.data.setLoopAll(!SoundboardConfig.data.isLoopAll());
            SoundboardConfig.save();
            SoundboardAudioSystem.setGlobalLooping(SoundboardConfig.data.isLoopAll());
            b.setMessage(GuiTools.loopLabel(SoundboardConfig.data.isLoopAll()));
        });
        loopBtn.tooltip(Text.translatable("gui.opensoundboard.loop"));
        loopBtn.sizing(Sizing.fixed(30), Sizing.content());

        setStartBtn = Components.button(Text.translatable("gui.opensoundboard.set_start"), b -> {
            if (selectedFile == null) return;
            var data = SoundboardConfig.get(selectedFile.getName());
            data.setStartingPoint(Math.max(0f, SoundboardAudioSystem.getProgress(selectedFile.getName())));
            SoundboardConfig.save();
            if (client.player != null)
                client.player.sendMessage(Text.translatable("message.opensoundboard.start_point_set", selectedFile.getName()), true);
        });
        setStartBtn.sizing(Sizing.fixed(80), Sizing.content());
        setStartBtn.tooltip(Text.translatable("tooltip.opensoundboard.set_start"));

        leftCtrl.child(timeField);
        centerCtrl.child(stopButton).child(backBtn).child(pauseBtn).child(forwardBtn).child(loopBtn);
        rightCtrl.child(setStartBtn);
        controlsRow.child(leftCtrl).child(centerCtrl).child(rightCtrl);
        pane.child(controlsRow.margins(Insets.top(5)));
        root.child(pane);
    }

    // ---------------------------------------------------------------
    // Builder helpers
    // ---------------------------------------------------------------

    private ButtonComponent buildToolbarButton(String labelKey, String tooltipKey, int width, java.util.function.Consumer<ButtonComponent> action) {
        var btn = Components.button(Text.translatable(labelKey), action);
        btn.sizing(Sizing.fixed(width), Sizing.content());
        btn.tooltip(Text.translatable(tooltipKey));
        return btn;
    }

    private ButtonComponent buildControlButton(String symbol, String tooltipKey, java.util.function.Consumer<ButtonComponent> action) {
        var btn = Components.button(Text.literal(symbol), action);
        btn.tooltip(Text.translatable(tooltipKey));
        btn.sizing(Sizing.fixed(30), Sizing.content());
        return btn;
    }

    private static int pctFromSlider(String v) {
        try { return Math.max(0, Math.min(100, (int) Math.round(Double.parseDouble(v) * 100d))); }
        catch (Exception ignored) { return 0; }
    }

    private void setPlaybackControlsActive(boolean active) {
        timelineSlider.active(active);
        timeField.setEditable(active);
        pauseBtn.active(active);
        setStartBtn.active(active);
        backBtn.active(active);
        forwardBtn.active(active);
    }

    // ---------------------------------------------------------------
    // Scroll
    // ---------------------------------------------------------------

    private void scrollToSound(String name) {
        if (name == null || scrollContainer == null || resultsList == null) return;
        var children = resultsList.children();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof FlowLayout row && name.equals(row.id())) {
                double t = children.size() <= 1 ? 0.0 : (i / (double) (children.size() - 1));
                scrollContainer.scrollTo(Math.max(0.0, Math.min(1.0, t)));
                return;
            }
        }
    }

    // ---------------------------------------------------------------
    // Sort
    // ---------------------------------------------------------------

    private static Text sortModeLabel() {
        return Text.translatable("gui.opensoundboard.sort." + SoundboardConfig.data.getSortMode()).formatted(Formatting.AQUA);
    }

    private static Text sortDirLabel() {
        return Text.literal(SoundboardConfig.data.isSortAscending() ? "▲" : "▼").formatted(Formatting.AQUA);
    }

    private Comparator<File> sortComparator() {
        Comparator<File> base = switch (SoundboardConfig.data.getSortMode()) {
            case "date"   -> Comparator.comparingLong(File::lastModified);
            case "length" -> Comparator.comparingLong(f -> { long d = SoundboardAudioSystem.getDurationMillis(f.getName()); return d > 0 ? d : 0L; });
            default       -> Comparator.comparing(f -> f.getName().toLowerCase());
        };
        if (!SoundboardConfig.data.isSortAscending()) base = base.reversed();
        return Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite()).reversed().thenComparing(base);
    }

    // ---------------------------------------------------------------
    // Scan
    // ---------------------------------------------------------------

    private void scanSounds() {
        sanitizeAndRenameSoundFiles();
        String query = queryField.getText().trim().toLowerCase();
        resultsList.clearChildren();
        results = new ArrayList<>();
        rowWidgets.clear();

        if (SoundboardConfig.data.isShowSubfolders()) scanWithSubfolders(query);
        else scanFlat(query);

        if (selectedFile != null && results.stream().noneMatch(f -> Objects.equals(f.getName(), selectedFile.getName())))
            selectSound(null);

        if (scrollContainer != null) scrollContainer.scrollTo(savedScrollPos);
        SoundboardAudioSystem.preloadAll();
    }

    private void scanFlat(String query) {
        File dir = currentFolder != null ? currentFolder : OpenSoundboardClient.soundDir;
        if (currentFolder != null) resultsList.child(buildBackRow());
        File[] files = dir.listFiles((d, n) -> n.endsWith(".mp3"));
        if (files == null) files = new File[0];
        appendSorted(files, query);
    }

    private void scanWithSubfolders(String query) {
        if (currentFolder != null) {
            resultsList.child(buildBackRow());
            File[] files = currentFolder.listFiles((d, n) -> n.endsWith(".mp3"));
            if (files == null) files = new File[0];
            appendSorted(files, query);
        } else {
            File[] subdirs = OpenSoundboardClient.soundDir.listFiles(File::isDirectory);
            if (subdirs != null) {
                Arrays.sort(subdirs, Comparator.comparing(File::getName));
                for (File sub : subdirs) {
                    File[] mp3s = sub.listFiles((d, n) -> n.endsWith(".mp3"));
                    if (mp3s == null || mp3s.length == 0) continue;
                    boolean anyMatch = query.isEmpty() || sub.getName().toLowerCase().contains(query)
                            || Arrays.stream(mp3s).anyMatch(f -> f.getName().toLowerCase().contains(query));
                    if (anyMatch) resultsList.child(buildFolderRow(sub));
                }
            }
            File[] rootFiles = OpenSoundboardClient.soundDir.listFiles((d, n) -> n.endsWith(".mp3"));
            if (rootFiles == null) rootFiles = new File[0];
            appendSorted(rootFiles, query);
        }
    }

    private void appendSorted(File[] files, String query) {
        List<File> filtered = Arrays.stream(files)
                .filter(f -> f.getName().toLowerCase().contains(query))
                .sorted(sortComparator())
                .collect(Collectors.toList());
        results.addAll(filtered);
        filtered.forEach(f -> resultsList.child(buildRow(f)));
    }

    // ---------------------------------------------------------------
    // Row builders
    // ---------------------------------------------------------------

    private FlowLayout buildFolderRow(File dir) {
        var row = Containers.horizontalFlow(Sizing.fill(), Sizing.content());
        row.gap(3).padding(Insets.of(1)).verticalAlignment(VerticalAlignment.CENTER);
        row.surface(Surface.flat(0x33FFFF00));

        String label = GuiTools.trimName(client.textRenderer, "📁 " + dir.getName(), listWidth - FAV_WIDTH - PLAY_WIDTH - 20);
        var nameLabel = Components.label(Text.literal(label).formatted(Formatting.YELLOW));
        nameLabel.horizontalTextAlignment(HorizontalAlignment.LEFT).horizontalSizing(Sizing.expand());

        var openBtn = Components.button(Text.literal("▶"), b -> {});
        openBtn.sizing(Sizing.fixed(PLAY_WIDTH), Sizing.content());
        openBtn.active(false);

        row.child(openBtn);
        row.child(Containers.horizontalFlow(Sizing.fixed(FAV_WIDTH), Sizing.content()));
        row.child(nameLabel);
        row.mouseDown().subscribe((mx, my) -> {
            currentFolder = dir;
            SoundboardConfig.saveLastOpenedFolder(dir);
            savedScrollPos = 0.0;
            scanSounds();
            return true;
        });
        return row;
    }

    private FlowLayout buildBackRow() {
        var row = Containers.horizontalFlow(Sizing.fill(), Sizing.content());
        row.gap(3).padding(Insets.of(1)).verticalAlignment(VerticalAlignment.CENTER);
        row.surface(Surface.flat(0x33FFFFFF));

        var icon = Components.button(Text.literal("←"), b -> {});
        icon.sizing(Sizing.fixed(PLAY_WIDTH), Sizing.content());
        icon.active(false);

        var nameLabel = Components.label(
                Text.literal("← " + (currentFolder != null ? currentFolder.getName() : "")).formatted(Formatting.GRAY));
        nameLabel.horizontalTextAlignment(HorizontalAlignment.LEFT).horizontalSizing(Sizing.expand());

        row.child(icon);
        row.child(Containers.horizontalFlow(Sizing.fixed(FAV_WIDTH), Sizing.content()));
        row.child(nameLabel);
        row.mouseDown().subscribe((mx, my) -> {
            currentFolder = null;
            SoundboardConfig.saveLastOpenedFolder(null);
            savedScrollPos = 0.0;
            scanSounds();
            return true;
        });
        return row;
    }

    private FlowLayout buildRow(File file) {
        final String key = file.getName();
        var row = Containers.horizontalFlow(Sizing.fill(), Sizing.content());
        row.gap(3).padding(Insets.of(1)).verticalAlignment(VerticalAlignment.CENTER).id(key);

        var data = SoundboardConfig.get(key);

        var favBtn = Components.button(GuiTools.favoriteLabel(data.isFavorite()), b -> {
            data.setFavorite(!data.isFavorite());
            SoundboardConfig.save();
            scanSounds();
        });
        favBtn.tooltip(Text.translatable("gui.opensoundboard.favorite"));
        favBtn.sizing(Sizing.fixed(FAV_WIDTH), Sizing.content());

        var nameLabel = Components.label(Text.empty());
        nameLabel.horizontalTextAlignment(HorizontalAlignment.LEFT);

        var playBtn = Components.button(Text.translatable("gui.opensoundboard.play"), b -> {
            togglePlay(file); refreshListVisuals();
        });
        playBtn.sizing(Sizing.fixed(PLAY_WIDTH), Sizing.content());

        row.child(playBtn).child(favBtn).child(nameLabel);
        rowWidgets.put(key, new RowWidgets(favBtn, nameLabel, playBtn));

        row.mouseDown().subscribe((mouseX, mouseY) -> {
            selectSound(file);
            long now = System.currentTimeMillis();
            if (lastClickedFile != null && key.equals(lastClickedFile.getName()) && (now - lastClickMs) <= DOUBLE_CLICK_WINDOW_MS) {
                togglePlay(file); lastClickedFile = null; lastClickMs = 0;
            } else { lastClickedFile = file; lastClickMs = now; }
            refreshListVisuals();
            return true;
        });

        updateRowVisuals(file, row);
        return row;
    }

    // ---------------------------------------------------------------
    // Playback
    // ---------------------------------------------------------------

    private void togglePlay(File file) {
        String key = file.getName();
        if (SoundboardAudioSystem.isPlaying(key)) SoundboardAudioSystem.stop(key);
        else SoundboardAudioSystem.playFile(file, SoundboardConfig.get(key).getLocalVolume(), SoundboardConfig.get(key).getPlayerVolume());
    }

    private void updateRowVisuals(File file, FlowLayout row) {
        final String key = file.getName();
        boolean isSelected = selectedFile != null && key.equals(selectedFile.getName());
        boolean isPlaying  = SoundboardAudioSystem.isPlaying(key);
        row.surface(isSelected ? Surface.flat(0x33FFFFFF) : Surface.BLANK);
        RowWidgets w = rowWidgets.get(key);
        if (w == null) return;
        w.favBtn().setMessage(GuiTools.favoriteLabel(SoundboardConfig.get(key).isFavorite()));
        w.nameLabel().text(Text.literal(
                GuiTools.trimName(client.textRenderer, GuiTools.baseName(file), listWidth - FAV_WIDTH - PLAY_WIDTH - 20)
        ).formatted(isPlaying ? Formatting.YELLOW : Formatting.WHITE));
        w.playBtn().setMessage(isPlaying
                ? Text.translatable("gui.opensoundboard.stop").formatted(Formatting.RED)
                : Text.translatable("gui.opensoundboard.play"));
    }

    private void selectSound(File file) {
        selectedFile = file; isBinding = false;
        if (file == null) {
            detailLabel.text(Text.translatable("gui.opensoundboard.select_hint").formatted(Formatting.GRAY));
            detailLocalSlider.active(false);
            if (detailPlayerSlider != null) detailPlayerSlider.active(false);
            detailBindBtn.active(false);
            detailBindBtn.setMessage(Text.translatable("gui.opensoundboard.keybind.none"));
            setPlaybackControlsActive(false);
            timelineSlider.value(0);
            if (!timeField.isFocused()) timeField.setText("0:00.0");
        } else {
            var data = SoundboardConfig.get(file.getName());
            detailLabel.text(Text.translatable("gui.opensoundboard.settings_for", file.getName()).formatted(Formatting.YELLOW));
            detailLocalSlider.active(true); detailLocalSlider.value(data.getLocalVolume());
            if (!SoundboardConfig.data.isSyncAudio() && detailPlayerSlider != null) {
                detailPlayerSlider.active(true); detailPlayerSlider.value(data.getPlayerVolume());
            }
            detailBindBtn.active(true); updateBindButtonText(data.getKeybind());
            boolean playing = SoundboardAudioSystem.isPlaying(file.getName());
            setPlaybackControlsActive(playing);
            if (playing) {
                timelineSlider.value(SoundboardAudioSystem.getProgress(file.getName()));
                if (!timeField.isFocused()) timeField.setText(GuiTools.formatTimeMillis(SoundboardAudioSystem.getTimeMillis(file.getName())));
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
            float vol = local != null ? local.floatValue() : (player != null ? player.floatValue() : 1f);
            for (var entry : SoundboardConfig.sounds().entrySet()) {
                entry.getValue().setLocalVolume(vol); entry.getValue().setPlayerVolume(vol);
                SoundboardAudioSystem.setVolume(entry.getKey(), vol, vol);
            }
            data.setLocalVolume(vol); data.setPlayerVolume(vol);
            SoundboardAudioSystem.setVolume(file.getName(), vol, vol);
        } else {
            if (local  != null) data.setLocalVolume(local.floatValue());
            if (player != null) data.setPlayerVolume(player.floatValue());
            SoundboardAudioSystem.setVolume(file.getName(), data.getLocalVolume(), data.getPlayerVolume());
        }
        SoundboardConfig.save();
    }

    private void updateBindButtonText(SoundboardConfig.KeyBind keyBind) {
        detailBindBtn.setMessage(GuiTools.keyBindLabel(keyBind));
    }

    // ---------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean handled = super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        if (scrollContainer != null) {
            double contentH = resultsList.height(), viewH = scrollContainer.height();
            if (contentH > viewH) {
                double delta = (verticalAmount < 0 ? 14.0 : -14.0) / (contentH - viewH);
                savedScrollPos = Math.max(0.0, Math.min(1.0, savedScrollPos + delta));
            }
        }
        return handled;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        int keyCode = input.key(), scanCode = input.scancode(), modifiers = input.modifiers();
        if (queryField.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            if (!results.isEmpty()) { var f = results.get(0); selectSound(f); togglePlay(f); refreshListVisuals(); return true; }
        }
        if (isBinding && selectedFile != null) {
            var data = SoundboardConfig.get(selectedFile.getName());
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                data.setKeybind(null); pendingKeybind = null; SoundboardConfig.save();
                updateBindButtonText(data.getKeybind()); isBinding = false; return true;
            }
            pendingKeybind = new SoundboardConfig.KeyBind(keyCode, scanCode, modifiers);
            detailBindBtn.setMessage(GuiTools.keyBindLabel(pendingKeybind).copy().formatted(Formatting.YELLOW));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(input);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyInput input) {
        if (isBinding && selectedFile != null && input.modifiers() == 0) {
            var data = SoundboardConfig.get(selectedFile.getName());
            data.setKeybind(pendingKeybind); pendingKeybind = null; SoundboardConfig.save();
            updateBindButtonText(data.getKeybind()); isBinding = false; return true;
        }
        return super.keyReleased(input);
    }

    // ---------------------------------------------------------------
    // Tick
    // ---------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (pendingScrollTarget != null) { scrollToSound(pendingScrollTarget); pendingScrollTarget = null; }
        refreshDetailsUiState();
        refreshListVisuals();
    }

    private void refreshDetailsUiState() {
        loopBtn.setMessage(GuiTools.loopLabel(SoundboardConfig.data.isLoopAll()));
        var file = selectedFile;
        if (file == null) { setPlaybackControlsActive(false); return; }
        boolean playing = SoundboardAudioSystem.isPlaying(file.getName());
        setPlaybackControlsActive(playing);
        if (playing) {
            if (!timelineSlider.isFocused()) { float p = SoundboardAudioSystem.getProgress(file.getName()); if (p >= 0) timelineSlider.value(p); }
            if (!timeField.isFocused()) timeField.setText(GuiTools.formatTimeMillis(SoundboardAudioSystem.getTimeMillis(file.getName())));
            pauseBtn.setMessage(SoundboardAudioSystem.isPaused(file.getName()) ? Text.literal("▶") : Text.literal("⏸"));
        } else {
            if (!timelineSlider.isFocused()) timelineSlider.value(0);
            if (!timeField.isFocused()) timeField.setText("0:00.0");
        }
    }

    private void refreshListVisuals() {
        for (var child : resultsList.children()) {
            if (!(child instanceof FlowLayout row) || row.id() == null) continue;
            results.stream().filter(f -> Objects.equals(f.getName(), row.id())).findFirst()
                    .ifPresent(f -> updateRowVisuals(f, row));
        }
    }

    // ---------------------------------------------------------------
    // File sanitize
    // ---------------------------------------------------------------

    private void sanitizeAndRenameSoundFiles() {
        File[] allFiles = OpenSoundboardClient.soundDir.listFiles((dir, name) -> name.endsWith(".mp3"));
        if (allFiles == null || allFiles.length == 0) return;
        boolean changed = false;
        for (File file : allFiles) {
            String oldName = file.getName();
            String base = oldName.substring(0, oldName.length() - 4);
            String sanitized = de.xcrafttm.opensoundboard.tools.YtDlpManager.sanitizeTrackName(base);
            if (sanitized.isBlank()) sanitized = "track";
            String newName = sanitized + ".mp3";
            if (newName.equals(oldName)) continue;
            File target = new File(OpenSoundboardClient.soundDir, newName);
            for (int i = 2; target.exists(); i++)
                target = new File(OpenSoundboardClient.soundDir, sanitized + " (" + i + ").mp3");
            if (!file.renameTo(target)) continue;
            SoundboardAudioSystem.invalidateCache(oldName);
            SoundboardAudioSystem.preloadFile(target);
            if (selectedFile != null && selectedFile.getName().equals(oldName)) selectedFile = target;
            if (SoundboardConfig.data != null) {
                var old = SoundboardConfig.sounds().remove(oldName);
                if (old != null) { SoundboardConfig.sounds().put(target.getName(), old); changed = true; }
            }
        }
        if (changed) SoundboardConfig.save();
    }
}
