package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.YouTubeDownloadManager;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** yt-dlp downloader: URL field, audio-only toggle, download/folder, progress bar, and a log. */
public class YouTubeScreen extends OsbScreen {

    private final Screen parent;
    private TextField url;
    private Button downloadBtn;
    private ScrollList log;

    private int progress = 0;
    private int shownRevision = -1;

    private int px;
    private int py;
    private int pw;
    private int ph;
    private int barY;

    public YouTubeScreen(Screen parent) {
        super(Component.translatable("gui.opensoundboard.youtube.title"));
        this.parent = parent;
    }

    @Override
    protected void buildUi() {
        ph = screenBoxHeight();
        pw = screenBoxWidth(460);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        int cx = px + Theme.PAD;
        int cw = pw - Theme.PAD * 2;
        int y = py + 28;

        url = add(new TextField().maxLength(1024).placeholder(Component.translatable("gui.opensoundboard.youtube.url_hint").getString()));
        url.bounds(cx, y, cw, 20);
        y += 26;

        int folderW = 90;
        int actionGap = 6;
        int downloadW = Math.min(180, cw - folderW - actionGap);
        int actionX = cx + (cw - downloadW - actionGap - folderW) / 2;
        downloadBtn = add(new Button(downloadLabel(), b -> onDownload()));
        downloadBtn.bounds(actionX, y, downloadW, 16)
                .tooltip(Component.translatable("tooltip.opensoundboard.youtube").getString());
        add(new Button(Component.literal("📁 ").append(Component.translatable("gui.opensoundboard.folder")),
                b -> McCompat.openFolder(OpenSoundboardClient.soundDir)).secondary())
                .bounds(actionX + downloadW + actionGap, y, folderW, 16)
                .tooltip(Component.translatable("tooltip.opensoundboard.folder").getString());
        y += 26;

        barY = y - 5;
        y += 4;
        int saveY = py + ph - Theme.PAD - 10;
        log = add(new ScrollList().gap(1));
        log.bounds(cx, y, cw, saveY - 4 - y);
        syncDownloadState();

        add(new Button(Component.literal("✕"), b -> McCompat.setScreen(this.minecraft, parent)).secondary())
                .bounds(px + pw - 22, py + 3, 18, 16).tooltip(Component.translatable("gui.done").getString());
    }

    private Component downloadLabel() {
        return YouTubeDownloadManager.snapshot().active()
                ? Component.literal("⏹ ").append(Component.translatable("gui.opensoundboard.youtube.cancel"))
                : Component.literal("⬇ ").append(Component.translatable("gui.opensoundboard.youtube.download"));
    }

    private void addLog(String line) {
        if (log == null || line == null) return;
        log.addRow(new ScrollList.Row() {
            public int height() {
                return Math.max(10, (int) Math.ceil(9 * UiStyle.fontScale()) + 2);
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                c.text(c.trimText(line, rw - 8), rx + 4,
                        ry + (height() - c.lineHeight()) / 2, Theme.TEXT_MUTED);
            }
        });
    }

    private void onDownload() {
        if (YouTubeDownloadManager.snapshot().active()) {
            YouTubeDownloadManager.cancel();
            syncDownloadState();
            return;
        }
        String link = url.getText().trim();
        if (link.isBlank()) {
            log.clearRows();
            addLog("> " + Component.translatable("message.opensoundboard.youtube.provide_url").getString());
            return;
        }
        YouTubeDownloadManager.start(link);
        syncDownloadState();
    }

    private void syncDownloadState() {
        YouTubeDownloadManager.Snapshot snapshot = YouTubeDownloadManager.snapshot();
        progress = snapshot.progress();
        if (downloadBtn != null) {
            downloadBtn.setLabel(downloadLabel());
            downloadBtn.active = snapshot.state() != YouTubeDownloadManager.State.CANCELLING;
        }
        if (log == null || shownRevision == snapshot.revision()) return;

        log.clearRows();
        if (snapshot.logLines().isEmpty()) {
            addLog("> Waiting for Command...");
        } else {
            for (String line : snapshot.logLines()) addLog(line);
        }
        shownRevision = snapshot.revision();
    }

    @Override
    public void tick() {
        super.tick();
        syncDownloadState();
    }

    @Override
    protected void renderContent(UiCanvas c) {
        renderScreenBox(c, px, py, pw, ph);
        c.centeredText(Component.translatable("gui.opensoundboard.youtube.title"), px + pw / 2, py + 12, Theme.TEXT);

        // progress bar just above the log
        int cx = px + Theme.PAD;
        int cw = pw - Theme.PAD * 2;
        c.fillRoundRect(cx, barY, cw, 4, 0xFF3A3A44);
        int fill = (int) (cw * (Math.max(0, Math.min(100, progress)) / 100.0));
        if (fill > 0) c.fillRoundRect(cx, barY, fill, 4, Theme.ACCENT);

        c.text(Component.translatable("gui.opensoundboard.youtube.save_folder", OpenSoundboardClient.soundDir.getName()).getString(),
                cx, py + ph - Theme.PAD - 8, Theme.TEXT_MUTED);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
