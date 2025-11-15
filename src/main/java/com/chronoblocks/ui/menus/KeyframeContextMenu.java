package com.chronoblocks.ui.menus;

import com.chronoblocks.animation.core.Keyframe;
import imgui.ImGui;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.Consumer;

/**
 * 新动画系统的关键帧右键菜单。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class KeyframeContextMenu {

    private static final String MENU_SINGLE = "KeyframeContextMenu";
    private static final String MENU_BATCH = "KeyframeBatchContextMenu";

    private static Keyframe copiedKeyframe;

    private KeyframeContextMenu() {
    }

    public static void openSingle() {
        ImGui.openPopup(MENU_SINGLE);
    }

    public static void openBatch() {
        ImGui.openPopup(MENU_BATCH);
    }

    public static void render(Keyframe keyframe, Consumer<Keyframe> onModified) {
        if (ImGui.beginPopup(MENU_SINGLE)) {
            renderSingleMenu(keyframe, onModified);
            ImGui.endPopup();
        }
    }

    public static void renderBatch(List<Keyframe> keyframes, Consumer<List<Keyframe>> onModified) {
        if (ImGui.beginPopup(MENU_BATCH)) {
            renderBatchMenu(keyframes, onModified);
            ImGui.endPopup();
        }
    }

    private static void renderSingleMenu(Keyframe keyframe, Consumer<Keyframe> onModified) {
        if (keyframe == null) {
            ImGui.textDisabled("无效的关键帧");
            return;
        }

        ImGui.text(String.format("t = %.3fs", keyframe.time()));
        Object value = keyframe.value();
        if (value instanceof Vec3d vec) {
            ImGui.text(String.format("值: (%.2f, %.2f, %.2f)", vec.x, vec.y, vec.z));
        } else if (value instanceof Number number) {
            ImGui.text(String.format("值: %.3f", number.doubleValue()));
        }
        ImGui.text("插值: " + keyframe.easing().name());
        ImGui.separator();

        if (ImGui.beginMenu("设置插值")) {
            for (Keyframe.Easing easing : Keyframe.Easing.values()) {
                boolean selected = easing == keyframe.easing();
                if (ImGui.menuItem(easing.name(), "", selected)) {
                    keyframe.setEasing(easing);
                    notifyModified(onModified, keyframe);
                }
            }
            ImGui.endMenu();
        }

        if (ImGui.menuItem("复制", "Ctrl+C")) {
            copiedKeyframe = cloneKeyframe(keyframe);
        }
        boolean canPaste = copiedKeyframe != null;
        if (ImGui.menuItem("粘贴时间和值", "Ctrl+V", false, canPaste)) {
            pasteKeyframe(keyframe, copiedKeyframe);
            notifyModified(onModified, keyframe);
        }

        if (ImGui.menuItem("删除", "Del")) {
            // 删除由调用者处理
        }
    }

    private static void renderBatchMenu(List<Keyframe> keyframes, Consumer<List<Keyframe>> onModified) {
        if (keyframes == null || keyframes.isEmpty()) {
            ImGui.textDisabled("未选择关键帧");
            return;
        }

        ImGui.text(String.format("选中 %d 个关键帧", keyframes.size()));
        ImGui.separator();

        if (ImGui.beginMenu("批量设置插值")) {
            for (Keyframe.Easing easing : Keyframe.Easing.values()) {
                if (ImGui.menuItem(easing.name())) {
                    for (Keyframe kf : keyframes) {
                        kf.setEasing(easing);
                    }
                    notifyBatchModified(onModified, keyframes);
                }
            }
            ImGui.endMenu();
        }

        if (ImGui.menuItem("时间对齐 → 最早")) {
            double minTime = keyframes.stream().mapToDouble(Keyframe::time).min().orElse(0.0);
            for (Keyframe kf : keyframes) {
                kf.setTime(minTime);
            }
            notifyBatchModified(onModified, keyframes);
        }

        if (ImGui.menuItem("时间对齐 → 最晚")) {
            double maxTime = keyframes.stream().mapToDouble(Keyframe::time).max().orElse(0.0);
            for (Keyframe kf : keyframes) {
                kf.setTime(maxTime);
            }
            notifyBatchModified(onModified, keyframes);
        }
    }

    private static void notifyModified(Consumer<Keyframe> onModified, Keyframe keyframe) {
        if (onModified != null) {
            onModified.accept(keyframe);
        }
    }

    private static void notifyBatchModified(Consumer<List<Keyframe>> onModified, List<Keyframe> keyframes) {
        if (onModified != null) {
            onModified.accept(keyframes);
        }
    }

    private static void pasteKeyframe(Keyframe target, Keyframe source) {
        if (target == null || source == null) {
            return;
        }
        target.setTime(source.time());
        target.setValue(source.value());
        target.setEasing(source.easing());
    }

    private static Keyframe cloneKeyframe(Keyframe source) {
        Keyframe copy = new Keyframe(source.time(), source.value());
        copy.setEasing(source.easing());
        return copy;
    }
}


