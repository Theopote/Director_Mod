package com.director.timeline.layout;

import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.path.CameraPathClip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 时间线轨道片段布局：保证同轨道片段不重叠，拖动时自动就近插入到相邻片段前/后。
 */
public final class TimelineClipLayoutResolver {

    private static final float EPS = 0.001f;

    private TimelineClipLayoutResolver() {
    }

    public record Interval(float start, float end) {
        public boolean overlaps(float start, float end) {
            return end > this.start + EPS && start < this.end - EPS;
        }
    }

    public static List<Interval> clipIntervalsOnTrack(TimelineTrack track, TimelineClip exclude) {
        List<Interval> out = new ArrayList<>();
        if (track == null) {
            return out;
        }
        for (TimelineClip clip : track.getClips()) {
            if (clip == null || clip == exclude) {
                continue;
            }
            out.add(new Interval(clip.getStartTime(), clip.getEndTime()));
        }
        out.sort(Comparator.comparingDouble(Interval::start));
        return out;
    }

    public static List<Interval> pathClipIntervalsOnTrack(TimelineTrack track, CameraPathClip exclude) {
        List<Interval> out = new ArrayList<>();
        if (track == null) {
            return out;
        }
        for (CameraPathClip clip : track.getCameraPathClips()) {
            if (clip == null || clip == exclude || !clip.isEnabled()) {
                continue;
            }
            out.add(new Interval(clip.getStartTime(), clip.getEndTime()));
        }
        out.sort(Comparator.comparingDouble(Interval::start));
        return out;
    }

    /**
     * 将候选起点解析为不与其他片段重叠的最近合法位置（优先贴在阻挡片段前或后）。
     */
    public static float resolveNearestStart(
            List<Interval> occupied,
            float candidateStart,
            float length,
            float minStart) {
        if (length <= 0f) {
            return Math.max(minStart, candidateStart);
        }

        float desired = Math.max(minStart, candidateStart);
        List<Interval> sorted = new ArrayList<>(occupied);
        sorted.sort(Comparator.comparingDouble(Interval::start));
        float gapStart = minStart;
        float best = Float.NaN;
        float distance = Float.POSITIVE_INFINITY;
        for (Interval interval : sorted) {
            if (interval.end <= gapStart) continue;
            float latest = interval.start - length;
            if (latest >= gapStart) {
                float candidate = Math.clamp(desired, gapStart, latest);
                float delta = Math.abs(candidate - desired);
                if (delta < distance) { best = candidate; distance = delta; }
            }
            gapStart = Math.max(gapStart, interval.end);
        }
        float tail = Math.max(desired, gapStart);
        return Math.abs(tail - desired) < distance ? tail : best;
    }

    /**
     * 在指定位置插入片段时，将之后的片段整体后移以腾出空间（挤开）。
     */
    public static void rippleTimelineClipsForward(
            TimelineTrack track,
            float insertStart,
            float length,
            TimelineClip moving) {
        if (track == null || length <= 0f) {
            return;
        }
        float insertEnd = insertStart + length;
        List<TimelineClip> sorted = new ArrayList<>(track.getClips());
        sorted.sort(Comparator.comparingDouble(TimelineClip::getStartTime));
        for (TimelineClip clip : sorted) {
            if (clip == null || clip == moving) {
                continue;
            }
            float clipStart = clip.getStartTime();
            float clipEnd = clip.getEndTime();
            if (clipStart >= insertStart - EPS) {
                clip.offsetTime(length);
            } else if (clipEnd > insertStart + EPS && clipStart < insertEnd - EPS) {
                float delta = insertEnd - clipStart;
                clip.offsetTime(delta);
                insertEnd = Math.max(insertEnd, clip.getEndTime());
            }
        }
    }

    /**
     * Path Clip 挤开：将插入点之后的片段整体后移。
     */
    public static void ripplePathClipsForward(
            TimelineTrack track,
            float insertStart,
            float length,
            CameraPathClip moving) {
        if (track == null || length <= 0f) {
            return;
        }
        float insertEnd = insertStart + length;
        List<CameraPathClip> sorted = new ArrayList<>(track.getCameraPathClips());
        sorted.sort(Comparator.comparingDouble(CameraPathClip::getStartTime));
        for (CameraPathClip clip : sorted) {
            if (clip == null || clip == moving || !clip.isEnabled()) {
                continue;
            }
            float clipStart = clip.getStartTime();
            float clipEnd = clip.getEndTime();
            if (clipStart >= insertStart - EPS) {
                clip.offsetTime(length);
            } else if (clipEnd > insertStart + EPS && clipStart < insertEnd - EPS) {
                float delta = insertEnd - clipStart;
                clip.offsetTime(delta);
                insertEnd = Math.max(insertEnd, clip.getEndTime());
            }
        }
    }

    /**
     * 拖动释放：若候选位置与阻挡片段重叠面积较大，则挤开阻挡片段；否则就近插入。
     */
    public static float resolveStartForDrag(
            List<Interval> occupied,
            float candidateStart,
            float length,
            float minStart) {
        float nearest = resolveNearestStart(occupied, candidateStart, length, minStart);
        if (!intervalOverlapsAny(occupied, candidateStart, candidateStart + length)) {
            return Math.max(minStart, candidateStart);
        }

        Interval blocker = findOverlapping(occupied, candidateStart, candidateStart + length);
        if (blocker == null) {
            return nearest;
        }

        float overlap = Math.min(candidateStart + length, blocker.end)
                - Math.max(candidateStart, blocker.start);
        float overlapRatio = overlap / Math.max(length, EPS);
        if (overlapRatio > 0.35f) {
            return Math.max(minStart, candidateStart);
        }
        return nearest;
    }

    public static float clampResizeStart(
            List<Interval> occupied,
            float newStart,
            float currentEnd,
            float minLength,
            float minStart) {
        float maxStart = currentEnd - minLength;
        float clamped = Math.min(Math.max(minStart, newStart), maxStart);
        float floor = minStart;
        for (Interval other : occupied) {
            if (other.end <= currentEnd - minLength + EPS
                    && other.end > floor + EPS
                    && other.start < currentEnd - EPS) {
                floor = Math.max(floor, other.end);
            }
        }
        return Math.max(floor, clamped);
    }

    public static float clampResizeEnd(
            List<Interval> occupied,
            float currentStart,
            float newEnd,
            float minLength) {
        float minEnd = currentStart + minLength;
        float clamped = Math.max(minEnd, newEnd);
        float ceiling = Float.MAX_VALUE;
        for (Interval other : occupied) {
            if (other.start >= currentStart + minLength - EPS
                    && other.start < ceiling
                    && other.end > currentStart + EPS) {
                ceiling = Math.min(ceiling, other.start);
            }
        }
        if (Float.isFinite(ceiling)) {
            return Math.min(clamped, ceiling);
        }
        return clamped;
    }

    private static boolean intervalOverlapsAny(List<Interval> occupied, float start, float end) {
        return findOverlapping(occupied, start, end) != null;
    }

    public static boolean overlapsAny(List<Interval> occupied, float start, float end) {
        return intervalOverlapsAny(occupied, start, end);
    }

    private static Interval findOverlapping(List<Interval> occupied, float start, float end) {
        for (Interval interval : occupied) {
            if (interval.overlaps(start, end)) {
                return interval;
            }
        }
        return null;
    }
}
