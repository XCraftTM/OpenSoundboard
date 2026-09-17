package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import de.xcrafttm.opensoundboard.tools.GuiTools;
import de.xcrafttm.opensoundboard.tools.Keys;
import de.xcrafttm.opensoundboard.tools.McCompat;
import de.xcrafttm.opensoundboard.tools.SoundLibrary;
import de.xcrafttm.opensoundboard.tools.YouTubeDownloadManager;
import de.xcrafttm.opensoundboard.tools.YouTubeSearchManager;
import de.xcrafttm.opensoundboard.tools.YtDlpManager;
import de.xcrafttm.opensoundboard.ui.Icons;
import de.xcrafttm.opensoundboard.ui.OsbScreen;
import de.xcrafttm.opensoundboard.ui.TextWrap;
import de.xcrafttm.opensoundboard.ui.Theme;
import de.xcrafttm.opensoundboard.ui.Thumbnails;
import de.xcrafttm.opensoundboard.ui.UiCanvas;
import de.xcrafttm.opensoundboard.ui.UiStyle;
import de.xcrafttm.opensoundboard.ui.widgets.Button;
import de.xcrafttm.opensoundboard.ui.widgets.Choice;
import de.xcrafttm.opensoundboard.ui.widgets.ScrollList;
import de.xcrafttm.opensoundboard.ui.widgets.TextField;
import de.xcrafttm.opensoundboard.ui.widgets.Toggle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.List;

/**
 * Downloads audio with yt-dlp, either from a pasted link or by searching YouTube. Search results
 * look like YouTube's own list (thumbnail with duration, title, channel, views). The yt-dlp log is
 * hidden until the user asks for it.
 */
public class DownloaderScreen extends OsbScreen {

    private enum Mode { LINK, SEARCH }

    private static final int MODERN_FOOTER_H = 24;
    private static final long DOUBLE_CLICK_MS = 300;

    private static Mode mode = Mode.LINK;
    private static boolean showLog = false;
    private static String linkText = "";
    private static String searchText = "";

    private final Screen parent;
    private TextField input;
    private Button actionBtn;
    private Button logBtn;
    private ScrollList log;
    private ScrollList results;
    private Toggle lastFolderToggle;
    private int lastFolderLabelEnd;

    private int statusY;
    private int contentTop;
    private int contentBottom;
    private int shownLogRevision = -1;
    private int shownSearchRevision = -1;
    private YouTubeDownloadManager.Snapshot download = YouTubeDownloadManager.snapshot();
    private YouTubeSearchManager.Snapshot search = YouTubeSearchManager.snapshot();
    private String lastClickedId;
    private long lastClickTime;

    public DownloaderScreen(Screen parent) {
        super(Component.translatable("gui.opensoundboard.downloader.title"));
        this.parent = parent;
    }

