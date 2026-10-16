package de.xcrafttm.opensoundboard.tools;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Manages yt-dlp, ffmpeg, and deno (the JavaScript runtime yt-dlp needs for YouTube) in
 * config/opensoundboard/lib and runs yt-dlp to search YouTube and download audio.
 *
 * Every download is checked against the SHA-256 checksum its project publishes and is only put in
 * place once complete, so an interrupted or damaged download is never mistaken for an installed
 * tool. yt-dlp updates itself at most once a day, because YouTube regularly breaks old versions.
 * When no build exists for this platform (ffmpeg on macOS, for example), a copy installed on the
 * system is used instead.
 */
public final class YtDlpManager {

    private YtDlpManager() {
    }

    // ---------------------------------------------------------------- platform & binaries

    private enum Os { WINDOWS, LINUX, MACOS, OTHER }

    private static final Os OS;
    private static final boolean X64;
    private static final boolean ARM64;

    static {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        OS = name.contains("windows") ? Os.WINDOWS
                : name.contains("mac") || name.contains("darwin") ? Os.MACOS
                : name.contains("linux") ? Os.LINUX
                : Os.OTHER;
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        X64 = arch.equals("amd64") || arch.equals("x86_64");
        ARM64 = arch.equals("aarch64") || arch.equals("arm64");
    }

    private enum Kind {
        /** The download is the program itself. */
        FILE,
        /** A zip whose "bin" folder holds the programs (and their DLLs). */
        ZIP_BIN,
        /** A tar.xz whose "bin" folder holds the programs. */
        TAR_XZ_BIN,
        /** A zip with the program at its root. */
        ZIP_ROOT
    }

    /**
     * One tool: {@code asset} is the release file for this platform (null if there is none), and
     * {@code checksumEntry} names it in the checksum list ({@code null}: the list has one hash).
     */
    private record Binary(String id, String fileName, String asset, String url, String checksumUrl,
                          String checksumEntry, Kind kind) {
        File target() {
            return new File(libDir(), fileName);
        }
    }

    private static final String EXE = OS == Os.WINDOWS ? ".exe" : "";
    private static final String YT_DLP_BASE = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
    private static final String FFMPEG_BASE = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/";
    private static final String DENO_BASE = "https://github.com/denoland/deno/releases/latest/download/";

    private static final Binary YT_DLP = ytDlp();
    private static final Binary FFMPEG = ffmpeg();
    private static final Binary DENO = deno();

    private static Binary ytDlp() {
        String asset = switch (OS) {
            case WINDOWS -> X64 ? "yt-dlp.exe" : ARM64 ? "yt-dlp_arm64.exe" : null;
            case LINUX -> X64 ? "yt-dlp_linux" : ARM64 ? "yt-dlp_linux_aarch64" : null;
            case MACOS -> "yt-dlp_macos";
            case OTHER -> null;
        };
        return new Binary("yt-dlp", "yt-dlp" + EXE, asset, YT_DLP_BASE + asset, YT_DLP_BASE + "SHA2-256SUMS", asset, Kind.FILE);
    }

    private static Binary ffmpeg() {
        // Windows uses the "shared" build (DLLs sit next to the programs in bin); Linux uses the
        // static build, because the shared one needs its lib folder. There is no macOS build.
        String asset = switch (OS) {
            case WINDOWS -> X64 ? "ffmpeg-master-latest-win64-gpl-shared.zip" : ARM64 ? "ffmpeg-master-latest-winarm64-gpl-shared.zip" : null;
            case LINUX -> X64 ? "ffmpeg-master-latest-linux64-gpl.tar.xz" : ARM64 ? "ffmpeg-master-latest-linuxarm64-gpl.tar.xz" : null;
            default -> null;
        };
        Kind kind = OS == Os.WINDOWS ? Kind.ZIP_BIN : Kind.TAR_XZ_BIN;
        return new Binary("ffmpeg", "ffmpeg" + EXE, asset, FFMPEG_BASE + asset, FFMPEG_BASE + "checksums.sha256", asset, kind);
    }

