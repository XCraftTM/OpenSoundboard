package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Full-screen picker used by the wheel editor: search + list with a ▶ preview per row. */
public class SongPickerScreen extends OsbScreen {

    private static final int PREVIEW_W = 24;
    private static final int ROW_H = 18;

    private final Screen parent;
    private final Consumer<String> onPick;

    private int px;
    private int py;
    private int pw;
    private int ph;

    private TextField search;
    private ScrollList list;

    private String previewingName = null;
    private long previewStartMs = -1;
    private boolean waitingForStart = false;

    public SongPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Component.translatable("gui.opensoundboard.wheel.picker.title"));
        this.parent = parent;
        this.onPick = onPick;
    }

    @Override
    protected void buildUi() {
        ph = (int) (this.height * 0.9);
        pw = Math.max(360, (int) (this.width * 0.6));
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        int cx = px + Theme.PAD;
        int cw = pw - Theme.PAD * 2;
        int y = py + 26;

        search = add(new TextField().placeholder(Component.translatable("gui.opensoundboard.search_hint").getString())
                .onChange(s -> buildList()));
        search.bounds(cx, y, cw - 76, 18);
        add(new Button(Component.literal("✕ ").append(Component.translatable("gui.opensoundboard.wheel.picker.clear")),
                b -> pick(null)).secondary()).bounds(cx + cw - 70, y, 70, 18);
        y += 24;

        list = add(new ScrollList().gap(2));
        list.bounds(cx, y, cw, py + ph - Theme.PAD - y);
        buildList();

        add(new Button(Component.literal("✕"), b -> closeWithoutPick()).secondary())
                .bounds(px + pw - 22, py + 3, 18, 16).tooltip(Component.translatable("gui.cancel").getString());
    }

    private void buildList() {
        list.clearRows();
        String query = search == null ? "" : search.getText().trim().toLowerCase();
        File[] files = OpenSoundboardClient.soundDir.listFiles((d, n) -> n.endsWith(".mp3"));
        if (files == null) files = new File[0];
        List<File> sorted = Arrays.stream(files)
                .filter(f -> f.getName().toLowerCase().contains(query))
                .sorted(Comparator.comparing((File f) -> SoundboardConfig.get(f.getName()).isFavorite()).reversed()
                        .thenComparing(File::getName))
                .collect(Collectors.toList());
        for (File f : sorted) list.addRow(row(f));
    }

    private ScrollList.Row row(File file) {
        final String name = file.getName();
        return new ScrollList.Row() {
            public int height() {
                return ROW_H;
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                boolean fav = SoundboardConfig.get(name).isFavorite();
                boolean previewing = name.equals(previewingName);
                if (hovered) c.fillRoundRect(rx, ry, rw, ROW_H, Theme.ROW);
                c.fillRoundRect(rx + 3, ry + 2, PREVIEW_W, ROW_H - 4, previewing ? 0xFFB23A3A : Theme.BTN);
                c.centeredText(Component.literal(previewing ? "⏹" : "▶"), rx + 3 + PREVIEW_W / 2, ry + 5,
                        previewing ? Theme.TEXT_ON_ACCENT : Theme.TEXT);
                int nameX = rx + PREVIEW_W + 10;
                String label = (fav ? "★ " : "") + GuiTools.baseName(file);
                c.text(GuiTools.trimName(font, label, rw - (nameX - rx) - 6), nameX, ry + 5,
                        fav ? 0xFF8B85F0 : Theme.TEXT);
            }

            public boolean click(double mx, double my, int rx, int ry, int rw, int button) {
                if (button != 0) return false;
                if (mx < rx + 3 + PREVIEW_W) togglePreview(file);
                else pick(name);
                return true;
            }

            public String tooltip(double mx, int rx, int rw) {
                if (mx < rx + 3 + PREVIEW_W) return Component.translatable("gui.opensoundboard.wheel.picker.preview").getString();
                return GuiTools.baseName(file);
            }
        };
    }

    private void togglePreview(File file) {
        if (file.getName().equals(previewingName)) {
            stopPreview();
            return;
        }
        stopPreview();
        SoundboardConfig.SoundData data = SoundboardConfig.get(file.getName());
        SoundboardAudioSystem.playFile(file, data.getLocalVolume(), data.getPlayerVolume());
        previewingName = file.getName();
        previewStartMs = -1;
        waitingForStart = true;
    }

    private void stopPreview() {
        if (previewingName != null) SoundboardAudioSystem.stop(previewingName);
        previewingName = null;
        previewStartMs = -1;
        waitingForStart = false;
    }

    @Override
    public void tick() {
        super.tick();
        if (previewingName == null) return;
        boolean playing = SoundboardAudioSystem.isPlaying(previewingName);
        if (waitingForStart) {
            if (playing) {
                if (SoundboardAudioSystem.getDurationMillis(previewingName) > 0)
                    SoundboardAudioSystem.setCursor(previewingName, 1f / 3f);
                previewStartMs = System.currentTimeMillis();
                waitingForStart = false;
            }
            return;
        }
        if (!playing) {
            stopPreview();
        } else if (previewStartMs > 0 && System.currentTimeMillis() - previewStartMs >= 15_000L) {
            stopPreview();
        }
    }

    private void pick(String fileName) {
        stopPreview();
        onPick.accept(fileName);
        this.minecraft.setScreen(parent);
    }

    private void closeWithoutPick() {
        stopPreview();
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        closeWithoutPick();
    }

    @Override
    protected void renderContent(UiCanvas c) {
        c.fillRect(0, 0, this.width, this.height, Theme.SCRIM);
        c.fillRoundRect(px, py, pw, ph, Theme.PANEL);
        c.roundBorder(px, py, pw, ph, Theme.BORDER);
        c.fillRect(px + Theme.RADIUS, py, pw - Theme.RADIUS * 2, 3, Theme.ACCENT);
        c.centeredText(Component.translatable("gui.opensoundboard.wheel.picker.title"), px + pw / 2, py + 12, Theme.TEXT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
