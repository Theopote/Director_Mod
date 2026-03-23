package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.entity.EntityTransformClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntityTransformClipPropertyAdapter v1.0
 *
 * <p>实体位移/旋转/缩放片段属性面板适配器。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityTransformClipPropertyAdapter implements IPropertyAdapter<EntityTransformClip> {

    private static final String TITLE = Icons.SMILE + " 位移/旋转片段";

    private final ImFloat fromPosX = new ImFloat();
    private final ImFloat fromPosY = new ImFloat();
    private final ImFloat fromPosZ = new ImFloat();
    private final ImFloat toPosX = new ImFloat();
    private final ImFloat toPosY = new ImFloat();
    private final ImFloat toPosZ = new ImFloat();
    private final ImFloat fromRotX = new ImFloat();
    private final ImFloat fromRotY = new ImFloat();
    private final ImFloat fromRotZ = new ImFloat();
    private final ImFloat toRotX = new ImFloat();
    private final ImFloat toRotY = new ImFloat();
    private final ImFloat toRotZ = new ImFloat();
    private final ImFloat fromScaleX = new ImFloat();
    private final ImFloat fromScaleY = new ImFloat();
    private final ImFloat fromScaleZ = new ImFloat();
    private final ImFloat toScaleX = new ImFloat();
    private final ImFloat toScaleY = new ImFloat();
    private final ImFloat toScaleZ = new ImFloat();
    private final ImInt interpolationIndex = new ImInt();
    private final ImBoolean followPathBoolean = new ImBoolean();

    @Override
    public Class<EntityTransformClip> getTargetType() {
        return EntityTransformClip.class;
    }

    @Override
    public boolean renderProperties(EntityTransformClip clip) {
        if (clip == null) {
            ImGui.text("（无片段）");
            return false;
        }

        AtomicBoolean changed = new AtomicBoolean(false);

        UITheme.title(TITLE);

        // ========== 基础信息 ==========
        if (ImGui.collapsingHeader("⏱ 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            UITheme.beginGroup("时间范围");
            ImGui.text(String.format("开始: %.2fs", clip.getStartTime()));
            ImGui.text(String.format("结束: %.2fs", clip.getEndTime()));
            ImGui.text(String.format("时长: %.2fs", clip.getLength()));
            UITheme.endGroup();
        }

        // ========== 插值设置 ==========
        if (ImGui.collapsingHeader("📊 插值设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            TimelineInterpolation[] interps = TimelineInterpolation.values();
            String[] interpLabels = new String[interps.length];
            for (int i = 0; i < interps.length; i++) {
                interpLabels[i] = interps[i].getDisplayName();
                if (interps[i] == clip.getInterpolation()) {
                    interpolationIndex.set(i);
                }
            }

            if (ImGui.combo("插值类型", interpolationIndex, interpLabels)) {
                clip.setInterpolation(interps[interpolationIndex.get()]);
                changed.set(true);
            }

            followPathBoolean.set(clip.isFollowPath());
            if (ImGui.checkbox("跟随路径", followPathBoolean)) {
                clip.setFollowPath(followPathBoolean.get());
                changed.set(true);
            }

            if (clip.getPathId() != null) {
                ImGui.text("路径ID: " + clip.getPathId());
            }
        }

        // ========== 起始位置 ==========
        if (ImGui.collapsingHeader("📍 起始位置", ImGuiTreeNodeFlags.DefaultOpen)) {
            Vector3f fromPos = clip.getFromPos();
            fromPosX.set(fromPos.x);
            fromPosY.set(fromPos.y);
            fromPosZ.set(fromPos.z);

            UITheme.beginGroup("位置");
            if (ImGui.inputFloat("X", fromPosX)) {
                clip.setFromPos(fromPosX.get(), fromPos.y, fromPos.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Y", fromPosY)) {
                clip.setFromPos(fromPos.x, fromPosY.get(), fromPos.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Z", fromPosZ)) {
                clip.setFromPos(fromPos.x, fromPos.y, fromPosZ.get());
                changed.set(true);
            }
            UITheme.endGroup();

            Vector3f fromRot = clip.getFromRot();
            fromRotX.set(fromRot.x);
            fromRotY.set(fromRot.y);
            fromRotZ.set(fromRot.z);

            UITheme.beginGroup("旋转");
            if (ImGui.inputFloat("偏航 (Yaw)", fromRotX)) {
                clip.setFromRot(fromRotX.get(), fromRot.y, fromRot.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("俯仰 (Pitch)", fromRotY)) {
                clip.setFromRot(fromRot.x, fromRotY.get(), fromRot.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("翻滚 (Roll)", fromRotZ)) {
                clip.setFromRot(fromRot.x, fromRot.y, fromRotZ.get());
                changed.set(true);
            }
            UITheme.endGroup();

            Vector3f fromScale = clip.getFromScale();
            fromScaleX.set(fromScale.x);
            fromScaleY.set(fromScale.y);
            fromScaleZ.set(fromScale.z);

            UITheme.beginGroup("缩放");
            if (ImGui.inputFloat("X", fromScaleX)) {
                clip.setFromScale(fromScaleX.get(), fromScale.y, fromScale.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Y", fromScaleY)) {
                clip.setFromScale(fromScale.x, fromScaleY.get(), fromScale.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Z", fromScaleZ)) {
                clip.setFromScale(fromScale.x, fromScale.y, fromScaleZ.get());
                changed.set(true);
            }
            UITheme.endGroup();
        }

        // ========== 结束位置 ==========
        if (ImGui.collapsingHeader("🎯 结束位置", ImGuiTreeNodeFlags.DefaultOpen)) {
            Vector3f toPos = clip.getToPos();
            toPosX.set(toPos.x);
            toPosY.set(toPos.y);
            toPosZ.set(toPos.z);

            UITheme.beginGroup("位置");
            if (ImGui.inputFloat("X", toPosX)) {
                clip.setToPos(toPosX.get(), toPos.y, toPos.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Y", toPosY)) {
                clip.setToPos(toPos.x, toPosY.get(), toPos.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Z", toPosZ)) {
                clip.setToPos(toPos.x, toPos.y, toPosZ.get());
                changed.set(true);
            }
            UITheme.endGroup();

            Vector3f toRot = clip.getToRot();
            toRotX.set(toRot.x);
            toRotY.set(toRot.y);
            toRotZ.set(toRot.z);

            UITheme.beginGroup("旋转");
            if (ImGui.inputFloat("偏航 (Yaw)", toRotX)) {
                clip.setToRot(toRotX.get(), toRot.y, toRot.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("俯仰 (Pitch)", toRotY)) {
                clip.setToRot(toRot.x, toRotY.get(), toRot.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("翻滚 (Roll)", toRotZ)) {
                clip.setToRot(toRot.x, toRot.y, toRotZ.get());
                changed.set(true);
            }
            UITheme.endGroup();

            Vector3f toScale = clip.getToScale();
            toScaleX.set(toScale.x);
            toScaleY.set(toScale.y);
            toScaleZ.set(toScale.z);

            UITheme.beginGroup("缩放");
            if (ImGui.inputFloat("X", toScaleX)) {
                clip.setToScale(toScaleX.get(), toScale.y, toScale.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Y", toScaleY)) {
                clip.setToScale(toScale.x, toScaleY.get(), toScale.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Z", toScaleZ)) {
                clip.setToScale(toScale.x, toScale.y, toScaleZ.get());
                changed.set(true);
            }
            UITheme.endGroup();
        }

        return changed.get();
    }
}

