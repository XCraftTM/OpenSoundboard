package de.xcrafttm.opensoundboard.integration;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;
import de.xcrafttm.opensoundboard.tools.SoundboardAudioSystem;

public class SoundboardPlugin implements VoicechatPlugin {

    private static final String MOD_ID = "opensoundboard";

    @Override
    public String getPluginId() {
        return MOD_ID + "_plugin";
    }

    @Override
    public void registerEvents(EventRegistration reg) {
        reg.registerEvent(ClientVoicechatConnectionEvent.class, (SoundboardAudioSystem::onClientConnection));
        reg.registerEvent(MergeClientSoundEvent.class, (SoundboardAudioSystem::onMergeSound));
    }

    @Override
    public void initialize(VoicechatApi api) {
        SoundboardAudioSystem.initialize(api);
    }
}

