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
        assertTrue(new LimbBendTransform(q,new Vector3f(1)).rotation().equals(new Quaternionf(q).rotateY(-.5f),1e-5f));
    }
    @Test void skinAndClothingDoNotInvertWithinSupportedBendTurnRange() {
        float step=.001f;
        for(int degrees:new int[]{-160,-90,0,90,160}) for(float turn:new float[]{-1.5f,-.96f,0,.96f,1.5f}) {
            var bend=new LimbBendTransform(new Quaternionf().rotateY(turn).rotateX((float)java.lang.Math.toRadians(degrees)),new Vector3f(1));
            for(float y=-5.5f;y<6;y+=.5f) for(float x:new float[]{-3.25f,-2.25f,2.25f,3.25f}) for(float z:new float[]{-2.25f,2.25f}) {
                var p=new Vector3f(x,y,z); var base=bend.map(p);
                var a=bend.map(new Vector3f(p).add(step,0,0)).sub(base).div(step);
                var b=bend.map(new Vector3f(p).add(0,step,0)).sub(base).div(step);
                var c=bend.map(new Vector3f(p).add(0,0,step)).sub(base).div(step);
                assertTrue(a.cross(b).dot(c)>0,"Local mesh inversion at "+degrees+" / "+turn+" / "+y);
            }
        }
    }
    @Test void bendDirectionNeverTwistsAnUnbentLimbOrChangesCrossSectionWidth() {
        for(float direction:new float[]{-1.5f,-.9f,0,.9f,1.5f}) {
            var straight=new LimbBendTransform(new Quaternionf().rotateY(direction),new Vector3f(1));
            for(float y=-6;y<=6;y+=.25f) {
                var p=new Vector3f(3,y,-2);
                assertTrue(straight.map(p).equals(p,1e-5f));
            }
            var bent=new LimbBendTransform(new Quaternionf().rotateY(direction).rotateX(1.5f),new Vector3f(1));
            for(float y=-6;y<=6;y+=.25f) {
                var a=bent.map(new Vector3f(-1,y,-2));
                var b=bent.map(new Vector3f(3,y,-2));
                var c=bent.map(new Vector3f(-1,y,2));
                assertEquals(4,a.distance(b),1e-4);
                assertEquals(4,a.distance(c),1e-4);
                assertEquals(0,new Vector3f(b).sub(a).dot(new Vector3f(c).sub(a)),1e-4);
            }
        }
    }
    @Test void sharpFoldHasRigidEndsAndStraightLocalBevel() {
        var bend=new LimbBendTransform(new Quaternionf().rotateX((float)java.lang.Math.PI/2),new Vector3f(1),LimbBendMode.SHARP);
        for(float y:new float[]{-5,-3,-.5f,.5f,3,5}) {
            var p=new Vector3f(2,y,2);
            var middle=bend.map(p);
            var average=bend.map(new Vector3f(p).add(0,-.2f,0)).add(bend.map(new Vector3f(p).add(0,.2f,0))).mul(.5f);
            assertTrue(middle.equals(average,1e-5f),"Each rigid end and the bevel must remain straight");
            if(y<=-1.5f) assertTrue(middle.equals(p,1e-5f));
            if(y>=1.5f) assertTrue(middle.equals(bend.lowerFrame().transformPosition(new Vector3f(p)),1e-5f));
        }
        assertEquals(90,java.lang.Math.toDegrees(LimbBendTransform.angles(new Quaternionf().rotateX((float)java.lang.Math.PI/2)).x),1e-4);
        var pose=new RigPose(ArticulatedPlayerRig.DEFINITION);
        pose.setLimbBendMode(LimbBendMode.SHARP);
        assertEquals(LimbBendMode.SHARP,pose.copy().limbBendMode());
        assertEquals(LimbBendMode.SHARP,pose.overlay(new RigPose(pose.rig())).limbBendMode());
    }
    @Test void sharpFoldDoesNotInvertAsymmetricSkinAndClothing() {
        float step=.001f;
        for(int degrees:new int[]{-160,-90,0,90,160}) for(float direction:new float[]{-1.5f,-.96f,0,.96f,1.5f}) {
            var bend=new LimbBendTransform(new Quaternionf().rotateY(direction).rotateX((float)java.lang.Math.toRadians(degrees)),new Vector3f(1),LimbBendMode.SHARP);
            for(float y=-5.5f;y<6;y+=.25f) for(float x:new float[]{-3.25f,3.25f}) for(float z:new float[]{-2.25f,2.25f}) {
                var p=new Vector3f(x,y,z); var base=bend.map(p);
                var a=bend.map(new Vector3f(p).add(step,0,0)).sub(base).div(step);
                var b=bend.map(new Vector3f(p).add(0,step,0)).sub(base).div(step);
                var c=bend.map(new Vector3f(p).add(0,0,step)).sub(base).div(step);
                assertTrue(a.cross(b).dot(c)>0,"Inverted sharp fold at "+degrees+" / "+direction+" / "+y);
            }
        }
    }
    @Test void sharpFoldPreservesFullAngleAndIsLocallyValidWithNonUniformScale() {
        float h=.001f;
        for(float direction:new float[]{-1.5f,-.96f,0,.96f,1.5f}) for(int degrees:new int[]{-160,-120,-45,0,45,120,160})
            for(float sx:new float[]{.5f,2}) for(float sy:new float[]{.5f,2}) for(float sz:new float[]{.5f,2}) {
                var q=new Quaternionf().rotateY(direction).rotateX((float)java.lang.Math.toRadians(degrees));
                var bend=new LimbBendTransform(q,new Vector3f(sx,sy,sz),LimbBendMode.SHARP);
                assertTrue(bend.rotation().equals(new Quaternionf(q).rotateY(-direction),1e-5f),"No hidden angle limit");
                for(float y:new float[]{-1.4f,-.5f,0,.5f,1.4f}) for(float x:new float[]{-3.25f,3.25f}) for(float z:new float[]{-2.25f,2.25f}) {
                    var p=new Vector3f(x,y,z);var base=bend.map(p);
                    var a=bend.map(new Vector3f(p).add(h,0,0)).sub(base).div(h);
                    var b=bend.map(new Vector3f(p).add(0,h,0)).sub(base).div(h);
                    var c=bend.map(new Vector3f(p).add(0,0,h)).sub(base).div(h);
                    assertTrue(a.cross(b).dot(c)>0,"Inverted scaled bevel: "+degrees+" / "+direction+" / "+sx+","+sy+","+sz);
                }
            }
    }
}
