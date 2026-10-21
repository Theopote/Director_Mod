package com.director.actor.rig.skeleton;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic low-to-high composition. No frame history or mutation of input poses. */
public final class RigPoseComposer {
    public record Diagnostic(int layerIndex,String reason) {}
    public record Result(RigPose pose,List<Diagnostic> diagnostics) {
        public Result { diagnostics=List.copyOf(diagnostics); }
    }
    private record Edit(int bone,int channels,BoneTransform transform) {}
    private RigPoseComposer() {}

    public static Result compose(RigPose base,List<PoseLayer> layers) {
        Objects.requireNonNull(base,"base");Objects.requireNonNull(layers,"layers");
        var result=base.copy();var diagnostics=new ArrayList<Diagnostic>();
        for(int layerIndex=0;layerIndex<layers.size();layerIndex++) {
            var layer=Objects.requireNonNull(layers.get(layerIndex),"layer");
            if(layer.weight()==0 || layer.channelMask()==0) continue;
            var source=layer.sourceSnapshot();var reference=layer.referenceSnapshot();
            if(!sameLayout(base.rig(),source.rig()) || layer.mode()==PoseLayer.BlendMode.ADDITIVE && !sameLayout(source.rig(),reference.rig())) {
                diagnostics.add(new Diagnostic(layerIndex,"Incompatible bone layout"));continue;
            }
            // Validate the whole layer before applying any channel, so rejection is atomic.
            if(layer.mode()==PoseLayer.BlendMode.ADDITIVE && !validAdditiveScale(layer)) {
                diagnostics.add(new Diagnostic(layerIndex,"Additive scale requires nonzero reference and a finite positive ratio"));continue;
            }
            var edits=new ArrayList<Edit>();
            try {
                for(int bone=0;bone<source.rig().boneCount();bone++) {
                    int channels=source.authoredChannels(bone)&layer.channelMask();
                    if(!layer.includes(bone) || channels==0) continue;
                    var current=result.localTransform(bone);var value=source.localTransform(bone);
                    BoneTransform blended=layer.mode()==PoseLayer.BlendMode.OVERRIDE
                            ?override(current,value,channels,layer.weight())
                            :add(current,value,reference.localTransform(bone),channels,layer.weight());
                    edits.add(new Edit(bone,channels,blended));
                }
            } catch(IllegalArgumentException invalidTransform) {
                diagnostics.add(new Diagnostic(layerIndex,"Layer produced a non-finite transform"));continue;
            }
            for(var edit:edits) result.setLocalTransform(edit.bone(),edit.transform(),edit.channels());
        }
        return new Result(result,diagnostics);
    }
    public static boolean sameLayout(RigDefinition a,RigDefinition b) {
        if(a==b) return true;
        if(a.boneCount()!=b.boneCount()) return false;
        for(int i=0;i<a.boneCount();i++) if(!a.bone(i).id().equals(b.bone(i).id()) || a.bone(i).parentIndex()!=b.bone(i).parentIndex()) return false;
        return true;
    }
    private static BoneTransform override(BoneTransform current,BoneTransform source,int channels,float weight) {
        if(weight==1) return source;
        var t=current.translation();var r=current.rotation();var s=current.scale();
        if((channels&RigPose.TRANSLATION)!=0) t.lerp(source.translation(),weight);
        if((channels&RigPose.ROTATION)!=0) r.slerp(source.rotation(),weight);
        if((channels&RigPose.SCALE)!=0) s.lerp(source.scale(),weight);
        return new BoneTransform(t,r,s);
    }
    private static BoneTransform add(BoneTransform current,BoneTransform source,BoneTransform reference,int channels,float weight) {
        var t=current.translation();var r=current.rotation();var s=current.scale();
        if((channels&RigPose.TRANSLATION)!=0) t.fma(weight,source.translation().sub(reference.translation()));
        if((channels&RigPose.ROTATION)!=0) {
            var delta=reference.rotation().conjugate().mul(source.rotation()).normalize();
            r.mul(new Quaternionf().slerp(delta,weight)).normalize();
        }
        if((channels&RigPose.SCALE)!=0) s.mul(new Vector3f(1).lerp(source.scale().div(reference.scale()),weight));
        return new BoneTransform(t,r,s);
    }
    private static boolean validAdditiveScale(PoseLayer layer) {
        if((layer.channelMask()&RigPose.SCALE)==0) return true;
        var source=layer.sourceSnapshot();var reference=layer.referenceSnapshot();
        for(int i=0;i<source.rig().boneCount();i++) if(layer.includes(i) && (source.authoredChannels(i)&RigPose.SCALE)!=0) {
            var ref=reference.localTransform(i).scale();
            if(Math.abs(ref.x)<1e-6f || Math.abs(ref.y)<1e-6f || Math.abs(ref.z)<1e-6f) return false;
            var ratio=source.localTransform(i).scale().div(ref);
            if(!ratio.isFinite() || ratio.x<=0 || ratio.y<=0 || ratio.z<=0) return false;
        }
        return true;
    }
}
