package de.xcrafttm.opensoundboard.tools;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.audiochannel.ClientStaticAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.BufferedInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class SoundboardAudioSystem {

    private static VoicechatApi api = null;
    private static VoicechatClientApi clientApi = null;
    private static ClientStaticAudioChannel localAudioChannel = null;

    private static final ConcurrentLinkedQueue<PlayingSound> activeSounds = new ConcurrentLinkedQueue<>();
    private static final int FRAME_SIZE = 960;

    private SoundboardAudioSystem() {
    }

    public static void initialize(VoicechatApi api) {
        SoundboardAudioSystem.api = api;
    }

    public static void onClientConnection(ClientVoicechatConnectionEvent event) {
        if (event.isConnected()) {
            clientApi = event.getVoicechat();
            var category = clientApi.volumeCategoryBuilder()
                    .setId("soundboard")
                    .setName("Soundboard")
                    .build();
            clientApi.registerClientVolumeCategory(category);

            localAudioChannel = clientApi.createStaticAudioChannel(UUID.randomUUID());
            if (localAudioChannel != null) {
                localAudioChannel.setCategory(category.getId());
            }
        } else {
            stopAll();
            clientApi = null;
            localAudioChannel = null;
        }
    }

    public static void onMergeSound(MergeClientSoundEvent event) {
        VoicechatClientApi api = clientApi;
        if (api == null) {
            return;
        }

        if (api.isDisabled() || (api.isMuted() && !SoundboardConfig.data.isPlayWhileMuted())) {
            if (!activeSounds.isEmpty()) {
                activeSounds.clear();
            }
            return;
        }

        if (activeSounds.isEmpty()) {
            return;
        }

        boolean playLocally = SoundboardConfig.data.isPlayLocally();
        boolean hasAudio = false;

        // Use IntArray accumulators to prevent intermediate clipping
        int[] accumulatorPlayer = new int[FRAME_SIZE];
        int[] accumulatorLocal = new int[FRAME_SIZE];

        Iterator<PlayingSound> iterator = activeSounds.iterator();
        float globalLocal = SoundboardConfig.data.getGlobalLocalVolume();
        float globalPlayer = SoundboardConfig.data.getGlobalPlayerVolume();

        while (iterator.hasNext()) {
            PlayingSound sound = iterator.next();

            if (sound.isFinished()) {
                iterator.remove();
                continue;
            }

            if (sound.isPaused) {
                continue;
            }

            hasAudio = true;

            int samplesToRead = Math.min(FRAME_SIZE, sound.remaining());
            float pVol = sound.playerVolume * globalPlayer;
            float lVol = sound.localVolume * globalLocal;

            for (int i = 0; i < samplesToRead; i++) {
                short rawSample = sound.readNext();
                accumulatorPlayer[i] += (int) (rawSample * pVol);
                if (playLocally) {
                    accumulatorLocal[i] += (int) (rawSample * lVol);
                }
            }
        }

        if (hasAudio) {
            short[] mixedAudioPlayer = new short[FRAME_SIZE];
            short[] mixedAudioLocal = new short[FRAME_SIZE];

            for (int i = 0; i < FRAME_SIZE; i++) {
                mixedAudioPlayer[i] = (short) clampIntToShort(accumulatorPlayer[i]);
                if (playLocally) {
                    mixedAudioLocal[i] = (short) clampIntToShort(accumulatorLocal[i]);
                }
            }

            event.mergeAudio(mixedAudioPlayer);

            if (playLocally) {
                if (localAudioChannel != null) {
                    localAudioChannel.play(mixedAudioLocal);
                }
            }
        }
    }

    private static int clampIntToShort(int v) {
        if (v < Short.MIN_VALUE) return Short.MIN_VALUE;
        if (v > Short.MAX_VALUE) return Short.MAX_VALUE;
        return v;
    }

    private static short[] resample(short[] input, int inputRate, int outputRate) {
        // Cubic Catmull-Rom Interpolation
        double factor = (double) inputRate / (double) outputRate;
        int outputSize = (int) (input.length / factor);
        short[] output = new short[outputSize];

        for (int i = 0; i < outputSize; i++) {
            double inputIndex = i * factor;
            int index = (int) inputIndex;
            double fraction = inputIndex - index;

            double p0 = (index > 0) ? (double) input[index - 1] : (double) input[index];
            double p1 = (double) input[index];
            double p2 = (index < input.length - 1) ? (double) input[index + 1] : p1;
            double p3 = (index < input.length - 2) ? (double) input[index + 2] : p2;

            double a = -0.5 * p0 + 1.5 * p1 - 1.5 * p2 + 0.5 * p3;
            double b = p0 - 2.5 * p1 + 2.0 * p2 - 0.5 * p3;
            double c = -0.5 * p0 + 0.5 * p2;
            double d = p1;

            double sample = (a * fraction * fraction * fraction)
                    + (b * fraction * fraction)
                    + (c * fraction)
                    + d;

            if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;
            if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;

            output[i] = (short) (int) sample;
        }
        return output;
    }

    public static void playFile(File file, float localVol, float playerVol) {
        MinecraftClient client = MinecraftClient.getInstance();
        VoicechatClientApi api = clientApi;

        if (api == null) {
            if (client.player != null) {
                client.player.sendMessage(Text.translatable("message.opensoundboard.vc_not_connected"), true);
            }
            return;
        }

        if (api.isMuted() && !SoundboardConfig.data.isPlayWhileMuted()) {
            if (client.player != null) {
                client.player.sendMessage(Text.translatable("message.opensoundboard.muted_error"), true);
            }
            return;
        }

        if (SoundboardConfig.data.isSingleSongAtATime()) {
            stopAll();
        }

        CompletableFuture.runAsync(() -> {
            try {
                short[] pcmData = decodeMp3(file);
                if (pcmData != null && pcmData.length > 0) {
                    PlayingSound sound = new PlayingSound(file.getName(), pcmData, localVol, playerVol);
                    var data = SoundboardConfig.get(file.getName());
                    if (data.getStartingPoint() > 0f) {
                        sound.setCursor(data.getStartingPoint());
                    }
                    sound.isLooping = SoundboardConfig.data.isLoopAll();
                    activeSounds.add(sound);
                } else {
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(Text.translatable("message.opensoundboard.decode_failed", file.getName()), false);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static boolean isPlaying(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file) && !s.isFinished()) {
                return true;
            }
        }
        return false;
    }

    public static void stop(String file) {
        activeSounds.removeIf(s -> s.name.equals(file));
    }

    public static void pause(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) {
                s.isPaused = true;
            }
        }
    }

    public static void resume(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) {
                s.isPaused = false;
            }
        }
    }

    public static void setGlobalLooping(boolean looping) {
        for (PlayingSound s : activeSounds) {
            s.isLooping = looping;
        }
    }

    public static void setCursor(String file, float progress) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) {
                s.setCursor(progress);
            }
        }
    }

    public static void skip(String file, int seconds) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) {
                s.skip(seconds);
            }
        }
    }

    public static float getProgress(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) {
                return s.progress();
            }
        }
        return -1f;
    }

    public static int getTimeSeconds(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) {
                return s.timeSeconds();
            }
        }
        return 0;
    }

    public static int getDurationSeconds(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file)) {
                return s.durationSeconds();
            }
        }
        return 0;
    }

    public static boolean isPaused(String file) {
        for (PlayingSound s : activeSounds) {
            if (s.name.equals(file) && s.isPaused) {
                return true;
            }
        }
        return false;
    }

    public static String getActiveSoundName() {
        for (PlayingSound s : activeSounds) {
            if (!s.isFinished()) {
                return s.name;
            }
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

    private static short[] decodeMp3(File file) {
        VoicechatApi currentApi = api;
        if (currentApi == null) {
            return null;
        }

        try (BufferedInputStream stream = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
            var decoder = currentApi.createMp3Decoder(stream);
            if (decoder == null) {
                return null;
            }

            short[] rawPcm = decoder.decode();
            var format = decoder.getAudioFormat();

            if (format.getChannels() == 2) {
                rawPcm = stereoToMono(rawPcm);
            }

            if ((int) format.getSampleRate() != 48000) {
                rawPcm = resample(rawPcm, (int) format.getSampleRate(), 48000);
            }

            return rawPcm;
        } catch (Exception e) {
            System.err.println("Error decoding " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private static short[] stereoToMono(short[] stereo) {
        short[] mono = new short[stereo.length / 2];
        for (int i = 0; i < mono.length; i++) {
            int left = stereo[i * 2];
            int right = stereo[i * 2 + 1];
            mono[i] = (short) ((left + right) / 2);
        }
        return mono;
    }

    private static final class PlayingSound {
        public final String name;
        private final short[] samples;

        public volatile float localVolume;
        public volatile float playerVolume;

        private int cursor = 0;
        public volatile boolean isPaused = false;
        public volatile boolean isLooping = false;

        private PlayingSound(String name, short[] samples, float localVolume, float playerVolume) {
            this.name = name;
            this.samples = samples;
            this.localVolume = localVolume;
            this.playerVolume = playerVolume;
        }

        public boolean isFinished() {
            return !isLooping && cursor >= samples.length;
        }

        public int remaining() {
            return isLooping ? FRAME_SIZE : (samples.length - cursor);
        }

        public float progress() {
            if (samples.length == 0) return 0f;
            return (float) cursor / (float) samples.length;
        }

        public int timeSeconds() {
            return cursor / 48000;
        }

        public int durationSeconds() {
            return samples.length / 48000;
        }

        public void setCursor(float progress) {
            int v = (int) (progress * samples.length);
            if (v < 0) v = 0;
            if (v > samples.length) v = samples.length;
            cursor = v;
        }

        public void skip(int seconds) {
            int sampleDelta = seconds * 48000;
            int v = cursor + sampleDelta;
            if (v < 0) v = 0;
            if (v > samples.length) v = samples.length;
            cursor = v;
        }

        public short readNext() {
            if (cursor >= samples.length) {
                if (isLooping) {
                    cursor = 0;
                } else {
                    return 0;
                }
            }
            return (cursor < samples.length) ? samples[cursor++] : 0;
        }
    }
}
