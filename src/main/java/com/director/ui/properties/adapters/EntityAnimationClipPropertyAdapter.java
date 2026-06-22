package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.EntityAnimationClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntityAnimationClipPropertyAdapter v1.0
 *
 * <p>实体动画片段属性面板适配器。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityAnimationClipPropertyAdapter implements IPropertyAdapter<EntityAnimationClip> {

    private static final String TITLE = Icons.SMILE + " 动画片段";

    private final ImString animationIdString = new ImString("", 256);
    private final ImInt sourceIndex = new ImInt();
    private final ImBoolean loopBoolean = new ImBoolean();

    @Override
    public Class<EntityAnimationClip> getTargetType() {
        return EntityAnimationClip.class;
    }

    @Override
    public boolean renderProperties(EntityAnimationClip clip) {
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

        // ========== 动画源 ==========
        if (ImGui.collapsingHeader("🎬 动画源", ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityAnimationClip.AnimationSource[] sources = EntityAnimationClip.AnimationSource.values();
            String[] sourceLabels = new String[sources.length];
            for (int i = 0; i < sources.length; i++) {
                sourceLabels[i] = sources[i].name();
                if (sources[i] == clip.getSource()) {
                    sourceIndex.set(i);
                }
            }

            if (ImGui.combo("动画源", sourceIndex, sourceLabels)) {
                clip.setSource(sources[sourceIndex.get()]);
                changed.set(true);
            }
        }

        // ========== 动画设置 ==========
        if (ImGui.collapsingHeader("🎭 动画设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            animationIdString.set(clip.getAnimationId());
            if (ImGui.inputText("动画ID", animationIdString)) {
                clip.setAnimationId(animationIdString.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("例如: walk, attack, mymod:wave");
            }

            loopBoolean.set(clip.isLoop());
            if (ImGui.checkbox("循环播放", loopBoolean)) {
                clip.setLoop(loopBoolean.get());
                changed.set(true);
            }

            float[] speedArray = {clip.getSpeed()};
            if (ImGui.sliderFloat("播放速度", speedArray, 0.0f, 4.0f)) {
                clip.setSpeed(speedArray[0]);
                changed.set(true);
            }
        }

        return changed.get();
    }
}

