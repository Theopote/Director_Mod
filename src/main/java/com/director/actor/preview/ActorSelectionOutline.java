package com.director.actor.preview;

import com.director.actor.rig.skeleton.*;
import com.director.client.DirectorClientController;
import com.director.ui.actor.pose.ActorBoneSelectionController;
import com.director.ui.actor.selection.ActorSelectionHub;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import java.util.*;

/** Captures selection with each render state; masks only the native outline pass. */
public final class ActorSelectionOutline {
    public static final int COLOR=0xFFFFCB40;
    private static final Map<EntityRenderState,Set<String>> SELECTIONS=new WeakHashMap<>();
    record Mask(Set<ModelPart> parts,Map<ModelPart,Integer> halves) {}
    private static final ThreadLocal<Mask> MASK=new ThreadLocal<>();
    private ActorSelectionOutline() {}

    public static void capture(Entity entity,EntityRenderState state) {
        SELECTIONS.remove(state);
        if(!DirectorClientController.getInstance().isDirectorModeActive() || entity.isInvisible()) return;
        var custom=com.director.actor.model.CustomModelPreviewRenderer.getInstance().selectionTarget(entity);
        UUID actor=custom==null ? ActorPreviewManager.getInstance().getActorId(entity):custom.actorId();
        if(actor==null || !actor.equals(ActorSelectionHub.getSelectedActorId())) return;
        var bones=ActorBoneSelectionController.getInstance();
        Set<String> selected=bones.isActive() && actor.equals(bones.getActorId())
                ?Set.copyOf(bones.getSelectedBoneIds()):Set.of();
        if(custom!=null) {
            if(!selected.isEmpty() && !selected.contains(HumanoidRig.ROOT) && !selected.contains(custom.boneId())) return;
            selected=Set.of(); // A display entity already represents one selected bone's cuboid.
        }
        SELECTIONS.put(state,selected);
        state.outlineColor=COLOR;
    }
    public static boolean accepts(ModelPart part) {
        var mask=MASK.get(); return mask==null || mask.parts.contains(part);
    }
    public static int halfMask(ModelPart part) {
        var mask=MASK.get(); return mask==null ? 3:mask.halves.getOrDefault(part,3);
    }
    public static boolean acceptsQuad(ModelPart part,float restY) {
        int halves=halfMask(part);
        return halves==3 || (restY<=0 ? (halves&1)!=0 : (halves&2)!=0);
    }
    public static void render(Model<?> model,Object state,MatrixStack matrices,VertexConsumer vertices,int light,int overlay,int color) {
        renderSelected(model,state instanceof PlayerEntityRenderState player ? PlayerActorRenderPoses.get(player):null,
                SELECTIONS.get(state),matrices,vertices,light,overlay,color);
    }
    static void renderSelected(Model<?> model,RigPose pose,Set<String> selected,MatrixStack matrices,
                               VertexConsumer vertices,int light,int overlay,int color) {
        if(selected==null || selected.isEmpty() || selected.contains(HumanoidRig.ROOT)) {
            model.render(matrices,vertices,light,overlay,color); return;
        }
        var parts=model.getRootPart().traverse();
        boolean[] visible=new boolean[parts.size()],hidden=new boolean[parts.size()];
        for(int i=0;i<parts.size();i++) { visible[i]=parts.get(i).visible; hidden[i]=parts.get(i).hidden; }
        var mask=selectionMask(model.getRootPart(),selected);
        var allowed=mask.parts(); var halves=mask.halves();
        Mask previous=MASK.get();
        try {
            if(model instanceof PlayerEntityModel player && pose!=null) {
                segment(player.leftArm,player.leftSleeve,HumanoidRig.LEFT_ARM,ArticulatedPlayerRig.LEFT_FOREARM,selected,halves,pose);
                segment(player.rightArm,player.rightSleeve,HumanoidRig.RIGHT_ARM,ArticulatedPlayerRig.RIGHT_FOREARM,selected,halves,pose);
                segment(player.leftLeg,player.leftPants,HumanoidRig.LEFT_LEG,ArticulatedPlayerRig.LEFT_SHIN,selected,halves,pose);
                segment(player.rightLeg,player.rightPants,HumanoidRig.RIGHT_LEG,ArticulatedPlayerRig.RIGHT_SHIN,selected,halves,pose);
            }
            MASK.set(new Mask(allowed,halves));
            model.render(matrices,vertices,light,overlay,color);
        } finally {
            if(previous==null) MASK.remove(); else MASK.set(previous);
            for(int i=0;i<parts.size();i++) { parts.get(i).visible=visible[i]; parts.get(i).hidden=hidden[i]; }
        }
    }
    static Mask selectionMask(ModelPart root,Set<String> selected) {
        var allowed=Collections.newSetFromMap(new IdentityHashMap<ModelPart,Boolean>());
        var halves=new IdentityHashMap<ModelPart,Integer>();
        var getter=root.createPartGetter();
        for(String id:selected) {
            String name=switch(id) {
                case ArticulatedPlayerRig.LEFT_FOREARM -> "left_arm";
                case ArticulatedPlayerRig.RIGHT_FOREARM -> "right_arm";
                case ArticulatedPlayerRig.LEFT_SHIN -> "left_leg";
                case ArticulatedPlayerRig.RIGHT_SHIN -> "right_leg";
                default -> id;
            };
            ModelPart part=getter.apply(name);
            if(part==null && (HumanoidRig.LEFT_ARM.equals(name) || HumanoidRig.RIGHT_ARM.equals(name))) part=getter.apply("arms");
            if(part!=null) allowed.addAll(part.traverse());
        }
        return new Mask(allowed,halves);
    }
    private static void segment(ModelPart limb,ModelPart clothing,String upper,String lower,Set<String> selected,
                                Map<ModelPart,Integer> halves,RigPose pose) {
        int mask=(selected.contains(upper)?1:0)|(selected.contains(lower)?2:0);
        if(mask==0 || pose.rig().findBoneIndex(lower)<0) return;
        // Even an unbent, unauthored limb needs a split surface for precise half selection.
        var outlinePose=pose.copy(); outlinePose.setLocalTransform(lower,pose.localTransform(lower));
        PlayerJointModel.apply(limb,outlinePose,lower); PlayerJointModel.apply(clothing,outlinePose,lower);
        for(var part:List.of(limb,clothing)) if(part.hasChild("director_joint_connector"))
            halves.put(part.getChild("director_joint_connector"),mask);
    }
}
