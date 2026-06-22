package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.i18n.DirectorLang;
import com.director.i18n.TimelinePropertyAdapterI18n;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.properties.PropertyReflection;
import com.director.ui.theme.UITheme;
import com.director.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImString;
import imgui.type.ImBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 时间线序列属性适配器 v4.8 - Director Properties System
 * 
 * <p>为 Director 时间线系统的序列对象提供最高层属性编辑器。
 * 
 * <h3>时间线序列（TimelineSequence）</h3>
 * <p>TimelineSequence 是时间编辑系统的顶层容器，负责管理：
 * <ul>
 *   <li>多个 TimelineTrack（轨道）的集合</li>
 *   <li>全局播放参数（时长、循环、速度）</li>
 *   <li>轨道的添加、删除、排序</li>
 *   <li>序列级别的播放控制</li>
 * </ul>
 * 
 * <h3>支持的属性</h3>
 * <ul>
 *   <li>📋 基础信息（序列名称、总时长、时间缩放）</li>
 *   <li>⚙️ 播放控制（循环模式、播放速度、当前时间）</li>
 *   <li>🎞 轨道管理（轨道列表、添加、删除、跳转编辑）</li>
 *   <li>📊 统计信息（轨道总数、总关键帧数）</li>
 *   <li>⚡ 快捷操作（播放、暂停、停止、清空等）</li>
 * </ul>
 * 
 * <h3>UI 特性</h3>
 * <ul>
 *   <li>折叠式分组 - 清晰的属性分类</li>
 *   <li>轨道树形展开 - 可展开查看每个轨道</li>
 *   <li>跳转编辑 - 点击轨道跳转到轨道编辑器</li>
 *   <li>播放控制 - 内置播放器控制按钮</li>
 *   <li>智能提示 - Tooltip 说明</li>
 *   <li>实时统计 - 当前时间、轨道数等</li>
 * </ul>
 * 
 * <h3>与其他适配器的交互</h3>
 * <ul>
 *   <li>点击"编辑轨道"按钮 → TimelineTrackPropertyAdapter</li>
 *   <li>TimelineTrack → Container → Keyframe 的完整链路</li>
 * </ul>
 * 
 * <h3>注意事项</h3>
 * <p>由于时间线序列系统可能尚未完整实现，此适配器采用通用 Object 类型，
 * 使用反射和类型检查来动态渲染属性。当序列系统完整后，可以
 * 替换为具体的 TimelineSequence 类型。
 * 
 * @author Director Team
 * @version 4.8
 * @since 2025-10-16
 */
