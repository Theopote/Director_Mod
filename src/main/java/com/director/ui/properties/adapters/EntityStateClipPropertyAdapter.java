package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.model.entity.EntityStateClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntityStateClipPropertyAdapter v1.0
 *
 * <p>实体状态片段属性面板适配器。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityStateClipPropertyAdapter implements IPropertyAdapter<EntityStateClip> {

    private static final String TITLE = Icons.SMILE + " 状态片段";

    private final ImBoolean sneakingBoolean = new ImBoolean();
    private final ImBoolean sprintingBoolean = new ImBoolean();
    private final ImBoolean glowingBoolean = new ImBoolean();
    private final ImBoolean invulnerableBoolean = new ImBoolean();

    @Override
    public Class<EntityStateClip> getTargetType() {
        return EntityStateClip.class;
    }

    @Override
    public boolean renderProperties(EntityStateClip clip) {
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

        // ========== 行为状态 ==========
        if (ImGui.collapsingHeader("🏃 行为状态", ImGuiTreeNodeFlags.DefaultOpen)) {
            sneakingBoolean.set(clip.isSneaking());
            if (ImGui.checkbox("潜行", sneakingBoolean)) {
                clip.setSneaking(sneakingBoolean.get());
                changed.set(true);
            }

            sprintingBoolean.set(clip.isSprinting());
            if (ImGui.checkbox("冲刺", sprintingBoolean)) {
                clip.setSprinting(sprintingBoolean.get());
                changed.set(true);
            }
        }

        // ========== 视觉效果 ==========
        if (ImGui.collapsingHeader("✨ 视觉效果")) {
            glowingBoolean.set(clip.isGlowing());
            if (ImGui.checkbox("发光", glowingBoolean)) {
                clip.setGlowing(glowingBoolean.get());
                changed.set(true);
            }

            float[] visibilityArray = {clip.getVisibility()};
            if (ImGui.sliderFloat("可见性", visibilityArray, 0.0f, 1.0f)) {
                clip.setVisibility(visibilityArray[0]);
                changed.set(true);
            }
        }

        // ========== 战斗状态 ==========
        if (ImGui.collapsingHeader("🛡️ 战斗状态")) {
            invulnerableBoolean.set(clip.isInvulnerable());
            if (ImGui.checkbox("无敌", invulnerableBoolean)) {
                clip.setInvulnerable(invulnerableBoolean.get());
                changed.set(true);
            }
        }

        return changed.get();
    }
}

