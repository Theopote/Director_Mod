package com.director.actor.preview;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerJointSeamTest {
    private ModelPart.Cuboid cuboid(float dilation) {
        return new ModelPart.Cuboid(40,16,-2,0,-2,4,12,4,dilation,dilation,dilation,false,64,64,EnumSet.allOf(Direction.class));
    }
    private String key(Vector3f p) {
        return java.lang.Math.round(p.x*10000)+":"+java.lang.Math.round(p.y*10000)+":"+java.lang.Math.round(p.z*10000);
    }
    @Test void continuousSurfaceIsClosedHasFixedUvsAndFiniteNormalsAcrossPoseRange() throws Exception {
        for(float dilation:new float[]{0,.25f}) {
            var template=cuboid(dilation);
            var rest=ContinuousLimbMesh.build(template,6,new Quaternionf(),new Vector3f(1));
            for(int degrees:new int[]{-150,-90,-1,0,1,90,150,160}) {
                var q=new Quaternionf().rotateY(.4f).rotateX((float)Math.toRadians(degrees));
                var mesh=ContinuousLimbMesh.build(template,6,q,new Vector3f(1));
                assertEquals(rest.size(),mesh.size());
                if(dilation==0 && (degrees==90 || degrees==150 || degrees==0)) {
                    var report=java.nio.file.Path.of("build/reports/actor-limb-"+degrees+".json");
                    java.nio.file.Files.createDirectories(report.getParent());
                    java.nio.file.Files.writeString(report,new com.google.gson.Gson().toJson(mesh));
                }
                var edges=new HashMap<String,Integer>();
                for(int f=0;f<mesh.size();f++) {
                    var face=mesh.get(f);
                    for(int i=0;i<4;i++) {
                        var v=face[i];
                        assertTrue(v.position().isFinite()); assertTrue(v.normal().isFinite());
                        assertEquals(1,v.normal().length(),1e-4f);
                        assertEquals(rest.get(f)[i].u(),v.u()); assertEquals(rest.get(f)[i].v(),v.v());
                        String a=key(v.position()),b=key(face[(i+1)%4].position());
                        if(!a.equals(b)) edges.merge(a.compareTo(b)<0?a+"/"+b:b+"/"+a,1,Integer::sum);
                    }
                }
                for(int uses:edges.values()) assertEquals(2,uses,"Open or overlapping surface edge at "+degrees);
            }
        }
    }
    @Test void bothModesKeepAsymmetricArmAndClothingClosedWithStableUvs() throws Exception {
        for(var mode:com.director.actor.rig.skeleton.LimbBendMode.values()) for(float dilation:new float[]{0,.25f}) {
            var template=new ModelPart.Cuboid(40,16,-1,-2,-2,4,12,4,dilation,dilation,dilation,false,64,64,EnumSet.allOf(Direction.class));
            var rest=ContinuousLimbMesh.build(template,4,new Quaternionf(),new Vector3f(1),mode);
            for(float direction:new float[]{-.96f,0,.96f}) for(int degrees:new int[]{-160,-90,0,90,160}) {
                var mesh=ContinuousLimbMesh.build(template,4,new Quaternionf().rotateY(direction).rotateX((float)Math.toRadians(degrees)),new Vector3f(1),mode);
                var edges=new HashMap<String,Integer>();
                for(int f=0;f<mesh.size();f++) for(int i=0;i<4;i++) {
                    var v=mesh.get(f)[i];
                    assertTrue(v.position().isFinite()); assertTrue(v.normal().isFinite());
                    assertEquals(1,v.normal().length(),1e-4f);
                    assertEquals(rest.get(f)[i].u(),v.u()); assertEquals(rest.get(f)[i].v(),v.v());
                    String a=key(v.position()),b=key(mesh.get(f)[(i+1)%4].position());
                    if(!a.equals(b)) edges.merge(a.compareTo(b)<0?a+"/"+b:b+"/"+a,1,Integer::sum);
                }
                for(int uses:edges.values()) assertEquals(2,uses,"Unwelded arm in "+mode);
                if(dilation==0 && direction==.96f && (degrees==0 || degrees==90 || degrees==160)) {
                    var report=java.nio.file.Path.of("build/reports/actor-arm-"+mode+"-"+degrees+".json");
                    java.nio.file.Files.createDirectories(report.getParent());
                    java.nio.file.Files.writeString(report,new com.google.gson.Gson().toJson(mesh));
                }
            }
        }
    }
    @Test void wholeSurfaceRendererReplacesPatchAndOriginalPieces() {
        var full=cuboid(0);
        var parent=new ModelPart(List.of(full),Map.of());
        var upper=new ModelPart(List.of(full),Map.of());
        var lower=new ModelPart(List.of(full),Map.of()); lower.originY=6;
        var surface=new ModelPart(List.of(full),Map.of());
        PlayerJointSeam.update(parent,upper,lower,surface,new Quaternionf().rotateY(.2f).rotateX(1),new Vector3f(1));
        int[] count={0};
        var positions=new ArrayList<Vector3f>();
        var consumer=(net.minecraft.client.render.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
                net.minecraft.client.render.VertexConsumer.class.getClassLoader(),new Class[]{net.minecraft.client.render.VertexConsumer.class},(proxy,method,args)->{
                    if(method.getName().equals("vertex") && args.length==11) {
                        count[0]++;
                        positions.add(new Vector3f((Float)args[0],(Float)args[1],(Float)args[2]));
                        for(int i:new int[]{0,1,2,4,5,8,9,10}) assertTrue(Float.isFinite((Float)args[i]));
                    }
                    return method.getReturnType()==net.minecraft.client.render.VertexConsumer.class?proxy:null;
                });
        var entry=new net.minecraft.client.util.math.MatrixStack().peek();
        assertFalse(PlayerJointSeam.render(upper,entry,consumer,0,0,-1));
        assertTrue(PlayerJointSeam.render(surface,entry,consumer,0,0,-1));
        assertEquals((48*4+2)*4,count[0]);
        var smoothPositions=List.copyOf(positions); positions.clear();
        PlayerJointSeam.update(parent,upper,lower,surface,new Quaternionf().rotateY(.2f).rotateX(1),new Vector3f(1),
                com.director.actor.rig.skeleton.LimbBendMode.SHARP);
        assertTrue(PlayerJointSeam.render(surface,entry,consumer,0,0,-1));
        assertEquals(smoothPositions.size(),positions.size());
        assertNotEquals(smoothPositions,positions,"Switching mode must invalidate the unchanged-pose cache");
    }
}
