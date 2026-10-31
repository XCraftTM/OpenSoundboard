package de.xcrafttm.opensoundboard.audio;

/**
 * Changes the pitch of a whole sound without changing its speed or length, in two steps (the
 * approach of tools like SoundTouch):
 * <ol>
 *   <li>Resample by the pitch factor with a windowed-sinc (Lanczos) filter. This raises or lowers
 *       the pitch but also makes the sound shorter or longer.</li>
 *   <li>Time-stretch back to the original length with WSOLA: the sound is rebuilt from short,
 *       overlapping pieces, and each piece is taken from the spot whose waveform lines up best
 *       with where the previous piece left off. Because the pieces are aligned before they are
 *       blended, they never cancel each other out, so there is no pulsing ("wup wup") in the bass.</li>
 * </ol>
 * Works on mono samples at any rate; tuned for 48 kHz. The result has exactly the input's length,
 * so a playing sound can switch between versions without changing its position.
 */
public final class PitchShift {

    /** Piece length: 64 ms at 48 kHz, long enough for bass, short enough for crisp transients. */
    static final int FRAME = 3072;
    /** Output distance between pieces (50 % overlap). */
    private static final int HOP = FRAME / 2;
    /** How far (±) each piece may be moved to line up with the previous one: 16 ms. */
    private static final int TOLERANCE = 768;
    /** Periodic Hann window: two copies half a window apart add up to exactly 1. */
    private static final float[] WINDOW = new float[FRAME];

    /** Lanczos kernel lobes, and the lookup table resolution (entries per unit). */
    private static final int LOBES = 8;
    private static final int TABLE_RES = 512;
    private static final float[] KERNEL = new float[LOBES * TABLE_RES + 2];

    static {
        for (int i = 0; i < FRAME; i++) WINDOW[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / FRAME));
        for (int i = 0; i < KERNEL.length; i++) {
            double t = (double) i / TABLE_RES;
            KERNEL[i] = t >= LOBES ? 0f : (float) (sinc(t) * sinc(t / LOBES));
        }
    }

    private PitchShift() {
    }

    /** {@code input} at {@code pitch} times its pitch (2 = an octave up), with the same length. */
    public static short[] shift(short[] input, double pitch) {
        if (pitch == 1.0 || input.length == 0) return input;
        float[] resampled = resample(input, pitch);
        float[] stretched = stretch(resampled, input.length);
        short[] out = new short[input.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(stretched[i])));
        }
        return out;
    }

    // ---------------------------------------------------------------- step 1: resample

    /** Plays {@code input} {@code factor} times as fast (pitch and speed), band-limited against aliasing. */
    static float[] resample(short[] input, double factor) {
        int n = input.length;
        int length = Math.max(1, (int) (n / factor));
        double cutoff = Math.min(1.0, 1.0 / factor); // lower the cutoff when squeezing, to avoid aliasing
        int half = (int) Math.ceil(LOBES / cutoff);
        float[] out = new float[length];
        for (int j = 0; j < length; j++) {
            double x = j * factor;
            int center = (int) Math.floor(x);
            double sum = 0;
            double weights = 0;
            for (int k = center - half + 1; k <= center + half; k++) {
                if (k < 0 || k >= n) continue;
                double w = kernel(Math.abs(x - k) * cutoff);
                sum += input[k] * w;
                weights += w;
            }
            out[j] = weights > 1e-9 ? (float) (sum / weights) : 0f;
        }
        return out;
    }

    private static double kernel(double t) {
        double pos = t * TABLE_RES;
        int i = (int) pos;
        if (i >= KERNEL.length - 1) return 0;
        double frac = pos - i;
        return KERNEL[i] + (KERNEL[i + 1] - KERNEL[i]) * frac;
    }

    private static double sinc(double t) {
        if (t == 0) return 1;
        double x = Math.PI * t;
        return Math.sin(x) / x;
    }

    // ---------------------------------------------------------------- step 2: WSOLA time-stretch

    /** Stretch or squeeze {@code input} to {@code length} samples without changing its pitch. */
    static float[] stretch(float[] input, int length) {
        double hopIn = HOP * (double) input.length / length;
        float[] out = new float[length + FRAME];
        int previous = 0;
        for (int k = 0; ; k++) {
            int outPos = k * HOP;
            if (outPos >= length) break;
            int pos;
            if (k == 0) {
                pos = 0;
            } else {
                int nominal = (int) Math.round(k * hopIn);
                pos = bestMatch(input, previous + HOP, nominal - TOLERANCE, nominal + TOLERANCE);
            }
            for (int i = 0; i < FRAME; i++) {
                // The very first piece has nothing to overlap with at its start: no fade-in there.
                float w = k == 0 && i < HOP ? 1f : WINDOW[i];
                out[outPos + i] += sample(input, pos + i) * w;
            }
            previous = pos;
        }
        float[] result = new float[length];
        System.arraycopy(out, 0, result, 0, length);
        return result;
    }

    /**
     * The start in [from, to] whose first half-piece looks most like the natural continuation of
     * the previous piece (normalized cross-correlation). A coarse search on every 4th sample and
     * every 8th position is refined around the best candidate.
     */
    static int bestMatch(float[] input, int natural, int from, int to) {
        from = Math.max(0, from);
        to = Math.max(from, Math.min(to, input.length - 1));
        int best = Math.max(from, Math.min(to, natural));
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int c = from; c <= to; c += 8) {
            double score = similarity(input, natural, c, 4);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        int coarse = best;
        bestScore = Double.NEGATIVE_INFINITY;
        for (int c = Math.max(from, coarse - 8); c <= Math.min(to, coarse + 8); c++) {
            double score = similarity(input, natural, c, 1);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    private static double similarity(float[] input, int template, int candidate, int step) {
        double dot = 0;
        double energy = 1e-9;
        for (int i = 0; i < HOP; i += step) {
            double a = sample(input, template + i);
            double b = sample(input, candidate + i);
            dot += a * b;
            energy += b * b;
        }
        return dot / Math.sqrt(energy);
    }

    private static float sample(float[] input, int i) {
        return i >= 0 && i < input.length ? input[i] : 0f;
    }
}
