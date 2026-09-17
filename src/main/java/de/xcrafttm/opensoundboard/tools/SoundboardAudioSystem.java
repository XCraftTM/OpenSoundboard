package de.xcrafttm.opensoundboard.tools;

import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import fr.delthas.javamp3.Sound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Voice-chat-independent playback core: decodes MP3s (48 kHz mono), keeps the playing sounds,
 * and mixes one frame at a time for whichever {@link VoiceBackend} is currently connected.
 */
public final class SoundboardAudioSystem {

    public static final int SAMPLE_RATE = 48_000;

    /**
     * One mixed frame: what other players hear (null for local-only output), and what the local
     * player hears (null if local playback is off).
     */
    public record Frame(short[] player, short[] local) {
    }

    /** A faster (native) MP3 decoder supplied by a voice chat mod; returns interleaved PCM. */
    public interface Mp3Decoder {
        Decoded decode(InputStream in) throws Exception;
    }

    public record Decoded(short[] pcm, int sampleRate, int channels) {
    }

    private static volatile Mp3Decoder nativeDecoder;

    private static final List<VoiceBackend> backends = new CopyOnWriteArrayList<>();
    private static final ConcurrentLinkedQueue<PlayingSound> activeSounds = new ConcurrentLinkedQueue<>();

    /**
     * Lightweight duration cache: maps filename → sample count (48 kHz mono).
     * Only stores a single long per file – no PCM data kept in memory.
     */
    private static final ConcurrentHashMap<String, Long> durationCache = new ConcurrentHashMap<>();

