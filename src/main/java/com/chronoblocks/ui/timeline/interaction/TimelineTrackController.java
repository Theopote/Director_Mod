package com.chronoblocks.ui.timeline.interaction;

import com.chronoblocks.ui.timeline.commands.DeleteTrackCommand;
import com.chronoblocks.ui.timeline.commands.TimelineCommandHistory;
import com.chronoblocks.ui.timeline.model.*;
import imgui.ImGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 专门处理轨道相关的操作（CRUD）和菜单渲染
 * 
 * <p>职责：
 * <ul>
 *   <li>创建新轨道</li>
 *   <li>删除轨道</li>
 *   <li>重命名轨道</li>
 *   <li>渲染新建轨道菜单</li>
 *   <li>轨道上移/下移</li>
 *   <li>复制轨道</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class TimelineTrackController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineTrackController.class);
    
    private final TimelineModel model;
    private final TimelineCommandHistory commandHistory;
    private final Runnable onDataChangedCallback;

    public TimelineTrackController(TimelineModel model, TimelineCommandHistory history, Runnable callback) {
        this.model = model;
        this.commandHistory = history;
        this.onDataChangedCallback = callback;
    }

    /**
     * 打开新建轨道菜单
     */
    public void openMenu() {
        ImGui.openPopup("新建轨道##AddTrackMenu");
    }

    /**
     * 渲染新建轨道弹出菜单
     */
    public void renderAddTrackMenu() {
        if (ImGui.beginPopup("新建轨道##AddTrackMenu")) {
            ImGui.text("选择轨道类型：");
            ImGui.separator();
            
            for (TrackType type : TrackType.values()) {
                if (ImGui.menuItem(type.getIconAndName())) {
                    createNewTrack(type);
                }
                
                // 显示轨道类型说明
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip();
                    ImGui.text(type.getDescription());
                    ImGui.endTooltip();
                }
            }
            
            ImGui.endPopup();
        }
    }

    /**
     * 创建新轨道
     */
    private void createNewTrack(TrackType type) {
        // 计算同类型轨道的数量
        long sameTypeCount = model.getTracks().stream()
                .filter(t -> t.getType() == type)
                .count();
        
        // 创建新轨道（带编号）
        String trackName = type.getDisplayName() + " " + (sameTypeCount + 1);
        TimelineTrack newTrack = new TimelineTrack(trackName, type);
        model.addTrack(newTrack);
        
        // 触发数据改变回调
        onDataChangedCallback.run();
        
        LOGGER.info("[TrackController] 创建新轨道: {} (类型: {}, 同类型第{}条)", 
                   trackName, type.getDisplayName(), sameTypeCount + 1);
    }

    /**
     * 删除轨道（支持撤销）
     */
    public void deleteTrack(TimelineTrack track) {
        if (track == null) {
            return;
        }
        
        // 确认删除（如果轨道有片段）
        if (!track.getClips().isEmpty()) {
            LOGGER.warn("[TrackController] 轨道 '{}' 包含 {} 个片段，确认删除", 
                       track.getName(), track.getClips().size());
        }
        
        // 使用命令模式支持撤销
        DeleteTrackCommand cmd = new DeleteTrackCommand(model, track, model.getTracks());
        commandHistory.executeCommand(cmd);
        
        // 触发数据改变回调
        onDataChangedCallback.run();
        
        LOGGER.info("[TrackController] 已删除轨道: {} (类型: {})", 
                   track.getName(), track.getType().getDisplayName());
    }

    /**
     * 上移轨道（交换索引）
     */
    public void moveTrackUp(TimelineTrack track) {
        if (track == null) {
            return;
        }
        
        List<TimelineTrack> tracks = model.getTracks();
        int index = tracks.indexOf(track);
        
        if (index > 0) {
            // 交换位置
            Collections.swap(tracks, index, index - 1);
            onDataChangedCallback.run();
            
            LOGGER.info("[TrackController] 上移轨道: {} (索引 {} -> {})", 
                       track.getName(), index, index - 1);
        }
    }

    /**
     * 下移轨道（交换索引）
     */
    public void moveTrackDown(TimelineTrack track) {
        if (track == null) {
            return;
        }
        
        List<TimelineTrack> tracks = model.getTracks();
        int index = tracks.indexOf(track);
        
        if (index >= 0 && index < tracks.size() - 1) {
            // 交换位置
            Collections.swap(tracks, index, index + 1);
            onDataChangedCallback.run();
            
            LOGGER.info("[TrackController] 下移轨道: {} (索引 {} -> {})", 
                       track.getName(), index, index + 1);
        }
    }

    /**
     * 复制轨道（包括所有片段）
     */
    public void duplicateTrack(TimelineTrack track) {
        if (track == null) {
            return;
        }
        
        // 1. 计算新名称（控制器逻辑）
        long sameTypeCount = model.getTracks().stream()
                .filter(t -> t.getType() == track.getType())
                .count();
        String newName = track.getType().getDisplayName() + " " + (sameTypeCount + 1);
        
        // 2. 创建新轨道
        TimelineTrack newTrack = new TimelineTrack(newName, track.getType());

        // 3. 复制片段，并应用业务规则（控制器逻辑）
        for (TimelineClip clip : track.getClips()) {
            TimelineClip newClip = new TimelineClip(clip); // 使用拷贝构造函数
            newClip.setLabel(clip.getLabel() + " (副本)"); // 控制器负责命名
            
            // 使用 offsetTime 简化偏移逻辑（自动处理关键帧）
            newClip.offsetTime(0.5f);

            newTrack.addClip(newClip);
        }

        model.addTrack(newTrack);
        onDataChangedCallback.run();
        
        LOGGER.info("[TrackController] 已复制轨道: {} -> {}", track.getName(), newName);
    }
}