public class TimelineSequencePropertyAdapter implements IPropertyAdapter<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineSequencePropertyAdapter.class);

    @Override
    public boolean renderProperties(Object sequence) {
        if (sequence == null) {
            UITheme.spacingLarge();
            UITheme.label(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.NULL_SEQUENCE),
                    UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }
        
        // 使用数组包装以支持 lambda 修改
        final boolean[] modifiedRef = {false};
        
        // 标题
        UITheme.title("⏱ " + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.TITLE));
        
        try {
            // ========== 基础信息 ==========
            if (ImGui.collapsingHeader("📋 "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.SECTION_BASIC),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                // 序列名称
                String currentName = PropertyReflection.getString(sequence, "name", "getName",
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.DEFAULT_NAME));
                ImString nameBuffer = new ImString(currentName, 256);
                if (ImGui.inputText(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.SEQUENCE_NAME),
                        nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    if (PropertyReflection.setString(sequence, "name", "setName", nameBuffer.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineSequencePropertyAdapter] 序列名称已更新: {}", nameBuffer.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineSequence.SEQUENCE_NAME_TOOLTIP));
                }
                
                // 总时长
                float currentDuration = PropertyReflection.getFloat(sequence, "duration", "getDuration", 10.0f);
                float[] duration = {currentDuration};
                if (ImGui.dragFloat(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.TOTAL_DURATION),
                        duration, 0.1f, 0.1f, 600.0f)) {
                    if (PropertyReflection.setFloat(sequence, "duration", "setDuration", duration[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineSequencePropertyAdapter] 总时长已更新: {}", duration[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineSequence.TOTAL_DURATION_TOOLTIP));
                }
                
                // 时间缩放
                float currentTimeScale = PropertyReflection.getFloat(sequence, "timeScale", "getTimeScale", 1.0f);
                float[] timeScale = {currentTimeScale};
                if (ImGui.dragFloat(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.TIME_SCALE),
                        timeScale, 0.01f, 0.1f, 10.0f, "%.2f")) {
                    if (PropertyReflection.setFloat(sequence, "timeScale", "setTimeScale", timeScale[0])) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineSequencePropertyAdapter] 时间缩放已更新: {}", timeScale[0]);
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineSequence.TIME_SCALE_TOOLTIP));
                }
                
                // UUID 显示
                String id = PropertyReflection.getString(sequence, "id", "getId", null);
                if (id != null) {
                    UITheme.spacingSmall();
                    UITheme.label(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineSequence.SEQUENCE_ID, id),
                            UITheme.DarkTheme.TEXT_DISABLED);
                }
            }
            
            // ========== 播放控制 ==========
            if (ImGui.collapsingHeader("⚙️ "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.SECTION_PLAYBACK),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                // 循环播放
                boolean currentLoop = PropertyReflection.getBoolean(sequence, "loop", "isLoop", false);
                ImBoolean loop = new ImBoolean(currentLoop);
                if (ImGui.checkbox(
                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.LOOP_PLAYBACK),
                        loop)) {
                    if (PropertyReflection.setBoolean(sequence, "loop", "setLoop", loop.get())) {
                        modifiedRef[0] = true;
                        LOGGER.debug("[TimelineSequencePropertyAdapter] 循环播放已更新: {}", loop.get());
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineSequence.LOOP_PLAYBACK_TOOLTIP));
                }
                
                // 播放模式（如果支持）
                int currentPlayMode = PropertyReflection.getInt(sequence, "playMode", "getPlayMode", 0);
                if (currentPlayMode >= 0) {
                    String[] playModes = TimelinePropertyAdapterI18n.sequencePlayModes();
                    imgui.type.ImInt playModeIdx = new imgui.type.ImInt(currentPlayMode);
                    if (ImGui.combo(
                            TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.PLAY_MODE),
                            playModeIdx, playModes)) {
                        if (PropertyReflection.setInt(sequence, "playMode", "setPlayMode", playModeIdx.get())) {
                            modifiedRef[0] = true;
                            LOGGER.debug("[TimelineSequencePropertyAdapter] 播放模式已更新: {}", 
                                playModes[playModeIdx.get()]);
                        }
                    }
                    if (ImGui.isItemHovered()) {
                        ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineSequence.PLAY_MODE_TOOLTIP));
                    }
                }
                
                UITheme.spacingSmall();
                
                // 当前播放时间（只读）
                float currentTime = PropertyReflection.getFloat(sequence, "currentTime", "getCurrentTime", 0.0f);
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.CURRENT_TIME, currentTime), 
                    UITheme.DarkTheme.TEXT_SELECTED);
                
                // 播放状态（只读）
                boolean isPlaying = PropertyReflection.getBoolean(sequence, "playing", "isPlaying", false);
                String playStatus = isPlaying
                        ? TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.PLAYING_STATUS,
                                Icons.PLAY)
                        : TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.PAUSED_STATUS,
                                Icons.PAUSE);
                UITheme.label(playStatus, UITheme.DarkTheme.TEXT_DISABLED);
            }
            
            // ========== 轨道管理 ==========
            if (ImGui.collapsingHeader(Icons.FILM + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.SECTION_TRACKS),
                    ImGuiTreeNodeFlags.DefaultOpen)) {
                // 获取轨道列表
                Object tracksObj = PropertyReflection.readProperty(sequence, "tracks", "getTracks");
                
                if (tracksObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> tracks = (List<Object>) tracksObj;
                    
                    if (tracks.isEmpty()) {
                        UITheme.label(TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineSequence.TRACKS_NONE),
                                UITheme.DarkTheme.TEXT_DISABLED);
                        UITheme.label(TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineSequence.ADD_TRACK_HINT),
                                UITheme.DarkTheme.TEXT_DISABLED);
                    } else {
                        // 轨道列表
                        for (int i = 0; i < tracks.size(); i++) {
                            Object track = tracks.get(i);
                            ImGui.pushID(i);
                            
                            String trackName = PropertyReflection.getString(track, "name", "getName",
                                    TimelinePropertyAdapterI18n.tr(
                                            DirectorLang.Properties.AdapterTimelineSequence.DEFAULT_TRACK_NAME, i));
                            boolean trackEnabled = PropertyReflection.getBoolean(track, "enabled", "isEnabled", true);
                            String statusIcon = trackEnabled ? "●" : "○";
                            
                            if (ImGui.treeNodeEx(statusIcon + " " + trackName, 
                                    ImGuiTreeNodeFlags.Framed | ImGuiTreeNodeFlags.SpanAvailWidth)) {
                                
                                // 轨道简要信息
                                String trackType = PropertyReflection.getString(track, "type", "getType",
                                        TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.UNKNOWN));
                                UITheme.label(TimelinePropertyAdapterI18n.tr(
                                        DirectorLang.Properties.AdapterTimelineSequence.TRACK_TYPE, trackType),
                                        UITheme.DarkTheme.TEXT);
                                
                                float trackWeight = PropertyReflection.getFloat(track, "weight", "getWeight", 1.0f);
                                UITheme.label(TimelinePropertyAdapterI18n.tr(
                                        DirectorLang.Properties.AdapterTimelineSequence.TRACK_WEIGHT, trackWeight * 100), 
                                    UITheme.DarkTheme.TEXT);
                                
                                int keyframeCount = PropertyReflection.getInt(track, "keyframeCount", "getKeyframeCount", 0);
                                UITheme.label(TimelinePropertyAdapterI18n.tr(
                                        DirectorLang.Properties.AdapterTimelineSequence.TRACK_KEYFRAMES, keyframeCount),
                                        UITheme.DarkTheme.TEXT);
                                
                                UITheme.spacingSmall();
                                
                                // 操作按钮
                                if (ImGui.button(
                                        TimelinePropertyAdapterI18n.tr(
                                                DirectorLang.Properties.AdapterTimelineSequence.EDIT_TRACK),
                                        110, UITheme.Spacing.BUTTON_HEIGHT)) {
                                    // 切换选择到此轨道
                                    DirectorServices.get().uiSelection().setCurrentSelection(track);
                                    LOGGER.debug("[TimelineSequencePropertyAdapter] 跳转编辑轨道: {}", trackName);
                                }
                                if (ImGui.isItemHovered()) {
                                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                                            DirectorLang.Properties.AdapterTimelineSequence.EDIT_TRACK_TOOLTIP));
                                }
                                
                                ImGui.sameLine();
                                
                                if (ImGui.button(
                                        TimelinePropertyAdapterI18n.tr(DirectorLang.Common.DELETE) + "##track" + i,
                                        110, UITheme.Spacing.BUTTON_HEIGHT)) {
                                    // 调用 removeTrack 方法
                                    try {
                                        var removeMethod = sequence.getClass().getMethod("removeTrack", Object.class);
                                        removeMethod.invoke(sequence, track);
                                        modifiedRef[0] = true;
                                        LOGGER.info("[TimelineSequencePropertyAdapter] 删除轨道: {}", trackName);
                                    } catch (Exception e) {
                                        LOGGER.error("[TimelineSequencePropertyAdapter] 删除轨道失败", e);
                                    }
                                }
                                if (ImGui.isItemHovered()) {
                                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                                            DirectorLang.Properties.AdapterTimelineSequence.DELETE_TRACK_TOOLTIP));
                                }
                                
                                ImGui.treePop();
                            }
                            
                            ImGui.popID();
                        }
                        
                        UITheme.spacingSmall();
                        UITheme.label(TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineSequence.TRACK_HINT), 
                            UITheme.DarkTheme.TEXT_DISABLED);
                    }
                } else {
                    UITheme.label(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineSequence.CANNOT_GET_TRACKS),
                            UITheme.DarkTheme.TEXT_DISABLED);
                }
                
                UITheme.spacingSmall();
                
                // 添加和清空按钮
                if (ImGui.button("➕ "
                        + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.ADD_TRACK),
                        120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    LOGGER.info("[TimelineSequencePropertyAdapter] 添加新轨道");
                    // TODO: 创建并添加新轨道
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineSequence.ADD_TRACK_TOOLTIP));
                }
                
                ImGui.sameLine();
                
                if (ImGui.button("🗑 "
                        + TimelinePropertyAdapterI18n.tr(
                                DirectorLang.Properties.AdapterTimelineSequence.CLEAR_ALL_TRACKS),
                        120, UITheme.Spacing.BUTTON_HEIGHT)) {
                    LOGGER.warn("[TimelineSequencePropertyAdapter] 清空所有轨道");
                    // TODO: 清空轨道列表
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                            DirectorLang.Properties.AdapterTimelineSequence.CLEAR_ALL_TRACKS_TOOLTIP));
                }
            }
            
            // ========== 统计信息 ==========
            if (ImGui.collapsingHeader("📊 "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.SECTION_STATS))) {
                // 轨道统计
                Object tracksObj = PropertyReflection.readProperty(sequence, "tracks", "getTracks");
                int trackCount = 0;
                int totalKeyframes = 0;
                
                if (tracksObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Object> tracks = (List<Object>) tracksObj;
                    trackCount = tracks.size();
                    
                    // 统计总关键帧数
                    for (Object track : tracks) {
                        int count = PropertyReflection.getInt(track, "keyframeCount", "getKeyframeCount", 0);
                        totalKeyframes += count;
                    }
                }
                
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.TRACK_TOTAL, trackCount),
                        UITheme.DarkTheme.TEXT);
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.TOTAL_KEYFRAMES, totalKeyframes),
                        UITheme.DarkTheme.TEXT);
                
                // 时间统计
                float duration = PropertyReflection.getFloat(sequence, "duration", "getDuration", 0.0f);
                float timeScale = PropertyReflection.getFloat(sequence, "timeScale", "getTimeScale", 1.0f);
                float actualDuration = duration / timeScale;
                
                UITheme.spacingSmall();
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.DURATION_LABEL, duration), 
                    UITheme.DarkTheme.TEXT_DISABLED);
                UITheme.label(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.ACTUAL_DURATION, actualDuration, timeScale), 
                    UITheme.DarkTheme.TEXT_DISABLED);
            }
            
            UITheme.separator();
            
            // ========== 快捷操作 ==========
            UITheme.beginGroup(TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineCommon.QUICK_ACTIONS));
            
            // 播放控制
            UITheme.label(TimelinePropertyAdapterI18n.tr(
                    DirectorLang.Properties.AdapterTimelineSequence.PLAYBACK_CONTROL),
                    UITheme.DarkTheme.TEXT);
            UITheme.spacingSmall();
            
            if (ImGui.button(Icons.PLAY + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.PLAY),
                    80, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(sequence, "play")) {
                    LOGGER.info("[TimelineSequencePropertyAdapter] 播放序列");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.PLAY_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(Icons.PAUSE + " "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.PAUSE),
                    80, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(sequence, "pause")) {
                    LOGGER.info("[TimelineSequencePropertyAdapter] 暂停序列");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.PAUSE_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            if (ImGui.button("⏹ "
                    + TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.STOP),
                    80, UITheme.Spacing.BUTTON_HEIGHT)) {
                if (PropertyReflection.invokeVoid(sequence, "stop")) {
                    LOGGER.info("[TimelineSequencePropertyAdapter] 停止序列");
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.STOP_TOOLTIP));
            }
            
            UITheme.spacingSmall();
            UITheme.label(TimelinePropertyAdapterI18n.tr(
                    DirectorLang.Properties.AdapterTimelineSequence.SEQUENCE_OPS),
                    UITheme.DarkTheme.TEXT);
            UITheme.spacingSmall();
            
            // 序列操作
            if (ImGui.button(TimelinePropertyAdapterI18n.tr(
                    DirectorLang.Properties.AdapterTimelineSequence.DUPLICATE_SEQUENCE),
                    120, UITheme.Spacing.BUTTON_HEIGHT)) {
                LOGGER.info("[TimelineSequencePropertyAdapter] 复制序列");
                // TODO: 实现复制逻辑
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.DUPLICATE_SEQUENCE_TOOLTIP));
            }
            
            ImGui.sameLine();
            
            if (ImGui.button(TimelinePropertyAdapterI18n.tr(
                    DirectorLang.Properties.AdapterTimelineSequence.RESET_SEQUENCE),
                    120, UITheme.Spacing.BUTTON_HEIGHT)) {
                // 重置到初始状态
                PropertyReflection.setFloat(sequence, "currentTime", "setCurrentTime", 0.0f);
                PropertyReflection.invokeVoid(sequence, "stop");
                modifiedRef[0] = true;
                LOGGER.info("[TimelineSequencePropertyAdapter] 重置序列");
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(TimelinePropertyAdapterI18n.tr(
                        DirectorLang.Properties.AdapterTimelineSequence.RESET_SEQUENCE_TOOLTIP));
            }
            
            UITheme.endGroup();
            
        } catch (Exception e) {
            LOGGER.error("[TimelineSequencePropertyAdapter] 渲染序列属性时出错", e);
            UITheme.label(TimelinePropertyAdapterI18n.tr(
                    DirectorLang.Properties.AdapterTimelineCommon.RENDER_ERROR, e.getMessage()),
                    UITheme.DarkTheme.TEXT_DISABLED);
        }
        
        return modifiedRef[0];
    }
    @Override
    public Class<Object> getTargetType() {
        // 使用 Object 以支持各种序列类型
        return Object.class;
    }
    
    @Override
    public String getAdapterName() {
        return TimelinePropertyAdapterI18n.tr(DirectorLang.Properties.AdapterTimelineSequence.ADAPTER_NAME);
    }
    
    @Override
    public boolean supports(Object target) {
        if (target == null) return false;
        
        // 检查是否是时间线序列对象
        String className = target.getClass().getSimpleName().toLowerCase();
        return className.contains("sequence") || 
               className.contains("timeline");
    }
}
