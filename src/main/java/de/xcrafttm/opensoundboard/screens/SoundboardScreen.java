package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.Keys;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.SoundLibrary;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.tools.VoiceBackend;
import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.Widget;
import de.xcrafttm.opensoundboard.ui.widgets.Breadcrumbs;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import de.xcrafttm.opensoundboard.ui.widgets.Slider;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main soundboard screen: search and sorting, a scrollable folder + sound list, and a details
 * area for the selected sound (volumes, keybind, timeline, transport, start point).
 */
public class SoundboardScreen extends OsbScreen {

    private static final long DOUBLE_CLICK_MS = 300;

    private static File currentFolder = null;

    private TextField search;
    private ScrollList list;
    private Button sortModeBtn;
    private Button sortDirBtn;

    // details
    private final List<Widget> details = new ArrayList<>();
    private Slider localSlider;
    private Slider playerSlider;
    private Button bindBtn;
    private Slider timeline;
    private TextField timeField;
    private Button pauseBtn;
    private Button loopBtn;
    private int detailsTop;

    private File selected = null;
    private boolean binding = false;
    private String lastClickedName = null;
    private long lastClickTime = 0;

    private final List<File> results = new ArrayList<>();

    /**
     * Folder contents, read once per build instead of on every keystroke; null = not read yet.
     * {@link #deepSounds} (every sound below the folder, for searches) is only read once needed.
     */
    private List<File> folders;
    private List<Integer> folderCounts;
    private List<File> dirSounds;
    private List<File> deepSounds;

    public SoundboardScreen() {
        super(Component.translatable("gui.opensoundboard.title"));
    }

    @Override
    protected void buildUi() {
        details.clear();
        if (currentFolder == null) {
            currentFolder = SoundboardConfig.resolveLastOpenedFolder(OpenSoundboardClient.soundDir);
        }
        if (!SoundboardConfig.data.isShowSubfolders() || (currentFolder != null && !SoundLibrary.isInside(currentFolder))) {
            currentFolder = null;
        }

        boolean vanilla = vanilla();
        int ctl = UiStyle.controlHeight();
        int gap = vanilla ? 4 : Theme.GAP;
        layoutFrame(430, 0);

        if (vanilla) {
            Button refresh = add(new Button(Component.translatable("gui.opensoundboard.refresh"), b -> refresh()));
            Button folder = add(new Button(Component.translatable("gui.opensoundboard.folder"),
                    b -> McCompat.openFolder(OpenSoundboardClient.soundDir)));
            Button youtube = add(new Button(Component.translatable("gui.opensoundboard.downloader"),
                    b -> McCompat.setScreen(this.minecraft, new DownloaderScreen(this))));
            Button settings = add(new Button(Component.translatable("gui.opensoundboard.config"),
                    b -> McCompat.setScreen(this.minecraft, new SoundboardConfigScreen(this))));
            Button done = add(new Button(Component.translatable("gui.done"), b -> onClose()));
            refresh.tooltip(tip("tooltip.opensoundboard.refresh"));
            folder.tooltip(tip("tooltip.opensoundboard.folder"));
            youtube.tooltip(tip("tooltip.opensoundboard.youtube"));
            layoutFooter(refresh, folder, youtube, settings, done);
        } else {
            addHeaderButton(Icons.CLOSE, tip("gui.done"), this::onClose);
            addHeaderButton(Icons.SETTINGS, tip("tooltip.opensoundboard.config"),
                    () -> McCompat.setScreen(this.minecraft, new SoundboardConfigScreen(this)));
            addHeaderButton(Icons.DOWNLOAD, tip("tooltip.opensoundboard.youtube"),
                    () -> McCompat.setScreen(this.minecraft, new DownloaderScreen(this)));
            addHeaderButton(Icons.FOLDER, tip("tooltip.opensoundboard.folder"),
                    () -> McCompat.openFolder(OpenSoundboardClient.soundDir));
            addHeaderButton(Icons.REFRESH, tip("tooltip.opensoundboard.refresh"), this::refresh);
        }

        // Search + sorting
        int y = bodyY;
        int dirW = ctl;
        int modeW = vanilla ? 90 : 64;
        String query = search != null ? search.getText() : "";
        search = add(new TextField().icon(Icons.SEARCH).placeholder(tip("gui.opensoundboard.search_hint")).onChange(s -> scanSounds()));
        search.setText(query);
        search.bounds(bodyX, y, bodyW - modeW - dirW - gap * 2, ctl);
        sortModeBtn = add(new Button(sortModeLabel(), b -> cycleSortMode()).secondary());
        sortModeBtn.bounds(bodyX + bodyW - dirW - gap - modeW, y, modeW, ctl).tooltip(tip("tooltip.opensoundboard.sortMode"));
        sortDirBtn = add(new Button(sortDirIcon(), null, b -> toggleSortDir()).secondary());
        sortDirBtn.bounds(bodyX + bodyW - dirW, y, dirW, ctl).tooltip(tip("tooltip.opensoundboard.sortDir"));
        y += ctl + gap + 2;

        // Breadcrumbs while inside a folder
        if (currentFolder != null) {
            int crumbH = vanilla ? 14 : 12;
            int backW = crumbH + 2;
            add(new Button(Icons.BACK, null, b -> openFolder(currentFolder.getParentFile())).ghost())
                    .bounds(bodyX, y, backW, crumbH).tooltip(tip("gui.opensoundboard.folder.up"));
            add(new Breadcrumbs(breadcrumbs())).bounds(bodyX + backW + 4, y, bodyW - backW - 4, crumbH);
            y += crumbH + gap;
        }

        // Details block at the bottom of the body
        int lh = (int) Math.ceil(this.font.lineHeight * UiStyle.fontScale());
        int detailsH = Math.max(lh, ctl) + gap + ctl + gap + ctl + gap + ctl;
        detailsTop = bodyY + bodyH - detailsH;

        list = add(new ScrollList().framed(true).gap(vanilla ? 2 : 1));
        list.bounds(bodyX, y, bodyW, detailsTop - 8 - y);

        buildDetails(ctl, gap, Math.max(lh, ctl));

        folders = null;
        deepSounds = null;
        scanSounds();
        SoundboardAudioSystem.scanDurations();
        File active = SoundboardAudioSystem.getActiveSoundFile();
        if (selected == null && active != null && active.exists()) selected = active;
        refreshDetails();
    }

