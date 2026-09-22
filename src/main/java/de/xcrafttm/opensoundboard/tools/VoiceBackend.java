package de.xcrafttm.opensoundboard.tools;

/**
 * An audio output the soundboard can play through: a voice chat mod (implementations live in the
 * integration packages and are only loaded when their mod is installed) or local-only playback.
 */
public interface VoiceBackend {

    /** Stable id stored in the config, e.g. "svc". */
    String id();

    /** Display name, e.g. "Simple Voice Chat". */
    String name();

    /** Lower values win when several backends are connected at the same time. */
    int priority();

    /** Whether the backend is connected to a voice server and can transmit. */
    boolean isConnected();

    /** Whether the player's microphone is muted. */
    boolean isMicMuted();

    /** Whether voice chat is disabled entirely on the client. */
    boolean isDisabled();

    /** Local-only output: nothing is sent to other players, the local mix is always produced. */
    default boolean localOnly() {
        return false;
    }
}
