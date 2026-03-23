package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.model.entity.EntitySpawnClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntitySpawnClipPropertyAdapter v1.0
 *
 * <p>实体出现/绑定片段属性面板适配器。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntitySpawnClipPropertyAdapter implements IPropertyAdapter<EntitySpawnClip> {

    private static final String TITLE = Icons.SMILE + " 出现/绑定片段";

    private final ImFloat posXFloat = new ImFloat();
    private final ImFloat posYFloat = new ImFloat();
    private final ImFloat posZFloat = new ImFloat();
    private final ImFloat yawFloat = new ImFloat();
    private final ImFloat pitchFloat = new ImFloat();
    private final ImBoolean visibleBoolean = new ImBoolean();

    @Override
    public Class<EntitySpawnClip> getTargetType() {
        return EntitySpawnClip.class;
    }

    @Override
    public boolean renderProperties(EntitySpawnClip clip) {
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
            
            // 可编辑的结束时间和时长
            ImFloat endTimeFloat = new ImFloat(clip.getEndTime());
            if (ImGui.inputFloat("结束时间 (秒)", endTimeFloat)) {
                float newEndTime = Math.max(clip.getStartTime() + 0.1f, endTimeFloat.get());
                clip.setTimeRange(clip.getStartTime(), newEndTime);
                changed.set(true);
            }
            
            ImFloat durationFloat = new ImFloat(clip.getLength());
            if (ImGui.inputFloat("时长 (秒)", durationFloat)) {
                float newDuration = Math.max(0.1f, durationFloat.get());
                clip.setTimeRange(clip.getStartTime(), clip.getStartTime() + newDuration);
                changed.set(true);
            }
            
            UITheme.endGroup();
        }

        // ========== 位置和旋转 ==========
        if (ImGui.collapsingHeader("📍 位置和旋转", ImGuiTreeNodeFlags.DefaultOpen)) {
            Vector3f pos = clip.getPosition();
            posXFloat.set(pos.x);
            posYFloat.set(pos.y);
            posZFloat.set(pos.z);

            UITheme.beginGroup("位置");
            if (ImGui.inputFloat("X", posXFloat)) {
                clip.setPosition(posXFloat.get(), pos.y, pos.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Y", posYFloat)) {
                clip.setPosition(pos.x, posYFloat.get(), pos.z);
                changed.set(true);
            }
            if (ImGui.inputFloat("Z", posZFloat)) {
                clip.setPosition(pos.x, pos.y, posZFloat.get());
                changed.set(true);
            }
            UITheme.endGroup();

            yawFloat.set(clip.getYawDeg());
            pitchFloat.set(clip.getPitchDeg());

            UITheme.beginGroup("旋转");
            if (ImGui.inputFloat("偏航 (Yaw)", yawFloat)) {
                clip.setYawDeg(yawFloat.get());
                changed.set(true);
            }
            if (ImGui.inputFloat("俯仰 (Pitch)", pitchFloat)) {
                clip.setPitchDeg(pitchFloat.get());
                changed.set(true);
            }
            UITheme.endGroup();
        }

        // ========== 可见性 ==========
        if (ImGui.collapsingHeader("👁️ 可见性")) {
            visibleBoolean.set(clip.isVisibleOnSpawn());
            if (ImGui.checkbox("出现时可见", visibleBoolean)) {
                clip.setVisibleOnSpawn(visibleBoolean.get());
                changed.set(true);
            }
        }

        return changed.get();
    }
}