    private <T extends Widget> T detail(T w) {
        add(w);
        details.add(w);
        return w;
    }

    /** Whether the volume sliders control the master volume instead of the selected sound. */
    private static boolean masterSliders() {
        return SoundboardConfig.data.isMasterVolumeOnMainScreen();
    }

    private void buildDetails(int ctl, int gap, int nameLineH) {
        boolean master = masterSliders();
        boolean sync = master ? SoundboardConfig.data.isSyncGlobalVolume() : SoundboardConfig.data.isSyncAudio();
        String prefix = master ? "gui.opensoundboard.master_" : "gui.opensoundboard.";
        int y = detailsTop;

        int bindW = Math.min(130, bodyW / 3);
        bindBtn = detail(new Button(GuiTools.keyBindLabel(null), b -> startBinding()).secondary());
        bindBtn.bounds(bodyX + bodyW - bindW, y + (nameLineH - ctl) / 2, bindW, ctl);
        bindBtn.tooltip(tip("tooltip.opensoundboard.keybind"));
        y += nameLineH + gap;

        localSlider = detail(new Slider(0, v -> onVolume(v, true)).readout(v -> Component.translatable(
                prefix + (sync ? "sync_volume" : "local_volume"), String.valueOf(pct(v))))
                .onCommit(v -> SoundboardConfig.save()));
        playerSlider = detail(new Slider(0, v -> onVolume(v, false))
                .readout(v -> Component.translatable(prefix + "player_volume", String.valueOf(pct(v))))
                .onCommit(v -> SoundboardConfig.save()));
        if (sync) {
            localSlider.bounds(bodyX, y, bodyW, ctl);
            playerSlider.bounds(bodyX, y, bodyW, ctl);
            playerSlider.visible = false;
        } else {
            int half = (bodyW - gap) / 2;
            localSlider.bounds(bodyX, y, half, ctl);
            playerSlider.bounds(bodyX + bodyW - half, y, half, ctl);
        }
        if (master) {
            localSlider.tooltip(tip(sync ? "tooltip.opensoundboard.master_volume" : "tooltip.opensoundboard.globalLocalVolume"));
            playerSlider.tooltip(tip("tooltip.opensoundboard.globalPlayerVolume"));
        } else {
            localSlider.tooltip(tip("tooltip.opensoundboard.local_volume"));
            playerSlider.tooltip(tip("tooltip.opensoundboard.player_volume"));
        }
        y += ctl + gap;

        timeline = detail(new Slider(0, v -> {
            if (selected != null && SoundboardAudioSystem.isPlaying(selectedKey()))
                SoundboardAudioSystem.setCursor(selectedKey(), v.floatValue());
        }).readout(v -> timelineLabel()));
        timeline.bounds(bodyX, y, bodyW, ctl);
        y += ctl + gap;

        int timeW = vanilla() ? 64 : 58;
        timeField = detail(new TextField().maxLength(10).placeholder("0:00.0").onChange(t -> {
            if (selected == null || !timeField.isFocused()) return;
            if (!SoundboardAudioSystem.isPlaying(selectedKey())) return;
            long ms = GuiTools.parseTimeMillis(t);
            long dur = SoundboardAudioSystem.getDurationMillis(selectedKey());
            if (ms >= 0 && dur > 0) SoundboardAudioSystem.setCursor(selectedKey(), Math.max(0f, Math.min(1f, ms / (float) dur)));
        }));
        timeField.bounds(bodyX, y, timeW, ctl);
        timeField.tooltip(tip("tooltip.opensoundboard.time_field"));

        int tbw = vanilla() ? 20 : 22;
        int groupW = tbw * 3 + gap * 2;
        int tb = bodyX + (bodyW - groupW) / 2;
        detail(new Button(Icons.SKIP_BACK, null, b -> skip(-1)).secondary())
                .bounds(tb, y, tbw, ctl).tooltip(tip("gui.opensoundboard.skip_back"));
        pauseBtn = detail(new Button(Icons.PAUSE, null, b -> togglePause()).secondary());
        pauseBtn.bounds(tb + tbw + gap, y, tbw, ctl).tooltip(tip("gui.opensoundboard.pause_resume"));
        detail(new Button(Icons.SKIP_FORWARD, null, b -> skip(1)).secondary())
                .bounds(tb + (tbw + gap) * 2, y, tbw, ctl).tooltip(tip("gui.opensoundboard.skip_forward"));

        int rx = bodyX + bodyW;
        rx -= tbw;
        detail(new Button(Icons.STOP, null, b -> SoundboardAudioSystem.stopAll()).secondary().danger(true))
                .bounds(rx, y, tbw, ctl).tooltip(tip("gui.opensoundboard.stop_all"));
        rx -= tbw + gap;
        detail(new Button(Icons.START_HERE, null, b -> setStart()).secondary())
                .bounds(rx, y, tbw, ctl).tooltip(tip("tooltip.opensoundboard.set_start"));
        rx -= tbw + gap;
        loopBtn = detail(new Button(Icons.LOOP, null, b -> toggleLoop()).secondary());
        loopBtn.bounds(rx, y, tbw, ctl).tooltip(tip("gui.opensoundboard.loop"));
    }

