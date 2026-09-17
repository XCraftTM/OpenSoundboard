package de.xcrafttm.opensoundboard.integration.svc;

import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent;

/** Wires Simple Voice Chat events to {@link SimpleVoiceChatBackend}. */
public final class SimpleVoiceChatEvents {

    private SimpleVoiceChatEvents() {
    }

    public static void register(EventRegistration reg) {
        reg.registerEvent(ClientVoicechatConnectionEvent.class, SimpleVoiceChatBackend.INSTANCE::onClientConnection);
        reg.registerEvent(MergeClientSoundEvent.class, SimpleVoiceChatBackend.INSTANCE::onMergeSound);
    }
}
