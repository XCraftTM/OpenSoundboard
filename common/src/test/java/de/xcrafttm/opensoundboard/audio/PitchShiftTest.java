package de.xcrafttm.opensoundboard.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PitchShiftTest {

    private static final int RATE = 48_000;

    private static short[] sine(double hz, double seconds, int amplitude) {
        short[] out = new short[(int) (RATE * seconds)];
        for (int i = 0; i < out.length; i++) out[i] = (short) Math.round(amplitude * Math.sin(2 * Math.PI * hz * i / RATE));
        return out;
    }

    private static short[] mix(short[] a, short[] b) {
        short[] out = new short[a.length];
        for (int i = 0; i < a.length; i++) out[i] = (short) (a[i] + b[i]);
        return out;
    }

    private static double pitch(int semitones) {
        return Math.pow(2, semitones / 12.0);
    }

    /** Frequency from upward zero crossings over the middle second. */
    private static double frequency(short[] signal) {
        int from = RATE / 2;
        int crossings = 0;
        for (int i = from + 1; i < from + RATE; i++) if (signal[i - 1] < 0 && signal[i] >= 0) crossings++;
        return crossings;
    }

    /**
     * Largest / smallest loudness over windows of exactly one bass wave in the middle second
     * (1 = perfectly steady). One full wave makes the reading independent of where a window
     * starts, and it is short enough (18-36 ms) to catch fast pulsing.
     */
    private static double loudnessSwing(short[] signal, double bassHz) {
        int window = (int) Math.round(RATE / bassHz);
        double max = 0;
        double min = Double.MAX_VALUE;
        for (int start = RATE / 2; start + window <= RATE / 2 + RATE; start += window) {
            double sum = 0;
            for (int i = start; i < start + window; i++) sum += (double) signal[i] * signal[i];
            double rms = Math.sqrt(sum / window);
            max = Math.max(max, rms);
            min = Math.min(min, rms);
        }
        return max / min;
    }

    @Test
    void unchangedPitchReturnsTheInput() {
        short[] tone = sine(440, 1, 10_000);
        assertSame(tone, PitchShift.shift(tone, 1.0));
    }

    @Test
    void keepsTheLength() {
        short[] tone = sine(440, 2, 10_000);
        for (int semis : new int[]{-12, -5, 3, 7, 12}) {
            assertEquals(tone.length, PitchShift.shift(tone, pitch(semis)).length, "length at " + semis);
        }
    }

    @Test
    void sevenSemitonesUpIsAFifthHigher() {
        double hz = frequency(PitchShift.shift(sine(440, 3, 10_000), pitch(7)));
        assertTrue(Math.abs(hz - 659.3) < 659.3 * 0.02, "expected ~659 Hz, got " + hz);
    }

    @Test
    void anOctaveDownHalvesTheFrequency() {
        double hz = frequency(PitchShift.shift(sine(880, 3, 10_000), pitch(-12)));
        assertTrue(Math.abs(hz - 440) < 440 * 0.02, "expected ~440 Hz, got " + hz);
    }

    /**
     * The bug players heard: a fast "wup wup" pulsing on bass. A steady bass note has to stay
     * steady after shifting, in both directions and together with higher notes.
     */
    @Test
    void bassStaysSteady() {
        for (int semis : new int[]{-12, -7, -3, 3, 7, 12}) {
            double bassOut = 55 * pitch(semis);
            double bassSwing = loudnessSwing(PitchShift.shift(sine(55, 3, 12_000), pitch(semis)), bassOut);
            assertTrue(bassSwing < 1.1, "bass pulses at " + semis + " semitones: swing " + bassSwing);
            // 330 Hz is exactly 6x the bass, so one bass wave also holds whole melody waves.
            short[] chord = mix(sine(55, 3, 8_000), sine(330, 3, 4_000));
            double chordSwing = loudnessSwing(PitchShift.shift(chord, pitch(semis)), bassOut);
            assertTrue(chordSwing < 1.15, "bass + melody pulses at " + semis + " semitones: swing " + chordSwing);
        }
    }

    @Test
    void isFastEnoughToRunWhenASoundStarts() {
        short[] minute = mix(sine(55, 60, 8_000), sine(440, 60, 4_000));
        PitchShift.shift(minute, pitch(5)); // warm up
        long start = System.nanoTime();
        PitchShift.shift(minute, pitch(7));
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf("PitchShift: one minute of audio in %.2f s%n", seconds);
        assertTrue(seconds < 5, "one minute took " + seconds + " s");
    }
}
