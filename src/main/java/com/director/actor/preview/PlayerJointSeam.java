package com.director.actor.preview;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Weld the two cut rings to their common midpoint; retain the skin's per-face UVs. */
public final class PlayerJointSeam {
    private record Surface(ModelPart.Cuboid cuboid, ModelPart.Quad[] original) {}
    private static final Map<ModelPart,List<Surface>> SOURCES = new WeakHashMap<>();
    private PlayerJointSeam() {}

    public static Vector3f seamPoint(Vector3f cut, Quaternionf rotation, Vector3f scale) {
        // For an ordinary elbow/knee bend use a miter cut: preserve the limb's width
        // and depth instead of averaging corners, which narrows the whole segment.
        if (Math.abs(rotation.y)<1e-5f && Math.abs(rotation.z)<1e-5f
                && scale.equals(new Vector3f(1),1e-5f)) {
            var normal=rotation.transform(new Vector3f(0,1,0)).add(0,1,0);
            if (Math.abs(normal.y)>1e-4f) {
                float extension=-(normal.x*cut.x+normal.z*cut.z)/normal.y;
                if(Math.abs(extension)<=5.5f) return new Vector3f(cut.x,extension,cut.z);
            }
        }
        return new Vector3f(cut).add(rotation.transform(new Vector3f(cut).mul(scale))).mul(.5f);
    }
    public static void update(ModelPart upper,ModelPart lower,Quaternionf rotation,Vector3f scale) {
        if (!scale.isFinite() || scale.x<=0 || scale.y<=0 || scale.z<=0) return;
        deform(upper,lower.originY,rotation,scale,false);
        deform(lower,lower.originY,rotation,scale,true);
    }
    private static List<Surface> source(ModelPart part) {
        return SOURCES.computeIfAbsent(part,p -> {
            List<Surface> surfaces=new ArrayList<>();
            p.forEachCuboid(new MatrixStack(),(matrix,path,index,cuboid)->
                    surfaces.add(new Surface(cuboid,cuboid.sides.clone())));
            return surfaces;
        });
    }
    private static void deform(ModelPart part,float pivotY,Quaternionf rotation,Vector3f scale,boolean lower) {
        var inverse=new Quaternionf(rotation).conjugate();
        for (var surface:source(part)) {
            float cut=lower ? surface.cuboid.minY : surface.cuboid.maxY;
            for(int i=0;i<surface.original.length;i++) {
                var original=surface.original[i];
                var vertices=new ModelPart.Vertex[4];
                boolean cap=lower ? original.direction().y()<-.9f : original.direction().y()>.9f;
                for(int j=0;j<4;j++) {
                    var v=original.vertices()[j];
                    Vector3f p=new Vector3f(v.x(),v.y(),v.z());
                    // Dilation expands cut caps; choose the entire end ring using side normals.
                    boolean atCut=lower ? v.y()<=cut+.001f : v.y()>=cut-.001f;
                    if(atCut) {
                        var local=new Vector3f(v.x(),0,v.z());
                        var seam=seamPoint(local,rotation,scale);
                        p=lower ? inverse.transform(seam).div(scale) : seam.add(0,pivotY,0);
                    }
                    vertices[j]=new ModelPart.Vertex(p.x,p.y,p.z,v.u(),v.v());
                }
                // The two old end caps are now internal. Degenerate them to avoid flickering.
                if(cap) Arrays.fill(vertices,vertices[0]);
                var normal=new Vector3f(vertices[1].x()-vertices[0].x(),vertices[1].y()-vertices[0].y(),vertices[1].z()-vertices[0].z())
                        .cross(new Vector3f(vertices[2].x()-vertices[0].x(),vertices[2].y()-vertices[0].y(),vertices[2].z()-vertices[0].z()));
                if(normal.lengthSquared()<1e-10f) normal.set(original.direction());
                else {
                    var a=original.vertices()[0]; var b=original.vertices()[1]; var c=original.vertices()[2];
                    var winding=new Vector3f(b.x()-a.x(),b.y()-a.y(),b.z()-a.z())
                            .cross(new Vector3f(c.x()-a.x(),c.y()-a.y(),c.z()-a.z()));
                    normal.normalize(); if(winding.dot(original.direction())<0) normal.negate();
                }
                surface.cuboid.sides[i]=new ModelPart.Quad(vertices,normal);
            }
        }
    }
}
