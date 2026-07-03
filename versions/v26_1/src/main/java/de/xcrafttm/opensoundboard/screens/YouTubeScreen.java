package de.xcrafttm.opensoundboard.screens;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.tools.YtDlpManager;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * yt-dlp downloader UI (owo-ui). Ported from the legacy Kotlin screen.
 */
public class YouTubeScreen extends BaseOwoScreen<FlowLayout> {

    private static final Pattern PROGRESS_PERCENT = Pattern.compile("(\\d{1,3}\\.\\d)%|(\\d{1,3})%");

    private final Screen parent;

    private TextBoxComponent urlField;
    private ButtonComponent audioToggle;
    private ButtonComponent downloadBtn;

    private LabelComponent statusLabel;

    private FlowLayout logList;
    private ScrollContainer<FlowLayout> logScroll;

    private FlowLayout progressOuter;
    private FlowLayout progressInner;

    private volatile Process currentProcess = null;

    private boolean audioOnly = true;
    private int progress = 0;

    public YouTubeScreen() {
        this(null);
    }

    public YouTubeScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        int columnWidth = Math.min(520, Math.max(320, (int) (this.width * 0.7f)));

        root.surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.TOP)
                .padding(Insets.of(10));

        var title = UIComponents.label(Text.translatable("gui.opensoundboard.youtube.title").formatted(Formatting.BOLD));
        title.horizontalSizing(Sizing.fixed(columnWidth));
        title.horizontalTextAlignment(HorizontalAlignment.CENTER);
        root.child(title.margins(Insets.bottom(10)));

        urlField = UIComponents.textBox(Sizing.fixed(columnWidth));
        urlField.setPlaceholder(Text.translatable("gui.opensoundboard.youtube.url_hint"));
        urlField.setMaxLength(1024);
        root.child(urlField.margins(Insets.bottom(6)));

        var actions = (FlowLayout) UIContainers.horizontalFlow(Sizing.fixed(columnWidth), Sizing.content())
                .gap(6)
                .verticalAlignment(VerticalAlignment.CENTER);

        audioToggle = UIComponents.button(audioToggleLabel(), b -> {
            audioOnly = !audioOnly;
            b.setMessage(audioToggleLabel());
        });
        audioToggle.sizing(Sizing.fixed(150), Sizing.content());

        downloadBtn = UIComponents.button(Text.translatable("gui.opensoundboard.youtube.download"), b -> {
            if (currentProcess != null) {
                cancelDownload();
                return;
            }
            String url = urlField.getText().trim();
            if (url.isBlank()) {
                status(Text.translatable("message.opensoundboard.youtube.provide_url").formatted(Formatting.RED));
                return;
            }
            startDownload(url);
        });
        downloadBtn.sizing(Sizing.fixed(150), Sizing.content());

        var folderBtn = UIComponents.button(Text.translatable("gui.opensoundboard.folder"), b ->
                net.minecraft.util.Util.getOperatingSystem().open(OpenSoundboardClient.soundDir)
        );
        folderBtn.sizing(Sizing.fixed(100), Sizing.content());

        actions.child(audioToggle);
        actions.child(downloadBtn);
        actions.child(folderBtn);

        root.child(actions.margins(Insets.bottom(6)));

        statusLabel = UIComponents.label(Text.literal(" "));
        statusLabel.horizontalSizing(Sizing.fixed(columnWidth));
        root.child(statusLabel.margins(Insets.bottom(6)));

        // progress bar (like kotlin version)
        progressOuter = UIContainers.horizontalFlow(Sizing.fixed(columnWidth), Sizing.fixed(6));
        progressOuter.surface(Surface.flat(0xFF555555));

        progressInner = UIContainers.horizontalFlow(Sizing.fixed(0), Sizing.fixed(6));
        progressInner.surface(Surface.flat(0xFF00AA00));
        progressOuter.child(progressInner);

        root.child(progressOuter.margins(Insets.bottom(6)));
        updateProgressBar(columnWidth);

        logList = UIContainers.verticalFlow(Sizing.fixed(columnWidth - 12), Sizing.content()).gap(1);
        logScroll = UIContainers.verticalScroll(Sizing.fixed(columnWidth), Sizing.fixed(Math.max(140, this.height - 240)), logList);
        logScroll.surface(Surface.flat(0x66000000)).padding(Insets.of(6));
        root.child(logScroll);

        addLog("> Waiting for Command...");

        var saveInfo = UIComponents.label(Text.translatable("gui.opensoundboard.youtube.save_folder", OpenSoundboardClient.soundDir.getAbsolutePath())
                .formatted(Formatting.GRAY));
        saveInfo.horizontalSizing(Sizing.fixed(columnWidth));
        saveInfo.horizontalTextAlignment(HorizontalAlignment.CENTER);
        root.child(saveInfo.margins(Insets.top(6)));

        // Done button at the bottom
        var doneBtn = UIComponents.button(Text.translatable("gui.done"), b -> close());
        doneBtn.sizing(Sizing.fixed(120), Sizing.content());
        root.child(doneBtn.margins(Insets.top(8)));

        updateDownloadButton();
    }

    private void updateProgressBar(int columnWidth) {
        progress = Math.max(0, Math.min(100, progress));
        int px = (int) Math.round(columnWidth * (progress / 100d));
        progressInner.horizontalSizing(Sizing.fixed(px));
    }

    private Integer extractProgress(String line) {
        Matcher m = PROGRESS_PERCENT.matcher(line);
        if (!m.find()) return null;
        String g1 = m.group(1);
        String g2 = m.group(2);
        try {
            if (g1 != null) {
                return (int) Float.parseFloat(g1);
            }
            if (g2 != null) {
                return Integer.parseInt(g2);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Text audioToggleLabel() {
        return Text.translatable("gui.opensoundboard.youtube.audio_only")
                .append(Text.literal(": "))
                .append(Text.literal(audioOnly ? "ON" : "OFF").formatted(audioOnly ? Formatting.GREEN : Formatting.RED));
    }

    private void status(Text t) {
        statusLabel.text(t);
    }

    private void addLog(String line) {
        // This owo version doesn't support label.wrap(true), so we fold long lines manually
        if (logList == null || logScroll == null) return;

        // Use the scroll container width as the basis - logList.width() can be 0/very small during layout
        // and would cause extremely early wrapping (e.g. after 3-5 chars)
        int targetWidth = Math.max(120, logScroll.width() - 18 /*scroll+padding slack*/);
        var renderer = this.client != null ? this.client.textRenderer : null;

        if (renderer == null) {
            logList.child(UIComponents.label(Text.literal(line)).horizontalSizing(Sizing.fill(100)));
            logScroll.scrollTo(1.0);
            return;
        }

        String remaining = line == null ? "" : line;
        while (!remaining.isEmpty()) {
            int split = renderer.getTextHandler().getTrimmedLength(remaining, targetWidth, net.minecraft.text.Style.EMPTY);
            if (split <= 0 || split >= remaining.length()) {
                logList.child(UIComponents.label(Text.literal(remaining)).horizontalSizing(Sizing.fill(100)));
                break;
            }

            String part = remaining.substring(0, split);
            logList.child(UIComponents.label(Text.literal(part)).horizontalSizing(Sizing.fill(100)));
            remaining = remaining.substring(split).stripLeading();
        }

        logScroll.scrollTo(1.0);
    }

    private void cancelDownload() {
        if (currentProcess != null) {
            addLog("> Cancelling download...");
            currentProcess.destroy();
            currentProcess = null;
            status(Text.translatable("message.opensoundboard.youtube.cancelled").formatted(Formatting.YELLOW));
            updateDownloadButton();
        }
    }

    private void startDownload(String url) {
        status(Text.translatable("message.opensoundboard.youtube.downloading"));
        progress = 0;
        updateProgressBar(progressOuter.width());

        logList.clearChildren();
        addLog("> Starting download...");
        updateDownloadButton();

        CompletableFuture.runAsync(() -> {
            YtDlpManager.DownloadResult result = YtDlpManager.downloadUrlIntoSoundDir(
                    url,
                    audioOnly,
                    line -> {
                        Integer p = extractProgress(line);
                        if (client != null) {
                            client.execute(() -> {
                                if (p != null) {
                                    progress = p;
                                    updateProgressBar(progressOuter.width());
                                }
                                addLog(line);
                            });
                        }
                    },
                    proc -> currentProcess = proc
            );

            if (client != null) {
                client.execute(() -> {
                    currentProcess = null;
                    if (result.success()) {
                        status(Text.translatable("message.opensoundboard.youtube.finished").formatted(Formatting.GREEN));
                        progress = 100;
                    } else {
                        status(Text.translatable("message.opensoundboard.youtube.failed").formatted(Formatting.RED));
                    }
                    updateProgressBar(progressOuter.width());

                    if (result.messageOrLog() != null && !result.messageOrLog().isBlank()) {
                        addLog(result.messageOrLog());
                    }
                    updateDownloadButton();
                });
            }
        });
    }

    private void updateDownloadButton() {
        if (downloadBtn == null) return;
        downloadBtn.setMessage(currentProcess != null
                ? Text.translatable("gui.opensoundboard.youtube.cancel").formatted(Formatting.RED)
                : Text.translatable("gui.opensoundboard.youtube.download"));
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        } else {
            super.close();
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateDownloadButton();
    }
}