    @Override
    protected void buildUi() {
        shownLogRevision = -1;
        shownSearchRevision = -1;
        boolean vanilla = vanilla();
        int ctl = UiStyle.controlHeight();
        int gap = vanilla ? 4 : Theme.GAP;
        layoutFrame(470, vanilla ? 0 : MODERN_FOOTER_H);

        int y = bodyY;
        int modeW = 150;
        add(new Choice<>(List.of(Mode.LINK, Mode.SEARCH), mode,
                m -> Component.translatable(m == Mode.LINK ? "gui.opensoundboard.downloader.mode.link" : "gui.opensoundboard.downloader.mode.search"),
                m -> {
                    storeInput();
                    mode = m;
                    rebuildUi();
                }).label(Component.translatable("gui.opensoundboard.downloader.mode")))
                .bounds(bodyX, y, modeW, ctl);

        logBtn = new Button(Icons.LOG, logLabel(), b -> {
            showLog = !showLog;
            storeInput();
            rebuildUi();
        }).ghost();
        logBtn.tooltip(tip("tooltip.opensoundboard.downloader.log"));
        if (!vanilla) {
            int logW = Math.max(70, (int) Math.ceil(this.font.width(logLabel().getString()) * UiStyle.fontScale()) + 26);
            add(logBtn).bounds(bodyX + bodyW - logW, y, logW, ctl);
        } else {
            int toggleW = Math.min(150, bodyW - modeW - gap);
            lastFolderToggle = add(new Toggle(SoundboardConfig.data.isDownloadToLastFolder(), this::setDownloadToLastFolder)
                    .label(Component.translatable("gui.opensoundboard.downloader.last_folder")));
            lastFolderToggle.bounds(bodyX + bodyW - toggleW, y, toggleW, ctl);
        }
        y += ctl + gap;

        int actionW = vanilla ? 100 : 96;
        input = add(new TextField().maxLength(1024)
                .icon(mode == Mode.SEARCH ? Icons.SEARCH : null)
                .placeholder(tip(mode == Mode.LINK ? "gui.opensoundboard.youtube.url_hint" : "gui.opensoundboard.downloader.search_hint")));
        input.setText(mode == Mode.LINK ? linkText : searchText);
        input.bounds(bodyX, y, bodyW - actionW - gap, ctl);
        actionBtn = add(new Button(Icons.DOWNLOAD, Component.empty(), b -> onAction()).primary());
        actionBtn.bounds(bodyX + bodyW - actionW, y, actionW, ctl);
        y += ctl + gap + 4;

        statusY = y;
        y += (int) Math.ceil(this.font.lineHeight * UiStyle.fontScale()) + 10;

        contentTop = y;
        contentBottom = bodyY + bodyH;
        int area = contentBottom - contentTop;
        int logH = showLog ? (mode == Mode.SEARCH ? Math.max(48, area * 2 / 5) : area) : 0;

        if (mode == Mode.SEARCH) {
            int listBottom = showLog ? contentBottom - logH - gap : contentBottom;
            results = add(new ScrollList().framed(true).gap(vanilla ? 2 : 1));
            results.bounds(bodyX, contentTop, bodyW, listBottom - contentTop);
        } else {
            results = null;
        }
        if (showLog) {
            log = add(new ScrollList().gap(0).framed(true));
            log.emptyText(tip("gui.opensoundboard.youtube.log_empty"));
            log.bounds(bodyX, contentBottom - logH, bodyW, logH);
        } else {
            log = null;
        }

        Button folder = add(new Button(Icons.FOLDER, Component.translatable("gui.opensoundboard.open_folder"),
                b -> McCompat.openFolder(targetFolder())).ghost());
        folder.tooltip(tip("tooltip.opensoundboard.folder"));
        if (vanilla) {
            layoutFooter(folder, add(logBtn), add(new Button(Component.translatable("gui.done"), b -> onClose())));
        } else {
            folder.w = Math.max(80, (int) Math.ceil(this.font.width(tip("gui.opensoundboard.open_folder")) * UiStyle.fontScale()) + 24);
            layoutFooter(folder);
            addHeaderButton(Icons.CLOSE, tip("gui.done"), this::onClose);
            lastFolderToggle = add(new Toggle(SoundboardConfig.data.isDownloadToLastFolder(), this::setDownloadToLastFolder));
            lastFolderToggle.bounds(frameX + Theme.PAD, footerY + (MODERN_FOOTER_H - 12) / 2, 12, 12);
        }
        refreshLastFolderTooltip();
        sync();
    }

    private void setDownloadToLastFolder(boolean value) {
        SoundboardConfig.data.setDownloadToLastFolder(value);
        SoundboardConfig.save();
        refreshLastFolderTooltip();
    }

    private void refreshLastFolderTooltip() {
        lastFolderToggle.tooltip(tip("tooltip.opensoundboard.downloader.last_folder") + "\n"
                + Component.translatable("gui.opensoundboard.youtube.save_folder", targetLabel()).getString());
    }

