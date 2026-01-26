package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.entity.EntityLookAtClip;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntityLookAtClipPropertyAdapter v1.0
 *
 * <p>实体看向片段属性面板适配器。
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class EntityLookAtClipPropertyAdapter implements IPropertyAdapter<EntityLookAtClip> {

    private static final String TITLE = Icons.SMILE + " 看向片段";

    private final ImInt modeIndex = new ImInt();
    private final ImFloat worldPointX = new ImFloat();
    private final ImFloat worldPointY = new ImFloat();
    private final ImFloat worldPointZ = new ImFloat();

    @Override
    public Class<EntityLookAtClip> getTargetType() {
        return EntityLookAtClip.class;
    }

    @Override
    public boolean renderProperties(EntityLookAtClip clip) {
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

        // ========== 目标模式 ==========
        if (ImGui.collapsingHeader("🎯 目标模式", ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityLookAtClip.TargetMode[] modes = EntityLookAtClip.TargetMode.values();
            String[] modeLabels = new String[modes.length];
            for (int i = 0; i < modes.length; i++) {
                modeLabels[i] = modes[i].getDisplayName();
                if (modes[i] == clip.getMode()) {
                    modeIndex.set(i);
                }
            }

            if (ImGui.combo("目标模式", modeIndex, modeLabels)) {
                clip.setMode(modes[modeIndex.get()]);
                changed.set(true);
            }
        }

        // ========== 目标设置 ==========
        if (ImGui.collapsingHeader("📍 目标设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            EntityLookAtClip.TargetMode mode = clip.getMode();
            
            if (mode == EntityLookAtClip.TargetMode.WORLD_POINT) {
                Vector3f worldPoint = clip.getWorldPoint();
                worldPointX.set(worldPoint.x);
                worldPointY.set(worldPoint.y);
                worldPointZ.set(worldPoint.z);

                UITheme.beginGroup("世界坐标");
                if (ImGui.inputFloat("X", worldPointX)) {
                    clip.setWorldPoint(worldPointX.get(), worldPoint.y, worldPoint.z);
                    changed.set(true);
                }
                if (ImGui.inputFloat("Y", worldPointY)) {
                    clip.setWorldPoint(worldPoint.x, worldPointY.get(), worldPoint.z);
                    changed.set(true);
                }
                if (ImGui.inputFloat("Z", worldPointZ)) {
                    clip.setWorldPoint(worldPoint.x, worldPoint.y, worldPointZ.get());
                    changed.set(true);
                }
                UITheme.endGroup();
            } else if (mode == EntityLookAtClip.TargetMode.ENTITY_UUID) {
                UUID targetUuid = clip.getTargetEntityUuid();
                if (targetUuid != null) {
                    ImGui.text("目标实体UUID: " + targetUuid);
                } else {
                    ImGui.textDisabled("未设置目标实体");
                }
            } else if (mode == EntityLookAtClip.TargetMode.CAMERA) {
                ImGui.text("看向摄像机");
            }
        }

        // ========== 跟随强度 ==========
        if (ImGui.collapsingHeader("💪 跟随强度")) {
            float[] strengthArray = {clip.getStrength()};
            if (ImGui.sliderFloat("强度", strengthArray, 0.0f, 1.0f)) {
                clip.setStrength(strengthArray[0]);
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("头部跟随目标的强度（0=不跟随，1=完全跟随）");
            }
        }

        return changed.get();
    }
}

