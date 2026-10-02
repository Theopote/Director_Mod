package com.director.ui.actor.edit;
import com.director.timeline.model.*;
import com.director.ui.actor.pose.*;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.client.gizmo.GizmoMode;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ActorGestureContextTest {
    TimelineModel model=new TimelineModel(new TimelineViewportState());
    ActorBoneSelectionController selection=ActorBoneSelectionController.getInstance();
    ActorAnimationAuthoringSettings settings=ActorAnimationAuthoringSettings.getInstance();
    @BeforeEach void setup() { selection.activate(UUID.randomUUID()); settings.setBoneGizmoMode(GizmoMode.ROTATE); settings.setEditTarget(ActorAnimationAuthoringSettings.EditTarget.ANIMATION); }
    @AfterEach void clean() { selection.deactivate(); settings.setBoneGizmoMode(GizmoMode.ROTATE); settings.setEditTarget(ActorAnimationAuthoringSettings.EditTarget.ANIMATION); }
    ActorGestureContext capture() { return new ActorGestureContext(model,selection::getRevision,settings::getRevision); }
    @Test void boneAwayAndBackDoesNotReviveDraft() {
        var start=selection.getSelectedBoneId(); var context=capture();
        selection.selectBone(HumanoidRig.HEAD); selection.selectBone(start); assertFalse(context.isCurrent(model));
    }
    @Test void actorAwayAndBackDoesNotReviveDraft() {
        var actor=selection.getActorId(); var context=capture(); selection.synchronizeActor(UUID.randomUUID()); selection.synchronizeActor(actor);
        assertFalse(context.isCurrent(model));
    }
    @Test void additiveSelectionChangesCancelEvenIfActiveBoneRemainsSame() {
        selection.selectBone(HumanoidRig.HEAD,true); var context=capture();
        selection.selectBone(HumanoidRig.BODY,true); assertEquals(HumanoidRig.HEAD,selection.getSelectedBoneId());
        assertFalse(context.isCurrent(model));
    }
    @Test void modeAndTargetRoundTripsCancelDraft() {
        var context=capture(); settings.setBoneGizmoMode(GizmoMode.SCALE); settings.setBoneGizmoMode(GizmoMode.ROTATE); assertFalse(context.isCurrent(model));
        context=capture(); settings.setEditTarget(ActorAnimationAuthoringSettings.EditTarget.QUICK_POSE);
        settings.setEditTarget(ActorAnimationAuthoringSettings.EditTarget.ANIMATION); assertFalse(context.isCurrent(model));
    }
    @Test void hoverAndSameValueUpdatesLeaveDraftValid() {
        var context=capture(); selection.setHoveredBone(HumanoidRig.HEAD); selection.selectBone(selection.getSelectedBoneId());
        settings.setBoneGizmoMode(GizmoMode.ROTATE); settings.setEditTarget(settings.getEditTarget()); settings.setAssetId(settings.getAssetId());
        settings.setInterpolation(settings.getInterpolation()); settings.setNewAssetDuration(settings.getNewAssetDuration());
        settings.setAutoKeyframe(settings.isAutoKeyframe()); model.setCurrentTime(model.getCurrentTime()); assertTrue(context.isCurrent(model));
    }
    @Test void playbackTimeScrubAndProjectRoundTripsCannotReviveDraft() {
        var context=capture(); model.setPlaying(true); model.setPlaying(false); assertFalse(context.isCurrent(model));
        context=capture(); model.setCurrentTime(1); model.setCurrentTime(0); assertFalse(context.isCurrent(model));
        context=capture(); model.setScrubbing(true); model.setScrubbing(false); assertFalse(context.isCurrent(model));
        assertFalse(capture().isCurrent(new TimelineModel(new TimelineViewportState())));
    }
}
