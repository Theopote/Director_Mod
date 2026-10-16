package com.director.actor.rig.skeleton;
import org.joml.*;

/** Continuous elbow/knee bend in pixel model space. X bends, Y selects the bend plane without twisting the skin. */
public final class LimbBendTransform {
    public static final float SHARP_HALF = 1.5f;
    private final float angle,twist,half;
    private final Quaternionf swing;
    private final Matrix4f lower;
    private final Vector3f scale;
    private final LimbBendMode mode;
    public LimbBendTransform(Quaternionf rotation,Vector3f scale) {
        this(rotation,scale,LimbBendMode.SMOOTH);
    }
    public LimbBendTransform(Quaternionf rotation,Vector3f scale,LimbBendMode mode) {
        this.mode=java.util.Objects.requireNonNull(mode);
        var angles=angles(rotation);
        float limit=(float)java.lang.Math.toRadians(160);
        angle=clamp(angles.x,-limit,limit); twist=angles.y;
        half=java.lang.Math.max(2,java.lang.Math.abs(angle)*2.1f);
        this.scale=new Vector3f(scale);
        swing=new Quaternionf().rotateY(twist).rotateX(angle).rotateY(-twist);
        lower=createLowerFrame();
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
    public Quaternionf rotation() { return new Quaternionf(swing); }
    public Vector3f map(Vector3f rest) {
        if(mode==LimbBendMode.SHARP) return sharp(rest);
        float y=rest.y;
        if(y<=-half) return new Vector3f(rest);
        if(y>=half) return lower.transformPosition(new Vector3f(rest));
        float u=(y+half)/(2*half);
        var center=center(u);
        float smooth=u*u*(3-2*u);
        var frame=new Quaternionf().rotateY(twist).rotateX(angle*u).rotateY(-twist);
        var cross=new Vector3f(rest.x*(1+(scale.x-1)*smooth),0,rest.z*(1+(scale.z-1)*smooth));
        return frame.transform(cross).add(center);
    }
    /** A short ruled bevel connects rigid ends, without a rounded transition or an unbounded miter. */
    private Vector3f sharp(Vector3f rest) {
        if(rest.y<=-SHARP_HALF) return new Vector3f(rest);
        if(rest.y>=SHARP_HALF) return lower.transformPosition(new Vector3f(rest));
        float u=(rest.y+SHARP_HALF)/(2*SHARP_HALF);
        var upper=new Vector3f(rest.x,-SHARP_HALF,rest.z);
        var bottom=lower.transformPosition(new Vector3f(rest.x,SHARP_HALF,rest.z));
        return upper.lerp(bottom,u);
    }
    /**
     * Move the fold towards the inside support edge only when the bevel would invert.
     * The support covers wide/slim arms, legs and their quarter-pixel clothing shell.
     * Skin and shell deliberately share this frame, as do sockets and bone queries.
     */
    private Vector3f sharpOffset() {
        float a=java.lang.Math.abs(angle),sin=(float)java.lang.Math.sin(a),cos=(float)java.lang.Math.cos(a);
        if(a<1e-5f) return new Vector3f();
        float x=java.lang.Math.abs((float)java.lang.Math.sin(twist))*3.25f;
        float z=java.lang.Math.abs((float)java.lang.Math.cos(twist))*2.25f;
        float support=x+z,scaledSupport=x*scale.x+z*scale.z;
        // Positive longitudinal derivative at both ends of every interpolated section.
        float required=java.lang.Math.max(scaledSupport-SHARP_HALF*(1+scale.y*cos)/sin,
                support-SHARP_HALF*(scale.y+cos)/sin);
        float radius=java.lang.Math.max(0,required+.2f);
        var hinge=new Quaternionf().rotateY(twist).transform(new Vector3f(0,0,java.lang.Math.copySign(radius,angle)));
        return new Vector3f(hinge).sub(swing.transform(new Vector3f(hinge)));
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
    private Matrix4f createLowerFrame() {
        if(mode==LimbBendMode.SHARP) return new Matrix4f().translate(sharpOffset()).rotate(swing).scale(scale);
        return new Matrix4f().translate(center(1)).rotate(swing).scale(scale).translate(0,-half,0);
    }
    public Matrix4f lowerFrame() { return new Matrix4f(lower); }
    public Vector3f lowerOffset() { return lower.getTranslation(new Vector3f()); }
}
