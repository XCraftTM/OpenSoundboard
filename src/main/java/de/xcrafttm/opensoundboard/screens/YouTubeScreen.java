package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.YtDlpManager;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** yt-dlp downloader: URL field, audio-only toggle, download/folder, progress bar, and a log. */
public class YouTubeScreen extends OsbScreen {

    private static final Pattern PROGRESS = Pattern.compile("(\\d{1,3}\\.\\d)%|(\\d{1,3})%");

    private final Screen parent;
    private TextField url;
    private Button audioBtn;
    private Button downloadBtn;
    private ScrollList log;

    private volatile Process currentProcess = null;
    private boolean audioOnly = true;
    private int progress = 0;

    private int px;
    private int py;
    private int pw;
    private int ph;

    public YouTubeScreen(Screen parent) {
        super(Component.translatable("gui.opensoundboard.youtube.title"));
        this.parent = parent;
    }

    @Override
    protected void buildUi() {
        ph = (int) (this.height * 0.9);
        pw = Math.max(460, Math.min(680, (int) (this.width * 0.7)));
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        int cx = px + Theme.PAD;
        int cw = pw - Theme.PAD * 2;
        int y = py + 28;

        url = add(new TextField().maxLength(1024).placeholder(Component.translatable("gui.opensoundboard.youtube.url_hint").getString()));
        url.bounds(cx, y, cw, 20);
        y += 26;

        audioBtn = add(new Button(audioLabel(), b -> {
            audioOnly = !audioOnly;
            b.setLabel(audioLabel());
        }).secondary());
        audioBtn.bounds(cx, y, 150, 18);
        downloadBtn = add(new Button(downloadLabel(), b -> onDownload()));
        downloadBtn.bounds(cx + 156, y, 120, 18);
        add(new Button(Component.translatable("gui.opensoundboard.folder"),
                b -> McCompat.openFolder(OpenSoundboardClient.soundDir)).secondary())
                .bounds(cx + cw - 90, y, 90, 18);
        y += 26;

        int doneY = py + ph - Theme.PAD - 22;
        int saveY = doneY - 14;
        log = add(new ScrollList().gap(1));
        log.bounds(cx, y, cw, saveY - 6 - y);
        addLog("> Waiting for Command...");

        add(new Button(Component.translatable("gui.done"), b -> this.minecraft.setScreen(parent)))
                .bounds(px + (pw - 160) / 2, doneY, 160, 22);
    }

    private Component audioLabel() {
        return Component.translatable("gui.opensoundboard.youtube.audio_only")
                .copy().append(": " + (audioOnly ? "ON" : "OFF"));
    }

    private Component downloadLabel() {
        return currentProcess != null
                ? Component.translatable("gui.opensoundboard.youtube.cancel")
                : Component.translatable("gui.opensoundboard.youtube.download");
    }

    private void addLog(String line) {
        if (log == null || line == null) return;
        log.addRow(new ScrollList.Row() {
            public int height() {
                return 10;
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                c.text(line, rx + 4, ry + 1, Theme.TEXT_MUTED);
            }
        });
    }

    private void onDownload() {
        if (currentProcess != null) {
            currentProcess.destroy();
            currentProcess = null;
            downloadBtn.setLabel(downloadLabel());
            return;
        }
        String link = url.getText().trim();
        if (link.isBlank()) {
            addLog("> Please provide a valid URL.");
            return;
        }
        progress = 0;
        log.clearRows();
        addLog("> Starting download...");
        downloadBtn.setLabel(downloadLabel());

        CompletableFuture.runAsync(() -> {
            YtDlpManager.DownloadResult result = YtDlpManager.downloadUrlIntoSoundDir(link, audioOnly,
                    lineOut -> {
                        Integer p = extractProgress(lineOut);
                        if (this.minecraft != null) this.minecraft.execute(() -> {
                            if (p != null) progress = p;
                            addLog(lineOut);
                        });
                    },
                    proc -> currentProcess = proc);
            if (this.minecraft != null) this.minecraft.execute(() -> {
                currentProcess = null;
                progress = result.success() ? 100 : progress;
                if (result.messageOrLog() != null && !result.messageOrLog().isBlank()) addLog(result.messageOrLog());
                addLog(result.success() ? "> Finished." : "> Download failed.");
                downloadBtn.setLabel(downloadLabel());
            });
        });
    }

    private Integer extractProgress(String line) {
        Matcher m = PROGRESS.matcher(line);
        if (!m.find()) return null;
        try {
            if (m.group(1) != null) return (int) Float.parseFloat(m.group(1));
            if (m.group(2) != null) return Integer.parseInt(m.group(2));
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    protected void renderContent(UiCanvas c) {
        c.fillRect(0, 0, this.width, this.height, Theme.SCRIM);
        c.fillRoundRect(px, py, pw, ph, Theme.PANEL);
        c.roundBorder(px, py, pw, ph, Theme.BORDER);
        c.fillRect(px + Theme.RADIUS, py, pw - Theme.RADIUS * 2, 3, Theme.ACCENT);
        c.centeredText(Component.translatable("gui.opensoundboard.youtube.title"), px + pw / 2, py + 12, Theme.TEXT);

        // progress bar just above the log
        int cx = px + Theme.PAD;
        int cw = pw - Theme.PAD * 2;
        int barY = py + 28 + 26 + 26 - 8;
        c.fillRoundRect(cx, barY, cw, 4, 0xFF3A3A44);
        int fill = (int) (cw * (Math.max(0, Math.min(100, progress)) / 100.0));
        if (fill > 0) c.fillRoundRect(cx, barY, fill, 4, Theme.ACCENT);

        c.text(Component.translatable("gui.opensoundboard.youtube.save_folder", OpenSoundboardClient.soundDir.getName()).getString(),
                cx, py + ph - Theme.PAD - 22 - 12, Theme.TEXT_MUTED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
