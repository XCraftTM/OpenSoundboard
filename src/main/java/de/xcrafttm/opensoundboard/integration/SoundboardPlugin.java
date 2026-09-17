package de.xcrafttm.opensoundboard.integration;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.xcrafttm.opensoundboard.integration.svc.SimpleVoiceChatBackend;
import de.xcrafttm.opensoundboard.integration.svc.SimpleVoiceChatEvents;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;

/** Simple Voice Chat entrypoint; only loaded when Simple Voice Chat is installed. */
public class SoundboardPlugin implements VoicechatPlugin {

    private static final String MOD_ID = "opensoundboard";

    @Override
    public String getPluginId() {
        return MOD_ID + "_plugin";
    }

    @Override
    public void registerEvents(EventRegistration reg) {
        SimpleVoiceChatEvents.register(reg);
    }

    @Override
    public void initialize(VoicechatApi api) {
        SoundboardAudioSystem.registerBackend(SimpleVoiceChatBackend.INSTANCE);
        // Simple Voice Chat bundles a native MP3 decoder that is much faster than the Java fallback.
        SoundboardAudioSystem.setNativeDecoder(in -> {
            var decoder = api.createMp3Decoder(in);
            if (decoder == null) return null;
            short[] pcm = decoder.decode();
            var format = decoder.getAudioFormat();
            return new SoundboardAudioSystem.Decoded(pcm, (int) format.getSampleRate(), format.getChannels());
        });
    }
}