    // ---------------------------------------------------------------- scan / list

    private List<Breadcrumbs.Crumb> breadcrumbs() {
        List<Breadcrumbs.Crumb> crumbs = new ArrayList<>();
        crumbs.add(new Breadcrumbs.Crumb(tip("gui.opensoundboard.library_root"), () -> openFolder(null)));
        for (File folder : SoundLibrary.trail(currentFolder)) {
            crumbs.add(new Breadcrumbs.Crumb(folder.getName(), () -> openFolder(folder)));
        }
        return crumbs;
    }

    /** Navigate to {@code folder}; null or the sounds folder itself means the root. */
    private void openFolder(File folder) {
        currentFolder = SoundLibrary.isInside(folder) ? folder : null;
        SoundboardConfig.saveLastOpenedFolder(currentFolder == null ? null : SoundLibrary.relativePath(currentFolder));
        if (search != null) search.setText("");
        rebuildUi();
    }

    private String selectedKey() {
        return selected == null ? null : SoundLibrary.key(selected);
    }

    private File currentDir() {
        return currentFolder != null ? currentFolder : SoundLibrary.root();
    }

    /** Re-filter the list. Reads the folder from disk only the first time after a (re)build. */
    private void scanSounds() {
        String query = search == null ? "" : search.getText().trim().toLowerCase();
        results.clear();
        list.clearRows();

        boolean subfolders = SoundboardConfig.data.isShowSubfolders();
        File dir = currentDir();
        if (folders == null) {
            folders = subfolders ? SoundLibrary.folders(dir) : List.of();
            folderCounts = folders.stream().map(SoundLibrary::countSounds).toList();
            dirSounds = SoundLibrary.sounds(dir);
        }
        boolean hasFolders = false;
        if (query.isEmpty()) {
            for (int i = 0; i < folders.size(); i++) {
                list.addRow(folderRow(folders.get(i), folderCounts.get(i)));
                hasFolders = true;
            }
        }

        List<File> candidates = dirSounds;
        if (subfolders && !query.isEmpty()) {
            if (deepSounds == null) deepSounds = SoundLibrary.allSounds(dir);
            candidates = deepSounds;
        }
        List<File> sorted = candidates.stream()
                .filter(f -> f.getName().toLowerCase().contains(query))
                .sorted(comparator())
                .collect(Collectors.toList());
        results.addAll(sorted);
        if (hasFolders && !sorted.isEmpty()) list.addRow(dividerRow());
        for (File f : sorted) {
            File parent = f.getParentFile();
            String hint = parent != null && !parent.getAbsoluteFile().equals(dir.getAbsoluteFile())
                    ? SoundLibrary.folderOf(f) : null;
            list.addRow(soundRow(f, hint));
        }

        list.emptyText(tip(query.isEmpty() ? "gui.opensoundboard.empty" : "gui.opensoundboard.no_results"));
    }