    /**
     * Where new downloads go: the folder last opened in the soundboard when the toggle is on (and
     * that folder still exists), otherwise the main sounds folder.
     */
    private static File targetFolder() {
        if (SoundboardConfig.data.isDownloadToLastFolder()) {
            File last = SoundboardConfig.resolveLastOpenedFolder(SoundLibrary.root());
            if (last != null) return last;
        }
        return SoundLibrary.root();
    }

    private static String targetLabel() {
        File target = targetFolder();
        String relative = SoundLibrary.relativePath(target);
        String root = tip("gui.opensoundboard.library_root");
        return relative.isEmpty() ? root : root + "/" + relative;
    }

    private Component logLabel() {
        return Component.translatable(showLog ? "gui.opensoundboard.downloader.hide_log" : "gui.opensoundboard.downloader.show_log");
    }

    private void storeInput() {
        if (input == null) return;
        if (mode == Mode.LINK) linkText = input.getText();
        else searchText = input.getText();
    }

    // ---------------------------------------------------------------- actions

    private void onAction() {
        if (mode == Mode.SEARCH) {
            runSearch();
            return;
        }
        if (YouTubeDownloadManager.snapshot().active()) {
            YouTubeDownloadManager.cancel();
            sync();
            return;
        }
        String link = input.getText().trim();
        if (link.isBlank()) {
            input.setInvalid(true);
            return;
        }
        input.setInvalid(false);
        linkText = link;
        YouTubeDownloadManager.start(link, targetFolder());
        sync();
    }

    private void runSearch() {
        String query = input.getText().trim();
        if (query.isBlank()) {
            input.setInvalid(true);
            return;
        }
        input.setInvalid(false);
        searchText = query;
        YouTubeSearchManager.search(query);
        if (results != null) results.scrollToTop();
        sync();
    }

    private void download(YtDlpManager.SearchResult result) {
        if (YouTubeDownloadManager.snapshot().active()) return;
        YouTubeDownloadManager.start(result.url(), targetFolder());
        sync();
    }

    // ---------------------------------------------------------------- state sync

    private void sync() {
        download = YouTubeDownloadManager.snapshot();
        search = YouTubeSearchManager.snapshot();
        boolean downloading = download.active();

        if (mode == Mode.LINK) {
            actionBtn.setIcon(downloading ? Icons.STOP : Icons.DOWNLOAD);
            actionBtn.setLabel(Component.translatable(downloading ? "gui.opensoundboard.youtube.cancel" : "gui.opensoundboard.youtube.download"));
            actionBtn.active = download.state() != YouTubeDownloadManager.State.CANCELLING;
            if (downloading) actionBtn.secondary().danger(true);
            else actionBtn.primary().danger(false);
        } else {
            boolean searching = search.state() == YouTubeSearchManager.State.SEARCHING;
            actionBtn.setIcon(Icons.SEARCH);
            actionBtn.setLabel(Component.translatable(searching ? "gui.opensoundboard.downloader.searching" : "gui.opensoundboard.downloader.search"));
            actionBtn.active = true;
            actionBtn.primary().danger(false);
        }
        logBtn.setLabel(logLabel());

        if (log != null && shownLogRevision != download.revision()) {
            boolean follow = log.isAtBottom();
            log.clearRows();
            for (String line : download.logLines()) log.addRow(logRow(line));
            if (follow) log.scrollToBottom();
            shownLogRevision = download.revision();
        }

        if (results != null && shownSearchRevision != search.revision()) {
            results.clearRows();
            for (YtDlpManager.SearchResult result : search.results()) results.addRow(resultRow(result));
            results.emptyText(searchEmptyText());
            shownSearchRevision = search.revision();
        }
    }

