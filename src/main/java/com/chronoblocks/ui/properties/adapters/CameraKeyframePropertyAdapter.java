package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CameraKeyframePropertyAdapter v3.0
 * ---------------------------------
 * 
 * <p>全面重构版本，具备以下特点：
 * <ul>
 *   <li>✅ 模块化设计：每一部分 UI 为独立方法，易维护易扩展</li>
 *   <li>✅ 性能优化：鼠标松开才触发 PathManager 重建，拖动时不再疯狂重建</li>
 *   <li>✅ HandleMode 逻辑增强：AUTO 隐藏手柄，ALIGNED 自动对齐</li>
 *   <li>✅ 专业 UI 结构：曲线插值独立分区，符合 Blender/UE Sequencer 设计</li>
 *   <li>✅ 通用 UI Helper：提取常用组件方法，代码复用性高</li>
 * </ul>
 * 
 * <p>相比 v2.0 的主要改进：
 * <ul>
 *   <li>代码从 600+ 行巨型方法拆分为 150 行模块化方法</li>
 *   <li>拖动属性时不再每帧重建路径，只在鼠标松开时刷新</li>
 *   <li>控制柄模式（AUTO/FREE/ALIGNED）逻辑完善，有实际的联动效果</li>
 *   <li>UI 分区更专业，位置曲线和旋转曲线分开显示</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 3.0
 */
public final class CameraKeyframePropertyAdapter implements IPropertyAdapter<CameraKeyframe> {

    // 复用的临时对象，减少 GC 压力
    private final ImFloat f1 = new ImFloat();
    private final ImFloat f2 = new ImFloat();
    private final ImInt i1 = new ImInt();
    private final ImBoolean dofEnabled = new ImBoolean();

