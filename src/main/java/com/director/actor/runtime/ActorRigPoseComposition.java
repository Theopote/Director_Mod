package com.director.actor.runtime;

import com.director.actor.rig.skeleton.*;
import java.util.List;
import java.util.function.Consumer;

/** Procedural movement fills unkeyed channels; authored bone channels have priority. */
final class ActorRigPoseComposition {
    private ActorRigPoseComposition() {}
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
        RigPose base=procedural==null?new RigPose(authored.rig()):procedural.copy();
        if(procedural==null) base.setLimbBendMode(authored.limbBendMode());
        quickPose.accept(base);
        var result=RigPoseComposer.compose(base,List.of(settings.layer(authored)));
        result.diagnostics().forEach(diagnostics);
        return result.pose();
    }
}
