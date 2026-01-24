package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.Icons;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.container.ContainerTimelineService;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.entity.EntityKeyframe;
import com.chronoblocks.ui.timeline.playback.entity.TimelineEntityController;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.joml.Vector3f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EntityKeyframePropertyAdapter v1.0
 * 
 * <p>实体关键帧属性面板适配器，用于编辑实体关键帧的所有属性。
 * 
 * <p>UI 结构：
 * <ul>
 *   <li>⏱ 基础信息（时间、实体类型、插值）</li>
 *   <li>📍 位置和旋转（位置、旋转、缩放）</li>
 *   <li>🎬 动作和状态（动作类型、速度、循环、可见性）</li>
 *   <li>🏷️ 行为和交互（激活状态、名称标签）</li>
 * </ul>
 * 
 * <p>改动后自动触发 TimelineEntityController 刷新采样器 + 预览
 *
 * @author ChronoBlocks
 * @version 1.0
 */
public class EntityKeyframePropertyAdapter implements IPropertyAdapter<EntityKeyframe> {

    private static final String TITLE = Icons.SMILE + " 实体关键帧";

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

    private final ImInt entityTypeIndex = new ImInt();
    private final ImInt actionTypeIndex = new ImInt();
    private final ImInt interpolationIndex = new ImInt();

    private static final String[] ENTITY_TYPE_LABELS = buildEntityTypeLabels();
    private static final String[] ACTION_TYPE_LABELS = buildActionTypeLabels();
    private static final String[] INTERPOLATION_LABELS = buildInterpolationLabels();

    private static String[] buildEntityTypeLabels() {
        EntityKeyframe.EntityType[] types = EntityKeyframe.EntityType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }

    private static String[] buildActionTypeLabels() {
        EntityKeyframe.ActionType[] types = EntityKeyframe.ActionType.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }

    private static String[] buildInterpolationLabels() {
        com.chronoblocks.ui.timeline.model.TimelineInterpolation[] types = 
            com.chronoblocks.ui.timeline.model.TimelineInterpolation.values();
        String[] labels = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            labels[i] = types[i].getDisplayName();
        }
        return labels;
    }

    @Override
    public boolean renderProperties(EntityKeyframe kf) {
        if (kf == null) {
            UITheme.label("(未选中实体关键帧)", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        // ⭐ v1.0：提取公共对象，减少重复获取
        TimelineEntityController entityController = TimelineEntityController.getInstance();
        TimelineModel timelineModel = ContainerTimelineService.getInstance().getTimelineModel();
        
        // ⭐ v1.0：检查关键帧是否属于激活的实体轨道
        TimelineTrack keyframeTrack = null;
        if (timelineModel != null) {
            keyframeTrack = timelineModel.findTrackForKeyframe(kf);
        }
        
        TimelineTrack activeTrack = entityController.getActiveEntityTrack();
        boolean isTrackActive = (keyframeTrack != null && keyframeTrack == activeTrack);
        
        if (!isTrackActive) {
            UITheme.title(TITLE);
            UITheme.label(Icons.WARNING + " 此关键帧不属于激活的实体轨道", UITheme.DarkTheme.TEXT_WARNING);
            ImGui.textDisabled("当前激活轨道: " + (activeTrack != null ? activeTrack.getName() : "<无>"));
            if (keyframeTrack != null) {
                ImGui.textDisabled("关键帧所在轨道: " + keyframeTrack.getName());
            }
            UITheme.spacingMedium();
            UITheme.label("提示：只有激活轨道上的关键帧才能被编辑和影响世界中的实体。", UITheme.DarkTheme.TEXT_DISABLED);
            UITheme.label("请点击轨道头部的激活按钮来切换激活轨道。", UITheme.DarkTheme.TEXT_DISABLED);
            return false; // 禁用编辑
        }

        AtomicBoolean timeChanged = new AtomicBoolean(false);
        AtomicBoolean parameterChanged = new AtomicBoolean(false);

        UITheme.title(TITLE);

        // ==========================================================
        // ⏱ 基础信息
        // ==========================================================
        if (ImGui.collapsingHeader(Icons.INFO + " 基础信息", ImGuiTreeNodeFlags.DefaultOpen)) {
            timeFloat.set(kf.getTime());
            if (ImGui.dragFloat("时间 (秒)", timeFloat.getData(), 0.02f, 0, 10_000f, "%.2f")) {
                kf.setTime(timeFloat.get());
                timeChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("关键帧在时间线上的位置");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 实体类型预设
            entityTypeIndex.set(kf.getEntityType().ordinal());
            EntityKeyframe.EntityType[] entityTypes = EntityKeyframe.EntityType.values();

            if (ImGui.combo("实体类型", entityTypeIndex, ENTITY_TYPE_LABELS)) {
                EntityKeyframe.EntityType newType = entityTypes[entityTypeIndex.get()];
                kf.setEntityType(newType);
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.textWrapped("选择实体类型预设（NPC/玩家/动物/模型/自定义）");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 插值方式
            interpolationIndex.set(kf.getInterpolation().ordinal());
            com.chronoblocks.ui.timeline.model.TimelineInterpolation[] interpolationTypes = 
                com.chronoblocks.ui.timeline.model.TimelineInterpolation.values();

            if (ImGui.combo("插值模式", interpolationIndex, INTERPOLATION_LABELS)) {
                kf.setInterpolation(interpolationTypes[interpolationIndex.get()]);
                parameterChanged.set(true);
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
                parameterChanged.set(true);
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
                parameterChanged.set(true);
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
                parameterChanged.set(true);
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
                parameterChanged.set(true);
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
                parameterChanged.set(true);
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
                parameterChanged.set(true);
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
                parameterChanged.set(true);
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
                parameterChanged.set(true);
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
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("是否在实体上方显示名称标签");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();

            // 名称标签文本
            String displayName = kf.getDisplayName();
            if (displayName == null) displayName = "";
            ImString nameString = new ImString(displayName, 256);
            if (ImGui.inputText("名称标签", nameString)) {
                String newName = nameString.get().trim();
                kf.setDisplayName(newName.isEmpty() ? null : newName);
                parameterChanged.set(true);
            }
            if (ImGui.isItemHovered()) {
                ImGui.beginTooltip();
                ImGui.text("显示在实体上方的名称标签文本");
                ImGui.endTooltip();
            }

            UITheme.spacingSmall();
        }

        // ==========================================================
        // 触发实体控制器刷新
        // ==========================================================

        // ⭐ v1.0：参数变化时预览关键帧所在位置
        if (parameterChanged.get()) {
            entityController.rebuildFromActiveTrack();
            // 预览关键帧所在位置，而不是当前播放头位置
            entityController.previewAt(kf.getTime());
        }
        
        // ⭐ v1.0：时间变化只重建采样器，不触发预览
        if (timeChanged.get()) {
            entityController.rebuildFromActiveTrack();
            // 不调用预览，因为播放头位置没有改变
        }

        return parameterChanged.get() || timeChanged.get();
    }

    @Override
    public Class<EntityKeyframe> getTargetType() {
        return EntityKeyframe.class;
    }
}

