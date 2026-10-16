package de.xcrafttm.opensoundboard.audio;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

/**
 * Measures the length of an MP3 (MPEG 1/2/2.5 Layer III) from its frame headers, without decoding
 * any audio. Uses the Xing/Info frame count when the encoder wrote one, otherwise walks the frames.
 */
public final class Mp3Duration {

    /** Give up after this much data without a valid frame header (corrupt or not an MP3). */
    private static final int MAX_GARBAGE = 64 * 1024;

    private static final int[] BITRATES_V1 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1};
    private static final int[] BITRATES_V2 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1};
    private static final int[][] SAMPLE_RATES = {
            {11025, 12000, 8000},  // MPEG 2.5
            null,                  // reserved
            {22050, 24000, 16000}, // MPEG 2
            {44100, 48000, 32000}, // MPEG 1
    };

    private Mp3Duration() {
    }

    /** Duration in microseconds, or -1 if no MP3 frames were found. */
    public static long micros(InputStream raw) throws IOException {
        PushbackInputStream in = new PushbackInputStream(new BufferedInputStream(raw, 64 * 1024), 10);
        skipId3v2(in);

        long frames = 0;
        double seconds = 0;
        boolean first = true;
        int[] w = new int[4];
        if (!fill(in, w)) return -1;

        while (true) {
            int garbage = 0;
            Header header;
            while ((header = Header.parse(w)) == null) {
                int next = in.read();
                if (next < 0 || ++garbage > MAX_GARBAGE) return result(frames, seconds);
                w[0] = w[1];
                w[1] = w[2];
                w[2] = w[3];
                w[3] = next;
            }

            byte[] body = new byte[header.length - 4];
            int read = in.readNBytes(body, 0, body.length);
            if (read < body.length) {
                // Truncated last frame: count what is there.
                if (read > 0) seconds += header.seconds() * read / body.length;
                return result(frames, seconds);
            }
            if (first) {
                first = false;
                long xingFrames = header.xingFrames(body);
                if (xingFrames > 0) return Math.round(xingFrames * header.seconds() * 1_000_000);
            }
            frames++;
            seconds += header.seconds();
            if (!fill(in, w)) return result(frames, seconds);
        }
    }

    private static long result(long frames, double seconds) {
        return frames == 0 && seconds == 0 ? -1 : Math.round(seconds * 1_000_000);
    }

    private static boolean fill(InputStream in, int[] w) throws IOException {
        for (int i = 0; i < 4; i++) {
            int b = in.read();
            if (b < 0) return false;
            w[i] = b;
        }
        return true;
    }

    private static void skipId3v2(PushbackInputStream in) throws IOException {
        byte[] tag = new byte[10];
        int read = in.readNBytes(tag, 0, 10);
        if (read == 10 && tag[0] == 'I' && tag[1] == 'D' && tag[2] == '3') {
            long size = ((tag[6] & 0x7F) << 21) | ((tag[7] & 0x7F) << 14) | ((tag[8] & 0x7F) << 7) | (tag[9] & 0x7F);
            if ((tag[5] & 0x10) != 0) size += 10; // footer
            try {
                in.skipNBytes(size);
            } catch (EOFException ignored) {
            }
            return;
        }
        if (read > 0) in.unread(tag, 0, read);
    }

    private record Header(int version, int sampleRate, int samplesPerFrame, int length, boolean mono) {

        static Header parse(int[] b) {
            if (b[0] != 0xFF || (b[1] & 0xE0) != 0xE0) return null;
            int version = (b[1] >> 3) & 3;
            int layer = (b[1] >> 1) & 3;
            if (version == 1 || layer != 1) return null; // reserved version, or not Layer III
            int bitrateIndex = (b[2] >> 4) & 0xF;
            int rateIndex = (b[2] >> 2) & 3;
            if (bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return null; // free format / invalid
            boolean v1 = version == 3;
            int bitrate = (v1 ? BITRATES_V1 : BITRATES_V2)[bitrateIndex] * 1000;
            int sampleRate = SAMPLE_RATES[version][rateIndex];
            int padding = (b[2] >> 1) & 1;
            int length = (v1 ? 144 : 72) * bitrate / sampleRate + padding;
            if (length <= 4) return null;
            boolean mono = ((b[3] >> 6) & 3) == 3;
            return new Header(version, sampleRate, v1 ? 1152 : 576, length, mono);
        }

        double seconds() {
            return samplesPerFrame / (double) sampleRate;
        }

        /** Frame count from a Xing/Info header in this frame's body, or -1 if there is none. */
        long xingFrames(byte[] body) {
            boolean v1 = version == 3;
            int sideInfo = v1 ? (mono ? 17 : 32) : (mono ? 9 : 17);
            int at = sideInfo;
            if (body.length < at + 12) return -1;
            boolean xing = body[at] == 'X' && body[at + 1] == 'i' && body[at + 2] == 'n' && body[at + 3] == 'g';
            boolean info = body[at] == 'I' && body[at + 1] == 'n' && body[at + 2] == 'f' && body[at + 3] == 'o';
            if (!xing && !info) return -1;
            int flags = readInt(body, at + 4);
            if ((flags & 1) == 0) return -1;
            long frames = readInt(body, at + 8) & 0xFFFFFFFFL;
            return frames > 0 ? frames : -1;
        }

        private static int readInt(byte[] b, int at) {
            return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
        }
    }
}
