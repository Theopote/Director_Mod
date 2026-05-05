package com.director.client.gizmo;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.director.client.gizmo.handles.GizmoHandle;
import com.director.client.gizmo.handles.RotationHandle;
import com.director.client.gizmo.logic.TransformApplier;
import com.director.client.gizmo.util.SnapUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 馃Л GizmoInteractionController - Gizmo 浜や簰鎺у埗鍣?
 * <p>
 * 璐熻矗鍏夌嚎妫€娴嬨€丠andle 妫€娴嬩紭鍏堢骇銆佹嫋鎷界姸鎬佹満
 * 
 * @author Director Team
 * @version 1.0
 */
public class GizmoInteractionController {
    private GizmoContext context;
    private GizmoState state = GizmoState.IDLE;
    private GizmoHandle hoveredHandle = null;
    private GizmoHandle activeHandle = null;
    private RotationHandle activeRotationHandle = null;
    private List<GizmoHandle> handles = new ArrayList<>();
    private List<RotationHandle> rotationHandles = new ArrayList<>();
    private List<com.director.client.gizmo.handles.ResizeHandle> resizeHandles = new ArrayList<>();
    private TransformApplier transformApplier;

    // BOUNDS Gizmo锛氱Щ鍔ㄢ€滃寘鍥寸洅(鍖哄煙)鈥濋瑙堬紝鏉炬墜鍚庢寜杩囨护鍣ㄩ噸寤洪€夊尯
    private final com.director.core.selection.SelectionManager selectionManager =
        com.director.core.selection.SelectionManager.get();
    private Vec3d boundsMoveCarry = Vec3d.ZERO;
    
    public GizmoInteractionController(GizmoContext context) {
        this.context = context;
        // 鍒濆鍖?TransformApplier
        this.transformApplier = new TransformApplier(
            com.director.core.selection.SelectionManager.get()
        );
    }
    
    public void setTransformApplier(TransformApplier applier) {
        this.transformApplier = applier;
    }
    
    /**
     * 鏇存柊浜や簰鐘舵€?
     * 
     * @param rayOrigin 灏勭嚎璧风偣
     * @param rayDir 灏勭嚎鏂瑰悜锛堝綊涓€鍖栵級
     * @param mouseDown 榧犳爣鏄惁鎸変笅
     * @param mouseReleased 榧犳爣鏄惁閲婃斁
     */
    public void update(Vec3d rayOrigin, Vec3d rayDir, boolean mouseDown, boolean mouseReleased) {
        if (state == GizmoState.DRAGGING) {
            if (mouseReleased) {
                // 缁撴潫鎷栨嫿
                endDrag();
            } else if (mouseDown && (activeHandle != null || activeRotationHandle != null)) {
                // 鏇存柊鎷栨嫿
                updateDrag(rayOrigin, rayDir);
            }
        } else {
            if (mouseDown) {
                // Mouse-down frame may arrive before a hover frame.
                // Refresh hover first so click-drag can start immediately.
                detectHover(rayOrigin, rayDir);
                // 寮€濮嬫嫋鎷?
                startDrag(rayOrigin, rayDir);
            } else {
                // 妫€娴嬫偓鍋?
                detectHover(rayOrigin, rayDir);
            }
        }
    }
    
    /**
     * 妫€娴嬫偓鍋?
     */
    private void detectHover(Vec3d rayOrigin, Vec3d rayDir) {
        GizmoHandle closestHandle = null;
        RotationHandle closestRotationHandle = null;
        double closestDistance = Double.POSITIVE_INFINITY;
        
        // 浼樺厛妫€娴嬫棆杞幆锛堝鏋滃瓨鍦級
        for (RotationHandle rotHandle : rotationHandles) {
            double distance = rotHandle.intersectRay(rayOrigin, rayDir);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestRotationHandle = rotHandle;
                closestHandle = null; // 鏃嬭浆鐜紭鍏?
            }
        }
        
        // 妫€娴?ResizeHandle锛堜紭鍏堢骇楂樹簬鏅€?Handle锛?
        for (com.director.client.gizmo.handles.ResizeHandle resizeHandle : resizeHandles) {
            HitResult hit = resizeHandle.intersectRay(rayOrigin, rayDir);
            if (hit.isHit() && hit.getDistance() < closestDistance) {
                closestDistance = hit.getDistance();
                closestHandle = resizeHandle;
                closestRotationHandle = null;
            }
        }
        
