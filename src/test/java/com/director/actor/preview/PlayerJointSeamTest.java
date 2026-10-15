package com.director.actor.preview;
import net.minecraft.client.model.ModelPart;
import net.minecraft.util.math.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class PlayerJointSeamTest {
    private ModelPart.Cuboid cuboid(float y,float dilation) {
        return new ModelPart.Cuboid(0,0,-2,y,-2,4,6,4,dilation,dilation,dilation,false,64,64,EnumSet.allOf(Direction.class));
    }
    @Test void upperAndLowerCutRingsCoincideIncludingClothingAndScale() {
        for(float dilation:new float[]{0,.25f}) for(int degrees:new int[]{0,45,90,150}) {
            var u=cuboid(0,dilation); var l=cuboid(0,dilation);
            var upper=new ModelPart(List.of(u),Map.of()); var lower=new ModelPart(List.of(l),Map.of()); lower.originY=6;
            var rotation=new Quaternionf().rotateX((float)Math.toRadians(degrees)).rotateY(.2f);
            var scale=new Vector3f(1.2f,.8f,1.1f);
            PlayerJointSeam.update(upper,lower,rotation,scale);
            for(float x:new float[]{-2-dilation,2+dilation}) for(float z:new float[]{-2-dilation,2+dilation}) {
                var seam=PlayerJointSeam.seamPoint(new Vector3f(x,0,z),rotation,scale).add(0,6,0);
                assertTrue(contains(u,seam,null,null));
                assertTrue(contains(l,seam,rotation,scale));
            }
            // Updating the same shared model must start from original vertices, not the last deformation.
            PlayerJointSeam.update(upper,lower,new Quaternionf(),new Vector3f(1));
            assertTrue(contains(u,new Vector3f(-2-dilation,6,-2-dilation),null,null));
            assertTrue(contains(l,new Vector3f(-2-dilation,6,-2-dilation),new Quaternionf(),new Vector3f(1)));
        }
    }
    private boolean contains(ModelPart.Cuboid cuboid,Vector3f target,Quaternionf rotation,Vector3f scale) {
        for(var side:cuboid.sides) {
            assertTrue(new Vector3f(side.direction()).isFinite());
            for(var vertex:side.vertices()) {
                var p=new Vector3f(vertex.x(),vertex.y(),vertex.z());
                if(rotation!=null) rotation.transform(p.mul(scale)).add(0,6,0);
                if(p.distance(target)<1e-4f) return true;
            }
        }
        return false;
    }
}
