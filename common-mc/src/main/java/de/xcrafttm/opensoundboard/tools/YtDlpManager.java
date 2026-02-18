package de.xcrafttm.opensoundboard.tools;

import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Minimal yt-dlp + ffmpeg manager ported from the original Kotlin implementation.
 *
 * This downloads the binaries into config/opensoundboard/lib and then runs yt-dlp
 * to download audio into the sound directory.
 */
public final class YtDlpManager {

    private YtDlpManager() {
    }

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");

    private static final String YT_DLP_NAME = IS_WINDOWS ? "yt-dlp.exe" : "yt-dlp";
    private static final String YT_DLP_URL = IS_WINDOWS
            ? "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
            : "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";

    private static final String FFMPEG_NAME = IS_WINDOWS ? "ffmpeg.exe" : "ffmpeg";
    private static final String FFMPEG_URL = IS_WINDOWS
            ? "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl-shared.zip"
            : "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl-shared.tar.xz";

    private static File libDir() {
        File dir = new File(new File(FabricLoader.getInstance().getConfigDir().toFile(), "opensoundboard"), "lib");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    private static File ytDlpFile() {
        return new File(libDir(), YT_DLP_NAME);
    }

    private static File ffmpegFile() {
        return new File(libDir(), FFMPEG_NAME);
    }

    public static File soundDir() {
        File dir = new File(FabricLoader.getInstance().getGameDir().toFile(), "opensoundboard");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static synchronized boolean ensureBinariesPresent() {
        return ensureYtDlpPresent() && ensureFfmpegPresent();
    }

    public static synchronized boolean ensureYtDlpPresent() {
        File bin = ytDlpFile();
        if (bin.exists() && bin.canExecute()) return true;

        try {
            downloadBinary(YT_DLP_URL, bin);
            //noinspection ResultOfMethodCallIgnored
            bin.setExecutable(true, false);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    public static synchronized boolean ensureFfmpegPresent() {
        File bin = ffmpegFile();
        if (bin.exists() && bin.canExecute()) return true;

        try {
            String archiveName = IS_WINDOWS ? "ffmpeg.zip" : "ffmpeg.tar.xz";
            File archiveFile = new File(libDir(), archiveName);
            downloadBinary(FFMPEG_URL, archiveFile);

            if (IS_WINDOWS) {
                extractZipFlattenBin(archiveFile, libDir());
            } else {
                // Best-effort: rely on system tar on Linux
                extractTarXzFlattenBin(archiveFile, libDir());
            }

            //noinspection ResultOfMethodCallIgnored
            archiveFile.delete();
            //noinspection ResultOfMethodCallIgnored
            bin.setExecutable(true, false);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private static void downloadBinary(String urlStr, File dest) throws Exception {
        URI uri = URI.create(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("User-Agent", "OpenSoundboard-Downloader");

        conn.connect();
        int code = conn.getResponseCode();
        if (code >= 400) {
            conn.disconnect();
            throw new RuntimeException("Failed to download from " + urlStr + ": HTTP " + code);
        }

        try (InputStream input = new BufferedInputStream(conn.getInputStream());
             OutputStream output = Files.newOutputStream(dest.toPath())) {
            input.transferTo(output);
        } finally {
            conn.disconnect();
        }
    }

    private static void extractZipFlattenBin(File zipFile, File destDir) throws IOException {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile)) {
            zip.stream().forEach(entry -> {
                String name = entry.getName();
                if (entry.isDirectory()) return;

                // Legacy behavior: take everything that looks like /bin/ffmpeg(.exe)
                if (name.contains("/bin/") || name.startsWith("bin/")) {
                    String fileName = name.substring(name.lastIndexOf('/') + 1);
                    File out = new File(destDir, fileName);
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream outStream = Files.newOutputStream(out.toPath())) {
                        in.transferTo(outStream);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void extractTarXzFlattenBin(File archiveFile, File destDir) throws IOException, InterruptedException {
        // Kotlin version used "tar" too; we keep that for Linux. If tar is unavailable, this will fail gracefully.
        ProcessBuilder listPb = new ProcessBuilder("tar", "-tJf", archiveFile.getAbsolutePath());
        Process procList = listPb.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(procList.getInputStream()));
        String firstEntry = r.readLine();
        procList.waitFor(30, TimeUnit.SECONDS);

        if (firstEntry == null || firstEntry.isBlank()) return;
        String rootDir = firstEntry.split("/", 2)[0];
        if (rootDir.isBlank()) return;

        ProcessBuilder pb = new ProcessBuilder(
                "tar", "-xJf", archiveFile.getAbsolutePath(),
                "--strip-components=2",
                "-C", destDir.getAbsolutePath(),
                rootDir + "/bin"
        );
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor(2, TimeUnit.MINUTES);
    }

    public record DownloadResult(boolean success, String messageOrLog) {
    }

    /**
     * Convert a title into a safe filename using the allowed character set.
     * Rules:
     * - replace '_' with ' '
     * - keep only A-Za-z0-9 and the characters: space, '-', '(', ')', '[', ']'
     * - collapse whitespace
     * - trim
     */
    public static String sanitizeTrackName(String name) {
        if (name == null) return "";
        String s = name.replace('_', ' ');
        // remove disallowed chars
        s = s.replaceAll("[^A-Za-z0-9äöüÄÖÜß.\\-\\s\\(\\)\\[\\]]", "");
        // collapse whitespace
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    public static DownloadResult downloadUrlIntoSoundDir(String url, boolean audioOnly, Consumer<String> onProgress, Consumer<Process> onProcessStart) {
        if (url == null || url.isBlank()) return new DownloadResult(false, "message.opensoundboard.empty_url");

        if (!ensureBinariesPresent()) {
            return new DownloadResult(false, "message.opensoundboard.binaries_missing");
        }

        File ytDlp = ytDlpFile();
        File ffmpeg = ffmpegFile();
        File soundDir = soundDir();

        // Save only sanitized names. We do it via yt-dlp template to avoid renaming after download.
        // We still keep the original title for display/metadata in yt-dlp logs.
        String outputPattern = new File(soundDir, "%(title)." + "200B.%(ext)s").getAbsolutePath();

        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(ytDlp.getAbsolutePath());
        args.addAll(java.util.List.of("--ffmpeg-location", ffmpeg.getAbsolutePath()));
        // make yt-dlp avoid problematic path chars/surprises
        args.add("--restrict-filenames");
        // additionally replace underscores with spaces in final names and remove chars we don't want
        // we do this by postprocessing the output filename with the sanitize rules using --replace-in-metadata
        // and --replace-in-chapter (best effort). Then we rely on --output template.
        args.addAll(java.util.List.of("--replace-in-metadata", "title", "_", " "));
        if (audioOnly) {
            args.addAll(java.util.List.of("-x", "--audio-format", "mp3"));
        }

        args.addAll(java.util.List.of("-o", outputPattern, url));

        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            pb.directory(soundDir);
            pb.redirectErrorStream(true);

            Process proc = pb.start();
            if (onProcessStart != null) onProcessStart.accept(proc);

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        output.append(line).append('\n');
                        if (onProgress != null) onProgress.accept(line);
                    }
                }
            }

            boolean finished = proc.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                proc.destroyForcibly();
                return new DownloadResult(false, "message.opensoundboard.youtube.timeout");
            }

            int exit = proc.exitValue();
            String outStr = output.toString();
            if (exit == 0) {
                return new DownloadResult(true, outStr.isBlank() ? "message.opensoundboard.download_completed" : outStr);
            } else {
                return new DownloadResult(false, outStr.isBlank() ? "message.opensoundboard.youtube.exit_code" : outStr);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return new DownloadResult(false, "Exception: " + t.getMessage());
        }
    }
}

