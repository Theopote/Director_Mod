package com.director.actor.preview;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.*;
import java.util.*;

/** A single closed skin surface replaces both rigid pieces and the old patch mesh. */
public final class PlayerJointSeam {
    private static final Map<ModelPart,State> STATES=new WeakHashMap<>();
    private static final ThreadLocal<Object> OWNER=new ThreadLocal<>();
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    private static final class Scratch {
        final Vector3f normal=new Vector3f();
        final Vector3f position=new Vector3f();
    }
    private static final class PoseMesh {
        ContinuousLimbMesh.Vertex[] cap;
        Quaternionf rotation; Vector3f scale;
        com.director.actor.rig.skeleton.LimbBendMode mode;
        List<ContinuousLimbMesh.Vertex[]> mesh=List.of();
    }
    private static final class State {
        final ContinuousLimbMesh.Template template;
        final ModelPart.Cuboid cube;
        /** Per render-owner meshes so shared PlayerEntityModel parts do not thrash across actors. */
        final Map<Object,PoseMesh> poses=new WeakHashMap<>();
        State(ModelPart parent,ModelPart lower) {
            List<ModelPart.Cuboid> cubes=new ArrayList<>();
            parent.forEachCuboid(new MatrixStack(),(entry,path,index,cube)->cubes.add(cube));
            cube=cubes.getFirst();
            template=ContinuousLimbMesh.prepare(cube,lower.originY);
        }
    }
    private PlayerJointSeam() {}
    /** Bind mesh cache lookups to the entity currently being posed/rendered on this thread. */
    public static void beginOwner(Object owner) { OWNER.set(owner); }
    public static void endOwner() { OWNER.remove(); }
    public static void update(ModelPart parent,ModelPart upper,ModelPart lower,ModelPart surface,Quaternionf rotation,Vector3f scale) {
        update(parent,upper,lower,surface,rotation,scale,com.director.actor.rig.skeleton.LimbBendMode.SMOOTH);
    }
    public static void update(ModelPart parent,ModelPart upper,ModelPart lower,ModelPart surface,Quaternionf rotation,Vector3f scale,
                              com.director.actor.rig.skeleton.LimbBendMode mode) {
        var state=STATES.computeIfAbsent(surface,key->new State(parent,lower));
        Object owner=OWNER.get();
        if(owner==null) owner=surface;
        var pose=state.poses.computeIfAbsent(owner,key->new PoseMesh());
        if(pose.mode==mode && pose.rotation!=null && pose.rotation.equals(rotation,1e-6f) && pose.scale.equals(scale,1e-6f)) return;
        pose.mesh=ContinuousLimbMesh.deform(state.template,rotation,scale,mode);
        pose.cap=ContinuousLimbMesh.selectionCap(state.cube,rotation,scale,mode);
        pose.mode=mode;
        pose.rotation=new Quaternionf(rotation); pose.scale=new Vector3f(scale);
    }
    public static boolean render(ModelPart part,MatrixStack.Entry entry,VertexConsumer consumer,int light,int overlay,int color) {
        var state=STATES.get(part); if(state==null) return false;
        Object owner=OWNER.get();
        var pose=owner==null?null:state.poses.get(owner);
        if(pose==null) {
            // Outline / tests may render without an owner; use any cached pose for this part.
            pose=state.poses.values().stream().findFirst().orElse(null);
            if(pose==null) return false;
        }
        var scratch=SCRATCH.get();
        for(int i=0;i<pose.mesh.size();i++) {
          if(!ActorSelectionOutline.acceptsQuad(part,state.template.quadCenterY(i))) continue;
          for(var vertex:pose.mesh.get(i)) emit(entry,consumer,vertex,light,overlay,color,scratch,false);
        }
        int mask=ActorSelectionOutline.halfMask(part);
        if(mask==1 || mask==2) for(int i=0;i<4;i++) {
            var vertex=pose.cap[mask==1?i:3-i];
            emit(entry,consumer,vertex,light,overlay,color,scratch,mask==2);
        }
        return true;
    }
    private static void emit(MatrixStack.Entry entry,VertexConsumer consumer,ContinuousLimbMesh.Vertex vertex,
                             int light,int overlay,int color,Scratch scratch,boolean negateNormal) {
        entry.transformNormal(vertex.normal(),scratch.normal);
        if(negateNormal) scratch.normal.negate();
        scratch.position.set(vertex.position()).div(16);
        entry.getPositionMatrix().transformPosition(scratch.position);
        consumer.vertex(scratch.position.x,scratch.position.y,scratch.position.z,color,vertex.u(),vertex.v(),
                overlay,light,scratch.normal.x,scratch.normal.y,scratch.normal.z);
    }
}
