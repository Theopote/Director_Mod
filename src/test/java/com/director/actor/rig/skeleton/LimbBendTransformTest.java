package com.director.actor.rig.skeleton;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LimbBendTransformTest {
    @Test void lowerFrameMatchesSurfaceAndDoesNotFlipPastNinetyDegrees() {
        for(float angle:new float[]{0,.5f,1.6f,2.5f,2.79f}) {
            var rotation=new Quaternionf().rotateY(.4f).rotateX(angle);
            var bend=new LimbBendTransform(rotation,new Vector3f(1.2f,.9f,1.1f));
            assertEquals(angle,LimbBendTransform.angles(rotation).x,1e-5f);
            assertEquals(.4f,LimbBendTransform.angles(rotation).y,1e-5f);
            for(var p:new Vector3f[]{new Vector3f(2,6,-2),new Vector3f(-2,6,2)})
                assertTrue(bend.map(p).equals(bend.lowerFrame().transformPosition(new Vector3f(p)),1e-5f));
            assertTrue(bend.map(new Vector3f(2,-6,2)).equals(new Vector3f(2,-6,2),1e-5f));
        }
    }
    @Test void straightMappingMatchesRestAndTurnBendHasOnlyTwoAxes() {
        var bend=new LimbBendTransform(new Quaternionf(),new Vector3f(1));
        for(float y=-6;y<=6;y+=.25f) {
            var p=new Vector3f(2,y,-2);
            assertTrue(bend.map(p).equals(p,1e-5f));
        }
        var q=new Quaternionf().rotateY(.5f).rotateX(2.5f);
        assertTrue(new LimbBendTransform(q,new Vector3f(1)).rotation().equals(q,1e-5f));
    }
    @Test void skinAndClothingDoNotInvertWithinSupportedBendTurnRange() {
        float step=.001f;
        for(int degrees:new int[]{-160,-90,0,90,160}) for(float turn:new float[]{-.8f,0,.8f}) {
            var bend=new LimbBendTransform(new Quaternionf().rotateY(turn).rotateX((float)java.lang.Math.toRadians(degrees)),new Vector3f(1));
            for(float y=-5.5f;y<6;y+=.5f) for(float x:new float[]{-2.25f,2.25f}) for(float z:new float[]{-2.25f,2.25f}) {
                var p=new Vector3f(x,y,z); var base=bend.map(p);
                var a=bend.map(new Vector3f(p).add(step,0,0)).sub(base).div(step);
                var b=bend.map(new Vector3f(p).add(0,step,0)).sub(base).div(step);
                var c=bend.map(new Vector3f(p).add(0,0,step)).sub(base).div(step);
                assertTrue(a.cross(b).dot(c)>0,"Local mesh inversion at "+degrees+" / "+turn+" / "+y);
            }
        }
    }
}
