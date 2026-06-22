package com.director.ui.properties.adapters;

import com.director.i18n.DirectorLang;
import com.director.i18n.TimelinePropertyAdapterI18n;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyReflection;
import com.director.ui.properties.PropertyAdapterRegistry;
import com.director.ui.actor.track.ActorTrackClipAuthoringService;
import com.director.ui.actor.track.ActorTrackClipKind;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import com.director.ui.timeline.interaction.ActionBrushState;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.TimelineTrack;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import imgui.type.ImInt;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 时间线轨道属性适配器 v4.8 - Director Properties System
 * 
 * <p>为 Director 时间线系统的轨道对象提供专用属性编辑器。
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>📋 基本信息（名称、启用状态、轨道类型、ID）</li>
 *   <li>📦 容器绑定（绑定容器名称、选择器）</li>
 *   <li>🎨 混合与权重（混合模式、权重、叠加模式）</li>
 *   <li>⏱ 对齐与范围（对齐模式、开始/结束时间）</li>
 *   <li>👁 可视化参数（轨道颜色、显示偏移）</li>
 *   <li>📊 统计信息（关键帧数量、持续时间）</li>
 *   <li>⚡ 快捷操作（复制、清空、锁定等）</li>
 * </ul>
 * 
 * <h3>混合模式</h3>
 * <ul>
 *   <li>Override - 覆盖模式（默认）</li>
 *   <li>Additive - 叠加模式</li>
 *   <li>Multiply - 乘法模式</li>
 *   <li>Subtract - 减法模式</li>
 *   <li>Custom - 自定义模式</li>
 * </ul>
 * 
 * <h3>对齐模式</h3>
 * <ul>
 *   <li>Global Time - 全局时间轴对齐</li>
 *   <li>Container Time - 容器时间对齐</li>
 *   <li>Local Offset - 本地偏移对齐</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>实时预览 - 当前值显示</li>
 *   <li>快捷操作 - 复制、清空等</li>
 *   <li>智能提示 - Tooltip 说明</li>
 *   <li>颜色拾取 - 轨道颜色编辑</li>
 *   <li>统计信息 - 实时计算显示</li>
 * </ul>
 * 
 * <h3>注意事项</h3>
 * <p>由于时间线系统可能尚未完整实现，此适配器采用通用 Object 类型，
 * 使用反射和类型检查来动态渲染属性。当时间线系统完整后，可以
 * 替换为具体的 TimelineTrack 类型。
 * 
 * @author Director Team
 * @version 4.8
 * @since 2025-10-16
 */