    private static Binary deno() {
        String triple = null;
        String cpu = X64 ? "x86_64" : ARM64 ? "aarch64" : null;
        if (cpu != null) {
            triple = switch (OS) {
                case WINDOWS -> cpu + "-pc-windows-msvc";
                case LINUX -> cpu + "-unknown-linux-gnu";
                case MACOS -> cpu + "-apple-darwin";
                case OTHER -> null;
            };
        }
        String asset = triple == null ? null : "deno-" + triple + ".zip";
        return new Binary("deno", "deno" + EXE, asset, DENO_BASE + asset, DENO_BASE + asset + ".sha256sum", null, Kind.ZIP_ROOT);
    }

    private static File libDir() {
        File dir = new File(new File(FabricLoader.getInstance().getConfigDir().toFile(), "opensoundboard"), "lib");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    // ---------------------------------------------------------------- install state

    /** Records which release file each tool was installed from, and when yt-dlp last checked for updates. */
    private static final String STATE_FILE = "installed.properties";
    private static final long UPDATE_CHECK_INTERVAL_MS = TimeUnit.HOURS.toMillis(24);

    private static Properties readState() {
        Properties state = new Properties();
        File file = new File(libDir(), STATE_FILE);
        if (file.isFile()) {
            try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                state.load(reader);
            } catch (IOException | IllegalArgumentException e) {
                state.clear();
            }
        }
        return state;
    }

