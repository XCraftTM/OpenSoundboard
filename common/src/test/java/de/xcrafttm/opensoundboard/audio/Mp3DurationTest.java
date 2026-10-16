package de.xcrafttm.opensoundboard.audio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Mp3DurationTest {

    /** MPEG 1 Layer III, 128 kbps, 44.1 kHz, stereo, no padding: 417-byte frames of 1152 samples. */
    private static final byte[] HEADER_V1 = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00};
    private static final int FRAME_V1 = 417;

    /** MPEG 2 Layer III, 64 kbps, 22.05 kHz, mono: 208-byte frames of 576 samples. */
    private static final byte[] HEADER_V2 = {(byte) 0xFF, (byte) 0xF3, (byte) 0x80, (byte) 0xC0};
    private static final int FRAME_V2 = 208;

    private static long micros(byte[] data) throws IOException {
        return Mp3Duration.micros(new ByteArrayInputStream(data));
    }

    private static void frames(ByteArrayOutputStream out, byte[] header, int length, int count) {
        for (int i = 0; i < count; i++) {
            out.writeBytes(header);
            out.writeBytes(new byte[length - 4]);
        }
    }

    private static long expected(long frames, int samplesPerFrame, int sampleRate) {
        return Math.round(frames * samplesPerFrame / (double) sampleRate * 1_000_000);
    }

    @Test
    void countsFrames() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        frames(out, HEADER_V1, FRAME_V1, 100);
        assertEquals(expected(100, 1152, 44100), micros(out.toByteArray()));
    }

    @Test
    void mpeg2Frames() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        frames(out, HEADER_V2, FRAME_V2, 50);
        assertEquals(expected(50, 576, 22050), micros(out.toByteArray()));
    }

    @Test
    void skipsId3TagsAndTrailingGarbage() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{'I', 'D', '3', 4, 0, 0, 0, 0, 0x02, 0x00}); // 256-byte tag (syncsafe)
        out.writeBytes(new byte[256]);
        frames(out, HEADER_V1, FRAME_V1, 40);
        byte[] id3v1 = new byte[128];
        id3v1[0] = 'T';
        id3v1[1] = 'A';
        id3v1[2] = 'G';
        out.writeBytes(id3v1);
        assertEquals(expected(40, 1152, 44100), micros(out.toByteArray()));
    }

    @Test
    void resyncsAfterJunk() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{1, 2, 3, (byte) 0xFF, 0x00, 5});
        frames(out, HEADER_V1, FRAME_V1, 10);
        assertEquals(expected(10, 1152, 44100), micros(out.toByteArray()));
    }

    @Test
    void usesXingFrameCount() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] first = new byte[FRAME_V1];
        System.arraycopy(HEADER_V1, 0, first, 0, 4);
        int at = 4 + 32; // MPEG 1 stereo side info
        first[at] = 'X';
        first[at + 1] = 'i';
        first[at + 2] = 'n';
        first[at + 3] = 'g';
        first[at + 7] = 1;            // flags: frame count present
        first[at + 10] = 0x27;        // 10000 frames (0x2710)
        first[at + 11] = 0x10;
        out.writeBytes(first);
        frames(out, HEADER_V1, FRAME_V1, 5); // the real stream is shorter; the Xing count wins
        assertEquals(expected(10000, 1152, 44100), micros(out.toByteArray()));
    }

    @Test
    void notAnMp3() throws IOException {
        assertEquals(-1, micros(new byte[5000]));
        assertEquals(-1, micros(new byte[0]));
    }
}
