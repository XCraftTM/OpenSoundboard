package de.xcrafttm.opensoundboard.tools;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.audio.Mp3Duration;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Voice-chat-independent playback core: decodes MP3s (48 kHz mono), keeps the playing sounds,
 * and mixes one frame at a time for whichever {@link VoiceBackend} is currently connected.
 * Sounds are identified by their key ({@link SoundLibrary#key}).
 */
public final class SoundboardAudioSystem {

    public static final int SAMPLE_RATE = 48_000;

    /**
     * One mixed frame: what other players hear (null when nothing is sent to them), and what the
     * local player hears (null when nothing plays locally).
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

    /** Voice chats request a frame every 20 ms; this much silence means they stopped. */
    private static final long VOICE_STALL_NANOS = 150_000_000L;

    private static final List<VoiceBackend> backends = new CopyOnWriteArrayList<>();
    /** Connection state of each voice chat at the last {@link #tick()}, to notice disconnects. */
    private static final Map<VoiceBackend, Boolean> wasConnected = new HashMap<>();
    /** System.nanoTime() of the last frame the active voice chat requested. */
    private static volatile long lastVoiceFrame = 0L;
    private static final ConcurrentLinkedQueue<PlayingSound> activeSounds = new ConcurrentLinkedQueue<>();

    /**
     * Sounds that were started but are still decoding, by key → request id. They already count as
     * playing, so pressing a sound again stops it instead of starting a second copy, and Stop works
     * before the first sample is heard. A decode only starts playing if its request is still here.
     */
    private static final Map<String, Long> loading = new ConcurrentHashMap<>();
    private static final AtomicLong requestIds = new AtomicLong();
    /** Guards the hand-over from {@link #loading} to {@link #activeSounds} against stop calls. */
    private static final Object PLAYBACK_LOCK = new Object();

    /**
     * Lightweight duration cache: maps sound key → sample count (48 kHz mono).
     * Only stores a single long per file – no PCM data kept in memory.
     */
    private static final ConcurrentHashMap<String, Long> durationCache = new ConcurrentHashMap<>();
    private static final AtomicBoolean libraryScanQueued = new AtomicBoolean();

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

    /**
     * The output sounds go to: the voice chat picked in the settings if it is connected, otherwise
     * the connected output with the highest priority (voice chats before local playback), or null.
     */
    public static VoiceBackend activeBackend() {
        String preferred = SoundboardConfig.data == null ? null : SoundboardConfig.data.getPreferredVoiceChat();
        if (preferred != null && !"auto".equals(preferred)) {
            for (VoiceBackend backend : backends) {
                if (backend.id().equals(preferred) && backend.isConnected()) return backend;
            }
        }
        for (VoiceBackend backend : backends) {
            if (backend.isConnected()) return backend;
        }
        return null;
    }

    /** Every installed voice chat backend (local playback excluded), in priority order. */
    public static List<VoiceBackend> voiceChats() {
        return backends.stream().filter(b -> !b.localOnly()).toList();
    }

    /** Called every client tick: stops all sounds when a voice chat disconnects. */
    public static void tick() {
        for (VoiceBackend backend : backends) {
            if (backend.localOnly()) continue;
            boolean connected = backend.isConnected();
            Boolean before = wasConnected.put(backend, connected);
            if (Boolean.TRUE.equals(before) && !connected) stopAll();
        }
    }

    /**
     * Whether the active voice chat has stopped taking audio, e.g. Simple Voice Chat with voice chat
     * disabled (it stops polling the microphone, so no frames are requested). Local output then
     * keeps sounds running so they neither freeze nor go silent.
     */
    public static boolean isVoiceChatPaused() {
        VoiceBackend active = activeBackend();
        return active != null && isPaused(active);
    }

    private static boolean isPaused(VoiceBackend voiceChat) {
        return !voiceChat.localOnly()
                && (voiceChat.isDisabled() || System.nanoTime() - lastVoiceFrame > VOICE_STALL_NANOS);
    }

    /** Label for the current output, e.g. "Simple Voice Chat" or "Simple Voice Chat (paused, only you)". */
    public static String outputLabel() {
        VoiceBackend backend = activeBackend();
        if (backend == null) return Component.translatable("gui.opensoundboard.output.none").getString();
        if (isPaused(backend)) return Component.translatable("gui.opensoundboard.output.paused", backend.name()).getString();
        return backend.name();
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
        VoiceBackend active = activeBackend();
        if (active == null) return null;
        if (backend != active) {
            // Local output stands in while the active voice chat is paused.
            if (!backend.localOnly() || !isPaused(active)) return null;
        } else if (!backend.localOnly()) {
            if (backend.isDisabled()) return null;
            lastVoiceFrame = System.nanoTime();
        }
        if (active.isMicMuted() && !SoundboardConfig.data.isPlayWhileMuted()) {
            // Previews only play for the local player, so muting does not stop them.
            activeSounds.removeIf(s -> !s.previewOnly);
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
        boolean hasPlayer = false;
        boolean hasLocal = false;

        Iterator<PlayingSound> iterator = activeSounds.iterator();
        while (iterator.hasNext()) {
            PlayingSound sound = iterator.next();
            if (sound.isFinished()) {
                iterator.remove();
                continue;
            }
            if (sound.isPaused) continue;
            boolean toPlayer = !localOnly && !sound.previewOnly;
            boolean toLocal = playLocally || sound.previewOnly;
            hasPlayer |= toPlayer;
            hasLocal |= toLocal;

            int toRead = Math.min(sourceSamples, sound.remaining(sourceSamples));
            float pVol = sound.playerVolume * globalPlayer;
            float lVol = sound.localVolume * globalLocal;
            for (int i = 0; i < toRead; i++) {
                short raw = sound.readNext();
                if (toPlayer) accumulatorPlayer[i] += (int) (raw * pVol);
                if (toLocal) accumulatorLocal[i] += (int) (raw * lVol);
            }
        }
        if (!hasPlayer && !hasLocal) return null;

        short[] player = hasPlayer ? clampAndResample(accumulatorPlayer, samples) : null;
        short[] local = hasLocal ? clampAndResample(accumulatorLocal, samples) : null;
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

    /**
     * Measure every sound in the library (all folder depths) that has no known length yet. The
     * folder walk and the measuring both run in the background.
     */
    public static void scanDurations() {
        if (!libraryScanQueued.compareAndSet(false, true)) return;
        scanExecutor.submit(() -> {
            libraryScanQueued.set(false);
            File soundDir = SoundLibrary.root();
            if (soundDir == null || !soundDir.exists()) return;
            for (File file : SoundLibrary.allSounds(soundDir)) measure(file);
        });
    }

    /** Measure a single file in the background without keeping any PCM data. */
    public static void scanFile(File file) {
        scanExecutor.submit(() -> measure(file));
    }

    private static void measure(File file) {
        String key = SoundLibrary.key(file);
        if (durationCache.containsKey(key)) return;
        long samples = countSamples(file);
        if (samples > 0) durationCache.put(key, samples);
    }

    public static void invalidateDurationCache(String key) {
        durationCache.remove(key);
    }

    public static void clearDurationCache() {
        durationCache.clear();
    }

    // ----------------------------------------------------------------
    // Decoding
    // ----------------------------------------------------------------

    /** Length in 48 kHz samples: read from the MP3 frame headers, or by decoding as a fallback. */
    private static long countSamples(File file) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            long micros = Mp3Duration.micros(in);
            if (micros > 0) return micros * SAMPLE_RATE / 1_000_000L;
        } catch (IOException ignored) {
        }
        // Streams the pure-Java decoder so no PCM is kept, even for very long files.
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
            OpenSoundboardClient.LOGGER.warn("[OpenSoundboard] Error decoding {}: {}", file.getName(), e.getMessage());
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

    /** Play {@code file} with its saved volumes. */
    public static void play(File file) {
        var data = SoundboardConfig.get(SoundLibrary.key(file));
        playFile(file, data.getLocalVolume(), data.getPlayerVolume());
    }

    /** Play {@code file}, or stop it if it is already playing (or still loading). */
    public static void toggle(File file) {
        String key = SoundLibrary.key(file);
        if (isPlaying(key)) stop(key);
        else play(file);
    }

    /** What a sound keybind or the sound wheel does, following the "keybind mode" setting. */
    public static void trigger(File file) {
        String key = SoundLibrary.key(file);
        boolean playing = isPlaying(key);
        switch (SoundboardConfig.data.getKeybindMode()) {
            case "pause_resume" -> {
                if (!playing) play(file);
                else if (isLoading(key)) stop(key);
                else if (isPaused(key)) resume(key);
                else pause(key);
            }
            case "play_restart" -> play(file);
            default -> toggle(file);
        }
    }

    public static void playFile(File file, float localVol, float playerVol) {
        playFile(file, localVol, playerVol, -1f, false);
    }

    /**
     * Start {@code file}, replacing any copy of it that is already playing. With "single song at a
     * time" every other sound stops too.
     */
    public static void playFile(File file, float localVol, float playerVol, float startProgress, boolean startPaused) {
        start(file, localVol, playerVol, startProgress, startPaused, false);
    }

    /**
     * Play {@code file} for the local player only: it is never sent to voice chat, plays even with
     * "play locally" off or the microphone muted, and leaves other sounds running. Starting a
     * preview stops the previous one.
     */
    public static void preview(File file, float localVol, float startProgress) {
        start(file, localVol, 0f, startProgress, false, true);
    }

    private static void start(File file, float localVol, float playerVol, float startProgress, boolean startPaused, boolean previewOnly) {
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

        if (!previewOnly && backend.isMicMuted() && !SoundboardConfig.data.isPlayWhileMuted()) {
            if (client.player != null) {
                actionBar(client.player, Component.translatable("message.opensoundboard.muted_error"));
            }
            return;
        }

        String key = SoundLibrary.key(file);
        String id = previewOnly ? previewKey(key) : key;
        long request = requestIds.incrementAndGet();
        synchronized (PLAYBACK_LOCK) {
            if (previewOnly) {
                activeSounds.removeIf(s -> s.previewOnly);
                loading.keySet().removeIf(k -> k.startsWith(PREVIEW_PREFIX));
            } else if (SoundboardConfig.data.isSingleSongAtATime()) {
                stopAllLocked();
            }
            stopLocked(id);
            loading.put(id, request);
        }

        // Decode async on demand – no PCM is kept resident in memory between plays
        CompletableFuture.runAsync(() -> {
            short[] pcmData = decodeMp3(file);
            if (pcmData == null || pcmData.length == 0) {
                boolean wanted = loading.remove(id, request);
                if (wanted) {
                    client.execute(() -> {
                        if (client.player != null) {
                            chat(client.player, Component.translatable("message.opensoundboard.decode_failed", file.getName()));
                        }
                    });
                }
                return;
            }
            durationCache.putIfAbsent(key, (long) pcmData.length);
            PlayingSound sound = new PlayingSound(id, file, pcmData, localVol, playerVol, previewOnly);
            if (startProgress >= 0f) {
                sound.setCursor(startProgress);
            } else {
                var data = SoundboardConfig.get(key);
                if (data.getStartingPoint() > 0f) sound.setCursor(data.getStartingPoint());
            }
            sound.isLooping = !previewOnly && SoundboardConfig.data.isLoopAll();
            sound.isPaused = startPaused;
            synchronized (PLAYBACK_LOCK) {
                // Stopped or replaced while decoding: drop it.
                if (!loading.remove(id, request)) return;
                activeSounds.add(sound);
            }
        });
    }

    private static final String PREVIEW_PREFIX = "\0preview:";

    /**
     * Playback id of the preview of {@code key}. Previews are separate from the sound playing for
     * real, so previewing never stops or moves it; pass this id to {@link #isPlaying}, {@link #stop}…
     */
    public static String previewKey(String key) {
        return PREVIEW_PREFIX + key;
    }

    /** Whether {@code key} is playing, paused, or still loading. */
    public static boolean isPlaying(String key) {
        if (loading.containsKey(key)) return true;
        for (PlayingSound s : activeSounds) {
            if (s.key.equals(key) && !s.isFinished()) return true;
        }
        return false;
    }

    /** Whether {@code key} was started but is still being decoded. */
    public static boolean isLoading(String key) {
        return loading.containsKey(key);
    }

    public static void stop(String key) {
        synchronized (PLAYBACK_LOCK) {
            stopLocked(key);
        }
    }

    private static void stopLocked(String key) {
        loading.remove(key);
        activeSounds.removeIf(s -> s.key.equals(key));
    }

    public static void stopAll() {
        synchronized (PLAYBACK_LOCK) {
            stopAllLocked();
        }
    }

    private static void stopAllLocked() {
        loading.clear();
        activeSounds.clear();
    }

    public static void pause(String key) {
        for (PlayingSound s : activeSounds) {
            if (s.key.equals(key)) s.isPaused = true;
        }
    }

    public static void resume(String key) {
        for (PlayingSound s : activeSounds) {
            if (s.key.equals(key)) s.isPaused = false;
        }
    }

    public static void setGlobalLooping(boolean looping) {
        for (PlayingSound s : activeSounds) {
            if (!s.previewOnly) s.isLooping = looping;
        }
    }

    public static void setCursor(String key, float progress) {
        for (PlayingSound s : activeSounds) {
            if (s.key.equals(key)) s.setCursor(progress);
        }
    }

    public static void skip(String key, int seconds) {
        for (PlayingSound s : activeSounds) {
            if (s.key.equals(key)) s.skip(seconds);
        }
    }

    public static float getProgress(String key) {
        for (PlayingSound s : activeSounds) {
            if (s.key.equals(key)) return s.progress();
        }
        return -1f;
    }

    public static long getTimeMillis(String key) {
        for (PlayingSound s : activeSounds) {
            if (s.key.equals(key)) return s.timeMillis();
        }
        return 0;
    }

    public static long getDurationMillis(String key) {
        for (PlayingSound s : activeSounds) {
            if (s.key.equals(key)) return s.durationMillis();
        }
        Long samples = durationCache.get(key);
        return samples != null ? samples * 1000L / SAMPLE_RATE : 0;
    }

    public static boolean isPaused(String key) {
        for (PlayingSound s : activeSounds) {
            if (s.key.equals(key) && s.isPaused) return true;
        }
        return false;
    }

    /** The first sound that is playing for real (previews excluded), or null. */
    public static File getActiveSoundFile() {
        for (PlayingSound s : activeSounds) {
            if (!s.isFinished() && !s.previewOnly) return s.file;
        }
        return null;
    }

    public static void setVolume(String key, float localVol, float playerVol) {
        for (PlayingSound sound : activeSounds) {
            if (sound.key.equals(key) && !sound.previewOnly) {
                sound.localVolume = localVol;
                sound.playerVolume = playerVol;
            }
        }
    }

    private static final class PlayingSound {
        final String key;
        final File file;
        final boolean previewOnly;
        private final short[] samples;

        volatile float localVolume;
        volatile float playerVolume;

        private volatile int cursor = 0;
        volatile boolean isPaused = false;
        volatile boolean isLooping = false;

        private PlayingSound(String key, File file, short[] samples, float localVolume, float playerVolume, boolean previewOnly) {
            this.key = key;
            this.file = file;
            this.samples = samples;
            this.localVolume = localVolume;
            this.playerVolume = playerVolume;
            this.previewOnly = previewOnly;
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

        long timeMillis() {
            return (long) cursor * 1000L / SAMPLE_RATE;
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