    private ScrollList.Row folderRow(File folder, int count) {
        final String name = folder.getName();
        return new ScrollList.Row() {
            public int height() {
                return SoundRows.rowHeight();
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                SoundRows.drawFolder(c, name, count, rx, ry, rw, height(), hovered);
            }

            public boolean click(double mx, double my, int rx, int ry, int rw, int button) {
                if (button != 0) return false;
                openFolder(folder);
                return true;
            }
        };
    }

    private ScrollList.Row dividerRow() {
        return new ScrollList.Row() {
            public int height() {
                return 5;
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                SoundRows.drawDivider(c, rx, ry, rw, height());
            }
        };
    }

    private ScrollList.Row soundRow(File file, String folderHint) {
        final String key = SoundLibrary.key(file);
        return new ScrollList.Row() {
            public int height() {
                return SoundRows.rowHeight();
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                boolean sel = selected != null && key.equals(selectedKey());
                SoundRows.drawSound(c, file, rx, ry, rw, height(), sel, hovered, true, folderHint);
            }

            public boolean click(double mx, double my, int rx, int ry, int rw, int button) {
                if (button != 0) return false;
                if (SoundRows.inPlayZone(mx, rx)) {
                    select(file);
                    SoundboardAudioSystem.toggle(file);
                } else if (SoundRows.inStarZone(mx, rx)) {
                    var data = SoundboardConfig.get(key);
                    data.setFavorite(!data.isFavorite());
                    SoundboardConfig.save();
                    scanSounds();
                } else {
                    long now = System.currentTimeMillis();
                    if (key.equals(lastClickedName) && now - lastClickTime < DOUBLE_CLICK_MS) {
                        SoundboardAudioSystem.toggle(file);
                        lastClickedName = null;
                    } else {
                        lastClickedName = key;
                        lastClickTime = now;
                    }
                    select(file);
                }
                return true;
            }

            public String tooltip(double mx, int rx, int rw) {
                if (SoundRows.inPlayZone(mx, rx)) {
                    return tip(SoundboardAudioSystem.isPlaying(key) ? "gui.opensoundboard.stop" : "gui.opensoundboard.play");
                }
                if (SoundRows.inStarZone(mx, rx)) return tip("gui.opensoundboard.favorite");
                return null;
            }
        };
    }

