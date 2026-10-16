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
        Quaternionf rotation; Vector3f scale;
        com.director.actor.rig.skeleton.LimbBendMode mode;
        List<ContinuousLimbMesh.Vertex[]> mesh=List.of();
        State(ModelPart parent,ModelPart lower) {
            List<ModelPart.Cuboid> cubes=new ArrayList<>();
            parent.forEachCuboid(new MatrixStack(),(entry,path,index,cube)->cubes.add(cube));
            template=ContinuousLimbMesh.prepare(cubes.getFirst(),lower.originY);
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
        state.mode=mode;
        state.rotation=new Quaternionf(rotation); state.scale=new Vector3f(scale);
    }
    public static boolean render(ModelPart part,MatrixStack.Entry entry,VertexConsumer consumer,int light,int overlay,int color) {
        var state=STATES.get(part); if(state==null) return false;
        for(var quad:state.mesh) for(var vertex:quad) {
            var n=entry.transformNormal(vertex.normal(),new Vector3f());
            var p=entry.getPositionMatrix().transformPosition(new Vector3f(vertex.position()).div(16));
            consumer.vertex(p.x,p.y,p.z,color,vertex.u(),vertex.v(),overlay,light,n.x,n.y,n.z);
        }
        return true;
    }
}
