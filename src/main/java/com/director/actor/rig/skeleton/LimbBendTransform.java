package com.director.actor.rig.skeleton;
import org.joml.*;

/** Continuous elbow/knee bend in pixel model space. X bends, Y selects the bend plane without twisting the skin. */
public final class LimbBendTransform {
    private final float angle,twist,half;
    private final Vector3f scale;
    private final LimbBendMode mode;
    public LimbBendTransform(Quaternionf rotation,Vector3f scale) {
        this(rotation,scale,LimbBendMode.SMOOTH);
    }
    public LimbBendTransform(Quaternionf rotation,Vector3f scale,LimbBendMode mode) {
        this.mode=java.util.Objects.requireNonNull(mode);
        var angles=angles(rotation);
        // A square miter must not reach the far end of either six-pixel segment.
        float limit=(float)java.lang.Math.toRadians(mode==LimbBendMode.SHARP?110:160);
        angle=clamp(angles.x,-limit,limit); twist=angles.y;
        half=java.lang.Math.max(2,java.lang.Math.abs(angle)*2.1f);
        this.scale=new Vector3f(scale);
    }
    /** Two-axis Y*X decomposition without the 90-degree Euler branch flip. */
    public static Vector2f angles(Quaternionf rotation) {
        var q=new Quaternionf(rotation).normalize();
        if(q.w<0) q.mul(-1);
        float pitch=(float)(2*java.lang.Math.atan2(q.x,q.w));
        float turn=(float)(2*java.lang.Math.atan2(q.y,q.w));
        return new Vector2f(clamp(pitch,-(float)java.lang.Math.toRadians(160),(float)java.lang.Math.toRadians(160)),
                clamp(turn,-(float)java.lang.Math.PI/2,(float)java.lang.Math.PI/2));
    }
    public static boolean isJoint(String id) {
        return ArticulatedPlayerRig.LEFT_FOREARM.equals(id)||ArticulatedPlayerRig.RIGHT_FOREARM.equals(id)
                ||ArticulatedPlayerRig.LEFT_SHIN.equals(id)||ArticulatedPlayerRig.RIGHT_SHIN.equals(id);
    }
    private static float clamp(float v,float lo,float hi) { return java.lang.Math.max(lo,java.lang.Math.min(hi,v)); }
    /** Stored keyframe encoding, distinct from the resulting physical swing. */
    public static Quaternionf encodedRotation(float bend,float direction) {
        var a=angles(new Quaternionf().rotateY(direction).rotateX(bend));
        return new Quaternionf().rotateY(a.y).rotateX(a.x);
    }
    public Quaternionf rotation() { return new Quaternionf().rotateY(twist).rotateX(angle).rotateY(-twist); }
    public Vector3f map(Vector3f rest) {
        if(mode==LimbBendMode.SHARP) return sharp(rest);
        float y=rest.y;
        if(y<=-half) return new Vector3f(rest);
        if(y>=half) return lowerFrame().transformPosition(new Vector3f(rest));
        float u=(y+half)/(2*half);
        var center=center(u);
        float smooth=u*u*(3-2*u);
        var frame=new Quaternionf().rotateY(twist).rotateX(angle*u).rotateY(-twist);
        var cross=new Vector3f(rest.x*(1+(scale.x-1)*smooth),0,rest.z*(1+(scale.z-1)*smooth));
        return frame.transform(cross).add(center);
    }
    /** Shared miter ring: each half is straight, with no rounded transition or cut-face overlap. */
    private Vector3f sharp(Vector3f rest) {
        var cross=new Quaternionf().rotateY(-twist).transform(new Vector3f(rest.x,0,rest.z));
        cross.y=-(float)java.lang.Math.tan(angle*.5f)*cross.z;
        var seam=new Quaternionf().rotateY(twist).transform(cross);
        // Scaling a lower half changes its profile; meet the same ring from both sides.
        seam.x*=.5f*(1+scale.x); seam.z*=.5f*(1+scale.z);
        var onCut=new Vector3f(rest.x,0,rest.z);
        if(rest.y<=0) return new Vector3f(rest).fma(clamp(1+rest.y/6,0,1),seam.sub(onCut));
        var frame=lowerFrame();
        return frame.transformPosition(new Vector3f(rest))
                .fma(clamp(1-rest.y/6,0,1),seam.sub(frame.transformPosition(onCut)));
    }
    private Vector3f center(float u) {
        double a=angle,k=scale.y-1,L=2*half;
        double y,z;
        if(java.lang.Math.abs(a)<1e-4) { y=-half+L*(u+.5*k*u*u); z=0; }
        else {
            double sin=java.lang.Math.sin(a*u),cos=java.lang.Math.cos(a*u);
            y=-half+L*(sin/a+k*(u*sin/a+(cos-1)/(a*a)));
            z=L*((1-cos)/a+k*(-u*cos/a+sin/(a*a)));
        }
        return new Quaternionf().rotateY(twist).transform(new Vector3f(0,(float)y,(float)z));
    }
    /** Maps the lower segment's original rest coordinates to the continuous surface. */
    public Matrix4f lowerFrame() {
        if(mode==LimbBendMode.SHARP) return new Matrix4f().rotate(rotation()).scale(scale);
        return new Matrix4f().translate(center(1)).rotate(rotation()).scale(scale).translate(0,-half,0);
    }
    public Vector3f lowerOffset() { return lowerFrame().getTranslation(new Vector3f()); }
}
