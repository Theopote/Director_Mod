package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.camera.CameraKeyframe;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * CameraKeyframePropertyAdapter v2.0
 *
 * 负责在属性面板中渲染 & 编辑摄像机关键帧的所有可调参数，包括：
 *   - 时间、位置、旋转、FOV、DOF……
 *   - KeyType（影响关键帧 UI + 曲线行为）
 *   - SegmentPathType（从此关键帧 → 下一个关键帧的曲线模式）
 *   - Bezier 控制柄
 *
 * 优化版本：减少内存分配、添加工具提示、重置按钮、输入验证，提升用户体验和性能。
 */
public class CameraKeyframePropertyAdapter implements IPropertyAdapter<CameraKeyframe> {

    // 常量定义，便于维护
    private static final String TITLE = Icons.CLIPBOARD + " 摄像机关键帧";
    private static final String CAPTURE_TOOLTIP = "将当前游戏摄像机的位置、旋转、FOV等参数写入此关键帧";
    private static final float DEFAULT_FOV = 90.0f;
    private static final float DEFAULT_NEAR = 0.1f;
    private static final float DEFAULT_FAR = 1000.0f;

    // 复用 ImFloat 对象，减少 GC
    private final ImFloat timeFloat = new ImFloat();
    private final ImFloat fovFloat = new ImFloat();
    private final ImFloat nearFloat = new ImFloat();
    private final ImFloat farFloat = new ImFloat();
    private final ImFloat focusFloat = new ImFloat();
    private final ImFloat apertureFloat = new ImFloat();

