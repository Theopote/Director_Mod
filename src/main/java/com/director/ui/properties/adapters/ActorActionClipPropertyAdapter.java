package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.actor.Actor;
import com.director.actor.ActorRegistry;
import com.director.actor.ActorRegistryManager;
import com.director.actor.action.ActorActionType;
import com.director.actor.rig.ActorRigProfile;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.selection.SelectionManager;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.ui.timeline.model.entity.ActorActionClip;
import com.director.ui.timeline.model.entity.ActorTrack;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ActorActionClipPropertyAdapter v2.0
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
 *   <li>根据 RigProfile 禁用不支持的 Action（UI 层联动）</li>
 * </ul>
 *
 * @author Director Team
 * @version 2.0
 */
public class ActorActionClipPropertyAdapter implements IPropertyAdapter<ActorActionClip> {

    private static final String TITLE = Icons.SMILE + " 动作语义片段";

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
            renderActionSelector(clip, changed);
        }

        // ========== RigProfile 警告（如果当前 Action 不支持）==========
        renderRigWarningIfNeeded(clip);

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
        if (ImGui.collapsingHeader(Icons.LOOP + " 循环设置")) {
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

    // =========================================================
    // Actor Resolution (v2.0 新增)
    // =========================================================

    /**
     * 解析当前 Clip 对应的 Actor（安全做法）
     * <p>⚠️ 不要在 Adapter 里缓存 Actor，每次 render 都"就地取"，避免 Selection 不一致
     *
     * @return Actor，如果无法解析则返回 null
     */
    private Actor resolveActorForClip() {
        SelectionManager sel = DirectorServices.get().uiSelection();
        TimelineTrack track = sel.getSelectedTrack();
        
        if (track == null || track.getType() != TrackType.ACTOR) {
            return null;
        }

        ActorTrack actorTrack = track.getActorTrackPayload();
        if (actorTrack == null) {
            return null;
        }

        var actorRef = actorTrack.getActorRef();
        if (actorRef == null) {
            return null;
        }

        ActorRegistry actorRegistry = ActorRegistryManager.getInstance();

        return actorRegistry.getActor(actorRef.getActorId()).orElse(null);
    }

    // =========================================================
    // Action Selector with RigProfile Support (v2.0 新增)
    // =========================================================

    /**
     * 渲染动作选择器（根据 RigProfile 禁用不支持的 Action）
     *
     * @param clip ActorActionClip
     * @param changed 修改标志
     */
    private void renderActionSelector(ActorActionClip clip, AtomicBoolean changed) {
        ActorActionType current = clip.getActionType();
        Actor actor = resolveActorForClip();
        ActorRigProfile rig = actor != null ? actor.getRigProfile() : null;

        // 构建显示标签（包含支持状态提示）
        String currentLabel = current.name();
        if (rig != null && !rig.supports(current)) {
            currentLabel = current.name() + " ⚠";
        }

        if (ImGui.beginCombo("动作类型", currentLabel)) {
            ActorActionType[] types = ActorActionType.values();
            for (ActorActionType type : types) {
                boolean supported = rig == null || rig.supports(type);
                boolean selected = (type == current);

                // 构建选项标签
                String label = type.name();
                if (!supported) {
                    label = label + " (不支持)";
                }

                // 如果不支持，禁用该选项
                if (!supported) {
                    ImGui.beginDisabled();
                }

                if (ImGui.selectable(label, selected)) {
                    if (supported) {
                        clip.setActionType(type);
                        changed.set(true);
                    }
                }

                // 如果不支持，显示 Tooltip 解释原因
                if (!supported && ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.textDisabled("此动作不被当前 RigProfile 支持");
                    ImGui.textDisabled(String.format("Actor: %s, Rig: %s",
                            actor.getName(),
                        rig.getClass().getSimpleName()));
                    ImGui.endTooltip();
                }

                if (selected) {
                    ImGui.setItemDefaultFocus();
                }

                if (!supported) {
                    ImGui.endDisabled();
                }
            }

            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("选择演员在这段时间内的动作语义状态\n例如：RUN（奔跑）、WALK（行走）、DANCE（跳舞）\n不支持的选项会被禁用");
        }
    }

    // =========================================================
    // RigProfile Warning (v2.0 新增)
    // =========================================================

    /**
     * 如果当前 Action 不被 RigProfile 支持，显示警告
     *
     * @param clip ActorActionClip
     */
    private void renderRigWarningIfNeeded(ActorActionClip clip) {
        Actor actor = resolveActorForClip();
        if (actor == null) {
            return;
        }

        ActorRigProfile rig = actor.getRigProfile();
        if (rig == null) {
            return;
        }

        ActorActionType currentAction = clip.getActionType();
        if (!rig.supports(currentAction)) {
            ImGui.spacing();
            ImGui.textColored(1.0f, 0.6f, 0.2f, 1.0f, 
                "⚠ 此动作不被当前演员的 RigProfile 支持");
            ImGui.textDisabled(String.format("Actor: %s, Action: %s, Rig: %s", 
                actor.getName(),
                currentAction.name(),
                rig.getClass().getSimpleName()));
            ImGui.textDisabled("建议：更改动作类型或更换演员的 RigProfile");
        }
    }
}
