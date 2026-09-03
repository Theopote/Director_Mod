package com.director.actor.animation.asset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Editable, sorted keyframe channel with deterministic sampling. */
public final class KeyframeTrack<T> {
    private static final float TIME_EPSILON = 1.0e-5f;
    private final ChannelValueAdapter<T> values;
    private final List<ChannelKeyframe<T>> keyframes = new ArrayList<>();

    public KeyframeTrack(ChannelValueAdapter<T> values) {
        this.values = Objects.requireNonNull(values, "values");
    }

    public void put(float time, T value, InterpolationMode interpolation) {
        ChannelKeyframe<T> inserted = copied(new ChannelKeyframe<>(time, value, interpolation));
        for (int i = 0; i < keyframes.size(); i++) {
            if (Math.abs(keyframes.get(i).time() - time) <= TIME_EPSILON) {
                keyframes.set(i, inserted);
                return;
            }
        }
        keyframes.add(inserted);
        keyframes.sort(Comparator.comparingDouble(ChannelKeyframe::time));
    }

    public boolean removeAt(float time) {
        return keyframes.removeIf(frame -> Math.abs(frame.time() - time) <= TIME_EPSILON);
    }

    public T sample(float time, T fallback) {
        if (keyframes.isEmpty()) return values.copy(fallback);
        if (time <= keyframes.getFirst().time()) return values.copy(keyframes.getFirst().value());
        if (time >= keyframes.getLast().time()) return values.copy(keyframes.getLast().value());

        int low = 0;
        int high = keyframes.size() - 1;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (keyframes.get(middle).time() <= time) low = middle; else high = middle;
        }
        ChannelKeyframe<T> from = keyframes.get(low);
        ChannelKeyframe<T> to = keyframes.get(high);
        if (from.interpolation() == InterpolationMode.STEP) return values.copy(from.value());
        float amount = (time - from.time()) / (to.time() - from.time());
        if (from.interpolation() == InterpolationMode.SMOOTH) amount = amount * amount * (3f - 2f * amount);
        return values.interpolate(from.value(), to.value(), amount);
    }

    public List<ChannelKeyframe<T>> keyframes() {
        List<ChannelKeyframe<T>> copy = new ArrayList<>(keyframes.size());
        for (ChannelKeyframe<T> frame : keyframes) copy.add(copied(frame));
        return Collections.unmodifiableList(copy);
    }

    public boolean isEmpty() { return keyframes.isEmpty(); }
    public int size() { return keyframes.size(); }

    private ChannelKeyframe<T> copied(ChannelKeyframe<T> frame) {
        return new ChannelKeyframe<>(frame.time(), values.copy(frame.value()), frame.interpolation());
    }
}