    private Comparator<File> comparator() {
        Comparator<File> base = switch (SoundboardConfig.data.getSortMode()) {
            case "date" -> {
                // Each file's date is read from disk once per sort, not once per comparison.
                java.util.Map<File, Long> dates = new java.util.HashMap<>();
                yield Comparator.comparingLong(f -> dates.computeIfAbsent(f, File::lastModified));
            }
            case "length" -> Comparator.comparingLong(f -> Math.max(0, SoundboardAudioSystem.getDurationMillis(SoundLibrary.key(f))));
            default -> Comparator.comparing(f -> f.getName().toLowerCase());
        };
        if (!SoundboardConfig.data.isSortAscending()) base = base.reversed();
        return Comparator.comparing((File f) -> SoundboardConfig.get(SoundLibrary.key(f)).isFavorite()).reversed().thenComparing(base);
    }

    // ---------------------------------------------------------------- selection / details

    private void select(File file) {
        selected = file;
        binding = false;
        refreshDetails();
    }

    private void refreshDetails() {
        boolean hasSelection = selected != null;
        boolean master = masterSliders();
        for (Widget w : details) w.active = hasSelection;
        loopBtn.active = true;
        loopBtn.setSelected(SoundboardConfig.data.isLoopAll());
        if (master) {
            localSlider.active = true;
            playerSlider.active = true;
            localSlider.set(SoundboardConfig.data.getGlobalLocalVolume());
            playerSlider.set(SoundboardConfig.data.getGlobalPlayerVolume());
        }

        if (!hasSelection) {
            pauseBtn.setIcon(Icons.PLAY);
            if (!master) {
                localSlider.set(0);
                playerSlider.set(0);
            }
            timeline.set(0);
            bindBtn.setLabel(GuiTools.keyBindLabel(null));
            return;
        }
        String key = selectedKey();
        var data = SoundboardConfig.get(key);
        if (!master) {
            localSlider.set(data.getLocalVolume());
            playerSlider.set(data.getPlayerVolume());
        }
        bindBtn.setLabel(binding
                ? Component.translatable("gui.opensoundboard.keybind.listening")
                : GuiTools.keyBindLabel(data.getKeybind()));
        bindBtn.setSelected(binding);

        boolean playing = SoundboardAudioSystem.isPlaying(key);
        pauseBtn.setIcon(playing && !SoundboardAudioSystem.isPaused(key) ? Icons.PAUSE : Icons.PLAY);
        timeline.active = playing;
        if (playing) {
            timeline.set(SoundboardAudioSystem.getProgress(key));
            if (!timeField.isFocused()) timeField.setText(GuiTools.formatTimeMillis(SoundboardAudioSystem.getTimeMillis(key)));
        } else {
            timeline.set(0);
            if (!timeField.isFocused()) timeField.setText("");
        }
    }

