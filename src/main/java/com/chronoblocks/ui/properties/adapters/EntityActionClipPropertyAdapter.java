package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.entity.EntityActionClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntityActionClipPropertyAdapter v1.0
 *
 * <p>实体动作片段属性面板适配器。
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class EntityActionClipPropertyAdapter implements IPropertyAdapter<EntityActionClip> {

    private static final String TITLE = Icons.SMILE + " 动作片段";

    private final ImInt actionTypeIndex = new ImInt();
    private final ImBoolean triggerOnceBoolean = new ImBoolean();

    @Override
    public Class<EntityActionClip> getTargetType() {
        return EntityActionClip.class;
    }

    @Override
    public boolean renderProperties(EntityActionClip clip) {
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

        // ========== 动作类型 ==========
        if (ImGui.collapsingHeader("⚡ 动作类型", ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityActionClip.ActionType[] actionTypes = EntityActionClip.ActionType.values();
            String[] actionLabels = new String[actionTypes.length];
            for (int i = 0; i < actionTypes.length; i++) {
                actionLabels[i] = actionTypes[i].getDisplayName();
                if (actionTypes[i] == clip.getActionType()) {
                    actionTypeIndex.set(i);
                }
            }

            if (ImGui.combo("动作类型", actionTypeIndex, actionLabels)) {
                clip.setActionType(actionTypes[actionTypeIndex.get()]);
                changed.set(true);
            }
        }

        // ========== 触发设置 ==========
        if (ImGui.collapsingHeader("🔔 触发设置")) {
            triggerOnceBoolean.set(clip.isTriggerOnce());
            if (ImGui.checkbox("只触发一次", triggerOnceBoolean)) {
                clip.setTriggerOnce(triggerOnceBoolean.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("如果启用，动作只在片段开始时触发一次");
            }
        }

        // ========== 参数 ==========
        Map<String, Object> params = clip.getParams();
        if (!params.isEmpty()) {
            if (ImGui.collapsingHeader("📋 参数")) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    ImGui.text(entry.getKey() + ": " + entry.getValue());
                }
            }
        }

        return changed.get();
    }
}

