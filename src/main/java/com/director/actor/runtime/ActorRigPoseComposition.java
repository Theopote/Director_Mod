package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import java.util.List;
import java.util.function.Consumer;

/** Procedural movement fills unkeyed channels; authored bone channels have priority. */
final class ActorRigPoseComposition {
    private ActorRigPoseComposition() {}
    /** Lower preset is composed before manual channels, using its own clock and envelope. */
    static RigPose prependPreset(RigPose base,com.director.timeline.playback.entity.ActorAnimationLayerSample preset,
            Consumer<RigPoseComposer.Diagnostic> diagnostics,java.util.Map<String,Float> weights) {
        return prependPreset(base,preset,diagnostics,weights,null);
    }
    static RigPose prependPreset(RigPose base,com.director.timeline.playback.entity.ActorAnimationLayerSample preset,
            Consumer<RigPoseComposer.Diagnostic> diagnostics,java.util.Map<String,Float> weights,
            com.director.ui.actor.pose.ActorBoneAnimationDraft draft) {
        if(preset==null) return base;
        var pose=draft==null?preset.sample(base.rig()):draft.authoredPose();
        if(pose==null) return base;
        var result=RigPoseComposer.compose(base,List.of(preset.settings().layer(pose,preset.envelope())));
        result.diagnostics().forEach(diagnostics);
        if(result.diagnostics().isEmpty()) for(int i=0;i<pose.rig().boneCount();i++)
            if(preset.settings().contributes(pose,i,preset.envelope()))
                weights.merge(pose.rig().bone(i).id(),preset.settings().effectiveWeight(preset.envelope()),Math::max);
        return result.pose();
    }
    static RigPose compose(RigPose procedural,RigPose authored,Consumer<RigPose> quickPose) {
        return compose(procedural,authored,quickPose,diagnostic->{
            throw new IllegalArgumentException(diagnostic.reason());
        });
    }
    static RigPose compose(RigPose procedural,RigPose authored,Consumer<RigPose> quickPose,
                           Consumer<RigPoseComposer.Diagnostic> diagnostics) {
        return compose(procedural,authored,quickPose,diagnostics,PoseBlendSettings.DEFAULT);
    }
    static RigPose compose(RigPose procedural,RigPose authored,Consumer<RigPose> quickPose,
                           Consumer<RigPoseComposer.Diagnostic> diagnostics,PoseBlendSettings settings) {
        return compose(procedural,authored,quickPose,diagnostics,settings,1);
    }
    static RigPose compose(RigPose procedural,RigPose authored,Consumer<RigPose> quickPose,
                           Consumer<RigPoseComposer.Diagnostic> diagnostics,PoseBlendSettings settings,float envelope) {
        RigPose base=procedural==null?new RigPose(authored.rig()):procedural.copy();
        if(procedural==null) base.setLimbBendMode(authored.limbBendMode());
        quickPose.accept(base);
        var result=RigPoseComposer.compose(base,List.of(settings.layer(authored,envelope)));
        result.diagnostics().forEach(diagnostics);
        return result.pose();
    }
}
