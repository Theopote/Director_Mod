package com.director.actor.rig.skeleton;
import org.joml.*;

/** Continuous elbow/knee bend in pixel model space. X bends, Y selects twist. */
public final class LimbBendTransform {
    private final float angle,twist,half;
    private final Vector3f scale;
    public LimbBendTransform(Quaternionf rotation,Vector3f scale) {
        var angles=angles(rotation);
        angle=angles.x; twist=angles.y;
        half=java.lang.Math.max(2,java.lang.Math.abs(angle)*1.75f);
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
    public Quaternionf rotation() { return new Quaternionf().rotateY(twist).rotateX(angle); }
    public Vector3f map(Vector3f rest) {
        float y=rest.y;
        if(y<=-half) return new Vector3f(rest);
        if(y>=half) return lowerFrame().transformPosition(new Vector3f(rest));
        float u=(y+half)/(2*half);
        var center=center(u);
        float smooth=u*u*(3-2*u);
        var frame=new Quaternionf().rotateY(twist).rotateX(angle*u).rotateY(-twist*(1-smooth));
        var cross=new Vector3f(rest.x*(1+(scale.x-1)*smooth),0,rest.z*(1+(scale.z-1)*smooth));
        return frame.transform(cross).add(center);
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
        return new Matrix4f().translate(center(1)).rotate(rotation()).scale(scale).translate(0,-half,0);
    }
    public Vector3f lowerOffset() { return lowerFrame().getTranslation(new Vector3f()); }
}