    @Override
    protected void renderContent(UiCanvas c) {
        renderFrame(c, getTitle().getString());
        renderOutputStatus(c);

        boolean vanilla = vanilla();
        int ctl = UiStyle.controlHeight();
        int lineH = Math.max(c.lineHeight(), ctl);
        int textY = c.centeredTextY(detailsTop, lineH);
        int maxW = bindBtn.x - bodyX - 8;
        if (!vanilla) c.hLine(bodyX, detailsTop - 5, bodyW, Theme.border);

        if (selected == null) {
            c.text(c.trimText(tip("gui.opensoundboard.select_hint"), maxW), bodyX, textY,
                    vanilla ? UiStyle.VANILLA_TEXT_MUTED : Theme.textMuted);
        } else {
            String name = GuiTools.baseName(selected);
            int nameX = bodyX;
            if (SoundboardAudioSystem.isPlaying(selectedKey())) {
                c.icon(Icons.NOTE, bodyX, detailsTop + (lineH - Icons.NOTE.height) / 2, vanilla ? 0xFFFFFF55 : Theme.accent);
                nameX += Icons.NOTE.width + 5;
            }
            c.text(c.trimText(name, maxW - (nameX - bodyX)), nameX, textY, vanilla ? UiStyle.VANILLA_TEXT : Theme.text);
        }
    }

    /** Shows where sounds currently go: a voice chat, local-only playback, or nowhere. */
    private void renderOutputStatus(UiCanvas c) {
        VoiceBackend backend = SoundboardAudioSystem.activeBackend();
        String status = SoundboardAudioSystem.outputLabel();
        if (vanilla()) {
            int color = backend == null ? 0xFFFF5555 : UiStyle.VANILLA_TEXT_MUTED;
            c.rightText(c.trimText(status, this.width / 3), this.width - 8, (UiStyle.VANILLA_HEADER_H - c.lineHeight()) / 2 + 1, color);
            return;
        }
        int titleEnd = frameX + Theme.PAD + c.textWidth(getTitle().getString());
        int maxW = frameX + frameW - 5 * 18 - 8 - (titleEnd + 12);
        if (maxW < 30) return;
        int textY = c.centeredTextY(frameY, Theme.HEADER_H + 1);
        int dotColor = backend == null ? Theme.DANGER
                : (backend.localOnly() || SoundboardAudioSystem.isVoiceChatPaused() ? Theme.textFaint : Theme.accent);
        c.fillRect(titleEnd + 8, textY + c.lineHeight() / 2 - 2, 3, 3, dotColor);
        c.text(c.trimText(status, maxW), titleEnd + 15, textY, Theme.textFaint);
    }

    private void onVolume(double v, boolean local) {
        float f = (float) v;
        if (masterSliders()) {
            if (SoundboardConfig.data.isSyncGlobalVolume() || local) SoundboardConfig.data.setGlobalLocalVolume(f);
            if (SoundboardConfig.data.isSyncGlobalVolume() || !local) SoundboardConfig.data.setGlobalPlayerVolume(f);
            return;
        }
        if (selected == null) return;
        var data = SoundboardConfig.get(selectedKey());
        if (SoundboardConfig.data.isSyncAudio()) {
            data.setLocalVolume(f);
            data.setPlayerVolume(f);
        } else if (local) {
            data.setLocalVolume(f);
        } else {
            data.setPlayerVolume(f);
        }
        SoundboardAudioSystem.setVolume(selectedKey(), data.getLocalVolume(), data.getPlayerVolume());
    }

    private void togglePause() {
        if (selected == null) return;
        String key = selectedKey();
        if (!SoundboardAudioSystem.isPlaying(key)) {
            SoundboardAudioSystem.play(selected);
        } else if (SoundboardAudioSystem.isLoading(key)) {
            SoundboardAudioSystem.stop(key);
        } else if (SoundboardAudioSystem.isPaused(key)) {
            SoundboardAudioSystem.resume(key);
        } else {
            SoundboardAudioSystem.pause(key);
        }
    }

    private void skip(int dir) {
        if (selected != null) SoundboardAudioSystem.skip(selectedKey(), dir * SoundboardConfig.data.getSkipAmountSeconds());
    }

    private void toggleLoop() {
        SoundboardConfig.data.setLoopAll(!SoundboardConfig.data.isLoopAll());
        SoundboardConfig.save();
        SoundboardAudioSystem.setGlobalLooping(SoundboardConfig.data.isLoopAll());
    }

    private void setStart() {
        if (selected == null) return;
        var data = SoundboardConfig.get(selectedKey());
        data.setStartingPoint(Math.max(0f, SoundboardAudioSystem.getProgress(selectedKey())));
        SoundboardConfig.save();
    }

