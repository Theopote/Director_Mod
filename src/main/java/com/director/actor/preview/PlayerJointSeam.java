package com.director.actor.preview;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.*;
import java.util.*;

/** A single closed skin surface replaces both rigid pieces and the old patch mesh. */
public final class PlayerJointSeam {
    private static final Map<ModelPart,State> STATES=new WeakHashMap<>();
    private static final class State {
        final ContinuousLimbMesh.Template template;
        final ModelPart.Cuboid cube;
        ContinuousLimbMesh.Vertex[] cap;
        Quaternionf rotation; Vector3f scale;
        com.director.actor.rig.skeleton.LimbBendMode mode;
        List<ContinuousLimbMesh.Vertex[]> mesh=List.of();
        State(ModelPart parent,ModelPart lower) {
            List<ModelPart.Cuboid> cubes=new ArrayList<>();
            parent.forEachCuboid(new MatrixStack(),(entry,path,index,cube)->cubes.add(cube));
            cube=cubes.getFirst();
            template=ContinuousLimbMesh.prepare(cube,lower.originY);
        }
    }
    private PlayerJointSeam() {}
    public static void update(ModelPart parent,ModelPart upper,ModelPart lower,ModelPart surface,Quaternionf rotation,Vector3f scale) {
        update(parent,upper,lower,surface,rotation,scale,com.director.actor.rig.skeleton.LimbBendMode.SMOOTH);
    }
    public static void update(ModelPart parent,ModelPart upper,ModelPart lower,ModelPart surface,Quaternionf rotation,Vector3f scale,
                              com.director.actor.rig.skeleton.LimbBendMode mode) {
        var state=STATES.computeIfAbsent(surface,key->new State(parent,lower));
        if(state.mode==mode && state.rotation!=null && state.rotation.equals(rotation,1e-6f) && state.scale.equals(scale,1e-6f)) return;
        state.mesh=ContinuousLimbMesh.deform(state.template,rotation,scale,mode);
        state.cap=ContinuousLimbMesh.selectionCap(state.cube,rotation,scale,mode);
        state.mode=mode;
        state.rotation=new Quaternionf(rotation); state.scale=new Vector3f(scale);
    }
    public static boolean render(ModelPart part,MatrixStack.Entry entry,VertexConsumer consumer,int light,int overlay,int color) {
        var state=STATES.get(part); if(state==null) return false;
        for(int i=0;i<state.mesh.size();i++) {
          if(!ActorSelectionOutline.acceptsQuad(part,state.template.quadCenterY(i))) continue;
          for(var vertex:state.mesh.get(i)) {
            var n=entry.transformNormal(vertex.normal(),new Vector3f());
            var p=entry.getPositionMatrix().transformPosition(new Vector3f(vertex.position()).div(16));
            consumer.vertex(p.x,p.y,p.z,color,vertex.u(),vertex.v(),overlay,light,n.x,n.y,n.z);
        }
        }
        int mask=ActorSelectionOutline.halfMask(part);
        if(mask==1 || mask==2) for(int i=0;i<4;i++) {
            var vertex=state.cap[mask==1?i:3-i];
            var n=entry.transformNormal(vertex.normal(),new Vector3f());
            if(mask==2) n.negate();
            var p=entry.getPositionMatrix().transformPosition(new Vector3f(vertex.position()).div(16));
            consumer.vertex(p.x,p.y,p.z,color,vertex.u(),vertex.v(),overlay,light,n.x,n.y,n.z);
        }
        return true;
    }
}