    private static void writeState(Properties state) {
        File file = new File(libDir(), STATE_FILE);
        File temp = new File(libDir(), STATE_FILE + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp.toPath(), StandardCharsets.UTF_8)) {
            state.store(writer, "OpenSoundboard tool installs");
        } catch (IOException e) {
            OpenSoundboardClient.LOGGER.warn("[OpenSoundboard] Could not save {}: {}", STATE_FILE, e.getMessage());
            return;
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            OpenSoundboardClient.LOGGER.warn("[OpenSoundboard] Could not save {}: {}", STATE_FILE, e.getMessage());
        }
    }

    /** Thrown when a tool cannot be prepared; {@code messageKey} is shown to the player. */
    private static final class ToolException extends Exception {
        final String messageKey;

        ToolException(String messageKey, String detail) {
            super(detail);
            this.messageKey = messageKey;
        }
    }

    /** Locations of the prepared tools; {@code ffmpegDir} holds ffmpeg and ffprobe. */
    private record Tools(File ytDlp, File ffmpegDir, File deno) {
    }

    /** Make sure yt-dlp, ffmpeg, and deno are ready, installing or updating them as needed. */
    private static synchronized Tools prepare(Consumer<String> onProgress) throws ToolException {
        Properties state = readState();
        File ytDlp = provide(YT_DLP, state, onProgress, "message.opensoundboard.platform_unsupported");
        File ffmpeg = provide(FFMPEG, state, onProgress, "message.opensoundboard.ffmpeg_missing");
        File deno = provide(DENO, state, onProgress, "message.opensoundboard.platform_unsupported");
        if (YT_DLP.asset() != null) maybeUpdateYtDlp(ytDlp, state, onProgress);
        return new Tools(ytDlp, ffmpeg.getAbsoluteFile().getParentFile(), deno);
    }

    /** The tool's path: our verified install, a fresh install, or the system's copy. */
    private static File provide(Binary binary, Properties state, Consumer<String> onProgress, String missingKey) throws ToolException {
        if (binary.asset() == null) {
            File system = findOnSystem(binary.fileName());
            if (system != null) return system;
            throw new ToolException(missingKey, "No " + binary.id() + " build for this platform and none installed on the system");
        }
        File target = binary.target();
        if (target.isFile() && binary.asset().equals(state.getProperty(binary.id()))) return target;
        try {
            install(binary, onProgress);
        } catch (Exception e) {
            OpenSoundboardClient.LOGGER.warn("[OpenSoundboard] Could not install {}: {}", binary.id(), e.getMessage());
            log(onProgress, "[OpenSoundboard] Could not install " + binary.id() + ": " + e.getMessage());
            throw new ToolException("message.opensoundboard.binaries_missing", e.getMessage());
        }
        state.setProperty(binary.id(), binary.asset());
        if (binary == YT_DLP) state.setProperty("yt-dlp.checked", String.valueOf(System.currentTimeMillis()));
        writeState(state);
        return target;
    }

    /** Looks for an installed program on the PATH and in the usual Homebrew/system folders. */
    private static File findOnSystem(String fileName) {
        List<String> dirs = new ArrayList<>();
        String path = System.getenv("PATH");
        if (path != null) dirs.addAll(List.of(path.split(File.pathSeparator)));
        if (OS != Os.WINDOWS) dirs.addAll(List.of("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"));
        for (String dir : dirs) {
            if (dir.isBlank()) continue;
            File candidate = new File(dir, fileName);
            if (candidate.isFile() && candidate.canExecute()) return candidate;
        }
        return null;
    }

    private static void maybeUpdateYtDlp(File ytDlp, Properties state, Consumer<String> onProgress) {
        long checked;
        try {
            checked = Long.parseLong(state.getProperty("yt-dlp.checked", "0"));
        } catch (NumberFormatException e) {
            checked = 0;
        }
        long now = System.currentTimeMillis();
        if (now - checked < UPDATE_CHECK_INTERVAL_MS) return;
        log(onProgress, "[OpenSoundboard] Checking for yt-dlp updates...");
        try {
            int exit = run(List.of(ytDlp.getAbsolutePath(), "-U"), libDir(), 120, true,
                    line -> log(onProgress, line), null);
            if (exit != 0) log(onProgress, "[OpenSoundboard] yt-dlp update check failed; using the installed version.");
        } catch (IOException e) {
            log(onProgress, "[OpenSoundboard] yt-dlp update check failed: " + e.getMessage());
        }
        // Checked either way, so a failing update does not slow down every download.
        state.setProperty("yt-dlp.checked", String.valueOf(now));
        writeState(state);
    }

    // ---------------------------------------------------------------- download & install

    private static void install(Binary binary, Consumer<String> onProgress) throws Exception {
        File lib = libDir();
        File part = new File(lib, binary.asset() + ".part");
        File staging = new File(lib, ".staging-" + binary.id());
        try {
            deleteRecursively(staging);
            downloadFile(binary.url(), part, onProgress);
            verifyChecksum(binary, part, onProgress);

            List<File> installed = new ArrayList<>();
            switch (binary.kind()) {
                case FILE -> {
                    move(part, binary.target());
                    installed.add(binary.target());
                }
                case ZIP_BIN -> {
                    extractZip(part, staging, name -> name.contains("/bin/") || name.startsWith("bin/"));
                    installed.addAll(moveAll(staging, lib));
                }
                case TAR_XZ_BIN -> {
                    extractTarXzBin(part, staging);
                    installed.addAll(moveAll(staging, lib));
                }
                case ZIP_ROOT -> {
                    extractZip(part, staging, name -> name.equals(binary.fileName()) || name.endsWith("/" + binary.fileName()));
                    installed.addAll(moveAll(staging, lib));
                }
            }
            if (!binary.target().isFile()) throw new IOException(binary.fileName() + " was not found in " + binary.asset());
            for (File file : installed) {
                //noinspection ResultOfMethodCallIgnored
                file.setExecutable(true, false);
            }
            log(onProgress, "[OpenSoundboard]   " + binary.id() + " is ready");
        } finally {
            Files.deleteIfExists(part.toPath());
            deleteRecursively(staging);
        }
    }

    private static void downloadFile(String urlStr, File dest, Consumer<String> onProgress) throws IOException {
        String fileName = dest.getName().replace(".part", "");
        log(onProgress, "[OpenSoundboard] Downloading " + fileName + " ...");
        log(onProgress, "[OpenSoundboard]   from: " + urlStr);

        HttpURLConnection conn = open(urlStr);
        try {
            long totalBytes = conn.getContentLengthLong(); // -1 if unknown
            long downloaded = 0;
            try (InputStream raw = conn.getInputStream();
                 OutputStream output = Files.newOutputStream(dest.toPath())) {
                byte[] buf = new byte[64 * 1024];
                int lastReportedPercent = -1;
                int read;
                while ((read = raw.read(buf)) != -1) {
                    output.write(buf, 0, read);
                    downloaded += read;

                    if (totalBytes > 0) {
                        int bucket = (int) (downloaded * 100 / totalBytes) / 10 * 10; // report every 10 %
                        if (bucket > lastReportedPercent) {
                            lastReportedPercent = bucket;
                            log(onProgress, String.format("[OpenSoundboard]   %s  %d%% (%s / %s)", fileName, bucket,
                                    humanReadableBytes(downloaded), humanReadableBytes(totalBytes)));
                        }
                    } else if (downloaded / (5 * 1024 * 1024) > (downloaded - read) / (5 * 1024 * 1024)) {
                        log(onProgress, String.format("[OpenSoundboard]   %s  downloaded %s...", fileName, humanReadableBytes(downloaded)));
                    }
                }
            }
            if (totalBytes > 0 && downloaded != totalBytes) {
                throw new IOException("Download of " + fileName + " was incomplete (" + downloaded + " of " + totalBytes + " bytes)");
            }
            log(onProgress, String.format("[OpenSoundboard]   %s  done (%s)", fileName, humanReadableBytes(downloaded)));
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);
        conn.setRequestProperty("User-Agent", "OpenSoundboard-Downloader");
        int code = conn.getResponseCode();
        if (code >= 400) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " for " + url);
        }
        return conn;
    }

    private static final Pattern SHA256 = Pattern.compile("\\b([0-9a-fA-F]{64})\\b");

    private static void verifyChecksum(Binary binary, File file, Consumer<String> onProgress) throws Exception {
        String list;
        HttpURLConnection conn = open(binary.checksumUrl());
        try (InputStream in = conn.getInputStream()) {
            list = new String(in.readNBytes(1024 * 1024), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
        String expected = expectedHash(list, binary.checksumEntry());
        if (expected == null) throw new IOException("No published checksum for " + binary.asset());

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] buf = new byte[64 * 1024];
            int read;
            while ((read = in.read(buf)) != -1) digest.update(buf, 0, read);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException("Checksum mismatch for " + binary.asset() + " (expected " + expected + ", got " + actual + ")");
        }
        log(onProgress, "[OpenSoundboard]   " + binary.asset() + " checksum verified");
    }

    /** The hash for {@code entry} in a "hash  filename" list, or the only hash when {@code entry} is null. */
    private static String expectedHash(String list, String entry) {
        for (String line : list.split("\\R")) {
            Matcher m = SHA256.matcher(line);
            if (!m.find()) continue;
            if (entry == null) return m.group(1);
            String name = line.substring(m.end()).trim();
            if (name.startsWith("*")) name = name.substring(1);
            if (name.equals(entry)) return m.group(1);
        }
        return null;
    }

    private static void extractZip(File zipFile, File destDir, java.util.function.Predicate<String> include) throws IOException {
        Files.createDirectories(destDir.toPath());
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || !include.test(name)) continue;
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (fileName.isEmpty() || fileName.equals("..")) continue;
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, new File(destDir, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Extracts the "bin" folder of a tar.xz with the system's tar (present on Linux and macOS). */
    private static void extractTarXzBin(File archive, File destDir) throws IOException {
        Files.createDirectories(destDir.toPath());
        List<String> entries = new ArrayList<>();
        int listed = run(List.of("tar", "-tJf", archive.getAbsolutePath()), destDir, 120, false, entries::add, null);
        if (listed != 0 || entries.isEmpty()) throw new IOException("tar could not read " + archive.getName());
        String rootDir = entries.get(0).split("/", 2)[0];
        if (rootDir.isBlank()) throw new IOException("Unexpected layout in " + archive.getName());

        List<String> output = new ArrayList<>();
        int exit = run(List.of("tar", "-xJf", archive.getAbsolutePath(), "--strip-components=2",
                "-C", destDir.getAbsolutePath(), rootDir + "/bin"), destDir, 300, true, output::add, null);
        if (exit != 0) throw new IOException("tar failed (" + exit + "): " + String.join(" ", output));
    }

    private static void move(File from, File to) throws IOException {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<File> moveAll(File fromDir, File toDir) throws IOException {
        List<File> moved = new ArrayList<>();
        File[] files = fromDir.listFiles(File::isFile);
        if (files == null) return moved;
        for (File file : files) {
            File target = new File(toDir, file.getName());
            move(file, target);
            moved.add(target);
        }
        return moved;
    }

    private static void deleteRecursively(File dir) throws IOException {
        if (!dir.exists()) return;
        try (Stream<java.nio.file.Path> paths = Files.walk(dir.toPath())) {
            for (java.nio.file.Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }

    private static void log(Consumer<String> onProgress, String message) {
        OpenSoundboardClient.LOGGER.info(message);
        if (onProgress != null) onProgress.accept(message);
    }

    private static String humanReadableBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ---------------------------------------------------------------- processes

    /** {@link #run} result when the process was killed for taking too long. */
    private static final int TIMED_OUT = Integer.MIN_VALUE;

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "OpenSoundboard Process Watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Run a process, passing each output line (UTF-8) to {@code lines}. A watchdog kills it after
     * {@code timeoutSeconds} even while it is silent, so a hung process can never block forever.
     *
     * @return the exit code, or {@link #TIMED_OUT}
     */
    private static int run(List<String> args, File dir, long timeoutSeconds, boolean includeErrors,
                           Consumer<String> lines, Consumer<Process> onStart) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(args).directory(dir);
        if (includeErrors) pb.redirectErrorStream(true);
        else pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        if (onStart != null) onStart.accept(process);

        AtomicBoolean timedOut = new AtomicBoolean();
        ScheduledFuture<?> kill = WATCHDOG.schedule(() -> {
            timedOut.set(true);
            destroyTree(process);
        }, timeoutSeconds, TimeUnit.SECONDS);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) lines.accept(line);
        } finally {
            kill.cancel(false);
        }
        try {
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                destroyTree(process);
                return TIMED_OUT;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            destroyTree(process);
            throw new IOException("Interrupted", e);
        }
        return timedOut.get() ? TIMED_OUT : process.exitValue();
    }

    /** Stop a process and everything it started (yt-dlp runs ffmpeg and deno). */
    static void destroyTree(Process process) {
        if (process == null) return;
        process.descendants().forEach(child -> {
            child.destroy();
            if (child.isAlive()) child.destroyForcibly();
        });
        process.destroy();
        if (process.isAlive()) process.destroyForcibly();
    }

    // ---------------------------------------------------------------- yt-dlp

    public record DownloadResult(boolean success, String messageOrLog) {
    }

    /** One YouTube search hit. {@code durationSeconds} and {@code viewCount} are -1 when unknown. */
    public record SearchResult(String id, String title, String channel, int durationSeconds, long viewCount) {
        public String url() {
            return "https://www.youtube.com/watch?v=" + id;
        }

        public String thumbnailUrl() {
            return "https://i.ytimg.com/vi/" + id + "/mqdefault.jpg";
        }
    }

    public record SearchResponse(List<SearchResult> results, String error) {
    }

    /**
     * Search YouTube through yt-dlp ("ytsearchN:") without downloading anything. Uses the flat
     * playlist mode, so it only fetches the result page metadata and is fast.
     */
    public static SearchResponse search(String query, int limit, Consumer<Process> onProcessStart) {
        if (query == null || query.isBlank()) return new SearchResponse(List.of(), "message.opensoundboard.empty_query");
        Tools tools;
        try {
            tools = prepare(null);
        } catch (ToolException e) {
            return new SearchResponse(List.of(), e.messageKey);
        }

        List<String> args = new ArrayList<>();
        args.add(tools.ytDlp().getAbsolutePath());
        args.addAll(List.of("--flat-playlist", "-J", "--no-warnings", "--ignore-config", "--encoding", "utf-8"));
        args.addAll(List.of("--js-runtimes", "deno:" + tools.deno().getAbsolutePath()));
        args.add("--");
        args.add("ytsearch" + Math.max(1, limit) + ":" + query.trim());

        try {
            StringBuilder json = new StringBuilder();
            int exit = run(args, libDir(), 60, false, line -> json.append(line).append('\n'), onProcessStart);
            if (exit == TIMED_OUT) return new SearchResponse(List.of(), "message.opensoundboard.youtube.timeout");
            if (exit != 0 || json.isEmpty()) return new SearchResponse(List.of(), "message.opensoundboard.search_failed");
            return new SearchResponse(parseSearch(json.toString()), null);
        } catch (Exception e) {
            OpenSoundboardClient.LOGGER.warn("[OpenSoundboard] YouTube search failed: {}", e.getMessage());
            return new SearchResponse(List.of(), "message.opensoundboard.search_failed");
        }
    }

    private static List<SearchResult> parseSearch(String json) {
        List<SearchResult> out = new ArrayList<>();
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("entries") || !root.get("entries").isJsonArray()) return out;
        for (com.google.gson.JsonElement element : root.getAsJsonArray("entries")) {
            if (!element.isJsonObject()) continue;
            com.google.gson.JsonObject entry = element.getAsJsonObject();
            String id = string(entry, "id");
            if (id == null || !id.matches("[A-Za-z0-9_-]{6,20}")) continue;
            String title = string(entry, "title");
            String channel = string(entry, "channel");
            if (channel == null) channel = string(entry, "uploader");
            int duration = entry.has("duration") && entry.get("duration").isJsonPrimitive()
                    ? (int) Math.round(entry.get("duration").getAsDouble()) : -1;
            long views = entry.has("view_count") && entry.get("view_count").isJsonPrimitive()
                    ? entry.get("view_count").getAsLong() : -1;
            out.add(new SearchResult(id, title != null ? title : id, channel != null ? channel : "", duration, views));
        }
        return out;
    }

    private static String string(com.google.gson.JsonObject object, String key) {
        com.google.gson.JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    /**
     * Download {@code url} with yt-dlp into {@code targetDir} (the main sounds folder when null).
     * Only the linked video is downloaded, even when the link is part of a playlist.
     */
    public static DownloadResult downloadUrlIntoSoundDir(String url, File targetDir, boolean audioOnly, Consumer<String> onProgress, Consumer<Process> onProcessStart) {
        if (url == null || url.isBlank()) return new DownloadResult(false, "message.opensoundboard.empty_url");

        Tools tools;
        try {
            tools = prepare(onProgress);
        } catch (ToolException e) {
            return new DownloadResult(false, e.messageKey);
        }

        File soundDir = targetDir != null ? targetDir : SoundLibrary.root();
        if (!soundDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            soundDir.mkdirs();
        }

        // Save only sanitized names. We do it via yt-dlp template to avoid renaming after download.
        String outputPattern = new File(soundDir, "%(title)." + "200B.%(ext)s").getAbsolutePath();

        List<String> args = new ArrayList<>();
        args.add(tools.ytDlp().getAbsolutePath());
        args.addAll(List.of("--ffmpeg-location", tools.ffmpegDir().getAbsolutePath()));
        // make yt-dlp avoid problematic path chars/surprises
        args.add("--restrict-filenames");
        args.addAll(List.of("--replace-in-metadata", "title", "_", " "));
        args.add("--no-playlist");
        args.addAll(List.of("--encoding", "utf-8"));
        if (audioOnly) {
            args.addAll(List.of("-x", "--audio-format", "mp3"));
        }
        args.addAll(List.of("--js-runtimes", "deno:" + tools.deno().getAbsolutePath()));
        args.addAll(List.of("-o", outputPattern));
        // "--" ends the options: a link can never be read as one (e.g. "--exec ...").
        args.addAll(List.of("--", url.trim()));

        try {
            StringBuilder output = new StringBuilder();
            int exit = run(args, soundDir, TimeUnit.MINUTES.toSeconds(30), true, line -> {
                if (line.isBlank()) return;
                output.append(line).append('\n');
                if (onProgress != null) onProgress.accept(line);
            }, onProcessStart);

            if (exit == TIMED_OUT) return new DownloadResult(false, "message.opensoundboard.youtube.timeout");
            String outStr = output.toString();
            if (exit == 0) {
                return new DownloadResult(true, outStr.isBlank() ? "message.opensoundboard.download_completed" : outStr);
            }
            return new DownloadResult(false, outStr.isBlank() ? "message.opensoundboard.youtube.exit_code" : outStr);
        } catch (Exception e) {
            OpenSoundboardClient.LOGGER.warn("[OpenSoundboard] Download failed: {}", e.getMessage());
            return new DownloadResult(false, "Exception: " + e.getMessage());
        }
    }
}
