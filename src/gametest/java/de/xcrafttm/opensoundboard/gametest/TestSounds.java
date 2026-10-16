package de.xcrafttm.opensoundboard.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the sound library the tests run against. The MP3s are generated, not shipped: silent
 * MPEG-1 Layer III frames, which every decoder plays as silence, so tests are quiet and need no
 * binary fixtures.
 */
final class TestSounds {

    static final String AIRHORN = "Airhorn.mp3";
    static final String APPLAUSE = "Applause.mp3";
    static final String BRUH = "Bruh.mp3";
    static final String INTRO_ROOT = "intro.mp3";
    static final String LONG_SONG = "Long Song.mp3";
    static final String INTRO_GAMES = "Games/intro.mp3";
    static final String MARIO_COIN = "Games/Mario Coin.mp3";
    static final String LEVEL_UP = "Games/Retro/Level Up.mp3";

    /** Rows of the root folder list: the "Games" folder, a divider, then the five root sounds. */
    static final int ROOT_ROWS = 7;
    static final int ROOT_SOUNDS = 5;

    /** MPEG-1 Layer III, 128 kbps, 44.1 kHz, mono, no CRC: 417-byte frames of 1152 samples. */
    private static final byte[] HEADER = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0xC0};
    private static final int FRAME_BYTES = 417;
    private static final double FRAME_SECONDS = 1152 / 44100.0;

    private TestSounds() {
    }

    static void createLibrary(Path root) throws IOException {
        write(root, AIRHORN, 2);
        write(root, APPLAUSE, 4);
        write(root, BRUH, 1);
        write(root, INTRO_ROOT, 2);
        write(root, LONG_SONG, 60);
        write(root, INTRO_GAMES, 3);
        write(root, MARIO_COIN, 1);
        write(root, LEVEL_UP, 2);
    }

    private static void write(Path root, String key, double seconds) throws IOException {
        Path file = root.resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, silentMp3(seconds));
    }

    static byte[] silentMp3(double seconds) {
        int frames = (int) Math.ceil(seconds / FRAME_SECONDS);
        byte[] data = new byte[frames * FRAME_BYTES];
        for (int i = 0; i < frames; i++) System.arraycopy(HEADER, 0, data, i * FRAME_BYTES, HEADER.length);
        return data;
    }
}
