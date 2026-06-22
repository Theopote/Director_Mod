package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.entity.EntityDespawnClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntityDespawnClipPropertyAdapter v1.0
 *
 * <p>实体离场片段属性面板适配器。
 *
 * @author Director Team
 * @version 1.0
 */
public class EntityDespawnClipPropertyAdapter implements IPropertyAdapter<EntityDespawnClip> {

    private static final String TITLE = Icons.SMILE + " 离场片段";

    private final ImInt modeIndex = new ImInt();

    @Override
    public Class<EntityDespawnClip> getTargetType() {
        return EntityDespawnClip.class;
    }

    @Override
    public boolean renderProperties(EntityDespawnClip clip) {
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

        // ========== 离场方式 ==========
        if (ImGui.collapsingHeader("🚪 离场方式", ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityDespawnClip.DespawnMode[] modes = EntityDespawnClip.DespawnMode.values();
            String[] modeLabels = new String[modes.length];
            for (int i = 0; i < modes.length; i++) {
                modeLabels[i] = modes[i].getDisplayName();
                if (modes[i] == clip.getMode()) {
                    modeIndex.set(i);
                }
            }

            if (ImGui.combo("离场方式", modeIndex, modeLabels)) {
                clip.setMode(modes[modeIndex.get()]);
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("隐藏：实体不可见但保留\n删除：完全移除实体");
            }
        }

        return changed.get();
    }
}

