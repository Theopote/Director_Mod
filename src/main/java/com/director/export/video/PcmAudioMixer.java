package com.director.export.video;

/** Block mixer; source positions use the absolute output clock to preserve resampling phase. */
final class PcmAudioMixer {
    private PcmAudioMixer() {}

    static void mix(float[] output, int frames, int channels, int sampleRate, long blockStart,
            float[] source, int sourceChannels, int sourceRate, long soundStart, float gain, float pitch) {
        int sourceFrames = source.length / sourceChannels;
        double step = (double) pitch * sourceRate / sampleRate;
        if (step <= 0 || sourceFrames == 0) return;
        int first = (int) Math.min(frames, Math.max(0L, soundStart - blockStart));
        for (int frame = first; frame < frames; frame++) {
            double position = (blockStart + frame - soundStart) * step;
            if (position >= sourceFrames) break;
            int index = (int) position;
            float fraction = (float) (position - index);
            float left = sample(source, sourceChannels, sourceFrames, index, fraction, 0);
            float right = sourceChannels >= 2
                    ? sample(source, sourceChannels, sourceFrames, index, fraction, 1) : left;
            int base = frame * channels;
            if (channels == 1) output[base] += (left + right) * 0.5f * gain;
            else {
                output[base] += left * gain;
                output[base + 1] += right * gain;
            }
        }
    }

    private static float sample(float[] source, int channels, int frames, int index, float fraction, int ch) {
        float current = source[index * channels + ch];
        if (index + 1 >= frames) return current;
        return current + (source[(index + 1) * channels + ch] - current) * fraction;
    }
}
