package com.director.client.gizmo.handles;

import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.Axis;
import com.director.client.gizmo.HitResult;
import com.director.client.gizmo.util.RaycastUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 馃П ResizeHandle - 缂╂斁鎵嬫焺
 * 
 * 閫夊尯 Gizmo 鐨?缂╂斁鎵嬫焺"锛屼綅浜庨€夊尯鐨勯潰鎴栬涓娿€?
 * 鐢ㄦ埛閫氳繃榧犳爣鎷栨嫿鎺у埗閫夊尯杈圭晫鎵╁睍鎴栨敹缂┿€?
 * 
 * 鉁?鏀寔锛?
 *  - 闈㈡墜鏌勶細娌垮崟涓€ Axis 璋冩暣澶у皬
 *  - 瑙掔偣鎵嬫焺锛氬悓鏃舵部涓変釜鏂瑰悜缂╂斁
 *  - 灏勭嚎妫€娴?+ 璺濈鎶曞奖
 * 
 * @author Director
 * @version 1.0
 */
public class ResizeHandle extends AbstractGizmoHandle {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResizeHandle.class);
    
    /** 鎵嬫焺绫诲瀷 */
    public enum Type {
        /** 闈㈡墜鏌勶紙娌垮崟涓€杞寸缉鏀撅級 */
        FACE,
        /** 瑙掔偣鎵嬫焺锛堟部涓変釜杞寸缉鏀撅級 */
        CORNER
    }
    
    private final Type type;
    private final Axis[] axes;
    private final double radius;
    private final boolean maxFace;

    public ResizeHandle(String id, Type type, Vec3d position, double radius, Axis... axes) {
        this(id, type, position, radius, true, axes);
    }

    public ResizeHandle(String id, Type type, Vec3d position, double radius, boolean maxFace, Axis... axes) {
        super(id, position);
        this.type = type;
        this.radius = radius;
        this.maxFace = maxFace;
        this.axes = axes != null && axes.length > 0 ? axes : new Axis[0];
    }
    
    @Override
    public HitResult intersectRay(Vec3d rayOrigin, Vec3d rayDir) {
        // 浣跨敤鐞冧綋妫€娴嬶紙绮楃暐妫€娴嬶級
        double distance = RaycastUtil.rayToSphereDistance(rayOrigin, rayDir, position, radius);
        if (distance < Double.POSITIVE_INFINITY) {
            LOGGER.debug("[ResizeHandle] 鍛戒腑 {} 鎵嬫焺 (dist={})", type, distance);
            return new HitResult(this, distance);
        }
        return HitResult.miss();
    }
    
    @Override
    public void onDragStart(Vec3d rayOrigin, Vec3d rayDir) {
        super.onDragStart(rayOrigin, rayDir);
        LOGGER.debug("[ResizeHandle] 寮€濮嬫嫋鎷?{} 鎵嬫焺 at {}", type, dragStartHitPoint != null ? dragStartHitPoint : position);
    }
    
    @Override
    public Vec3d onDragUpdate(Vec3d rayOrigin, Vec3d rayDir) {
        if (!dragging || dragStartHitPoint == null) {
            return Vec3d.ZERO;
        }
        
        // 璁＄畻褰撳墠鍛戒腑鐐癸紙浣跨敤骞抽潰鎶曞奖锛?
        Vec3d currentHit = calculateDragPoint(rayOrigin, rayDir);
        if (currentHit == null) {
            currentHit = rayOrigin.add(rayDir.multiply(5.0)); // 鍥為€€锛氬浐瀹氳窛绂?
        }
        
        // 璁＄畻澧為噺
        Vec3d delta = currentHit.subtract(dragStartHitPoint);
        
        // 鏍规嵁鎵嬫焺绫诲瀷闄愬埗澧為噺鏂瑰悜
        Vec3d axisDelta = getAxisDelta(delta);
        
        // 鏇存柊鎷栨嫿璧风偣锛堢敤浜庝笅涓€娆¤绠楋級
        dragStartHitPoint = currentHit;
        
        return axisDelta;
    }
    
    /**
     * 璁＄畻鎷栨嫿鐐癸紙鏍规嵁鎵嬫焺绫诲瀷锛?
     */
    private Vec3d calculateDragPoint(Vec3d rayOrigin, Vec3d rayDir) {
        if (type == Type.FACE && axes.length > 0) {
            // 闈㈡墜鏌勶細鎶曞奖鍒板瀭鐩翠簬杞寸殑骞抽潰
            Vec3d axisVec = getAxisVector(axes[0]);
            Vec3d planePoint = position;
            Vec3d planeNormal = axisVec;
            
            return RaycastUtil.rayToPlaneIntersection(rayOrigin, rayDir, planePoint, planeNormal);
        } else {
            // 瑙掔偣鎵嬫焺锛氫娇鐢ㄦ渶杩戠殑杞存柟鍚戞姇褰?
            // 绠€鍖栧疄鐜帮細浣跨敤鍥哄畾璺濈鎶曞奖
            return null; // 杩斿洖 null 瑙﹀彂鍥為€€
        }
    }
    
    /**
     * 杩斿洖娌胯酱鏂瑰悜鐨勭缉鏀惧€硷紙鐢ㄤ簬浼犻€掔粰 TransformApplier锛?
     */
    public Vec3d getAxisDelta(Vec3d worldDelta) {
        double dx = 0, dy = 0, dz = 0;
        
        if (axes != null && axes.length > 0) {
            for (Axis a : axes) {
                switch (a) {
                    case X -> dx = worldDelta.x;
                    case Y -> dy = worldDelta.y;
                    case Z -> dz = worldDelta.z;
                }
            }
        } else {
            // 濡傛灉娌℃湁鎸囧畾杞达紝浣跨敤瀹屾暣澧為噺锛堣鐐规ā寮忥級
            dx = worldDelta.x;
            dy = worldDelta.y;
            dz = worldDelta.z;
        }
        
        return new Vec3d(dx, dy, dz);
    }
    
    /**
     * 鑾峰彇杞村悜閲?
     */
    private Vec3d getAxisVector(Axis axis) {
        return switch (axis) {
            case X -> new Vec3d(1, 0, 0);
            case Y -> new Vec3d(0, 1, 0);
            case Z -> new Vec3d(0, 0, 1);
        };
    }
    
    // ========== Getters & Setters ==========
    
    public Type getHandleType() {
        return type;
    }
    
    public Axis[] getAxes() {
        return axes != null ? axes.clone() : new Axis[0];
    }
    
    public double getRadius() {
        return radius;
    }

    public boolean isMaxFace() {
        return maxFace;
    }
    
    @Override
    public String toString() {
        return String.format("ResizeHandle[type=%s, axes=%d, pos=%s]", 
            type, axes != null ? axes.length : 0, position);
    }
}

