package de.xcrafttm.opensoundboard.integration.plasmo;

import de.xcrafttm.opensoundboard.OpenSoundboardClient;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.tools.VoiceBackend;
import su.plo.voice.api.addon.AddonLoaderScope;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.client.PlasmoVoiceClient;
import su.plo.voice.api.client.audio.source.LoopbackSource;
import su.plo.voice.api.client.event.audio.capture.AudioCaptureEvent;
import su.plo.voice.api.event.EventSubscribe;

import javax.sound.sampled.AudioFormat;

/**
 * Plasmo Voice backend, loaded as a client addon. The soundboard mix is added to the raw
 * microphone samples before Plasmo Voice runs its activations, so sounds are sent whenever the
 * player's activation is active (voice activation picks them up on its own; push-to-talk needs
 * the key held). The local mix plays through a loopback source.
 */
@Addon(
        id = "pv-addon-opensoundboard",
        name = "OpenSoundboard",
        scope = AddonLoaderScope.CLIENT,
        version = "1.0.0",
        authors = {"XCraftTM"}
)
public final class PlasmoVoiceBackend implements VoiceBackend {

    @InjectPlasmoVoice
    private PlasmoVoiceClient voiceClient;

    private LoopbackSource loopback;

    /** Called from the client entrypoint only when Plasmo Voice is installed. */
    public static void register() {
        PlasmoVoiceBackend backend = new PlasmoVoiceBackend();
        PlasmoVoiceClient.getAddonsLoader().load(backend);
        SoundboardAudioSystem.registerBackend(backend);
        OpenSoundboardClient.LOGGER.info("[OpenSoundboard] Plasmo Voice support enabled");
    }

    @Override
    public String name() {
        return "Plasmo Voice";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean isConnected() {
        PlasmoVoiceClient client = voiceClient;
        return client != null
                && client.getServerInfo().isPresent()
                && client.getUdpClientManager().isConnected();
    }

    @Override
    public boolean isMicMuted() {
        PlasmoVoiceClient client = voiceClient;
        if (client == null) return false;
        return client.getConfig().getVoice().getMicrophoneDisabled().value()
                || client.getAudioCapture().isServerMuted();
    }

    @Override
    public boolean isDisabled() {
        PlasmoVoiceClient client = voiceClient;
        return client == null || client.getConfig().getVoice().getDisabled().value();
    }

    @EventSubscribe
    public void onAudioCapture(AudioCaptureEvent event) {
        short[] samples = event.getSamples();
        AudioFormat format = event.getDevice().getFormat();
        int channels = Math.max(1, format.getChannels());
        int frameSamples = samples.length / channels;

        SoundboardAudioSystem.Frame frame = SoundboardAudioSystem.mixFrame(this, frameSamples, (int) format.getSampleRate());
        if (frame == null) return;

        short[] player = frame.player();
        for (int i = 0; i < frameSamples; i++) {
            for (int ch = 0; ch < channels; ch++) {
                int index = i * channels + ch;
                samples[index] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, samples[index] + player[i]));
            }
        }

        if (frame.local() != null) writeLocal(frame.local());
    }

    private void writeLocal(short[] local) {
        try {
            if (loopback == null || loopback.isClosed()) {
                loopback = voiceClient.getSourceManager().createLoopbackSource(true);
                loopback.initialize(false);
            }
            loopback.write(local);
        } catch (Exception e) {
            OpenSoundboardClient.LOGGER.warn("[OpenSoundboard] Plasmo Voice loopback failed: {}", e.getMessage());
            loopback = null;
        }
    }
}
