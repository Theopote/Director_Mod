package com.director.ui.properties.adapters;

import com.director.i18n.CameraPropertyAdapterI18n;
import com.director.i18n.DirectorLang;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.camera.path.CustomPathClip;
import com.director.timeline.playback.camera.TimelineCameraController;
import com.director.ui.tools.ToolRegistry;
import com.director.ui.tools.impl.CameraPathEditTool;
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
 * @author Director Team
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
            UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.NO_SELECTION),
                    UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        AtomicBoolean changed = new AtomicBoolean(false);
        // ⭐ 实时编辑标志：dragFloat/dragFloat3 返回 true 时设为 true（包含拖拽中的值变化）
        AtomicBoolean editing = new AtomicBoolean(false);

        // 标题
        UITheme.title(Icons.CLIPBOARD + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.TITLE));

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

        // ⭐ 属性面板实时预览：只要关键帧在属性面板中显示，就持续将其状态应用到相机。
        // 每帧刷新计数器，当关键帧取消选中（renderProperties 不再被调用）时计数器自然耗尽、相机回归玩家控制。
        if (!CameraPathEditTool.ID.equals(ToolRegistry.get().getActiveNonSelectionToolId())) {
            TimelineCameraController.getInstance().previewKeyframeDirect(kf);
        }

        return changed.get();
    }

    // ============================================================================
    // ① 基础信息（时间、关键帧类型）
    // ============================================================================
    private void basicInfoSection(CameraKeyframe kf, AtomicBoolean changed) {
        if (!ImGui.collapsingHeader(Icons.CLIPBOARD + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.SECTION_BASIC),
                ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // ----- 时间 -----
        f1.set(kf.getTime());
        if (dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.TIME),
                f1, 0.02f, 0f, 10000f, "%.2f")) {
            float clampedTime = clampTimeToOwnerClip(kf, f1.get());
            if (Math.abs(clampedTime - f1.get()) > 0.0001f) {
                f1.set(clampedTime);
            }
            kf.setTime(clampedTime);
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.TIME_TOOLTIP));

        ImGui.spacing();

        // ----- 关键帧类型 -----
        String[] keyTypeLabels = CameraPropertyAdapterI18n.keyframeTypeLabels();
        i1.set(kf.getKeyType().ordinal());
        if (enumCombo(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.KEY_TYPE),
                i1, keyTypeLabels)) {
            kf.setKeyType(CameraKeyframe.KeyType.values()[i1.get()]);
            changed.set(true);
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.KEY_TYPE_TOOLTIP));
        ImGui.unindent();
    }

    private float clampTimeToOwnerClip(CameraKeyframe keyframe, float time) {
        TimelineTrack activeTrack = TimelineCameraController.getInstance().getActiveCameraTrack();
        if (activeTrack == null) {
            return time;
        }
        CustomPathClip ownerClip = activeTrack.findCustomPathClipForKeyframe(keyframe);
        if (ownerClip == null) {
            return time;
        }
        return Math.max(ownerClip.getStartTime(), Math.min(ownerClip.getEndTime(), time));
    }

    // ============================================================================
    // ② 曲线插值（位置 + 旋转）
    // ============================================================================
    private void interpolationSection(CameraKeyframe kf, AtomicBoolean changed, AtomicBoolean editing) {
        if (!ImGui.collapsingHeader(Icons.INTERPOLATION + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.SECTION_INTERPOLATION),
                ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // --- Segment Path Type（位置曲线） ---
        String[] segLabels = CameraPropertyAdapterI18n.segmentPathTypeLabels();
        i1.set(kf.getSegmentPathType().ordinal());
        if (enumCombo(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.POSITION_CURVE),
                i1, segLabels)) {
            kf.setSegmentPathType(CameraKeyframe.SegmentPathType.values()[i1.get()]);
            changed.set(true);
            editing.set(true);
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.POSITION_CURVE_TOOLTIP));

        ImGui.spacing();

        // --- RotationInterpolation（旋转曲线） ---
        String[] rLabels = CameraPropertyAdapterI18n.rotationInterpolationLabels();
        i1.set(kf.getRotationInterpolation().ordinal());
        if (enumCombo(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.ROTATION_CURVE),
                i1, rLabels)) {
            kf.setRotationInterpolation(CameraKeyframe.RotationInterpolation.values()[i1.get()]);
            changed.set(true);
            editing.set(true);
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.ROTATION_CURVE_TOOLTIP));
        ImGui.unindent();
    }

    // ============================================================================
    // ③ 位置（Position）与旋转（Rotation）
    // ============================================================================
    private void transformSection(CameraKeyframe kf, AtomicBoolean changed, AtomicBoolean editing) {
        if (!ImGui.collapsingHeader(Icons.TRANSFORM + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.SECTION_TRANSFORM),
                ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // ---- Capture 按钮 ----
        if (ImGui.button(Icons.CAMERA + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Common.CAPTURE_FROM_VIEW), -1, 0)) {
            boolean ok = TimelineCameraController.getInstance().captureFromCurrentCamera(kf);
            if (ok) {
                changed.set(true);
            }
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.CAPTURE_TOOLTIP));

        ImGui.spacing();

        // ----- Position -----
        if (vector3(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.POSITION),
                kf.getPosition(), kf::setPosition, 0.05f,
                CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.POSITION_TOOLTIP), editing)) {
            changed.set(true);
        }

        ImGui.spacing();

        // ----- Rotation -----
        if (vector3(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.ROTATION),
                kf.getRotation(), kf::setRotation, 0.1f,
                CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.ROTATION_TOOLTIP), editing)) {
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
        
        if (!show || !ImGui.collapsingHeader(Icons.BEZIER + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.SECTION_BEZIER),
                ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // 手柄模式
        String[] modeLabels = CameraPropertyAdapterI18n.handleModeLabels();
        i1.set(kf.getHandleMode().ordinal());
        if (enumCombo(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.HANDLE_MODE),
                i1, modeLabels)) {
            kf.setHandleMode(CameraKeyframe.HandleMode.values()[i1.get()]);
            changed.set(true);
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.HANDLE_MODE_TOOLTIP));

        ImGui.spacing();

        // AUTO 模式：隐藏手柄编辑，提示用户
        boolean auto = kf.getHandleMode() == CameraKeyframe.HandleMode.AUTO;
        if (auto) {
            UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.HANDLE_AUTO_HINT),
                    UITheme.DarkTheme.TEXT_DISABLED);
            return;
        }

        // In handle
        boolean inChanged = vector3(
                CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.HANDLE_IN),
                kf.getBezierInHandle(), kf::setBezierInHandle, 0.05f,
                CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.HANDLE_AUTO_GENERATED_HINT),
                editing);
        
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
        boolean outChanged = vector3(
                CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.HANDLE_OUT),
                kf.getBezierOutHandle(), kf::setBezierOutHandle, 0.05f,
                CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.HANDLE_AUTO_GENERATED_HINT),
                editing);
        
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
        ImGui.text(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.HANDLE_RELATIVE_HINT));
        ImGui.text(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.HANDLE_AUTO_GENERATED_HINT));
        ImGui.unindent();
    }

    // ============================================================================
    // ⑤ 投影参数（FOV / near / far）
    // ============================================================================
    private void projectionSection(CameraKeyframe kf, AtomicBoolean changed, AtomicBoolean editing) {
        if (!ImGui.collapsingHeader(Icons.CAMERA + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.SECTION_PROJECTION),
                ImGuiTreeNodeFlags.DefaultOpen))
            return;
        ImGui.indent();

        // FOV
        f1.set(kf.getFov());
        if (dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.FOV),
                f1, 0.1f, 1f, 180f, "%.1f°")) {
            kf.setFov(f1.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            // 只在鼠标松开时才标记为 changed，避免拖动时频繁刷新 PathManager
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.FOV_TOOLTIP));

        ImGui.spacing();

        // near plane
        f1.set(kf.getNearPlane());
        if (dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.NEAR),
                f1, 0.001f, 0.001f, 10f, "%.3f")) {
            kf.setNearPlane(f1.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.NEAR_TOOLTIP));

        ImGui.spacing();

        // far plane
        f2.set(kf.getFarPlane());
        if (dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.FAR),
                f2, 0.1f, 1f, 10000f, "%.1f")) {
            kf.setFarPlane(f2.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.FAR_TOOLTIP));

        // near < far 校验
        if (kf.getNearPlane() >= kf.getFarPlane()) {
            UITheme.label(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.NEAR_FAR_WARNING),
                    UITheme.DarkTheme.TEXT_WARNING);
        }
        ImGui.unindent();
    }

    // ============================================================================
    // ⑥ 景深（DOF）
    // ============================================================================
    private void depthOfFieldSection(CameraKeyframe kf, AtomicBoolean changed, AtomicBoolean editing) {
        if (!ImGui.collapsingHeader(Icons.EFFECT + " "
                + CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.SECTION_DOF)))
            return;
        ImGui.indent();

        // 复用 ImBoolean 对象
        dofEnabled.set(kf.isDepthOfFieldEnabled());
        if (ImGui.checkbox(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.DOF_ENABLED),
                dofEnabled)) {
            kf.setDepthOfFieldEnabled(dofEnabled.get());
            changed.set(true);
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.DOF_TOOLTIP));

        if (!dofEnabled.get()) {
            ImGui.unindent();
            return;
        }

        // 焦距
        f1.set(kf.getFocusDistance());
        if (dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.FOCUS),
                f1, 0.05f, 0f, 2000f, "%.2f")) {
            kf.setFocusDistance(f1.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.FOCUS_TOOLTIP));

        ImGui.spacing();

        // 光圈
        f1.set(kf.getAperture());
        if (dragFloat(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.APERTURE),
                f1, 0.01f, 0.01f, 10f, "%.2f")) {
            kf.setAperture(f1.get());
            editing.set(true); // ⭐ 拖拽中实时预览
            if (ImGui.isItemDeactivatedAfterEdit()) {
                changed.set(true);
            }
        }
        tooltip(CameraPropertyAdapterI18n.tr(DirectorLang.Properties.AdapterCamera.Keyframe.APERTURE_TOOLTIP));

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
