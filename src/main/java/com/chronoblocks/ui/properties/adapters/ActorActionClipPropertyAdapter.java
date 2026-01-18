package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.actor.action.ActorActionType;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.entity.ActorActionClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ActorActionClipPropertyAdapter v1.0
 *
 * <p>演员动作语义片段属性面板适配器。
 *
 * <p>负责编辑 ActorActionClip 的属性：
 * <ul>
 *   <li>动作类型（RUN / DANCE / CARRY 等）</li>
 *   <li>动作强度（intensity）</li>
 *   <li>是否循环（loop）</li>
 *   <li>基础时间信息（start / end）</li>
 * </ul>
 *
 * <p>设计原则：
 * <ul>
 *   <li>只描述"感觉"，不直接控制动画</li>
 *   <li>不操作世界，不设置位置/旋转</li>
 *   <li>只影响 Runtime 表现策略</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class ActorActionClipPropertyAdapter implements IPropertyAdapter<ActorActionClip> {

    private static final String TITLE = Icons.SMILE + " 动作语义片段";

    private final ImInt actionTypeIndex = new ImInt();
    private final ImFloat intensityFloat = new ImFloat();
    private final ImBoolean loopBoolean = new ImBoolean();
    private final ImFloat startTimeFloat = new ImFloat();
    private final ImFloat endTimeFloat = new ImFloat();

    @Override
    public Class<ActorActionClip> getTargetType() {
        return ActorActionClip.class;
    }

    @Override
    public boolean renderProperties(ActorActionClip clip) {
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

        // ========== 动作选择 ==========
        if (ImGui.collapsingHeader("🎭 动作语义", ImGuiTreeNodeFlags.DefaultOpen)) {
            ActorActionType[] types = ActorActionType.values();
            String[] typeLabels = new String[types.length];
            for (int i = 0; i < types.length; i++) {
                typeLabels[i] = types[i].name();
                if (types[i] == clip.getActionType()) {
                    actionTypeIndex.set(i);
                }
            }

            if (ImGui.combo("动作类型", actionTypeIndex, typeLabels)) {
                clip.setActionType(types[actionTypeIndex.get()]);
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("选择演员在这段时间内的动作语义状态\n例如：RUN（奔跑）、WALK（行走）、DANCE（跳舞）");
            }
        }

        // ========== 动作强度 ==========
        if (ImGui.collapsingHeader("💪 动作强度", ImGuiTreeNodeFlags.DefaultOpen)) {
            intensityFloat.set(clip.getIntensity());
            float[] intensityArray = {intensityFloat.get()};
            
            if (ImGui.sliderFloat("强度", intensityArray, 0.0f, 1.0f, "%.2f")) {
                clip.setIntensity(intensityArray[0]);
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("动作强度 / 风格\n例如：RUN 的快慢、DANCE 的幅度");
            }
            
            ImGui.sameLine();
            ImGui.textDisabled("(Style / Energy)");
        }

        // ========== 循环设置 ==========
        if (ImGui.collapsingHeader("🔄 循环设置")) {
            loopBoolean.set(clip.isLoop());
            if (ImGui.checkbox("循环播放", loopBoolean)) {
                clip.setLoop(loopBoolean.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("是否循环播放动作\n例如：DANCE / IDLE 通常需要循环");
            }
        }

        // ========== 时间编辑（可选）==========
        if (ImGui.collapsingHeader("⏰ 时间编辑")) {
            float start = clip.getStartTime();
            float end = clip.getEndTime();
            float duration = end - start;

            startTimeFloat.set(start);
            endTimeFloat.set(end);

            UITheme.beginGroup("时间范围");
            if (ImGui.inputFloat("开始时间", startTimeFloat)) {
                if (startTimeFloat.get() < end) {
                    clip.setTimeRange(startTimeFloat.get(), end);
                    changed.set(true);
                }
            }

            if (ImGui.inputFloat("结束时间", endTimeFloat)) {
                if (endTimeFloat.get() > start) {
                    clip.setTimeRange(start, endTimeFloat.get());
                    changed.set(true);
                }
            }

            ImGui.text(String.format("时长: %.2fs", duration));
            UITheme.endGroup();
        }

        return changed.get();
    }
}