    /** Single-threaded background executor for duration scanning. */
    private static final ExecutorService scanExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "opensoundboard-scan");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private SoundboardAudioSystem() {
    }

    // ----------------------------------------------------------------
    // Voice backends
    // ----------------------------------------------------------------

    public static void registerBackend(VoiceBackend backend) {
        if (backends.contains(backend)) return;
        backends.add(backend);
        backends.sort(Comparator.comparingInt(VoiceBackend::priority));
    }

    /** The connected output with the highest priority (voice chats before local playback), or null. */
    public static VoiceBackend activeBackend() {
        for (VoiceBackend backend : backends) {
            if (backend.isConnected()) return backend;
        }
        return null;
    }

    /** Use {@code decoder} instead of the bundled pure-Java decoder (it stays as the fallback). */
    public static void setNativeDecoder(Mp3Decoder decoder) {
        nativeDecoder = decoder;
    }

    /** Whether a voice chat mod (not just local playback) is available. */
    public static boolean hasVoiceChat() {
        for (VoiceBackend backend : backends) {
            if (!backend.localOnly()) return true;
        }
        return false;
    }

    /**
     * Mix the next frame for {@code backend}. Returns null when this backend is not the active
     * one, playback is blocked (disabled/muted), or nothing is playing.
     *
     * @param samples    samples per channel the backend needs
     * @param sampleRate the backend's sample rate
     */
    public static Frame mixFrame(VoiceBackend backend, int samples, int sampleRate) {
        if (backend != activeBackend()) return null;
        if (backend.isDisabled() || (backend.isMicMuted() && !SoundboardConfig.data.isPlayWhileMuted())) {
            if (!activeSounds.isEmpty()) activeSounds.clear();
            return null;
        }
        if (activeSounds.isEmpty()) return null;

        int sourceSamples = sampleRate == SAMPLE_RATE ? samples : (int) Math.round(samples * (double) SAMPLE_RATE / sampleRate);
        boolean localOnly = backend.localOnly();
        boolean playLocally = localOnly || SoundboardConfig.data.isPlayLocally();
        float globalLocal = SoundboardConfig.data.getGlobalLocalVolume();
        float globalPlayer = SoundboardConfig.data.getGlobalPlayerVolume();

        // Int accumulators prevent intermediate clipping.
        int[] accumulatorPlayer = new int[sourceSamples];
        int[] accumulatorLocal = new int[sourceSamples];
        boolean hasAudio = false;

        Iterator<PlayingSound> iterator = activeSounds.iterator();
        while (iterator.hasNext()) {
            PlayingSound sound = iterator.next();
            if (sound.isFinished()) {
                iterator.remove();
                continue;
            }
            if (sound.isPaused) continue;
            hasAudio = true;

            int toRead = Math.min(sourceSamples, sound.remaining(sourceSamples));
            float pVol = sound.playerVolume * globalPlayer;
            float lVol = sound.localVolume * globalLocal;
            for (int i = 0; i < toRead; i++) {
                short raw = sound.readNext();
                if (!localOnly) accumulatorPlayer[i] += (int) (raw * pVol);
                if (playLocally) accumulatorLocal[i] += (int) (raw * lVol);
            }
        }
        if (!hasAudio) return null;

        short[] player = localOnly ? null : clampAndResample(accumulatorPlayer, samples);
        short[] local = playLocally ? clampAndResample(accumulatorLocal, samples) : null;
        return new Frame(player, local);
    }

    private static short[] clampAndResample(int[] mixed, int targetLength) {
        short[] out = new short[targetLength];
        if (mixed.length == targetLength) {
            for (int i = 0; i < targetLength; i++) out[i] = clamp(mixed[i]);
            return out;
        }
        double step = (double) mixed.length / targetLength;
        for (int i = 0; i < targetLength; i++) {
            double pos = i * step;
            int index = (int) pos;
            int next = Math.min(mixed.length - 1, index + 1);
            double frac = pos - index;
            out[i] = clamp((int) Math.round(mixed[Math.min(index, mixed.length - 1)] * (1 - frac) + mixed[next] * frac));
        }
        return out;
    }

    private static short clamp(int v) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
    }

    // ----------------------------------------------------------------
    // Duration scanning (lightweight – no PCM kept in memory)
    // ----------------------------------------------------------------

    /** Scan every sound in the library (all folder depths) in the background. */
    public static void scanDurations() {
        File soundDir = SoundLibrary.root();
        if (soundDir == null || !soundDir.exists()) return;
        for (File file : SoundLibrary.allSounds(soundDir)) {
            if (!durationCache.containsKey(file.getName())) scanFile(file);
        }
    }

    /** Count the samples of a single file in the background without keeping any PCM data. */
    public static void scanFile(File file) {
        scanExecutor.submit(() -> {
            if (durationCache.containsKey(file.getName())) return;
            long samples = countSamples(file);
            if (samples > 0) durationCache.put(file.getName(), samples);
        });
    }

    public static void invalidateDurationCache(String fileName) {
        durationCache.remove(fileName);
    }

    public static void clearDurationCache() {
        durationCache.clear();
    }

    // ----------------------------------------------------------------
    // Decoding
    // ----------------------------------------------------------------

    private static long countSamples(File file) {
        short[] nativePcm = decodeNative(file);
        if (nativePcm != null) return nativePcm.length;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()));
             Sound sound = new Sound(in)) {
            int bytesPerFrame = sound.isStereo() ? 4 : 2;
            byte[] buffer = new byte[64 * 1024];
            long bytes = 0;
            int read;
            while ((read = sound.read(buffer)) > 0) bytes += read;
            return (bytes / bytesPerFrame) * SAMPLE_RATE / Math.max(1, sound.getSamplingFrequency());
        } catch (Exception e) {
            return -1;
        }
    }

    /** Decode with the native decoder into 48 kHz mono, or null if unavailable/failed. */
    private static short[] decodeNative(File file) {
        Mp3Decoder decoder = nativeDecoder;
        if (decoder == null) return null;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            Decoded decoded = decoder.decode(in);
            if (decoded == null || decoded.pcm() == null || decoded.pcm().length == 0) return null;
            short[] pcm = decoded.channels() == 2 ? stereoToMono(decoded.pcm()) : decoded.pcm();
            return decoded.sampleRate() == SAMPLE_RATE ? pcm : resample(pcm, decoded.sampleRate(), SAMPLE_RATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static short[] stereoToMono(short[] stereo) {
        short[] mono = new short[stereo.length / 2];
        for (int i = 0; i < mono.length; i++) mono[i] = (short) ((stereo[i * 2] + stereo[i * 2 + 1]) / 2);
        return mono;
    }

    /** Decode an MP3 fully into 48 kHz mono PCM. */
    private static short[] decodeMp3(File file) {
        short[] nativePcm = decodeNative(file);
        if (nativePcm != null) return nativePcm;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()));
             Sound sound = new Sound(in)) {
            boolean stereo = sound.isStereo();
            boolean bigEndian = sound.getAudioFormat().isBigEndian();
            int rate = sound.getSamplingFrequency();

            short[] mono = new short[1 << 20];
            int count = 0;
            byte[] buffer = new byte[64 * 1024];
            int carry = 0;
            byte[] pending = new byte[4];
            int frameBytes = stereo ? 4 : 2;
            int read;
            while ((read = sound.read(buffer)) > 0) {
                int offset = 0;
                // Complete a frame split across two reads.
                while (carry > 0 && carry < frameBytes && offset < read) pending[carry++] = buffer[offset++];
                if (carry == frameBytes) {
                    if (count == mono.length) mono = Arrays.copyOf(mono, mono.length * 2);
                    mono[count++] = frameToMono(pending, 0, stereo, bigEndian);
                    carry = 0;
                }
                for (; offset + frameBytes <= read; offset += frameBytes) {
                    if (count == mono.length) mono = Arrays.copyOf(mono, mono.length * 2);
                    mono[count++] = frameToMono(buffer, offset, stereo, bigEndian);
                }
                while (offset < read) pending[carry++] = buffer[offset++];
            }
            short[] pcm = Arrays.copyOf(mono, count);
            return rate == SAMPLE_RATE ? pcm : resample(pcm, rate, SAMPLE_RATE);
        } catch (IOException | RuntimeException e) {
            System.err.println("Error decoding " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static short frameToMono(byte[] data, int offset, boolean stereo, boolean bigEndian) {
        int left = sample(data, offset, bigEndian);
        if (!stereo) return (short) left;
        int right = sample(data, offset + 2, bigEndian);
        return (short) ((left + right) / 2);
    }

    private static int sample(byte[] data, int offset, boolean bigEndian) {
        return bigEndian
                ? (short) ((data[offset] << 8) | (data[offset + 1] & 0xFF))
                : (short) ((data[offset + 1] << 8) | (data[offset] & 0xFF));
    }

    private static short[] resample(short[] input, int inputRate, int outputRate) {
        // Cubic Catmull-Rom interpolation
        double factor = (double) inputRate / (double) outputRate;
        int outputSize = (int) (input.length / factor);
        short[] output = new short[outputSize];

        for (int i = 0; i < outputSize; i++) {
            double inputIndex = i * factor;
            int index = (int) inputIndex;
            double fraction = inputIndex - index;

            double p0 = (index > 0) ? input[index - 1] : input[index];
            double p1 = input[index];
            double p2 = (index < input.length - 1) ? input[index + 1] : p1;
            double p3 = (index < input.length - 2) ? input[index + 2] : p2;

            double a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3;
            double b = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3;
            double c = -0.5 * p0 + 0.5 * p2;

            double sample = a * fraction * fraction * fraction + b * fraction * fraction + c * fraction + p1;
            output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
        }
        return output;
    }

    // ----------------------------------------------------------------
    // Playback control
    // ----------------------------------------------------------------

    // Action-bar / chat message helpers. 26.x split displayClientMessage(Component, boolean)
    // into sendOverlayMessage(Component) (action bar) and sendSystemMessage(Component) (chat).
    private static void actionBar(LocalPlayer player, Component msg) {
        //? if >=26 {
        player.sendOverlayMessage(msg);
        //?} else {
        /*player.displayClientMessage(msg, true);
        *///?}
    }

    private static void chat(LocalPlayer player, Component msg) {
        //? if >=26 {
        player.sendSystemMessage(msg);
        //?} else {
        /*player.displayClientMessage(msg, false);
        *///?}
    }

    public static void playFile(File file, float localVol, float playerVol) {
        playFile(file, localVol, playerVol, -1f, false);
    }

    public static void playFile(File file, float localVol, float playerVol, float startProgress, boolean startPaused) {
        Minecraft client = Minecraft.getInstance();
        VoiceBackend backend = activeBackend();

        if (backend == null) {
            if (client.player != null) {
                actionBar(client.player, Component.translatable(hasVoiceChat()
                        ? "message.opensoundboard.vc_not_connected"
                        : "message.opensoundboard.no_output"));
            }
            return;
        }

        if (backend.isMicMuted() && !SoundboardConfig.data.isPlayWhileMuted()) {
            if (client.player != null) {
                actionBar(client.player, Component.translatable("message.opensoundboard.muted_error"));
            }
            return;
        }

        if (SoundboardConfig.data.isSingleSongAtATime()) {
            stopAll();
        }

        // Decode async on demand – no PCM is kept resident in memory between plays
        CompletableFuture.runAsync(() -> {
            short[] pcmData = decodeMp3(file);
            if (pcmData != null && pcmData.length > 0) {
                durationCache.putIfAbsent(file.getName(), (long) pcmData.length);
                PlayingSound sound = new PlayingSound(file, pcmData, localVol, playerVol);
                if (startProgress >= 0f) {
                    sound.setCursor(startProgress);
                } else {
                    var data = SoundboardConfig.get(file.getName());
                    if (data.getStartingPoint() > 0f) sound.setCursor(data.getStartingPoint());
                }
                sound.isLooping = SoundboardConfig.data.isLoopAll();
                sound.isPaused = startPaused;
                activeSounds.add(sound);
            } else {
                client.execute(() -> {
                    if (client.player != null) {
                        chat(client.player, Component.translatable("message.opensoundboard.decode_failed", file.getName()));
                    }
                });
            }
        });
    }

    public static boolean isPlaying(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file) && !s.isFinished()) return true;
        }
        return false;
    }

    public static void stop(String file) {
        activeSounds.removeIf(s -> s.name.equals(file));
    }

    public static void pause(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) s.isPaused = true;
        }
    }

    public static void resume(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) s.isPaused = false;
        }
    }

    public static void setGlobalLooping(boolean looping) {
        for (PlayingSound s : activeSounds) s.isLooping = looping;
    }

    public static void setCursor(String file, float progress) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) s.setCursor(progress);
        }
    }

    public static void skip(String file, int seconds) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) s.skip(seconds);
        }
    }

    public static float getProgress(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) return s.progress();
        }
        return -1f;
    }

    public static int getTimeSeconds(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) return s.timeSeconds();
        }
        return 0;
    }

    public static long getTimeMillis(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) return s.timeMillis();
        }
        return 0;
    }

    public static int getDurationSeconds(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) return s.durationSeconds();
        }
        Long samples = durationCache.get(file);
        return samples != null ? (int) (samples / SAMPLE_RATE) : 0;
    }

    public static long getDurationMillis(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) return s.durationMillis();
        }
        Long samples = durationCache.get(file);
        return samples != null ? samples * 1000L / SAMPLE_RATE : 0;
    }

    public static boolean isPaused(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file) && s.isPaused) return true;
        }
        return false;
    }

    public static String getActiveSoundName() {
        for (PlayingSound s : activeSounds) {
            if (!s.isFinished()) return s.name;
        }
        return null;
    }

    public static File getActiveSoundFile() {
        for (PlayingSound s : activeSounds) {
            if (!s.isFinished()) return s.file;
        }
        return null;
    }

    public static void setVolume(String file, float localVol, float playerVol) {
        for (PlayingSound sound : activeSounds) {
            if (sound.name.equals(file)) {
                sound.localVolume = localVol;
                sound.playerVolume = playerVol;
            }
        }
    }

    public static void stopAll() {
        activeSounds.clear();
    }

    private static final class PlayingSound {
        final String name;
        final File file;
        private final short[] samples;

        volatile float localVolume;
        volatile float playerVolume;

        private volatile int cursor = 0;
        volatile boolean isPaused = false;
        volatile boolean isLooping = false;

        private PlayingSound(File file, short[] samples, float localVolume, float playerVolume) {
            this.name = file.getName();
            this.file = file;
            this.samples = samples;
            this.localVolume = localVolume;
            this.playerVolume = playerVolume;
        }

        boolean isFinished() {
            return !isLooping && cursor >= samples.length;
        }

        int remaining(int frame) {
            return isLooping ? frame : (samples.length - cursor);
        }

        float progress() {
            return samples.length == 0 ? 0f : (float) cursor / samples.length;
        }

        int timeSeconds() {
            return cursor / SAMPLE_RATE;
        }

        long timeMillis() {
            return (long) cursor * 1000L / SAMPLE_RATE;
        }

        int durationSeconds() {
            return samples.length / SAMPLE_RATE;
        }

        long durationMillis() {
            return (long) samples.length * 1000L / SAMPLE_RATE;
        }

        void setCursor(float progress) {
            cursor = Math.max(0, Math.min(samples.length, (int) (progress * samples.length)));
        }

        void skip(int seconds) {
            cursor = Math.max(0, Math.min(samples.length, cursor + seconds * SAMPLE_RATE));
        }

        short readNext() {
            int c = cursor;
            if (c >= samples.length) {
                if (!isLooping) return 0;
                c = 0;
            }
            cursor = c + 1;
            return samples[c];
        }
    }
}
