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
 * 摄像机关键帧属性适配器。
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
            if (ImGui.dragFloat("时间", timeFloat.getData(), 0.05f, 0.0f, 10_000f, "%.2fs")) {
                keyframe.setTime(timeFloat.get());
                modified.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("关键帧在时间线上的位置 (秒)");
            }
            addResetButton(() -> {
                timeFloat.set(0.0f);
                keyframe.setTime(0.0f);
                modified.set(true);
            }, "重置时间");

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

            modified.set(modified.get() | renderVector3("位置 (XYZ)", keyframe.getPosition(), keyframe::setPosition, 0.1f, null, "世界坐标中的摄像机位置"));

            ImGui.spacing();

            modified.set(modified.get() | renderVector3("旋转 (偏航/俯仰/翻滚)", keyframe.getRotation(), keyframe::setRotation, 1.0f, new float[]{-180f, 180f}, "摄像机的欧拉角旋转 (度)"));

            ImGui.spacing();
        }

        // ========== 投影参数 ==========
        if (ImGui.collapsingHeader("🎥 投影参数", ImGuiTreeNodeFlags.DefaultOpen)) {
            fovFloat.set(keyframe.getFov());
            if (ImGui.dragFloat("视野 (FOV)", fovFloat.getData(), 0.5f, 10.0f, 150.0f, "%.1f°")) {
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
            if (ImGui.dragFloat("近裁剪面", nearFloat.getData(), 0.01f, 0.01f, 100f, "%.2f")) {
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
            if (ImGui.dragFloat("远裁剪面", farFloat.getData(), 1.0f, 1.0f, 10000f, "%.1f")) {
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
                if (ImGui.dragFloat("焦距", focusFloat.getData(), 0.5f, 0.1f, 1000.0f, "%.1f")) {
                    keyframe.setFocusDistance(focusFloat.get());
                    modified.set(true);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("焦点距离");
                }

                apertureFloat.set(keyframe.getAperture());
                if (ImGui.dragFloat("光圈 (f-stop)", apertureFloat.getData(), 0.1f, 1.4f, 22.0f, "%.1f")) {
                    keyframe.setAperture(apertureFloat.get());
                    modified.set(true);
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("光圈值，控制模糊强度");
                }

                ImGui.unindent();
            }
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