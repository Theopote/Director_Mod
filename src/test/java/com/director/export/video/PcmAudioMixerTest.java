package com.director.export.video;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class PcmAudioMixerTest {
    @Test void retainsTailOfSoundStartingBeforeExportRange() {
        float[] output = new float[4];
        PcmAudioMixer.mix(output, 4, 1, 4, 0, new float[] {1, 2, 3, 4}, 1, 4, -2, 1, 1);
        assertArrayEquals(new float[] {3, 4, 0, 0}, output);
    }

    @Test void blockBoundariesPreservePitchAndResamplingPhase() {
        float[] source = new float[120];
        for (int i = 0; i < source.length; i++) source[i] = (float) Math.sin(i * 0.13);
        float[] whole = new float[94];
        PcmAudioMixer.mix(whole, 47, 2, 48000, 0, source, 1, 44100, -3, 0.7f, 1.3f);
        float[] joined = new float[94];
        for (int offset = 0; offset < 47; offset += 7) {
            int count = Math.min(7, 47 - offset);
            float[] block = new float[count * 2];
            PcmAudioMixer.mix(block, count, 2, 48000, offset, source, 1, 44100, -3, 0.7f, 1.3f);
            System.arraycopy(block, 0, joined, offset * 2, block.length);
        }
        assertArrayEquals(whole, joined);
    }

    @Test void mixesStereoToMonoAndAddsOverlappingSounds() {
        float[] output = new float[4];
        float[] stereo = {1, 0, 0, 1};
        PcmAudioMixer.mix(output, 4, 1, 4, 0, stereo, 2, 4, 1, 1, 1);
        PcmAudioMixer.mix(output, 4, 1, 4, 0, stereo, 2, 4, 2, 0.5f, 1);
        assertArrayEquals(new float[] {0, 0.5f, 0.75f, 0.25f}, output);
    }

    @Test void handlesLateBlocksBeyondIntegerSampleOffsetsAndExpiredSounds() {
        long offset = (long) Integer.MAX_VALUE + 100;
        float[] output = new float[2];
        PcmAudioMixer.mix(output, 2, 1, 48000, offset, new float[] {0.25f, 0.75f},
                1, 48000, offset, 1, 1);
        assertArrayEquals(new float[] {0.25f, 0.75f}, output);
        Arrays.fill(output, 0);
        PcmAudioMixer.mix(output, 2, 1, 48000, offset, new float[] {1}, 1, 48000, 0, 1, 1);
        assertArrayEquals(new float[2], output);
    }
}
