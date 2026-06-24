package com.director.timeline.playback.block;

import com.director.config.DirectorConfig;

/**
 * 时间线拖动播放头 / 松手追帧时的方块落地加速状态（供 {@link TimelineClipBlockController}
 * 与 {@link com.director.core.container.visibility.ContainerVisibilityService} 共享）。
 */
public final class TimelinePlacementBudget {

    private static volatile boolean scrubbing;
    private static volatile boolean instantWorld;
    private static volatile int catchUpFramesRemaining;

    private TimelinePlacementBudget() {
    }

    /**
     * @param timelineTimeChanged 本帧播放头时间相对上次静止预览发生变化（点击/拖动定位）
     */
    public static void beginFrame(boolean timelineScrubbing, boolean wasScrubbing, boolean timelineTimeChanged) {
        scrubbing = timelineScrubbing;
        if (wasScrubbing && !timelineScrubbing) {
            catchUpFramesRemaining = getCatchUpFrames();
        } else if (catchUpFramesRemaining > 0 && !timelineScrubbing) {
            catchUpFramesRemaining--;
        }
        instantWorld = timelineScrubbing || catchUpFramesRemaining > 0 || timelineTimeChanged;
    }

    public static boolean isScrubbing() {
        return scrubbing;
    }

    public static boolean isCatchUp() {
        return catchUpFramesRemaining > 0;
    }

    /** 拖动/追帧/定位：应跳过播放节流、提高落地预算。 */
    public static boolean isPlacementAccelerated() {
        return instantWorld;
    }

    /** @deprecated 使用 {@link #isPlacementAccelerated()} */
    @Deprecated
    public static boolean isAccelerated() {
        return isPlacementAccelerated();
    }

    public static int getPlaybackGlobalPerFrame() {
        return getConfigInt("timelineWorldPlacementsPerFrame", 128);
    }

    public static int getPlaybackPerContainerPerFrame() {
        return getConfigInt("timelineWorldPlacementsPerContainerPerFrame", 64);
    }

    public static int getScrubGlobalPerFrame() {
        return getConfigInt("timelineWorldPlacementsPerFrameScrubbing", 4096);
    }

    public static int getScrubPerContainerPerFrame() {
        return getConfigInt("timelineWorldPlacementsPerContainerPerFrameScrubbing", 2048);
    }

    public static int getCatchUpGlobalPerFrame() {
        return getConfigInt("timelineWorldPlacementsPerFrameCatchUp", 6144);
    }

    public static int getCatchUpPerContainerPerFrame() {
        return getConfigInt("timelineWorldPlacementsPerContainerPerFrameCatchUp", 3072);
    }

    public static int getVisibilityTickMultiplier() {
        if (scrubbing || instantWorld) {
            return getConfigInt("timelineVisibilityServerTickMultiplierScrubbing", 6);
        }
        if (catchUpFramesRemaining > 0) {
            return getConfigInt("timelineVisibilityServerTickMultiplierCatchUp", 8);
        }
        return getConfigInt("timelineVisibilityServerTickMultiplierPlayback", 1);
    }

    private static int getCatchUpFrames() {
        return getConfigInt("timelineScrubCatchUpFrames", 24);
    }

    private static int getConfigInt(String field, int defaultValue) {
        try {
            var perf = DirectorConfig.getConfig().performance;
            return switch (field) {
                case "timelineWorldPlacementsPerFrame" -> perf.timelineWorldPlacementsPerFrame;
                case "timelineWorldPlacementsPerContainerPerFrame" -> perf.timelineWorldPlacementsPerContainerPerFrame;
                case "timelineWorldPlacementsPerFrameScrubbing" -> perf.timelineWorldPlacementsPerFrameScrubbing;
                case "timelineWorldPlacementsPerContainerPerFrameScrubbing" -> perf.timelineWorldPlacementsPerContainerPerFrameScrubbing;
                case "timelineWorldPlacementsPerFrameCatchUp" -> perf.timelineWorldPlacementsPerFrameCatchUp;
                case "timelineWorldPlacementsPerContainerPerFrameCatchUp" -> perf.timelineWorldPlacementsPerContainerPerFrameCatchUp;
                case "timelineScrubCatchUpFrames" -> perf.timelineScrubCatchUpFrames;
                case "timelineVisibilityServerTickMultiplierScrubbing" -> perf.timelineVisibilityServerTickMultiplierScrubbing;
                case "timelineVisibilityServerTickMultiplierCatchUp" -> perf.timelineVisibilityServerTickMultiplierCatchUp;
                case "timelineVisibilityServerTickMultiplierPlayback" -> perf.timelineVisibilityServerTickMultiplierPlayback;
                default -> defaultValue;
            };
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    public static int getGlobalPerFrame() {
        if (scrubbing || instantWorld) {
            return getScrubGlobalPerFrame();
        }
        if (catchUpFramesRemaining > 0) {
            return getCatchUpGlobalPerFrame();
        }
        return getPlaybackGlobalPerFrame();
    }

    public static int getPerContainerPerFrame() {
        if (scrubbing || instantWorld) {
            return getScrubPerContainerPerFrame();
        }
        if (catchUpFramesRemaining > 0) {
            return getCatchUpPerContainerPerFrame();
        }
        return getPlaybackPerContainerPerFrame();
    }
}