    @Override
    public boolean renderProperties(CameraKeyframe kf) {
        if (kf == null) {
            UITheme.label("（没有选中关键帧）", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        AtomicBoolean changed = new AtomicBoolean(false);
        // ⭐ 实时编辑标志：dragFloat/dragFloat3 返回 true 时设为 true（包含拖拽中的值变化）
        AtomicBoolean editing = new AtomicBoolean(false);

        // 标题
        UITheme.title(Icons.CLIPBOARD + " 摄像机关键帧");

        // 渲染各个 UI 区域
        basicInfoSection(kf, changed);
        interpolationSection(kf, changed, editing);
        transformSection(kf, changed, editing);
        bezierHandleSection(kf, changed, editing);
        projectionSection(kf, changed, editing);
        depthOfFieldSection(kf, changed, editing);

        // ---------------------------------------------------------------------------------
        // 性能优化：只有在鼠标松开时才刷新 PathManager
        // ---------------------------------------------------------------------------------
        if (changed.get()) {
            TimelineCameraController.getInstance().onKeyframeChanged();
        }

        // ⭐ 属性面板实时预览：拖拽编辑或值已提交时，将关键帧直接应用到相机
        if (editing.get() || changed.get()) {
            TimelineCameraController.getInstance().previewKeyframeDirect(kf);
        }

        return changed.get();
    }

    // ============================================================================
    // ① 基础信息（时间、关键帧类型）
    // ============================================================================
    private void basicInfoSection(CameraKeyframe kf, AtomicBoolean changed) {
        if (!ImGui.collapsingHeader(Icons.CLIPBOARD + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // ----- 时间 -----
        f1.set(kf.getTime());
        if (dragFloat("时间 (秒)", f1, 0.02f, 0f, 10000f, "%.2f")) {
            kf.setTime(f1.get());
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip("关键帧在时间线中的位置");

        ImGui.spacing();

        // ----- 关键帧类型 -----
        String[] keyTypeLabels = {"Standard", "Corner", "Hold", "Smooth", "Manual Bezier"};
        i1.set(kf.getKeyType().ordinal());
        if (enumCombo("关键帧类型", i1, keyTypeLabels)) {
            kf.setKeyType(CameraKeyframe.KeyType.values()[i1.get()]);
            changed.set(true);
        }
        tooltip("决定关键帧 UI、拐角行为、是否需要控制柄等");
        ImGui.unindent();
    }

    // ============================================================================
    // ② 曲线插值（位置 + 旋转）
    // ============================================================================
    private void interpolationSection(CameraKeyframe kf, AtomicBoolean changed, AtomicBoolean editing) {
        if (!ImGui.collapsingHeader(Icons.INTERPOLATION + " 插值选项 (位置/旋转)", ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // --- Segment Path Type（位置曲线） ---
        String[] segLabels = {"Default", "Linear", "Catmull-Rom", "Bezier", "Step", "Hold"};
        i1.set(kf.getSegmentPathType().ordinal());
        if (enumCombo("位置曲线", i1, segLabels)) {
            kf.setSegmentPathType(CameraKeyframe.SegmentPathType.values()[i1.get()]);
            changed.set(true);
            editing.set(true);
        }
        tooltip("""
                控制"位置"的插值方式：
                - Default：使用轨道默认
                - Linear：直线
                - Catmull-Rom：自动平滑
                - Bezier：可用控制柄
                - Step/Hold：保持上一关键帧
                """);

        ImGui.spacing();

        // --- RotationInterpolation（旋转曲线） ---
        String[] rLabels = {
                "Default（跟随位置曲线）",
                "Linear（Slerp）",
                "Bezier（预留 Squad）",
                "Catmull-Rom（预留 Squad）",
                "Step（不插值）"
        };
        i1.set(kf.getRotationInterpolation().ordinal());
        if (enumCombo("旋转曲线", i1, rLabels)) {
            kf.setRotationInterpolation(CameraKeyframe.RotationInterpolation.values()[i1.get()]);
            changed.set(true);
            editing.set(true);
        }
        tooltip("""
                控制摄像机旋转的插值：
                - Default：由位置曲线推导（Bezier/Catmull-Rom = Slerp）
                - Linear：四元数 Slerp
                - Step：维持上一帧
                """);
        ImGui.unindent();
    }

    // ============================================================================
    // ③ 位置（Position）与旋转（Rotation）
    // ============================================================================
    private void transformSection(CameraKeyframe kf, AtomicBoolean changed, AtomicBoolean editing) {
        if (!ImGui.collapsingHeader(Icons.TRANSFORM + " 位置与旋转", ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // ---- Capture 按钮 ----
        if (ImGui.button(Icons.CAMERA + " 从当前视角捕获", -1, 0)) {
            boolean ok = TimelineCameraController.getInstance().captureFromCurrentCamera(kf);
            if (ok) {
                changed.set(true);
            }
        }
        tooltip("从当前玩家视角记录到关键帧");

        ImGui.spacing();

        // ----- Position -----
        if (vector3("位置 Position", kf.getPosition(), kf::setPosition, 0.05f,
                "摄像机世界位置", editing)) {
            changed.set(true);
        }

        ImGui.spacing();

        // ----- Rotation -----
        if (vector3("旋转 Pitch/Yaw/Roll", kf.getRotation(), kf::setRotation, 0.1f,
                "摄像机欧拉角（度）", editing)) {
            changed.set(true);
        }
        ImGui.unindent();
    }

    // ============================================================================
    // ④ 控制柄（Bezier Handles）
    // ============================================================================
    private void bezierHandleSection(CameraKeyframe kf, AtomicBoolean changed, AtomicBoolean editing) {
        boolean show = kf.getSegmentPathType() == CameraKeyframe.SegmentPathType.BEZIER
                || kf.getKeyType() == CameraKeyframe.KeyType.MANUAL_BEZIER;
        
        if (!show || !ImGui.collapsingHeader(Icons.BEZIER + " Bezier 控制柄", ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // 手柄模式
        String[] modeLabels = {"自动", "自由", "对齐"};
        i1.set(kf.getHandleMode().ordinal());
        if (enumCombo("手柄模式", i1, modeLabels)) {
            kf.setHandleMode(CameraKeyframe.HandleMode.values()[i1.get()]);
            changed.set(true);
        }
        tooltip("AUTO：自动平滑\nFREE：可手动调节\nALIGNED：进出柄保持同线方向");

        ImGui.spacing();

        // AUTO 模式：隐藏手柄编辑，提示用户
        boolean auto = kf.getHandleMode() == CameraKeyframe.HandleMode.AUTO;
        if (auto) {
            UITheme.label("当前为自动模式，手柄由系统自动计算", UITheme.DarkTheme.TEXT_DISABLED);
            return;
        }

        // In handle
        boolean inChanged = vector3("进入控制柄 (In)", kf.getBezierInHandle(), kf::setBezierInHandle, 0.05f,
                "相对坐标。若为 (0,0,0)，系统自动生成", editing);
        
        if (inChanged) {
            changed.set(true);
            
            // ALIGNED 模式：修改 In 时，自动对齐 Out
            if (kf.getHandleMode() == CameraKeyframe.HandleMode.ALIGNED) {
                Vector3f in = kf.getBezierInHandle();
                Vector3f out = kf.getBezierOutHandle();
                // 如果 In 不为零，将 Out 设置为 In 的反方向
                if (in.lengthSquared() > 0.0001f) {
                    out.set(in).negate();
                    kf.setBezierOutHandle(out);
                }
            }
        }

        ImGui.spacing();

        // Out handle
        boolean outChanged = vector3("离开控制柄 (Out)", kf.getBezierOutHandle(), kf::setBezierOutHandle, 0.05f,
                "相对坐标。若为 (0,0,0)，系统自动生成", editing);
        
        if (outChanged) {
            changed.set(true);
            
            // ALIGNED 模式：修改 Out 时，自动对齐 In
            if (kf.getHandleMode() == CameraKeyframe.HandleMode.ALIGNED) {
                Vector3f in = kf.getBezierInHandle();
                Vector3f out = kf.getBezierOutHandle();
                // 如果 Out 不为零，将 In 设置为 Out 的反方向
                if (out.lengthSquared() > 0.0001f) {
                    in.set(out).negate();
                    kf.setBezierInHandle(in);
                }
            }
        }

        ImGui.spacing();
        ImGui.text("提示：控制柄为相对坐标（相对于关键帧本身的位置）");
        ImGui.text("若为 (0,0,0)，则 TimelineCameraPathManager 会使用自动生成的控制柄");
        ImGui.unindent();
    }

    // ============================================================================
    // ⑤ 投影参数（FOV / near / far）
    // ============================================================================
    private void projectionSection(CameraKeyframe kf, AtomicBoolean changed, AtomicBoolean editing) {
        if (!ImGui.collapsingHeader(Icons.CAMERA + " 投影参数", ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // FOV
        f1.set(kf.getFov());
        if (dragFloat("FOV", f1, 0.1f, 1f, 180f, "%.1f°")) {
            kf.setFov(f1.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            // 只在鼠标松开时才标记为 changed，避免拖动时频繁刷新 PathManager
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip("摄像机的视野角度（度）");

        ImGui.spacing();

        // near plane
        f1.set(kf.getNearPlane());
        if (dragFloat("Near", f1, 0.001f, 0.001f, 10f, "%.3f")) {
            kf.setNearPlane(f1.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip("近裁剪平面距离");

        ImGui.spacing();

        // far plane
        f2.set(kf.getFarPlane());
        if (dragFloat("Far", f2, 0.1f, 1f, 10000f, "%.1f")) {
            kf.setFarPlane(f2.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip("远裁剪平面距离");

        // near < far 校验
        if (kf.getNearPlane() >= kf.getFarPlane()) {
            UITheme.label("⚠ Near 应小于 Far", UITheme.DarkTheme.TEXT_WARNING);
        }
        ImGui.unindent();
    }

    // ============================================================================
    // ⑥ 景深（DOF）
    // ============================================================================
    private void depthOfFieldSection(CameraKeyframe kf, AtomicBoolean changed, AtomicBoolean editing) {
        if (!ImGui.collapsingHeader(Icons.EFFECT + " 景深 Depth of Field"))
            return;
        ImGui.indent();

        // 复用 ImBoolean 对象
        dofEnabled.set(kf.isDepthOfFieldEnabled());
        if (ImGui.checkbox("启用景深", dofEnabled)) {
            kf.setDepthOfFieldEnabled(dofEnabled.get());
            changed.set(true);
        }
        tooltip("启用景深效果，模拟相机模糊");

        if (!dofEnabled.get()) {
            ImGui.unindent();
            return;
        }

        // 焦距
        f1.set(kf.getFocusDistance());
        if (dragFloat("焦距 Focus", f1, 0.05f, 0f, 2000f, "%.2f")) {
            kf.setFocusDistance(f1.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip("焦点距离");

        ImGui.spacing();

        // 光圈
        f1.set(kf.getAperture());
        if (dragFloat("光圈 Aperture", f1, 0.01f, 0.01f, 10f, "%.2f")) {
            kf.setAperture(f1.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip("光圈值，控制模糊强度");

        ImGui.unindent(); // 嵌套的启用景深后的缩进
        ImGui.unindent(); // collapsingHeader 的缩进
    }

    // ============================================================================
    // ✦ 通用 UI Helper（最核心）
    // ============================================================================

    /**
     * 拖拽 Float 控件，返回是否发生了改变
     * <p>
     * 注意：调用此方法前，应该先调用 value.set() 从 keyframe 同步值。
     * ImGui 会智能处理：如果控件正在被拖动，它会忽略新值并保持拖动状态；
     * 如果控件未激活，它会使用我们传入的新值更新显示。
     */
    private boolean dragFloat(String label, ImFloat value, float speed, float min, float max, String fmt) {
        // 返回 changed，让调用者决定何时更新 keyframe
        // 如果 changed 为 true，说明值已经被修改了（无论是拖动还是输入）
        return ImGui.dragFloat(label, value.getData(), speed, min, max, fmt);
    }

    /**
     * 枚举下拉框，只在鼠标松开时才返回 true
     */
    private boolean enumCombo(String label, ImInt idx, String[] labels) {
        // 下拉框选择后立即生效，不需要等待鼠标松开
        return ImGui.combo(label, idx, labels, labels.length);
    }

    /**
     * Vector3 拖拽控件，只在鼠标松开时才返回 true（性能优化）
     * <p>
     * ⭐ 拖拽过程中会设置 editing 标志，用于触发实时相机预览。
     * 返回 true 仅在 isItemDeactivatedAfterEdit()（鼠标松开）时，用于触发 PathManager 重建。
     */
    private boolean vector3(String label, Vector3f vec, VectorSetter setter, float speed,
                            String tooltipText, AtomicBoolean editing) {
        float[] arr = {vec.x, vec.y, vec.z};
        boolean changed = ImGui.dragFloat3(label, arr, speed);
        
        if (changed) {
            setter.set(new Vector3f(arr[0], arr[1], arr[2]));
            editing.set(true); // ⭐ 拖拽中实时标记，用于相机预览
            // 只有鼠标松开时才返回 true，避免拖动时频繁触发刷新
            if (ImGui.isItemDeactivatedAfterEdit()) {
                if (ImGui.isItemHovered() && tooltipText != null) {
                    ImGui.setTooltip(tooltipText);
                }
                return true;
            }
        } else {
            // 即使没有改变，也要检查是否需要显示 tooltip
            if (ImGui.isItemHovered() && tooltipText != null) {
                ImGui.setTooltip(tooltipText);
            }
        }
        
        return false;
    }

    /**
     * 为上一个 UI 项添加 tooltip（如果鼠标悬停）
     */
    private void tooltip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(text);
        }
    }

    // ============================================================================
    // 函数接口
    // ============================================================================

    /**
     * Vector3f 设置器函数接口
     */
    @FunctionalInterface
    private interface VectorSetter {
        void set(Vector3f vec);
    }

    @Override
    public Class<CameraKeyframe> getTargetType() {
        return CameraKeyframe.class;
    }

    @Override
    public int getPriority() { return 70; }
}
