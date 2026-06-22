package com.director.ui.properties.adapters;

import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.Icons;
import com.director.ui.theme.UITheme;
import com.director.timeline.model.TimelineInterpolation;
import com.director.ui.timeline.model.entity.EntityClipKeyframe;
import com.director.ui.timeline.model.entity.EntityKeyframe;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntityClipKeyframePropertyAdapter v1.0
 * 
 * <p>实体片段关键帧属性面板适配器，用于编辑 EntityClipKeyframe 的所有属性。
 * 
 * <p>UI 结构：
 * <ul>
 *   <li>⏱ 基础信息（时间、插值）</li>
 *   <li>📍 位置和旋转（位置、旋转、缩放）</li>
 *   <li>🎬 动作和状态（动作类型、速度、循环、可见性）</li>
 *   <li>🏷️ 行为和交互（激活状态、名称标签）</li>
 * </ul>
 *
 * @author Director
 * @version 1.0
 */
public class EntityClipKeyframePropertyAdapter implements IPropertyAdapter<EntityClipKeyframe> {

    private static final String TITLE = Icons.SMILE + " 片段关键帧";

    // ImGui 临时对象
    private final ImFloat timeFloat = new ImFloat();
    private final ImFloat posXFloat = new ImFloat();
    private final ImFloat posYFloat = new ImFloat();
    private final ImFloat posZFloat = new ImFloat();
    private final ImFloat rotXFloat = new ImFloat();
    private final ImFloat rotYFloat = new ImFloat();
    private final ImFloat rotZFloat = new ImFloat();
    private final ImFloat scaleXFloat = new ImFloat();
    private final ImFloat scaleYFloat = new ImFloat();
    private final ImFloat scaleZFloat = new ImFloat();
    private final ImFloat actionSpeedFloat = new ImFloat();
    private final ImFloat visibilityFloat = new ImFloat();
    private final ImBoolean actionLoopBool = new ImBoolean();
    private final ImBoolean activeBool = new ImBoolean();
    private final ImBoolean showNameTagBool = new ImBoolean();

    private final ImInt actionTypeIndex = new ImInt();
    private final ImInt interpolationIndex = new ImInt();

    private static final String[] ACTION_TYPE_LABELS = buildActionTypeLabels();
    private static final String[] INTERPOLATION_LABELS = buildInterpolationLabels();

    private static String[] buildActionTypeLabels() {
        EntityKeyframe.ActionType[] types = EntityKeyframe.ActionType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }

    private static String[] buildInterpolationLabels() {
        TimelineInterpolation[] types = TimelineInterpolation.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }

    @Override
    public boolean renderProperties(EntityClipKeyframe kf) {
        if (kf == null) {
            UITheme.label("(未选中片段关键帧)", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        AtomicBoolean changed = new AtomicBoolean(false);

        UITheme.title(TITLE);

        // ==========================================================
        // ⏱ 基础信息
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.INFO + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(kf.getTime());
            if (ImGui.dragFloat("时间 (秒)", timeFloat.getData(), 0.02f, 0, 10_000f, "%.2f")) {
                kf.setTime(timeFloat.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("关键帧在片段内的相对时间位置");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 插值方式
            interpolationIndex.set(kf.getInterpolation().ordinal());
            TimelineInterpolation[] interpolationTypes = TimelineInterpolation.values();

            if (ImGui.combo("插值模式", interpolationIndex, INTERPOLATION_LABELS)) {
                kf.setInterpolation(interpolationTypes[interpolationIndex.get()]);
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("线性：匀速变化 | 平滑：缓入缓出 | 步进：瞬间切换");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 📍 位置和旋转
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.PIN + " 位置和旋转", ImGuiTreeNodeFlags.DefaultOpen)) {
            Vector3f pos = kf.getPosition();
            posXFloat.set(pos.x);
            posYFloat.set(pos.y);
            posZFloat.set(pos.z);

            if (ImGui.dragFloat3("位置", new float[]{posXFloat.get(), posYFloat.get(), posZFloat.get()}, 0.1f, -10000f, 10000f, "%.2f")) {
                kf.setPosition(posXFloat.get(), posYFloat.get(), posZFloat.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("实体的世界坐标位置 (X, Y, Z)");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            Vector3f rot = kf.getRotation();
            rotXFloat.set(rot.x);
            rotYFloat.set(rot.y);
            rotZFloat.set(rot.z);

            if (ImGui.dragFloat3("旋转", new float[]{rotXFloat.get(), rotYFloat.get(), rotZFloat.get()}, 1.0f, -360f, 360f, "%.1f°")) {
                kf.setRotation(rotXFloat.get(), rotYFloat.get(), rotZFloat.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("实体的旋转角度（偏航、俯仰、翻滚，度）");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            Vector3f scale = kf.getScale();
            scaleXFloat.set(scale.x);
            scaleYFloat.set(scale.y);
            scaleZFloat.set(scale.z);

            if (ImGui.dragFloat3("缩放", new float[]{scaleXFloat.get(), scaleYFloat.get(), scaleZFloat.get()}, 0.01f, 0.01f, 10f, "%.2f")) {
                kf.setScale(scaleXFloat.get(), scaleYFloat.get(), scaleZFloat.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("实体的缩放比例 (X, Y, Z)，1.0 = 正常大小");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 🎬 动作和状态
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.ANIMATION + " 动作和状态", ImGuiTreeNodeFlags.DefaultOpen)) {
            // 动作类型
            actionTypeIndex.set(kf.getActionType().ordinal());
            EntityKeyframe.ActionType[] actionTypes = EntityKeyframe.ActionType.values();

            if (ImGui.combo("动作类型", actionTypeIndex, ACTION_TYPE_LABELS)) {
                kf.setActionType(actionTypes[actionTypeIndex.get()]);
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.textWrapped("选择实体的动作类型（待机/行走/奔跑/跳跃/攻击/交互/自定义）");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 动作速度
            actionSpeedFloat.set(kf.getActionSpeed());
            if (ImGui.sliderFloat("动作速度", actionSpeedFloat.getData(), 0.0f, 2.0f, "%.2f")) {
                kf.setActionSpeed(actionSpeedFloat.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("动作播放速度：0 = 静止，1 = 正常，2 = 快速");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 动作循环
            actionLoopBool.set(kf.isActionLoop());
            if (ImGui.checkbox("动作循环", actionLoopBool)) {
                kf.setActionLoop(actionLoopBool.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("开启：动作循环播放 | 关闭：动作播放一次");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 可见性
            visibilityFloat.set(kf.getVisibility());
            if (ImGui.sliderFloat("可见性", visibilityFloat.getData(), 0.0f, 1.0f, "%.2f")) {
                kf.setVisibility(visibilityFloat.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("实体可见性：0 = 完全隐藏，1 = 完全可见");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 🏷️ 行为和交互
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.TAG + " 行为和交互")) {
            // 激活状态
            activeBool.set(kf.isActive());
            if (ImGui.checkbox("激活", activeBool)) {
                kf.setActive(activeBool.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("实体是否参与场景（激活的实体才会被渲染和控制）");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 显示名称标签
            showNameTagBool.set(kf.isShowNameTag());
            if (ImGui.checkbox("显示名称标签", showNameTagBool)) {
                kf.setShowNameTag(showNameTagBool.get());
                changed.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("是否在实体上方显示名称标签");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        return changed.get();
    }

    @Override
    public Class<EntityClipKeyframe> getTargetType() {
        return EntityClipKeyframe.class;
    }
}
