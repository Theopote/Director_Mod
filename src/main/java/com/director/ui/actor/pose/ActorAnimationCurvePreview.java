package com.director.ui.actor.pose;

import com.director.actor.animation.asset.InterpolationMode;
import com.director.actor.animation.asset.KeyframeTrack;
import com.director.ui.timeline.render.TimelineColors;
import imgui.ImGui;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;

/** Read-only graph of one authored channel in asset time. */
final class ActorAnimationCurvePreview {
    private ActorAnimationCurvePreview() {}

    static List<Float> sampleTimes(KeyframeTrack<?> track, float start, float end) {
        var times = new TreeSet<Float>();
        for (int i = 0; i <= 96; i++) times.add(start + (end - start) * (i / 96f));
        InterpolationMode previous = null;
        for (var key : track.keyframes()) {
            float time = key.time();
            if (time > end) break;
            if (time >= start) {
                times.add(time);
                if (previous == InterpolationMode.STEP && time > start) times.add(Math.nextDown(time));
            }
            previous = key.interpolation();
        }
        return List.copyOf(times);
    }

    static void render(ActorAnimationDopeSheet.Row row, float start, float duration, float playhead) {
        switch (row.channel()) {
            case TRANSLATION -> plot(row.track().translation(), new Vector3f(), ActorAnimationCurvePreview::components, start, duration, playhead);
            case SCALE -> plot(row.track().scale(), new Vector3f(1), ActorAnimationCurvePreview::components, start, duration, playhead);
            case ROTATION -> plot(row.track().rotation(), new Quaternionf(), q -> new float[]{q.x, q.y, q.z, q.w},
                    start, duration, playhead);
        }
    }

    private static float[] components(Vector3f value) {
        return new float[]{value.x, value.y, value.z};
    }

    private static <T> void plot(KeyframeTrack<T> track, T fallback, Function<T, float[]> components,
                                 float start, float duration, float playhead) {
        var times = sampleTimes(track, start, start + duration);
        var values = new float[times.size()][];
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < times.size(); i++) {
            float[] sample = components.apply(track.sample(times.get(i), fallback));
            if (sample.length == 4 && i > 0) {
                // Equivalent quaternions q and -q should not introduce a sign jump in the graph.
                float dot = 0f;
                for (int axis = 0; axis < 4; axis++) dot += sample[axis] * values[i - 1][axis];
                if (dot < 0) for (int axis = 0; axis < 4; axis++) sample[axis] = -sample[axis];
            }
            values[i] = sample;
            for (float value : sample) {
                low = Math.min(low, value);
                high = Math.max(high, value);
            }
        }
        if (!Double.isFinite(low) || !Double.isFinite(high)) return;
        if (low == high) {
            double padding = Math.max(.5, Math.abs(low) * .05);
            low -= padding;
            high += padding;
        }

        float width = Math.max(1f, ImGui.getContentRegionAvailX());
        float height = 140f;
        float x = ImGui.getCursorScreenPosX(), y = ImGui.getCursorScreenPosY();
        ImGui.dummy(width, height);
        var draw = ImGui.getWindowDrawList();
        int[] colors = {TimelineColors.rgba(255, 125, 125, 255), TimelineColors.rgba(130, 230, 150, 255),
                TimelineColors.rgba(115, 185, 255, 255), TimelineColors.rgba(230, 175, 255, 255)};
        draw.addRectFilled(x, y, x + width, y + height, TimelineColors.rgba(24, 27, 32, 255), 3f);
        float top = y + 24f, bottom = y + height - 20f;
        for (int axis = 0; axis < values[0].length; axis++) {
            draw.addText(x + 8 + axis * 18, y + 4, colors[axis], "XYZW".substring(axis, axis + 1));
            for (int i = 1; i < times.size(); i++) {
                float fromX = x + (times.get(i - 1) - start) / duration * width;
                float toX = x + (times.get(i) - start) / duration * width;
                float fromY = bottom - (float) ((values[i - 1][axis] - low) / (high - low)) * (bottom - top);
                float toY = bottom - (float) ((values[i][axis] - low) / (high - low)) * (bottom - top);
                draw.addLine(fromX, fromY, toX, toY, colors[axis], 1.5f);
            }
        }
        float playheadX = x + (playhead - start) / duration * width;
        if (playheadX >= x && playheadX <= x + width)
            draw.addLine(playheadX, top, playheadX, bottom, TimelineColors.rgba(255, 190, 70, 255), 1f);
        draw.addText(x + 8, bottom + 3, TimelineColors.COLOR_TEXT_LIGHT, String.format("%.2f–%.2fs", start, start + duration));
        ImGui.textDisabled(String.format("%.3g … %.3g", low, high));
    }
}