    private String searchEmptyText() {
        return switch (search.state()) {
            case IDLE -> tip("gui.opensoundboard.downloader.search_empty");
            case SEARCHING -> tip("gui.opensoundboard.downloader.searching");
            case DONE -> tip("gui.opensoundboard.downloader.no_results");
            case FAILED -> search.error() != null && search.error().startsWith("message.")
                    ? tip(search.error()) : tip("message.opensoundboard.search_failed");
        };
    }

    @Override
    public void tick() {
        super.tick();
        sync();
    }

    /** Clicking the footer label toggles the checkbox, like a regular settings row. */
    @Override
    protected boolean screenMouseClicked(double mx, double my, int button) {
        if (button != 0 || vanilla() || lastFolderToggle == null) return false;
        boolean onLabel = mx >= lastFolderToggle.x && mx < lastFolderLabelEnd
                && my >= footerY && my < footerY + MODERN_FOOTER_H;
        if (!onLabel) return false;
        lastFolderToggle.toggle();
        return true;
    }

    @Override
    protected boolean screenKeyPressed(int key, int scan, int mods) {
        if ((key == Keys.ENTER || key == Keys.NUMPAD_ENTER) && input.isFocused()) {
            if (mode == Mode.SEARCH) runSearch();
            else if (!download.active()) onAction();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- rows

    private ScrollList.Row logRow(String line) {
        return new ScrollList.Row() {
            public int height() {
                return (int) Math.ceil(9 * UiStyle.fontScale()) + 2;
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                boolean command = line.startsWith(">");
                int color = vanilla()
                        ? (command ? UiStyle.VANILLA_TEXT : UiStyle.VANILLA_TEXT_MUTED)
                        : (command ? Theme.text : Theme.textMuted);
                c.text(c.trimText(line, rw - 10), rx + 5, ry + 1, color);
            }

            public String tooltip(double mx, int rx, int rw) {
                return line.length() > 60 ? line : null;
            }
        };
    }

    private ScrollList.Row resultRow(YtDlpManager.SearchResult result) {
        return new ScrollList.Row() {
            public int height() {
                int lh = (int) Math.ceil(9 * UiStyle.fontScale());
                return Math.max(vanilla() ? 44 : 42, lh * 3 + 12);
            }

            private int buttonX(int rx, int rw) {
                return rx + rw - 22 - 4;
            }

            public void draw(UiCanvas c, int rx, int ry, int rw, boolean hovered) {
                boolean vanilla = vanilla();
                int h = height();
                boolean downloadingThis = download.active() && result.url().equals(download.url());
                ScrollList.drawRowBackground(c, rx, ry, rw, h, downloadingThis, hovered);

                // Thumbnail with duration badge
                int thumbH = h - 8;
                int thumbW = thumbH * 16 / 9;
                int tx = rx + 4;
                int ty = ry + 4;
                if (!Thumbnails.draw(c, result.id(), result.thumbnailUrl(), tx, ty, thumbW, thumbH)) {
                    c.fillRect(tx, ty, thumbW, thumbH, vanilla ? 0xFF1E1E1E : Theme.surface);
                    c.icon(Icons.NOTE, tx, ty, thumbW, thumbH, vanilla ? 0xFF505050 : Theme.textFaint);
                }
                if (result.durationSeconds() >= 0) {
                    String duration = formatDuration(result.durationSeconds());
                    int bw = c.textWidth(duration) + 4;
                    int bh = c.lineHeight() + 1;
                    int bx = tx + thumbW - bw - 2;
                    int by = ty + thumbH - bh - 2;
                    c.fillRect(bx, by, bw, bh, 0xD0000000);
                    c.text(duration, bx + 2, by + 1, 0xFFFFFFFF, false);
                }

                // Download button
                int btnX = buttonX(rx, rw);
                int btnY = ry + (h - 20) / 2;
                boolean btnHover = c.hovered(btnX, btnY, 22, 20);
                boolean canDownload = !download.active();
                if (downloadingThis) {
                    String pct = download.progress() + "%";
                    c.rightText(pct, btnX + 22, c.centeredTextY(btnY, 20), vanilla ? 0xFFFFFF55 : Theme.accent);
                } else if (vanilla) {
                    c.sprite(!canDownload ? "widget/button_disabled" : (btnHover ? "widget/button_highlighted" : "widget/button"), btnX, btnY, 22, 20);
                    c.icon(Icons.DOWNLOAD, btnX, btnY, 22, 20, canDownload ? UiStyle.VANILLA_TEXT : UiStyle.VANILLA_TEXT_DISABLED);
                } else {
                    if (btnHover && canDownload) c.fillRoundRect(btnX, btnY, 22, 20, Theme.accent);
                    int iconColor = !canDownload ? Theme.textFaint : (btnHover ? Theme.onAccent : Theme.textMuted);
                    c.icon(Icons.DOWNLOAD, btnX, btnY, 22, 20, iconColor);
                }

                // Title (up to two lines) and channel / views
                int textX = tx + thumbW + 8;
                int textW = btnX - 8 - textX;
                int lh = c.lineHeight();
                List<String> lines = TextWrap.wrap(c.font, result.title(), textW, 2);
                int titleColor = vanilla ? UiStyle.VANILLA_TEXT : Theme.text;
                for (int i = 0; i < lines.size(); i++) {
                    c.text(lines.get(i), textX, ry + 4 + i * (lh + 1), titleColor);
                }
                String meta = metaLine(result);
                c.text(c.trimText(meta, textW), textX, ry + h - lh - 3, vanilla ? UiStyle.VANILLA_TEXT_MUTED : Theme.textMuted);
            }

            public boolean click(double mx, double my, int rx, int ry, int rw, int button) {
                if (button != 0) return false;
                if (mx >= buttonX(rx, rw)) {
                    if (download.active()) return false;
                    download(result);
                    return true;
                }
                long now = System.currentTimeMillis();
                if (result.id().equals(lastClickedId) && now - lastClickTime < DOUBLE_CLICK_MS) {
                    lastClickedId = null;
                    download(result);
                } else {
                    lastClickedId = result.id();
                    lastClickTime = now;
                }
                return true;
            }

            public String tooltip(double mx, int rx, int rw) {
                if (mx >= buttonX(rx, rw)) {
                    return tip(download.active() ? "tooltip.opensoundboard.downloader.busy" : "tooltip.opensoundboard.downloader.download");
                }
                return result.title() + (result.channel().isEmpty() ? "" : "\n" + result.channel());
            }
        };
    }

    private static String metaLine(YtDlpManager.SearchResult result) {
        StringBuilder meta = new StringBuilder(result.channel());
        if (result.viewCount() >= 0) {
            if (meta.length() > 0) meta.append(" · ");
            meta.append(Component.translatable("gui.opensoundboard.downloader.views", compactNumber(result.viewCount())).getString());
        }
        return meta.toString();
    }

    private static String formatDuration(int seconds) {
        if (seconds >= 3600) {
            return String.format("%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
        }
        return GuiTools.formatTimeSeconds(seconds);
    }

    private static String compactNumber(long value) {
        if (value < 1_000) return String.valueOf(value);
        String[] units = {"K", "M", "B"};
        double scaled = value;
        int unit = -1;
        while (scaled >= 1_000 && unit < units.length - 1) {
            scaled /= 1_000;
            unit++;
        }
        String number = scaled < 10 ? String.format(java.util.Locale.ROOT, "%.1f", scaled) : String.valueOf((long) scaled);
        return number.replace(".0", "") + units[unit];
    }

    // ---------------------------------------------------------------- frame

    private String statusText() {
        return switch (download.state()) {
            case IDLE -> tip(mode == Mode.SEARCH ? "gui.opensoundboard.downloader.state.idle_search" : "gui.opensoundboard.youtube.state.idle");
            case PREPARING -> Component.translatable("gui.opensoundboard.youtube.state.preparing", download.progress()).getString();
            case DOWNLOADING -> Component.translatable("gui.opensoundboard.youtube.state.downloading", download.progress()).getString();
            case CANCELLING -> tip("gui.opensoundboard.youtube.state.cancelling");
            case COMPLETED -> tip("gui.opensoundboard.youtube.state.completed");
            case FAILED -> tip("gui.opensoundboard.youtube.state.failed");
            case CANCELLED -> tip("gui.opensoundboard.youtube.state.cancelled");
        };
    }

    @Override
    protected void renderContent(UiCanvas c) {
        renderFrame(c, getTitle().getString());
        boolean vanilla = vanilla();

        YouTubeDownloadManager.State state = download.state();
        int progress = state == YouTubeDownloadManager.State.COMPLETED ? 100 : Math.max(0, Math.min(100, download.progress()));
        boolean showBar = state != YouTubeDownloadManager.State.IDLE;
        int statusColor;
        if (state == YouTubeDownloadManager.State.FAILED) statusColor = vanilla ? 0xFFFF5555 : Theme.DANGER;
        else if (state == YouTubeDownloadManager.State.COMPLETED) statusColor = vanilla ? 0xFF55FF55 : Theme.accent;
        else statusColor = vanilla ? UiStyle.VANILLA_TEXT : Theme.textMuted;
        c.text(c.trimText(statusText(), bodyW), bodyX, statusY, statusColor);

        int barY = statusY + c.lineHeight() + 3;
        if (vanilla) {
            if (showBar) {
                c.fillRect(bodyX, barY, bodyW, 4, 0xFF000000);
                c.border(bodyX, barY, bodyW, 4, 0xFF808080);
                int fill = (int) ((bodyW - 2) * (progress / 100.0));
                if (fill > 0) c.fillRect(bodyX + 1, barY + 1, fill, 2, 0xFF80FF20);
            }
        } else {
            c.fillRect(bodyX, barY, bodyW, 2, Theme.border);
            int fill = (int) (bodyW * (progress / 100.0));
            if (showBar && fill > 0) c.fillRect(bodyX, barY, fill, 2, Theme.accent);
        }

        if (mode == Mode.LINK && !showLog) {
            int cy = (contentTop + contentBottom) / 2;
            int iconColor = vanilla ? 0xFF606060 : Theme.textFaint;
            c.icon(Icons.DOWNLOAD, bodyX + bodyW / 2 - Icons.DOWNLOAD.width / 2, cy - 16, iconColor);
            int hintColor = vanilla ? UiStyle.VANILLA_TEXT_MUTED : Theme.textMuted;
            List<String> lines = TextWrap.wrap(c.font, tip("gui.opensoundboard.downloader.link_hint"), Math.min(bodyW - 20, 260), 3);
            for (int i = 0; i < lines.size(); i++) {
                c.centeredText(lines.get(i), bodyX + bodyW / 2, cy - 2 + i * (c.lineHeight() + 2), hintColor);
            }
        }

        if (!vanilla) {
            // Footer: [x] Save to last folder   Saves to Sounds/Games
            int textX = lastFolderToggle.x + lastFolderToggle.w + 6;
            int textY = c.centeredTextY(footerY, MODERN_FOOTER_H);
            int maxX = frameX + frameW - Theme.PAD - 110;
            String label = c.trimText(tip("gui.opensoundboard.downloader.last_folder"), Math.max(0, maxX - textX));
            c.text(label, textX, textY, Theme.text);
            lastFolderLabelEnd = textX + c.textWidth(label);
            int destX = lastFolderLabelEnd + 10;
            String destination = Component.translatable("gui.opensoundboard.youtube.save_folder", targetLabel()).getString();
            if (maxX - destX > 30) c.text(c.trimText(destination, maxX - destX), destX, textY, Theme.textFaint);
        }
    }

    private static String tip(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    public void onClose() {
        storeInput();
        McCompat.setScreen(this.minecraft, parent);
    }
}
