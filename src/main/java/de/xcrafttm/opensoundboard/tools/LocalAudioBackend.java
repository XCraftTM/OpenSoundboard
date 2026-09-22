package de.xcrafttm.opensoundboard.tools;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.config.SoundboardConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Fallback output used when no voice chat is connected (or none is installed): plays the local
 * mix on this computer only, through Java Sound on a dedicated thread. Nobody else hears it.
 */
public final class LocalAudioBackend implements VoiceBackend {

    public static final LocalAudioBackend INSTANCE = new LocalAudioBackend();

    private static final int FRAME_SIZE = 960;
    private static final long CLOSE_AFTER_IDLE_MS = 3_000;
    private static final AudioFormat FORMAT = new AudioFormat(SoundboardAudioSystem.SAMPLE_RATE, 16, 1, true, false);

    private SourceDataLine line;
    private boolean warnedUnavailable = false;

    private LocalAudioBackend() {
    }

    public static void start() {
        SoundboardAudioSystem.registerBackend(INSTANCE);
        Thread thread = new Thread(INSTANCE::run, "OpenSoundboard Local Audio");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public String id() {
        return "local";
    }

    @Override
    public String name() {
        return Component.translatable("gui.opensoundboard.output.local").getString();
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean isConnected() {
        return SoundboardConfig.data != null && SoundboardConfig.data.isLocalPlayback();
    }

    @Override
    public boolean isMicMuted() {
        return false;
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public boolean localOnly() {
        return true;
    }

    private void run() {
        byte[] bytes = new byte[FRAME_SIZE * 2];
        long idleSince = System.currentTimeMillis();
        while (true) {
            try {
                SoundboardAudioSystem.Frame frame = SoundboardAudioSystem.mixFrame(this, FRAME_SIZE, SoundboardAudioSystem.SAMPLE_RATE);
                if (frame == null || frame.local() == null) {
                    if (line != null && System.currentTimeMillis() - idleSince > CLOSE_AFTER_IDLE_MS) closeLine();
                    Thread.sleep(10);
                    continue;
                }
                idleSince = System.currentTimeMillis();
                if (!openLine()) {
                    Thread.sleep(1_000);
                    continue;
                }

                float master = masterVolume();
                short[] samples = frame.local();
                for (int i = 0; i < samples.length; i++) {
                    int value = Math.round(samples[i] * master);
                    bytes[i * 2] = (byte) value;
                    bytes[i * 2 + 1] = (byte) (value >> 8);
                }
                // write() blocks once the line buffer is full, which paces the loop in real time.
                line.write(bytes, 0, samples.length * 2);
            } catch (InterruptedException e) {
                closeLine();
                return;
            } catch (Exception e) {
                OpenSoundboardClient.LOGGER.warn("[OpenSoundboard] Local playback error: {}", e.getMessage());
                closeLine();
            }
        }
    }

    private boolean openLine() {
        if (line != null) return true;
        try {
            SourceDataLine opened = AudioSystem.getSourceDataLine(FORMAT);
            opened.open(FORMAT, FRAME_SIZE * 2 * 4);
            opened.start();
            line = opened;
            warnedUnavailable = false;
            return true;
        } catch (Exception e) {
            if (!warnedUnavailable) {
                OpenSoundboardClient.LOGGER.warn("[OpenSoundboard] No audio output for local playback: {}", e.getMessage());
                warnedUnavailable = true;
            }
            return false;
        }
    }

    private void closeLine() {
        if (line == null) return;
        try {
            line.drain();
            line.close();
        } catch (Exception ignored) {
        }
        line = null;
    }

    private static float masterVolume() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return 1f;
        return Math.max(0f, Math.min(1f, client.options.getSoundSourceVolume(SoundSource.MASTER)));
    }
}