    private void startBinding() {
        if (selected != null) {
            binding = true;
            refreshDetails();
        }
    }

    private Component timelineLabel() {
        if (selected == null) return Component.literal("0:00.0 / 0:00.0");
        String key = selectedKey();
        long dur = SoundboardAudioSystem.getDurationMillis(key);
        long passed = SoundboardAudioSystem.isPlaying(key) ? SoundboardAudioSystem.getTimeMillis(key) : 0;
        if (dur > 0) {
            passed = Math.max(0, Math.min(dur, passed));
            return Component.literal(GuiTools.formatTimeMillis(passed) + " / " + GuiTools.formatTimeMillis(dur));
        }
        return Component.literal("0:00.0 / 0:00.0");
    }

    // ---------------------------------------------------------------- toolbar helpers

    private Component sortModeLabel() {
        return Component.translatable("gui.opensoundboard.sort." + SoundboardConfig.data.getSortMode());
    }

    private Icons sortDirIcon() {
        return SoundboardConfig.data.isSortAscending() ? Icons.ARROW_UP : Icons.ARROW_DOWN;
    }

    private void cycleSortMode() {
        String[] modes = {"name", "date", "length"};
        String cur = SoundboardConfig.data.getSortMode();
        int next = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(cur)) next = (i + 1) % modes.length;
        SoundboardConfig.data.setSortMode(modes[next]);
        SoundboardConfig.save();
        sortModeBtn.setLabel(sortModeLabel());
        scanSounds();
    }

    private void toggleSortDir() {
        SoundboardConfig.data.setSortAscending(!SoundboardConfig.data.isSortAscending());
        SoundboardConfig.save();
        sortDirBtn.setIcon(sortDirIcon());
        scanSounds();
    }

    private static String tip(String key) {
        return Component.translatable(key).getString();
    }

    /**
     * Re-read the sounds folder: picks up added, removed, and moved files (settings follow a moved
     * file when its name is unique) and re-measures every sound's length. Never touches the files.
     */
    private void refresh() {
        SoundLibrary.relinkMissing();
        SoundboardAudioSystem.clearDurationCache();
        if (selected != null && !selected.isFile()) selected = null;
        rebuildUi();
    }

    private static int pct(double v) {
        return Math.max(0, Math.min(100, (int) Math.round(v * 100)));
    }

    // ---------------------------------------------------------------- input / tick

    @Override
    protected boolean screenKeyPressed(int key, int scan, int mods) {
        if (binding && selected != null) {
            // Shift/Ctrl/Alt alone are not a bind: wait for the key they are held with.
            if (Keys.isModifierKey(key)) return true;
            var data = SoundboardConfig.get(selectedKey());
            if (key == Keys.ESCAPE) {
                data.setKeybind(null);
            } else {
                data.setKeybind(Keys.pressedKeyBind(key, scan, mods));
            }
            SoundboardConfig.save();
            binding = false;
            refreshDetails();
            return true;
        }
        if (key == Keys.ENTER || key == Keys.NUMPAD_ENTER) {
            if (timeField != null && timeField.isFocused()) return false;
            // Typing a search and pressing Enter plays the first match; otherwise Enter plays the selection.
            if (search != null && search.isFocused() && !search.getText().isBlank() && !results.isEmpty()) {
                select(results.get(0));
                SoundboardAudioSystem.toggle(results.get(0));
                return true;
            }
            if (selected != null) {
                SoundboardAudioSystem.toggle(selected);
                return true;
            }
            if (!results.isEmpty()) {
                select(results.get(0));
                SoundboardAudioSystem.toggle(results.get(0));
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean acceptsTypedText() {
        return true;
    }

    /** Typing while nothing is focused starts a search. */
    @Override
    protected boolean screenCharTyped(char ch) {
        if (search == null || binding || ch < 32 || ch == 127 || Character.isWhitespace(ch)) return false;
        setFocused(search);
        return search.charTyped(ch);
    }

    @Override
    public void tick() {
        super.tick();
        refreshDetails();
    }
}
