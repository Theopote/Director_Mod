package com.director.export.video;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Activates sounds as their blocks arrive, retaining only overlapping decoded events. */
final class ScheduledAudioMixer {
    record Sound(float[] samples, int channels, int sampleRate, float gain, float pitch) {}
    @FunctionalInterface interface Loader { Sound load() throws IOException; }
    record Event(long startFrame, Loader loader) {}
    private record Active(long start, long end, Sound sound) {}
    private final List<Event> events;
    private final List<Active> active = new ArrayList<>();
    private int next;
    private long previousEnd;

    ScheduledAudioMixer(List<Event> events) {
        this.events = events.stream().sorted(Comparator.comparingLong(Event::startFrame)).toList();
    }

    void mix(float[] output, int frames, int channels, int sampleRate, long blockStart, Runnable checkCanceled)
            throws IOException {
        if (blockStart < previousEnd || frames < 0) throw new IllegalArgumentException("音频块必须按时间顺序混音");
        long blockEnd = Math.addExact(blockStart, frames);
        java.util.Arrays.fill(output, 0f);
        active.removeIf(sound -> sound.end() <= blockStart);
        while (next < events.size() && events.get(next).startFrame() < blockEnd) {
            checkCanceled.run();
            Event event = events.get(next++);
            Sound sound = event.loader().load();
            if (sound == null || sound.samples().length == 0 || sound.gain() <= 0) continue;
            double step = (double) sound.pitch() * sound.sampleRate() / sampleRate;
            long duration = (long) Math.ceil((sound.samples().length / sound.channels()) / step);
            long end = Math.addExact(event.startFrame(), duration);
            if (end > blockStart) active.add(new Active(event.startFrame(), end, sound));
        }
        for (Active clip : active) {
            checkCanceled.run();
            Sound sound = clip.sound();
            PcmAudioMixer.mix(output, frames, channels, sampleRate, blockStart,
                    sound.samples(), sound.channels(), sound.sampleRate(), clip.start(), sound.gain(), sound.pitch());
        }
        active.removeIf(sound -> sound.end() <= blockEnd);
        previousEnd = blockEnd;
    }
}
