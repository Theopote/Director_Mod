package com.director.export.video;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ScheduledAudioMixerTest {
    @Test void futureEventsAreNotDecodedDuringSilentEarlierBlocks() throws Exception {
        var loads = new AtomicInteger();
        var mixer = new ScheduledAudioMixer(List.of(new ScheduledAudioMixer.Event(8, () -> {
            loads.incrementAndGet();
            return new ScheduledAudioMixer.Sound(new float[] {1, 2}, 1, 4, 1, 1);
        })));
        float[] output = new float[4];
        mixer.mix(output, 4, 1, 4, 0, () -> {});
        mixer.mix(output, 4, 1, 4, 4, () -> {});
        assertEquals(0, loads.get());
        mixer.mix(output, 4, 1, 4, 8, () -> {});
        assertEquals(1, loads.get());
        assertArrayEquals(new float[] {1, 2, 0, 0}, output);
        mixer.mix(output, 4, 1, 4, 12, () -> {});
        assertEquals(1, loads.get());
        assertArrayEquals(new float[4], output);
    }

    @Test void lazyScheduledMixMatchesFullMixAcrossBlocksAndNegativeStarts() throws Exception {
        float[] source = new float[240];
        for (int i = 0; i < source.length; i++) source[i] = (float) Math.sin(i * 0.21);
        long[] starts = {25, -3, 7, 63, 90}; // Deliberately unsorted.
        float[] expected = new float[200];
        List<ScheduledAudioMixer.Event> events = new ArrayList<>();
        for (long start : starts) {
            PcmAudioMixer.mix(expected, 100, 2, 48000, 0, source, 2, 44100, start, 0.5f, 1.3f);
            events.add(new ScheduledAudioMixer.Event(start,
                    () -> new ScheduledAudioMixer.Sound(source, 2, 44100, 0.5f, 1.3f)));
        }
        var mixer = new ScheduledAudioMixer(events);
        float[] actual = new float[200];
        for (int offset = 0; offset < 100; offset += 7) {
            int frames = Math.min(7, 100 - offset);
            float[] block = new float[frames * 2];
            mixer.mix(block, frames, 2, 48000, offset, () -> {});
            System.arraycopy(block, 0, actual, offset * 2, block.length);
        }
        assertArrayEquals(expected, actual, 0.000001f);
    }

    @Test void expiredEventsDoNotContributeAndUnavailableSoundsDoNotBlockOtherEvents() throws Exception {
        var mixer = new ScheduledAudioMixer(List.of(
                new ScheduledAudioMixer.Event(-20, () -> new ScheduledAudioMixer.Sound(new float[] {9}, 1, 4, 1, 1)),
                new ScheduledAudioMixer.Event(0, () -> null),
                new ScheduledAudioMixer.Event(0, () -> new ScheduledAudioMixer.Sound(new float[] {1}, 1, 4, 1, 1))));
        float[] output = new float[4];
        mixer.mix(output, 4, 1, 4, 0, () -> {});
        assertArrayEquals(new float[] {1, 0, 0, 0}, output);
    }

    @Test void cancellationStopsBeforeDecodingNextEvent() {
        var mixer = new ScheduledAudioMixer(List.of(new ScheduledAudioMixer.Event(0,
                () -> { throw new AssertionError("decoded after cancellation"); })));
        assertThrows(CancellationException.class, () -> mixer.mix(new float[4], 4, 1, 4, 0,
                () -> { throw new CancellationException(); }));
    }
}
