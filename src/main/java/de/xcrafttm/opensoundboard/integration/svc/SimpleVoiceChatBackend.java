package de.xcrafttm.opensoundboard.integration.svc;

import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.audiochannel.ClientStaticAudioChannel;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;
import de.xcrafttm.opensoundboard.tools.VoiceBackend;

import java.util.UUID;

/**
 * Simple Voice Chat backend: merges the soundboard mix into the outgoing microphone stream and
 * plays the local mix through a static client audio channel.
 */
public final class SimpleVoiceChatBackend implements VoiceBackend {

    public static final SimpleVoiceChatBackend INSTANCE = new SimpleVoiceChatBackend();

    private static final int FRAME_SIZE = 960;

    private volatile VoicechatClientApi clientApi;
    private volatile ClientStaticAudioChannel localChannel;

    private SimpleVoiceChatBackend() {
    }

    @Override
    public String id() {
        return "svc";
    }

    @Override
    public String name() {
        return "Simple Voice Chat";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean isConnected() {
        return clientApi != null;
    }

    @Override
    public boolean isMicMuted() {
        VoicechatClientApi api = clientApi;
        return api != null && api.isMuted();
    }

    @Override
    public boolean isDisabled() {
        VoicechatClientApi api = clientApi;
        return api == null || api.isDisabled();
    }

    void onClientConnection(ClientVoicechatConnectionEvent event) {
        if (event.isConnected()) {
            VoicechatClientApi api = event.getVoicechat();
            var category = api.volumeCategoryBuilder()
                    .setId("soundboard")
                    .setName("Soundboard")
                    .build();
            api.registerClientVolumeCategory(category);

            ClientStaticAudioChannel channel = api.createStaticAudioChannel(UUID.randomUUID());
            if (channel != null) channel.setCategory(category.getId());
            localChannel = channel;
            clientApi = api;

            SoundboardAudioSystem.scanDurations();
        } else {
            SoundboardAudioSystem.stopAll();
            clientApi = null;
            localChannel = null;
        }
    }

    void onMergeSound(MergeClientSoundEvent event) {
        SoundboardAudioSystem.Frame frame = SoundboardAudioSystem.mixFrame(this, FRAME_SIZE, SoundboardAudioSystem.SAMPLE_RATE);
        if (frame == null) return;
        event.mergeAudio(frame.player());
        ClientStaticAudioChannel channel = localChannel;
        if (frame.local() != null && channel != null) channel.play(frame.local());
    }
}
