package com.director.timeline.playback;

import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import org.slf4j.Logger;

/**
 * 轨道级自动化关键帧添加时的通用校验（锁定 / 激活轨道）。
 */
public final class TrackKeyframeEditSupport {

    private TrackKeyframeEditSupport() {
    }

    public static boolean validateAddTarget(
            TimelineTrack track,
            TimelineTrack activeTrack,
            TrackType expectedType,
            Logger logger,
            String logTag
    ) {
        if (track == null || track.getType() != expectedType) {
            return false;
        }
        if (track.isLocked()) {
            logger.warn("[{}] 轨道已锁定，无法添加关键帧 (轨道: {})", logTag, track.getName());
            return false;
        }
        if (activeTrack != track) {
            logger.warn(
                    "[{}] 只能在激活的{}轨道上添加关键帧，当前轨道：{}，激活轨道：{}",
                    logTag,
                    activeTrackTypeLabel(expectedType),
                    track.getName(),
                    activeTrack != null ? activeTrack.getName() : "null"
            );
            return false;
        }
        return true;
    }

    private static String activeTrackTypeLabel(TrackType type) {
        return switch (type) {
            case CAMERA -> "摄像机";
            case WEATHER -> "天气";
            case TIME -> "时间";
            case ACTOR -> "演员";
            case SEASON -> "季节";
            default -> type.name();
        };
    }
}
