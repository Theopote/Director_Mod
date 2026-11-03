package com.director.ui.actor.pose;

import com.director.actor.preview.ActorPosePreview;
import com.director.actor.preview.ActorPreviewManager;
import com.director.actor.rig.skeleton.RigPose;
import com.director.timeline.model.TimelineModel;
import com.director.ui.actor.selection.ActorSelectionHub;
import java.util.ArrayList;
import java.util.Comparator;

/** Keyboard authoring shares the inspector's checked, undoable key commands. */
public final class ActorPoseKeyActions {
    private ActorPoseKeyActions() {}

    private static boolean available(TimelineModel model) {
        var selection=ActorBoneSelectionController.getInstance();
        return model!=null && !model.isPlaying() && !model.isScrubbing() && selection.isActive()
                && selection.getActorId()!=null && selection.getActorId().equals(ActorSelectionHub.getSelectedActorId())
                && !selection.getSelectedBoneIds().isEmpty()
                && !ActorBonePropertiesPanel.hasNumericDraft()
                && !ActorBoneGizmoController.getInstance().hasPendingDraft()
                && !ActorBoneGizmoController.getInstance().isDragging()
                && !com.director.ui.actor.gizmo.ActorGizmoController.getInstance().isDragging()
                && !ActorAnimationDopeSheet.isKeyInteractionActive()
                && !com.director.export.video.VideoExportManager.getInstance().isExporting();
    }

    public static boolean keySelected(TimelineModel model) {
        if(!available(model))return false;
        var selection=ActorBoneSelectionController.getInstance();
        var mode=ActorAnimationAuthoringSettings.getInstance().getInterpolation();
        var context=ActorAnimationEditContext.resolve(model,selection.getActorId());
        if(context!=null) return ActorEmbeddedAnimation.keyFromPreview(context,selection.getSelectedBoneIds(),mode,RigPose.ALL_CHANNELS);
        var owner=ActorAnimationAssetEditing.resolveActorTrack(model,selection.getActorId());
        var entity=ActorPreviewManager.getInstance().getPreview(selection.getActorId());
        var visible=entity instanceof ActorPosePreview preview?preview.getFinalRigPose():null;
        return ActorEmbeddedAnimation.firstKeys(model,owner,selection.getSelectedBoneIds(),RigPose.ALL_CHANNELS,mode,visible);
    }

    public static boolean seekSelected(TimelineModel model,boolean forward) {
        if(!available(model))return false;
        var selection=ActorBoneSelectionController.getInstance();
        var context=ActorAnimationEditContext.resolve(model,selection.getActorId());
        if(context==null || !context.isCurrent())return false;
        var times=new ArrayList<Float>();
        var clock=ActorAnimationKeyClock.projection(context.track(),context.clip(),context.start(),context.end());
        for(String bone:selection.getSelectedBoneIds()) context.asset().findBoneTrack(bone).ifPresent(track->{
            for(var channel:java.util.List.of(track.translation(),track.rotation(),track.scale()))
                for(var key:channel.keyframes()) times.addAll(clock.times(key.time()));
        });
        return seek(model,times,forward);
    }

    static boolean seek(TimelineModel model,java.util.List<Float> times,boolean forward) {
        float current=model.getCurrentTime();
        var target=times.stream().filter(Float::isFinite).filter(t->forward?t>current+1e-5f:t<current-1e-5f)
                .min(forward?Comparator.naturalOrder():Comparator.reverseOrder());
        target.ifPresent(model::setCurrentTime);
        return target.isPresent();
    }
}
