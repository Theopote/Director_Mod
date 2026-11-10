package com.director.ui.properties;

import com.director.i18n.CameraPropertyAdapterI18n;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang.Properties;
import com.director.i18n.DirectorLang.Properties.AdapterCamera.Keyframe;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.entity.EntityTimelineClip;
import com.director.timeline.model.selection.TimelineKeyframeMultiSelection;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.commands.BatchChangeCameraKeyframeKeyTypeCommand;
import com.director.ui.timeline.commands.BatchChangeCameraKeyframeRotationInterpolationCommand;
import com.director.ui.timeline.commands.BatchChangeCameraKeyframeSegmentTypeCommand;
import com.director.ui.timeline.commands.TimelineCommand;
import com.director.ui.timeline.commands.TimelineCommandBridge;
import imgui.ImGui;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Reads the timeline's actual selection and commits one undoable command for each edited field. */
public final class TimelineSelectionProperties {
    private static final String MIXED = "—";

    private TimelineSelectionProperties() {}

    public static boolean render(TimelineModel model, Object target) {
        if (model == null || model.getSelectedCount() < 2 || target == null) {
            return false;
        }
        List<Object> selected = model.getSelectedItems();
        if (selected.stream().noneMatch(item -> represents(item, target))) {
            return false;
        }

        UITheme.title(DirectorI18n.translate(Properties.MULTI_SELECT, selected.size()));
        if (selected.stream().allMatch(item -> item instanceof TimelineSelectable || item instanceof TimelineClip)) {
            PropertyRow.value(DirectorI18n.translate(Properties.Overview.SELECTION_NAME),
                    commonValue(selected, item -> item instanceof TimelineSelectable timed ? timed.getName()
                            : ((TimelineClip) item).getLabel(), Function.identity()));
            PropertyRow.value(DirectorI18n.translate(Properties.Overview.SELECTION_START),
                    commonValue(selected, item -> item instanceof TimelineSelectable timed ? timed.getStartTime()
                            : ((TimelineClip) item).getStartTime(), TimelineSelectionProperties::seconds));
            PropertyRow.value(DirectorI18n.translate(Properties.Overview.SELECTION_END),
                    commonValue(selected, item -> item instanceof TimelineSelectable timed ? timed.getEndTime()
                            : ((TimelineClip) item).getEndTime(), TimelineSelectionProperties::seconds));
        } else if (selected.stream().allMatch(TimelineTrack.class::isInstance)) {
            PropertyRow.value(DirectorI18n.translate(Properties.Overview.SELECTION_NAME),
                    commonValue(selected.stream().map(TimelineTrack.class::cast).toList(),
                            TimelineTrack::getName, Function.identity()));
        }

        if (selected.stream().allMatch(CameraKeyframe.class::isInstance)) {
            renderCameraFields(model, selected.stream().map(CameraKeyframe.class::cast).toList());
        } else {
            ImGui.textWrapped(DirectorI18n.translate(Properties.Overview.NO_BATCH_FIELDS));
        }
        return true;
    }

    private static boolean represents(Object item, Object target) {
        if (target instanceof TimelineKeyframeMultiSelection keys) {
            return keys.getClipKeyframes().contains(item) || keys.getAutomationKeyframes().contains(item);
        }
        return item == target || item instanceof EntityTimelineClip wrapper && wrapper.getEntityClip() == target;
    }

    private static void renderCameraFields(TimelineModel model, List<CameraKeyframe> frames) {
        boolean editable = !model.isPlaying() && !model.isScrubbing() && frames.stream().allMatch(frame -> {
            var track = model.findTrackForKeyframe(frame);
            return track != null && !track.isLocked();
        });
        ImGui.beginDisabled(!editable);
        try {
            enumField("keyType", Keyframe.KEY_TYPE, frames, CameraKeyframe::getKeyType,
                    CameraKeyframe.KeyType.values(), CameraPropertyAdapterI18n.keyframeTypeLabels(),
                    value -> new BatchChangeCameraKeyframeKeyTypeCommand(model, frames, value));
            enumField("positionCurve", Keyframe.POSITION_CURVE, frames, CameraKeyframe::getSegmentPathType,
                    CameraKeyframe.SegmentPathType.values(), CameraPropertyAdapterI18n.segmentPathTypeLabels(),
                    value -> new BatchChangeCameraKeyframeSegmentTypeCommand(model, frames, value));
            enumField("rotationCurve", Keyframe.ROTATION_CURVE, frames, CameraKeyframe::getRotationInterpolation,
                    CameraKeyframe.RotationInterpolation.values(), CameraPropertyAdapterI18n.rotationInterpolationLabels(),
                    value -> new BatchChangeCameraKeyframeRotationInterpolationCommand(model, frames, value));
        } finally {
            ImGui.endDisabled();
        }
        if (!editable) {
            ImGui.textWrapped(DirectorI18n.translate(Properties.Overview.BATCH_READ_ONLY));
        }
    }

    private static <E extends Enum<E>> void enumField(String id, String label, List<CameraKeyframe> frames,
                                                    Function<CameraKeyframe, E> read, E[] values, String[] labels,
                                                    Function<E, TimelineCommand> command) {
        E current = common(frames, read);
        PropertyRow.field(CameraPropertyAdapterI18n.tr(label));
        if (ImGui.beginCombo("##" + id, current == null ? MIXED : labels[current.ordinal()])) {
            try {
                for (E value : values) {
                    if (ImGui.selectable(labels[value.ordinal()], value == current) && value != current) {
                        TimelineCommandBridge.execute(command.apply(value));
                    }
                    if (value == current) {
                        ImGui.setItemDefaultFocus();
                    }
                }
            } finally {
                ImGui.endCombo();
            }
        }
    }

    private static <T, V> String commonValue(List<T> items, Function<T, V> read, Function<V, String> format) {
        V value = common(items, read);
        return value == null ? MIXED : format.apply(value);
    }

    private static <T, V> V common(List<T> items, Function<T, V> read) {
        V first = read.apply(items.getFirst());
        return items.stream().allMatch(item -> Objects.equals(first, read.apply(item))) ? first : null;
    }

    private static String seconds(float value) {
        return DirectorI18n.translate(Properties.Overview.SECONDS, value);
    }
}