public class TimelineTrackPropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineTrackPropertyAdapter.class);

    @Override
    public boolean renderProperties(Object track) {
        if (track == null) {
            UITheme.spacingLarge();
            UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.NULL_TRACK),
                    UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 使用数组包装以支持 lambda 修改
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title(Icons.FILM + " "
                + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.TITLE));
        
        try {
            // ========== 基本信息 ==========
            if (ImGui.collapsingHeader(Icons.CLIPBOARD + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.SECTION_BASIC),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                // 轨道名称
                String currentName = PropertyReflection.getString(track, "name", "getName",
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.DEFAULT_NAME));
                ImString nameBuffer = new ImString(currentName, 256);
                if (ImGui.inputText(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.TRACK_NAME),
                        nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (PropertyReflection.setString(track, "name", "setName", nameBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 轨道名称已更新: {}", nameBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.TRACK_NAME_TOOLTIP));
                }
                
                // 启用状态
                boolean currentEnabled = PropertyReflection.getBoolean(track, "enabled", "isEnabled", true);
                ImBoolean enabled = new ImBoolean(currentEnabled);
                if (ImGui.checkbox(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ENABLE_TRACK),
                        enabled)) {
                    if (PropertyReflection.setBoolean(track, "enabled", "setEnabled", enabled.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 启用状态已更新: {}", enabled.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.ENABLE_TRACK_TOOLTIP));
                }
                
                // 轨道类型（只读）
                String type = PropertyReflection.getString(track, "type", "getType",
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.UNKNOWN));
                UITheme.spacingSmall();
                UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.TRACK_TYPE, type),
                        UITheme.DarkTheme.TEXT);
                
                // UUID 显示
                String id = PropertyReflection.getString(track, "id", "getId", null);
                if (id != null) {
                    UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.TRACK_ID, id),
                            UITheme.DarkTheme.TEXT_DISABLED);
                }
                ImGui.unindent();
            }
            
            // ========== 容器绑定 ==========
            if (ImGui.collapsingHeader(Icons.BOX + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.SECTION_CONTAINER_BINDING))) {
                ImGui.indent();
                String currentContainer = PropertyReflection.getString(track, "boundContainerName", "getBoundContainerName",
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.UNBOUND));
                ImString containerBuffer = new ImString(currentContainer, 256);
                if (ImGui.inputText(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.BOUND_CONTAINER),
                        containerBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (PropertyReflection.setString(track, "boundContainerName", "setBoundContainerName", containerBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 绑定容器已更新: {}", containerBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.BOUND_CONTAINER_TOOLTIP));
                }
                
                UITheme.spacingSmall();
                
                if (ImGui.button(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.SELECT_CONTAINER),
                        120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    LOGGER.info("[TimelineTrackPropertyAdapter] 打开容器选择对话框（待实现）");
                    // TODO: 打开容器选择对话框
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.SELECT_CONTAINER_TOOLTIP));
                }
                
                ImGui.sameLine();
                
                if (ImGui.button(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.CLEAR_BINDING),
                        120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    if (PropertyReflection.setString(track, "boundContainerName", "setBoundContainerName", null)) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 已清除容器绑定");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.CLEAR_BINDING_TOOLTIP));
                }
                ImGui.unindent();
            }
            
            // ========== 混合与权重 ==========
            if (ImGui.collapsingHeader(Icons.PALETTE + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.SECTION_BLEND),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                // 混合模式
                String[] blendModes = TimelinePropertyAdapterI18n.trackBlendModes();
                int currentBlendMode = PropertyReflection.getInt(track, "blendMode", "getBlendMode", 0);
                ImInt blendModeIdx = new ImInt(currentBlendMode);
                if (ImGui.combo(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.BLEND_MODE),
                        blendModeIdx, blendModes)) {
                    if (PropertyReflection.setInt(track, "blendMode", "setBlendMode", blendModeIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 混合模式已更新: {}",
                            blendModes[blendModeIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.BLEND_MODE_TOOLTIP));
                }
                
                // 权重
                float currentWeight = PropertyReflection.getFloat(track, "weight", "getWeight", 1.0f);
                float[] weight = {currentWeight};
                if (ImGui.dragFloat(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.WEIGHT),
                        weight, 0.01f, 0.0f, 1.0f, "%.2f")) {
                    if (PropertyReflection.setFloat(track, "weight", "setWeight", weight[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 权重已更新: {}", weight[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.WEIGHT_TOOLTIP));
                }
                
                // 百分比显示
                UITheme.spacingSmall();
                UITheme.label(String.format("%.0f%%", weight[0] * 100), UITheme.DarkTheme.TEXT_DISABLED);
                
                UITheme.spacingSmall();
                
                // 叠加模式
                boolean currentAdditive = PropertyReflection.getBoolean(track, "additive", "isAdditive", false);
                ImBoolean additive = new ImBoolean(currentAdditive);
                if (ImGui.checkbox(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ADDITIVE_MODE),
                        additive)) {
                    if (PropertyReflection.setBoolean(track, "additive", "setAdditive", additive.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 叠加模式已更新: {}", additive.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.ADDITIVE_MODE_TOOLTIP));
                }
                ImGui.unindent();
            }
            
            // ========== 对齐与范围 ==========
            if (ImGui.collapsingHeader(Icons.CLOCK + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.SECTION_ALIGNMENT),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                ImGui.indent();
                // 对齐模式
                String[] alignmentModes = TimelinePropertyAdapterI18n.trackAlignmentModes();
                int currentAlignment = PropertyReflection.getInt(track, "alignmentMode", "getAlignmentMode", 0);
                ImInt alignmentIdx = new ImInt(currentAlignment);
                if (ImGui.combo(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ALIGNMENT_MODE),
                        alignmentIdx, alignmentModes)) {
                    if (PropertyReflection.setInt(track, "alignmentMode", "setAlignmentMode", alignmentIdx.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 对齐模式已更新: {}",
                            alignmentModes[alignmentIdx.get()]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.ALIGNMENT_MODE_TOOLTIP));
                }
                
                UITheme.spacingSmall();
                
                // 开始时间
                float currentStart = PropertyReflection.getFloat(track, "startTime", "getStartTime", 0.0f);
                float[] startTime = {currentStart};
                if (ImGui.dragFloat(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.START_TIME),
                        startTime, 0.05f, 0.0f, 600.0f)) {
                    if (PropertyReflection.setFloat(track, "startTime", "setStartTime", startTime[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 开始时间已更新: {}", startTime[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.START_TIME_TOOLTIP));
                }
                
                // 结束时间
                float currentEnd = PropertyReflection.getFloat(track, "endTime", "getEndTime", 5.0f);
                float[] endTime = {currentEnd};
                if (ImGui.dragFloat(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.END_TIME),
                        endTime, 0.05f, 0.0f, 600.0f)) {
                    if (PropertyReflection.setFloat(track, "endTime", "setEndTime", endTime[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 结束时间已更新: {}", endTime[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.END_TIME_TOOLTIP));
                }
                
                // 持续时间显示
                float duration = Math.max(0, endTime[0] - startTime[0]);
                UITheme.spacingSmall();
                UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.DURATION, duration),
                        UITheme.DarkTheme.TEXT_SELECTED);
                ImGui.unindent();
            }
            
            // ========== 可视化参数 ==========
            if (ImGui.collapsingHeader(Icons.EYEDROPPER + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.SECTION_VISUAL))) {
                ImGui.indent();
                // 轨道颜色
                float[] colorRGBA = PropertyReflection.getFloatArray(track, "color", "getColor");
                if (colorRGBA == null || colorRGBA.length < 4) {
                    colorRGBA = new float[]{0.4f, 0.8f, 1.0f, 1.0f};
                }
                
                if (ImGui.colorEdit4(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.TRACK_COLOR),
                        colorRGBA)) {
                    if (PropertyReflection.setFloatArray(track, "color", "setColor", colorRGBA)) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineTrackPropertyAdapter] 轨道颜色已更新");
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineTrack.TRACK_COLOR_TOOLTIP));
                }
                
                UITheme.spacingSmall();
                
                // 可视化偏移（使用 Vector3fPropertyAdapter）
                Object visualOffset = PropertyReflection.readProperty(track, "visualOffset", "getVisualOffset");
                if (visualOffset != null) {
                    var vector3fAdapter = PropertyAdapterRegistry.getAdapterFor(visualOffset);
                    if (vector3fAdapter != null) {
                        UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.VISUAL_OFFSET),
                                UITheme.DarkTheme.TEXT);
                        if (vector3fAdapter.renderProperties(visualOffset)) {
                            modifiedRef[0] = true;
                        }
                    }
                }
                ImGui.unindent();
            }
            
            // ========== 统计信息 ==========
            if (ImGui.collapsingHeader(Icons.CHART + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.SECTION_STATS))) {
                ImGui.indent();
                // 关键帧统计
                int keyframeCount = PropertyReflection.getInt(track, "keyframeCount", "getKeyframeCount", 0);
                UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.KEYFRAME_TOTAL, keyframeCount),
                        UITheme.DarkTheme.TEXT);
                
                int enabledCount = PropertyReflection.getInt(track, "enabledKeyframeCount", "countEnabledKeyframes", 0);
                UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ENABLED_KEYFRAMES, enabledCount),
                        UITheme.DarkTheme.TEXT);
                
                // 持续时间统计
                float start = PropertyReflection.getFloat(track, "startTime", "getStartTime", 0.0f);
                float end = PropertyReflection.getFloat(track, "endTime", "getEndTime", 0.0f);
                float duration = Math.max(0, end - start);
                
                UITheme.spacingSmall();
                UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.TIME_RANGE, start, end),
                    UITheme.DarkTheme.TEXT_DISABLED);
                UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.DURATION_STATS, duration),
                    UITheme.DarkTheme.TEXT_DISABLED);
                
                // 权重信息
                float weight = PropertyReflection.getFloat(track, "weight", "getWeight", 1.0f);
                UITheme.spacingSmall();
                UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.CURRENT_WEIGHT, weight * 100),
                    UITheme.DarkTheme.TEXT_DISABLED);
                ImGui.unindent();
            }
            
            UITheme.separator();
            
            // ========== 演员轨道片段创建 ==========
            if (track instanceof TimelineTrack timelineTrack) {
                if (timelineTrack.getType() == TrackType.ACTOR) {
                    var actorTrack = timelineTrack.getActorTrackPayload();
                    if (actorTrack != null) {
                        var actorRef = actorTrack.getActorRef();
                        if (actorRef != null) {
                            UITheme.beginGroup(Icons.ANIMATION + " "
                                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.SECTION_ACTOR_CLIPS));
                            ImGui.textDisabled(TimelinePropertyAdapterI18n.tr(
                                    DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_HINT));

                            if (renderActorClipButton(timelineTrack, ActorTrackClipKind.TRANSFORM)) {
                                modifiedRef[0] = true;
                            }
                            ImGui.sameLine();
                            if (renderActorClipButton(timelineTrack, ActorTrackClipKind.LOOK_AT_CAMERA)) {
                                modifiedRef[0] = true;
                            }

                            if (renderActorClipButton(timelineTrack, ActorTrackClipKind.ACTION)) {
                                modifiedRef[0] = true;
                            }
                            ImGui.sameLine();
                            if (renderActorClipButton(timelineTrack, ActorTrackClipKind.POSE)) {
                                modifiedRef[0] = true;
                            }
                            ImGui.sameLine();
                            if (renderActorClipButton(timelineTrack, ActorTrackClipKind.STATE)) {
                                modifiedRef[0] = true;
                            }

                            UITheme.endGroup();
                            UITheme.separator();
                        }
                    }
                }
            }
            
            // ========== 快捷操作 ==========
            UITheme.beginGroup(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.QUICK_ACTIONS));
            
            // 第一行
            if (ImGui.button(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.DUPLICATE_TRACK),
                    100, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[TimelineTrackPropertyAdapter] 复制轨道");
                // TODO: 实现复制逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineTrack.DUPLICATE_TRACK_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.CLEAR_KEYFRAMES),
                    100, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[TimelineTrackPropertyAdapter] 清空关键帧");
                // TODO: 实现清空逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineTrack.CLEAR_KEYFRAMES_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            boolean locked = PropertyReflection.getBoolean(track, "locked", "isLocked", false);
            String lockText = locked
                    ? TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.UNLOCK_TRACK)
                    : TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.LOCK_TRACK);
            if (ImGui.button(lockText, 100, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.setBoolean(track, "locked", "setLocked", !locked)) {
                    modifiedRef[0] = true;
                    LOGGER.info("[TimelineTrackPropertyAdapter] 轨道锁定状态: {}", !locked);
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(locked
                        ? TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.UNLOCK_TRACK_TOOLTIP)
                        : TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.LOCK_TRACK_TOOLTIP));
            }
            
            // 第二行
            if (ImGui.button(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.RESET_WEIGHT),
                    100, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.setFloat(track, "weight", "setWeight", 1.0f)) {
                    modifiedRef[0] = true;
                    LOGGER.debug("[TimelineTrackPropertyAdapter] 重置权重为 1.0");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineTrack.RESET_WEIGHT_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.RESET_TIME),
                    100, UITheme.Spacing.BUTTON_HEIGHT)) {
                PropertyReflection.setFloat(track, "startTime", "setStartTime", 0.0f);
                PropertyReflection.setFloat(track, "endTime", "setEndTime", 5.0f);
                modifiedRef[0] = true;
                LOGGER.debug("[TimelineTrackPropertyAdapter] 重置时间范围");
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineTrack.RESET_TIME_TOOLTIP));
            }
            
            UITheme.endGroup();
            
        } catch (Exception e) {
            LOGGER.error("[TimelineTrackPropertyAdapter] 渲染轨道属性时出错", e);
            UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.RENDER_ERROR, e.getMessage()),
                    UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }

    private boolean renderActorClipButton(TimelineTrack track, ActorTrackClipKind kind) {
        String label;
        String tooltip;
        switch (kind) {
            case TRANSFORM:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_TRANSFORM);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_TRANSFORM_TOOLTIP);
                break;
            case LOOK_AT_CAMERA:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_LOOK_AT);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_LOOK_AT_TOOLTIP);
                break;
            case ACTION:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_ACTION);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_ACTION_TOOLTIP);
                break;
            case POSE:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_POSE);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_POSE_TOOLTIP);
                break;
            case STATE:
                label = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_STATE);
                tooltip = TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ACTOR_CLIP_STATE_TOOLTIP);
                break;
            default:
                label = kind.name();
                tooltip = kind.name();
                break;
        }

        if (ImGui.button(label + "##actorTrackCreate" + kind, 92, UITheme.Spacing.BUTTON_HEIGHT)) {
            var action = ActionBrushState.get();
            var clip = ActorTrackClipAuthoringService.getInstance().createClip(track, kind, action);
            boolean created = clip != null;
            if (created) {
                LOGGER.info("[TimelineTrackPropertyAdapter] 已创建演员轨道片段: {}", kind);
            }
            return created;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip);
        }
        return false;
    }

    @Override
    public Class<Object> getTargetType() {
        // 使用 Object 以支持各种轨道类型
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineTrack.ADAPTER_NAME);
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        // 检查是否是时间线轨道对象
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("track") || 
               className.contains("timeline");
    }
}

