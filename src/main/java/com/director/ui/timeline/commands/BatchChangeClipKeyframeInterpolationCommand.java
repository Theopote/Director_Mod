package com.director.ui.timeline.commands;

import com.director.ui.timeline.model.TimelineClip;
import com.director.ui.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.TimelineModel;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.model.clip.ClipKeyframe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 批量修改 ClipKeyframe 插值方式（支持一次 Undo/Redo）
 *
 * <p>支持跨多个 Clip 的关键帧（通过扫描 model 查找 owner clip）。</p>
 */
public class BatchChangeClipKeyframeInterpolationCommand extends BaseTimelineCommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchChangeClipKeyframeInterpolationCommand.class);

    private static final class Entry {
        final TimelineClip clip;
        final UUID keyframeId;
        final TimelineInterpolation oldInterpolation;
        Entry(TimelineClip clip, UUID keyframeId, TimelineInterpolation oldInterpolation) {
            this.clip = clip;
            this.keyframeId = keyframeId;
            this.oldInterpolation = oldInterpolation;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private final TimelineInterpolation newInterpolation;

    public BatchChangeClipKeyframeInterpolationCommand(TimelineModel model,
                                                       List<ClipKeyframe> keyframes,
                                                       TimelineInterpolation newInterpolation) {
        super(model, "clip_keyframe_interpolation_changed_batch");
        this.newInterpolation = newInterpolation;

        if (model == null || keyframes == null) {
            return;
        }

        for (ClipKeyframe kf : keyframes) {
            if (kf == null) continue;
            Owner owner = findOwner(model, kf);
            if (owner == null) continue;
            TimelineInterpolation old = kf.getInterpolation();
            entries.add(new Entry(owner.clip, kf.getId(), old));
        }
    }

    @Override
    protected void doExecute() {
        apply(newInterpolation);
        LOGGER.debug("[Timeline] 批量修改关键帧插值 -> {}", newInterpolation);
    }

    @Override
    protected void doUndo() {
        for (Entry e : entries) {
            ClipKeyframe kf = findKeyframe(e.clip, e.keyframeId);
            if (kf != null) {
                kf.setInterpolation(e.oldInterpolation);
            }
        }
        LOGGER.debug("[Timeline] 撤销批量修改关键帧插值");
    }

    private void apply(TimelineInterpolation interpolation) {
        for (Entry e : entries) {
            ClipKeyframe kf = findKeyframe(e.clip, e.keyframeId);
            if (kf != null) {
                kf.setInterpolation(interpolation);
            }
        }
    }

    private static ClipKeyframe findKeyframe(TimelineClip clip, UUID id) {
        if (clip == null || id == null) return null;
        for (ClipKeyframe kf : clip.getClipKeyframes()) {
            if (kf != null && id.equals(kf.getId())) return kf;
        }
        return null;
    }

    private record Owner(TimelineTrack track, TimelineClip clip) {}

    private static Owner findOwner(TimelineModel model, ClipKeyframe keyframe) {
        if (model == null || keyframe == null) return null;
        for (TimelineTrack t : model.getTracks()) {
            if (t == null) continue;
            for (TimelineClip c : t.getClips()) {
                if (c == null) continue;
                if (c.getClipKeyframes().contains(keyframe)) {
                    return new Owner(t, c);
                }
            }
        }
        return null;
    }

    @Override
    public String getDescription() {
        return "修改插值（批量）";
    }
}


