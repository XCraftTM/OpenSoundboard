package de.xcrafttm.opensoundboard.tools;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screen-independent owner of the active yt-dlp job. Closing and reopening the downloader screen
 * does not affect the process, status, progress, or log.
 */
public final class YouTubeDownloadManager {

    public enum State {
        IDLE,
        PREPARING,
        DOWNLOADING,
        CANCELLING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public record Snapshot(State state, int progress, List<String> logLines, int revision) {
        public boolean active() {
            return state == State.PREPARING || state == State.DOWNLOADING || state == State.CANCELLING;
        }
    }

    private static final Object LOCK = new Object();
    private static final Pattern PROGRESS = Pattern.compile("(\\d{1,3}(?:\\.\\d+)?)%");
    private static final int MAX_LOG_LINES = 250;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OpenSoundboard YouTube Downloader");
        thread.setDaemon(true);
        return thread;
    });

    private static final List<String> LOG_LINES = new ArrayList<>();
    private static State state = State.IDLE;
    private static Process process;
    private static boolean cancelRequested;
    private static int progress;
    private static int revision;
    private static int lastToastBucket = -1;

    private YouTubeDownloadManager() {
    }

    public static boolean start(String url) {
        if (url == null || url.isBlank()) return false;
        synchronized (LOCK) {
            if (isActive(state)) return false;
            state = State.PREPARING;
            process = null;
            cancelRequested = false;
            progress = 0;
            lastToastBucket = -1;
            LOG_LINES.clear();
            addLogLocked("> " + Component.translatable("message.opensoundboard.youtube.downloading").getString());
            revision++;
        }

        DownloadToast.preparing(0);
        EXECUTOR.execute(() -> runDownload(url));
        return true;
    }

    public static boolean cancel() {
        Process running;
        synchronized (LOCK) {
            if (!isActive(state)) return false;
            cancelRequested = true;
            state = State.CANCELLING;
            addLogLocked("> " + Component.translatable("toast.opensoundboard.youtube.cancelling").getString());
            revision++;
            running = process;
        }
        stopProcess(running);
        DownloadToast.cancelling();
        return true;
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(state, progress, List.copyOf(LOG_LINES), revision);
        }
    }

    private static void runDownload(String url) {
        YtDlpManager.DownloadResult result = YtDlpManager.downloadUrlIntoSoundDir(
                url,
                true,
                YouTubeDownloadManager::onOutput,
                YouTubeDownloadManager::onProcessStart
        );

        boolean cancelled;
        synchronized (LOCK) {
            cancelled = cancelRequested;
            process = null;
            if (cancelled) {
                state = State.CANCELLED;
                addLogLocked("> " + Component.translatable("message.opensoundboard.youtube.cancelled").getString());
            } else if (result.success()) {
                state = State.COMPLETED;
                progress = 100;
                addLogLocked("> " + Component.translatable("message.opensoundboard.youtube.finished").getString());
            } else {
                state = State.FAILED;
                addResultErrorLocked(result.messageOrLog());
                addLogLocked("> " + Component.translatable("message.opensoundboard.youtube.failed").getString());
            }
            revision++;
        }

        if (cancelled) DownloadToast.cancelled();
        else if (result.success()) DownloadToast.completed();
        else DownloadToast.failed();
    }

    private static void onProcessStart(Process startedProcess) {
        boolean cancel;
        synchronized (LOCK) {
            process = startedProcess;
            progress = 0;
            lastToastBucket = -1;
            cancel = cancelRequested;
            if (!cancel) state = State.DOWNLOADING;
            revision++;
        }
        if (cancel) stopProcess(startedProcess);
        else DownloadToast.downloading(0);
    }

    private static void onOutput(String line) {
        if (line == null || line.isBlank()) return;
        int parsedProgress = extractProgress(line);
        State currentState;
        boolean updateToast = false;

        synchronized (LOCK) {
            addLogLocked(line);
            currentState = state;
            if (parsedProgress >= 0 && currentState != State.CANCELLING) {
                progress = parsedProgress;
                int bucket = parsedProgress / 5;
                if (bucket != lastToastBucket) {
                    lastToastBucket = bucket;
                    updateToast = true;
                }
            }
            revision++;
        }

        if (updateToast) {
            if (currentState == State.PREPARING) DownloadToast.preparing(parsedProgress);
            else if (currentState == State.DOWNLOADING) DownloadToast.downloading(parsedProgress);
        }
    }

    private static int extractProgress(String line) {
        Matcher matcher = PROGRESS.matcher(line);
        if (!matcher.find()) return -1;
        try {
            return Math.max(0, Math.min(100, (int) Float.parseFloat(matcher.group(1))));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void addResultErrorLocked(String message) {
        if (message == null || message.isBlank()) return;
        if (message.startsWith("message.opensoundboard.")) {
            addLogLocked(Component.translatable(message).getString());
        } else if (LOG_LINES.isEmpty()) {
            addLogLocked(message);
        }
    }

    private static void addLogLocked(String line) {
        LOG_LINES.add(line);
        while (LOG_LINES.size() > MAX_LOG_LINES) LOG_LINES.remove(0);
    }

    private static boolean isActive(State value) {
        return value == State.PREPARING || value == State.DOWNLOADING || value == State.CANCELLING;
    }

    private static void stopProcess(Process running) {
        if (running == null) return;
        running.descendants().forEach(child -> {
            child.destroy();
            if (child.isAlive()) child.destroyForcibly();
        });
        running.destroy();
        if (running.isAlive()) running.destroyForcibly();
    }
}