    @Override
    public boolean renderProperties(CameraKeyframe keyframe) {
        if (keyframe == null) {
            UITheme.label("摄像机关键帧为空", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        AtomicBoolean modified = new AtomicBoolean(false);

        // 标题
        UITheme.title(TITLE);

        // ========== 基础信息 ==========
        if (ImGui.collapsingHeader("📋 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(keyframe.getTime());
            if (ImGui.dragFloat("时间 (秒)", timeFloat.getData(), 0.02f, 0.0f, 10_000f, "%.2f")) {
                keyframe.setTime(timeFloat.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("关键帧在时间线中的位置");
            }
            addResetButton(() -> {
                timeFloat.set(0.0f);
                keyframe.setTime(0.0f);
                modified.set(true);
            }, "重置时间");

            ImGui.spacing();

            // ⭐ 新增：关键帧类型
            CameraKeyframe.KeyType currentType = keyframe.getKeyType();
            String[] typeLabels = {"Standard", "Corner", "Hold", "Smooth", "Manual Bezier"};
            imgui.type.ImInt typeIdx = new imgui.type.ImInt(currentType.ordinal());
            if (ImGui.combo("关键帧类型 KeyType", typeIdx, typeLabels, typeLabels.length)) {
                CameraKeyframe.KeyType newType = CameraKeyframe.KeyType.values()[typeIdx.get()];
                keyframe.setKeyType(newType);
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("不同类型会显示不同关键帧图形并影响曲线插值逻辑");
            }

            ImGui.spacing();

            // 段轨迹类型（出段）
            CameraKeyframe.SegmentPathType segType = keyframe.getSegmentPathType();
            String[] segLabels = {"Default", "Linear", "Catmull-Rom", "Bezier", "Step", "Hold"};
            imgui.type.ImInt segIdx = new imgui.type.ImInt(segType.ordinal());
            if (ImGui.combo("从此关键帧到下一关键帧的曲线", segIdx, segLabels, segLabels.length)) {
                CameraKeyframe.SegmentPathType newType = CameraKeyframe.SegmentPathType.values()[segIdx.get()];
                keyframe.setSegmentPathType(newType);
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("""
Segment Path Type：
Default → 使用轨道默认轨迹类型（TimelineCameraPathManager）
Linear → 直线插值
Catmull-Rom → 自然平滑样条
Bezier → 贝塞尔曲线（可用控制柄）
Step → 使用当前关键帧值，不进行插值
Hold → 意味着此段保持当前关键帧（类似 Step，但与 UI keyType 更一致）
""");
            }

            ImGui.spacing();

            // —— 旋转插值方式 ——
            CameraKeyframe.RotationInterpolation rType = keyframe.getRotationInterpolation();
            String[] rLabels = {
                    "Default (跟随位置曲线)",
                    "Linear (Slerp)",
                    "Bezier (Slerp, 预留 Squad)",
                    "Catmull-Rom (Slerp, 预留 Squad)",
                    "Step (保持上一帧)"
            };
            imgui.type.ImInt rIndex = new imgui.type.ImInt(rType.ordinal());
            if (ImGui.combo("旋转插值方式", rIndex, rLabels, rLabels.length)) {
                keyframe.setRotationInterpolation(CameraKeyframe.RotationInterpolation.values()[rIndex.get()]);
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("""
                        控制摄像机"旋转"的插值方式：
                        Default → 由 Segment Path 推导（线性/样条/贝塞尔 → Slerp）
                        Linear → 使用 Quaternion Slerp
                        Bezier/Catmull-Rom → 当前版本仍为 Slerp，后续可升级为 Squad
                        Step → 旋转不插值，保持上一关键帧视角
                        """);
            }

            ImGui.spacing();
        }

        // ========== 位置与旋转 ==========
        if (ImGui.collapsingHeader("📍 位置与旋转", ImGuiTreeNodeFlags.DefaultOpen)) {
            // ⭐ 一键捕获按钮
            if (ImGui.button(Icons.CAMERA + " 从当前视角捕获", -1, 0)) {
                com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController
                        .getInstance()
                        .captureFromCurrentCamera(keyframe);
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(CAPTURE_TOOLTIP);
            }

            ImGui.spacing();

            modified.set(modified.get() | renderVector3("位置 Position", keyframe.getPosition(), keyframe::setPosition, 0.05f, null, "世界坐标中的摄像机位置"));

            ImGui.spacing();

            modified.set(modified.get() | renderVector3("旋转 Pitch/Yaw/Roll", keyframe.getRotation(), keyframe::setRotation, 0.1f, null, "摄像机的欧拉角旋转 (度)"));

            ImGui.spacing();
        }

        // ========== 贝塞尔控制柄（仅在 Bezier 或手动 Bezier 类型时显示） ==========
        boolean showHandles = keyframe.getSegmentPathType() == CameraKeyframe.SegmentPathType.BEZIER
                || keyframe.getKeyType() == CameraKeyframe.KeyType.MANUAL_BEZIER;
        if (showHandles && ImGui.collapsingHeader("🎛 贝塞尔控制柄 (Bezier Handles)", ImGuiTreeNodeFlags.DefaultOpen)) {
            Vector3f in = keyframe.getBezierInHandle();
            Vector3f out = keyframe.getBezierOutHandle();
            float[] inArr = {in.x, in.y, in.z};
            float[] outArr = {out.x, out.y, out.z};

            // 手柄模式
            CameraKeyframe.HandleMode mode = keyframe.getHandleMode();
            String[] modeLabels = {"自动", "自由", "对齐"};
            imgui.type.ImInt modeIdx = new imgui.type.ImInt(mode.ordinal());
            if (ImGui.combo("手柄模式", modeIdx, modeLabels, modeLabels.length)) {
                keyframe.setHandleMode(CameraKeyframe.HandleMode.values()[modeIdx.get()]);
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("AUTO: 自动平滑；FREE: 独立控制；ALIGNED: 进出柄对齐方向");
            }

            ImGui.spacing();

            if (ImGui.dragFloat3("进入控制柄 (In)", inArr, 0.05f)) {
                keyframe.setBezierInHandle(new Vector3f(inArr[0], inArr[1], inArr[2]));
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("进入方向控制柄（相对坐标；全部为 0 则自动生成）");
            }

            if (ImGui.dragFloat3("离开控制柄 (Out)", outArr, 0.05f)) {
                keyframe.setBezierOutHandle(new Vector3f(outArr[0], outArr[1], outArr[2]));
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("离开方向控制柄（相对坐标；全部为 0 则自动生成）");
            }

            ImGui.spacing();
            ImGui.text("提示：控制柄为相对坐标（相对于关键帧本身的位置）");
            ImGui.text("若为 (0,0,0)，则 TimelineCameraPathManager 会使用自动生成的控制柄");
        }

        // ========== 投影参数 ==========
        if (ImGui.collapsingHeader("🎥 投影参数", ImGuiTreeNodeFlags.DefaultOpen)) {
            fovFloat.set(keyframe.getFov());
            if (ImGui.dragFloat("视野 FOV", fovFloat.getData(), 0.1f, 1.0f, 180.0f, "%.1f°")) {
                keyframe.setFov(fovFloat.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("摄像机的视野角度 (度)");
            }
            addResetButton(() -> {
                fovFloat.set(DEFAULT_FOV);
                keyframe.setFov(DEFAULT_FOV);
                modified.set(true);
            }, "重置FOV");

            ImGui.spacing();

            nearFloat.set(keyframe.getNearPlane());
            if (ImGui.dragFloat("Near 平面", nearFloat.getData(), 0.001f, 0.001f, 10.0f, "%.3f")) {
                keyframe.setNearPlane(nearFloat.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("近裁剪平面距离");
            }
            addResetButton(() -> {
                nearFloat.set(DEFAULT_NEAR);
                keyframe.setNearPlane(DEFAULT_NEAR);
                modified.set(true);
            }, "重置近面");

            farFloat.set(keyframe.getFarPlane());
            if (ImGui.dragFloat("Far 平面", farFloat.getData(), 0.1f, 1.0f, 10000.0f, "%.1f")) {
                keyframe.setFarPlane(farFloat.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("远裁剪平面距离");
            }
            addResetButton(() -> {
                farFloat.set(DEFAULT_FAR);
                keyframe.setFarPlane(DEFAULT_FAR);
                modified.set(true);
            }, "重置远面");

            // 输入验证：确保 near < far
            if (nearFloat.get() >= farFloat.get()) {
                UITheme.label("警告: 近裁剪面应小于远裁剪面", UITheme.DarkTheme.TEXT_WARNING);
            }

            ImGui.spacing();
        }

        // ========== 镜头特效 ==========
        if (ImGui.collapsingHeader("📷 镜头特效")) {
            ImBoolean dof = new ImBoolean(keyframe.isDepthOfFieldEnabled());
            if (ImGui.checkbox("启用景深", dof)) {
                keyframe.setDepthOfFieldEnabled(dof.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("启用景深效果，模拟相机模糊");
            }

            if (dof.get()) {
                ImGui.indent();

                focusFloat.set(keyframe.getFocusDistance());
                if (ImGui.dragFloat("焦距 Focus", focusFloat.getData(), 0.05f, 0.0f, 2000.0f, "%.2f")) {
                    keyframe.setFocusDistance(focusFloat.get());
                    modified.set(true);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("焦点距离");
                }

                apertureFloat.set(keyframe.getAperture());
                if (ImGui.dragFloat("光圈 Aperture", apertureFloat.getData(), 0.01f, 0.01f, 10.0f, "%.2f")) {
                    keyframe.setAperture(apertureFloat.get());
                    modified.set(true);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("光圈值，控制模糊强度");
                }

                ImGui.unindent();
            }
        }

        // ==============================================
        // 修改触发 Timeline 刷新
        // ==============================================
        if (modified.get()) {
            // 通知 TimelineCameraController 刷新路径管理器
            com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController
                    .getInstance()
                    .onKeyframeChanged();
        }

        return modified.get();
    }

    @Override
    public Class<CameraKeyframe> getTargetType() {
        return CameraKeyframe.class;
    }

    // 辅助方法：渲染 Vector3f 拖拽控件
    private boolean renderVector3(String label, Vector3f vec, VectorSetter setter, float speed, float[] minMax, String tooltip) {
        float[] values = {vec.x, vec.y, vec.z};
        String format = minMax != null ? "%.1f" : "%.2f";
        boolean changed = ImGui.dragFloat3(label, values, speed, minMax != null ? minMax[0] : Float.MIN_VALUE, minMax != null ? minMax[1] : Float.MAX_VALUE, format);
        if (changed) {
            setter.set(new Vector3f(values[0], values[1], values[2]));
        }
        if (ImGui.isItemHovered() && tooltip != null) {
            ImGui.setTooltip(tooltip);
        }
        addResetButton(() -> {
            vec.set(0, 0, 0);
            setter.set(vec);
        }, "重置" + label);
        return changed;
    }

    // 辅助方法：添加重置按钮
    private void addResetButton(Runnable resetAction, String label) {
        ImGui.sameLine();
        if (ImGui.button("R##" + label)) {  // 唯一ID避免冲突
            resetAction.run();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("重置到默认值");
        }
    }

    // 函数接口用于 setPosition/setRotation
    @FunctionalInterface
    private interface VectorSetter {
        void set(Vector3f vec);
    }
}