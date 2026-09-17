package de.xcrafttm.opensoundboard.tools;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screen-independent owner of the latest YouTube search, so results survive closing and reopening
 * the downloader. Starting a new search cancels the previous one.
 */
public final class YouTubeSearchManager {

    public enum State { IDLE, SEARCHING, DONE, FAILED }

    public record Snapshot(State state, String query, List<YtDlpManager.SearchResult> results, String error, int revision) {
    }

    private static final int RESULT_LIMIT = 20;
    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OpenSoundboard YouTube Search");
        thread.setDaemon(true);
        return thread;
    });

    private static State state = State.IDLE;
    private static String query = "";
    private static List<YtDlpManager.SearchResult> results = List.of();
    private static String error;
    private static int revision;
    private static int generation;
    private static Process process;

    private YouTubeSearchManager() {
    }

    public static void search(String text) {
        if (text == null || text.isBlank()) return;
        int myGeneration;
        Process previous;
        synchronized (LOCK) {
            myGeneration = ++generation;
            previous = process;
            process = null;
            state = State.SEARCHING;
            query = text.trim();
            error = null;
            revision++;
        }
        if (previous != null) previous.destroyForcibly();

        EXECUTOR.execute(() -> {
            YtDlpManager.SearchResponse response = YtDlpManager.search(text, RESULT_LIMIT, started -> {
                synchronized (LOCK) {
                    if (generation == myGeneration) process = started;
                    else started.destroyForcibly();
                }
            });
            synchronized (LOCK) {
                if (generation != myGeneration) return;
                process = null;
                results = response.results();
                error = response.error();
                state = response.error() == null ? State.DONE : State.FAILED;
                revision++;
            }
        });
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(state, query, results, error, revision);
        }
    }
}