        // 妫€娴嬫櫘閫?Handle
        for (GizmoHandle handle : handles) {
            HitResult hit = handle.intersectRay(rayOrigin, rayDir);
            if (hit.isHit() && hit.getDistance() < closestDistance) {
                closestDistance = hit.getDistance();
                closestHandle = handle;
                closestRotationHandle = null; // 鏅€?Handle 浼樺厛浜庢棆杞幆
            }
        }
        
        if (closestRotationHandle != null) {
            // 鏃嬭浆鐜偓鍋滐紙褰撳墠绠€鍖栵紝涓嶆樉绀烘偓鍋滅姸鎬侊級
            hoveredHandle = null;
            state = GizmoState.HOVER;
        } else if (closestHandle != null) {
            if (hoveredHandle != closestHandle) {
                hoveredHandle = closestHandle;
                state = GizmoState.HOVER;
            }
        } else {
            hoveredHandle = null;
            state = GizmoState.IDLE;
        }
    }
    
    /**
     * 寮€濮嬫嫋鎷?
     */
    private void startDrag(Vec3d rayOrigin, Vec3d rayDir) {
        // 浼樺厛妫€娴嬫棆杞幆
        for (RotationHandle rotHandle : rotationHandles) {
            double distance = rotHandle.intersectRay(rayOrigin, rayDir);
            if (distance < Double.POSITIVE_INFINITY) {
                activeRotationHandle = rotHandle;
                rotHandle.beginRotation(rayOrigin, rayDir);
                state = GizmoState.DRAGGING;
                return;
            }
        }
        
        // 妫€娴嬫櫘閫?Handle
        if (hoveredHandle != null) {
            activeHandle = hoveredHandle;
            if (transformApplier != null) {
                transformApplier.resetAccumulators();
            }
            if (context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle)) {
                boundsMoveCarry = Vec3d.ZERO;
                selectionManager.setBoundsGizmoMovePreviewBounds(context.getTargetBox());
            }
            activeHandle.onDragStart(rayOrigin, rayDir);
            context.setActiveHandle(activeHandle);
            state = GizmoState.DRAGGING;
        }
    }
    
    /**
     * 鏇存柊鎷栨嫿
     * 
     * 瀹炴椂鑱斿姩娴佺▼锛?
     * - 璁＄畻鎷栨嫿澧為噺锛坉elta锛?
     * - 搴旂敤鍙樻崲锛堥€氳繃 TransformApplier锛?
     * - TransformApplier 鈫?SelectionManager.expandSelection()
     * - SelectionManager 鑷姩鏇存柊棰勮妗嗭紙閫氳繃 autoPreviewEnabled锛?
     */
    private void updateDrag(Vec3d rayOrigin, Vec3d rayDir) {
        if (activeRotationHandle != null) {
            // 鏇存柊鏃嬭浆
            activeRotationHandle.updateRotation(rayOrigin, rayDir);
            double deltaAngle = activeRotationHandle.getDeltaAngle();
            
            // 搴旂敤鏃嬭浆锛堥€氳繃 TransformApplier锛?
            if (transformApplier != null && Math.abs(deltaAngle) > 1e-6) {
                transformApplier.applyRotation(deltaAngle, activeRotationHandle.getAxis());
            }
        } else if (activeHandle != null) {
            Vec3d delta = activeHandle.onDragUpdate(rayOrigin, rayDir);
            
            // 搴旂敤鍚搁檮
            if (context.getSnapStep() > 0) {
                delta = SnapUtil.snapVec(delta, context.getSnapStep(), null);
            }
            
            // 馃З 搴旂敤鍙樻崲锛堝疄鏃惰仈鍔?SelectionManager锛?
            // - ResizeHandle 鈫?applyResize() 鈫?expandSelection() 鈫?鑷姩鏇存柊棰勮妗?
            // - AxisHandle/PlaneHandle 鈫?applyMove() 鈫?offsetSelection() 鈫?鑷姩鏇存柊棰勮妗?
            applyTransform(delta);
            
            // 鉁?BOUNDS Gizmo锛堢Щ鍔ㄥ寘鍥寸洅锛夋嫋鎷戒腑涓嶈鐢ㄢ€滃綋鍓嶉€夊尯 bounds鈥濊鐩?targetBox锛?
            // 鍚﹀垯浼氬嚭鐜扳€淕izmo 鑳藉姩浣嗗寘鍥寸洅涓嶅姩鈥濈殑鐜拌薄锛堝洜涓洪€夊尯姝ゆ椂杩樻病閲嶅缓锛夈€?
            // 猸?TRANSFORM Gizmo锛圓ctor 缂栬緫锛変笉搴旇浠庢柟鍧楅€夊尯鏇存柊 Box
            if (context != null && context.getType() == GizmoType.TRANSFORM) {
                // TRANSFORM Gizmo锛欱ox 鐢?ActorGizmoController.updateGizmoPosition() 绠＄悊锛屼笉浠庨€夊尯鏇存柊
            } else if (!(context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle))) {
                updateBoxFromSelection();
            }
        }
    }
    
    /**
     * 缁撴潫鎷栨嫿
     * <p>
     * 瀹炴椂鑱斿姩娴佺▼锛?
     * - 缁撴潫鎷栨嫿鐘舵€?
     * - 鏇存柊 Box锛堜粠 SelectionManager 鍚屾锛?
     * - 锛堝彲閫夛級娓呴櫎棰勮妗嗘垨淇濇寔鏄剧ず
     */
    private void endDrag() {
        if (activeRotationHandle != null) {
            activeRotationHandle.endRotation();
            activeRotationHandle = null;
            state = GizmoState.IDLE;
        } else if (activeHandle != null) {
            activeHandle.onDragEnd();
            if (context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle)) {
                Box moved = context.getTargetBox();
                selectionManager.clearBoundsGizmoMovePreviewBounds();
                selectionManager.rebuildSelectionFromBoundsForBoundsGizmo(moved);
            }
            activeHandle = null;
            if (context != null) {
                context.setActiveHandle(null);
            }
            if (transformApplier != null) {
                transformApplier.resetAccumulators();
            }
            state = GizmoState.IDLE;
            
            // 猸?TRANSFORM Gizmo锛圓ctor 缂栬緫锛変笉搴旇浠庢柟鍧楅€夊尯鏇存柊 Box
            // 鏇存柊 Box锛堜粠 SelectionManager 鍚屾锛岀‘淇?Gizmo 涓庨€夊尯涓€鑷达級
            if (context == null || context.getType() != GizmoType.TRANSFORM) {
                updateBoxFromSelection();
            }
            
            // 馃З 鍙€夛細娓呴櫎棰勮妗?
            // 娉ㄩ噴锛氬鏋滃笇鏈涙嫋鎷界粨鏉熷悗淇濇寔棰勮妗嗘樉绀猴紝鍙互娉ㄩ噴鎺変笅闈㈢殑浠ｇ爜
            // 褰撳墠瀹炵幇锛氫繚鎸侀瑙堟鏄剧ず锛屽洜涓?SelectionManager 宸茬粡鑷姩鏇存柊浜嗛瑙堟
            // 濡傛灉闇€瑕佹竻闄わ紝鍙互鍙栨秷娉ㄩ噴锛?
            // try {
            //     com.director.client.renderer.SelectionRenderer.getInstance().clearPreview();
            // } catch (Exception e) {
            //     // 濡傛灉 SelectionRenderer 涓嶅彲鐢紝蹇界暐閿欒
            // }
        }
        
        // 娓呴櫎鎮仠鐘舵€侊紝寮哄埗閲嶆柊妫€娴嬶紙榧犳爣鍙兘宸茬粡涓嶅湪鎵嬫焺涓婁簡锛?
        hoveredHandle = null;
    }
    
    /**
     * 搴旂敤鍙樻崲锛堟牴鎹?Handle 绫诲瀷锛?
     * <p>
     * 浣跨敤 TransformApplier 灏嗗彉鎹㈠簲鐢ㄥ埌閫夊尯鎴栧姩鐢荤洰鏍?
     */
    private void applyTransform(Vec3d delta) {
        if (transformApplier == null) {
            // 鍥為€€鍒版棫鏂规硶锛堢洿鎺ヤ慨鏀?Box锛?
            applyTransformLegacy(delta);
            return;
        }

        // 鉁?BOUNDS Gizmo锛氳酱/骞抽潰/涓績鎵嬫焺绉诲姩鈥滃寘鍥寸洅棰勮鈥濓紝涓嶅钩绉?selectionSet
        if (context != null && context.getType() == GizmoType.BOUNDS && isMoveHandle(activeHandle)) {
            applyBoundsMove(delta);
            return;
        }
        
        // 浣跨敤 TransformApplier 搴旂敤鍙樻崲
        if (activeHandle instanceof com.director.client.gizmo.handles.ResizeHandle resizeHandle) {
            // ResizeHandle锛氭寜鍏蜂綋闈㈠悜搴旂敤缂╂斁
            Vec3d axisDelta = resizeHandle.getAxisDelta(delta);
            Axis[] axes = resizeHandle.getAxes();
            if (resizeHandle.getHandleType() == com.director.client.gizmo.handles.ResizeHandle.Type.FACE && axes.length == 1) {
                double signedDelta = switch (axes[0]) {
                    case X -> axisDelta.x;
                    case Y -> axisDelta.y;
                    case Z -> axisDelta.z;
                };
                transformApplier.applyResize(axes[0], resizeHandle.isMaxFace(), signedDelta);
            } else {
                transformApplier.applyResize(axisDelta);
            }
        } else if (activeHandle instanceof com.director.client.gizmo.handles.AxisHandle) {
            // 杞存墜鏌勶細骞崇Щ閫夊尯
            transformApplier.applyMove(delta);
        } else if (activeHandle instanceof com.director.client.gizmo.handles.PlaneHandle) {
            // 骞抽潰鎵嬫焺锛氬湪骞抽潰涓婂钩绉婚€夊尯
            transformApplier.applyMove(delta);
        } else if (activeHandle instanceof com.director.client.gizmo.handles.CornerHandle corner) {
            // 瑙掔偣鎵嬫焺锛氫粠瑙掔偣璋冩暣閫夊尯澶у皬
            transformApplier.applyResizeFromCorner(delta, corner.getCornerSign());
        } else if (activeHandle instanceof com.director.client.gizmo.handles.CenterHandle) {
            // 涓績鎵嬫焺锛氬钩绉婚€夊尯锛堢Щ鍔ㄨ酱蹇冪偣浼氬悓鏃跺钩绉婚€夊尯锛?
            transformApplier.applyMove(delta);
            // 鏇存柊杞村績鐐?
            Vec3d newPivot = context.getPivot().add(delta);
            context.setPivot(newPivot);
        }
        
        // 猸?TRANSFORM Gizmo锛圓ctor 缂栬緫锛変笉搴旇浠庢柟鍧楅€夊尯鏇存柊 Box
        // 鏇存柊 Box锛堜粠 SelectionManager 鍚屾锛?
        if (context == null || context.getType() != GizmoType.TRANSFORM) {
            updateBoxFromSelection();
        }
    }

    private boolean isMoveHandle(GizmoHandle handle) {
        return handle instanceof com.director.client.gizmo.handles.AxisHandle
            || handle instanceof com.director.client.gizmo.handles.PlaneHandle
            || handle instanceof com.director.client.gizmo.handles.CenterHandle;
    }

    private int extractStep(double carry) {
        if (carry >= 1.0) return (int) Math.floor(carry);
        if (carry <= -1.0) return (int) Math.ceil(carry);
        return 0;
    }

    private void applyBoundsMove(Vec3d delta) {
        if (context == null) return;

        // 鍚搁檮淇濇寔涓€鑷?
        if (context.getSnapStep() > 0) {
            delta = SnapUtil.snapVec(delta, context.getSnapStep(), null);
        }

        boundsMoveCarry = boundsMoveCarry.add(delta);

        int stepX = extractStep(boundsMoveCarry.x);
        int stepY = extractStep(boundsMoveCarry.y);
        int stepZ = extractStep(boundsMoveCarry.z);
        if (stepX == 0 && stepY == 0 && stepZ == 0) {
            return;
        }

        boundsMoveCarry = new Vec3d(boundsMoveCarry.x - stepX, boundsMoveCarry.y - stepY, boundsMoveCarry.z - stepZ);

        Box box = context.getTargetBox();
        Vec3d pivot = context.getPivot();
        if (box == null || pivot == null) return;

        Box moved = box.offset(stepX, stepY, stepZ);
        Vec3d movedPivot = pivot.add(stepX, stepY, stepZ);

        context.setTargetBox(moved);
        context.setPivot(movedPivot);

        // 鍙洿鏂扳€滃寘鍥寸洅棰勮鈥濓紝閲嶅缓閫夊尯鍦ㄦ澗鎵嬫椂鍋?
        selectionManager.setBoundsGizmoMovePreviewBounds(moved);
    }
    
    /**
     * 鏃х増鍙樻崲搴旂敤鏂规硶锛堢洿鎺ヤ慨鏀?Box锛?
     */
    private void applyTransformLegacy(Vec3d delta) {
        Box currentBox = context.getTargetBox();
        Box newBox = currentBox;
        
        if (activeHandle instanceof com.director.client.gizmo.handles.AxisHandle) {
            newBox = com.director.client.gizmo.util.TransformUtil.translateBox(currentBox, delta);
        } else if (activeHandle instanceof com.director.client.gizmo.handles.PlaneHandle) {
            newBox = com.director.client.gizmo.util.TransformUtil.translateBox(currentBox, delta);
        } else if (activeHandle instanceof com.director.client.gizmo.handles.CornerHandle corner) {
            newBox = com.director.client.gizmo.util.TransformUtil.resizeBoxFromCorner(
                currentBox, delta, corner.getCornerSign()
            );
        } else if (activeHandle instanceof com.director.client.gizmo.handles.CenterHandle) {
            Vec3d newPivot = context.getPivot().add(delta);
            context.setPivot(newPivot);
            newBox = com.director.client.gizmo.util.TransformUtil.translateBox(currentBox, delta);
        }
        
        context.setTargetBox(newBox);
    }
    
    /**
     * 浠?SelectionManager 鍚屾 Box
     */
    private void updateBoxFromSelection() {
        var selectionManager = com.director.core.selection.SelectionManager.get();
        var selectionSet = selectionManager.getSelectionSet();
        
        if (selectionSet.isEmpty()) {
            return;
        }
        
        var blocks = selectionSet.asUnmodifiableSet();
        if (blocks.isEmpty()) {
            return;
        }

        Box newBox = getBox(blocks);
        context.setTargetBox(newBox);

        // 鉁?BOUNDS Gizmo锛歱ivot 姘歌繙鏄寘鍥寸洅鍑犱綍涓績
        if (context != null && context.getType() == GizmoType.BOUNDS) {
            Vec3d center = new Vec3d(
                (newBox.minX + newBox.maxX) / 2.0,
                (newBox.minY + newBox.maxY) / 2.0,
                (newBox.minZ + newBox.maxZ) / 2.0
            );
            context.setPivot(center);
        }
    }

    private static @NotNull Box getBox(Set<BlockPos> blocks) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    // ========== Getters & Setters ==========
    
    public void setHandles(List<GizmoHandle> handles) {
        this.handles = new ArrayList<>(handles);
    }
    
    public GizmoState getState() {
        return state;
    }
    
    public GizmoHandle getHoveredHandle() {
        return hoveredHandle;
    }
    
    public GizmoHandle getActiveHandle() {
        return activeHandle;
    }
    
    public List<GizmoHandle> getHandles() {
        return new ArrayList<>(handles);
    }
    
    public void setRotationHandles(List<RotationHandle> rotationHandles) {
        this.rotationHandles = new ArrayList<>(rotationHandles);
    }
    
    public List<RotationHandle> getRotationHandles() {
        return new ArrayList<>(rotationHandles);
    }
    
    public RotationHandle getActiveRotationHandle() {
        return activeRotationHandle;
    }
    
    public TransformApplier getTransformApplier() {
        return transformApplier;
    }
    
    public void setResizeHandles(List<com.director.client.gizmo.handles.ResizeHandle> resizeHandles) {
        this.resizeHandles = new ArrayList<>(resizeHandles);
    }
    
    public List<com.director.client.gizmo.handles.ResizeHandle> getResizeHandles() {
        return new ArrayList<>(resizeHandles);
    }
}

