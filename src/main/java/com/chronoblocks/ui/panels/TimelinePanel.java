package com.chronoblocks.ui.panels;

import com.chronoblocks.core.timeline.TimelineCommandHistory;
import com.chronoblocks.core.timeline.commands.*;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.ui.timeline.*;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.ImDrawList;
import imgui.type.ImString;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiInputTextFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.UUID;

/**
 * ChronoBlocks 专业级时间轴面板（架构重构版 v7.4）
 * <p>
 * 基于完全解耦的 MVC 架构设计：
 * <ul>
 *   <li>✅ Model: {@link TimelineModel} - 纯数据模型</li>
 *   <li>✅ View: {@link TimelineRenderer} - 纯渲染逻辑</li>
 *   <li>✅ Controller: {@link TimelinePanel} - 中央协调器</li>
 *   <li>✅ Command Pattern: 完整的撤销/重做</li>
 *   <li>✅ Interaction Handler: 专职交互处理器</li>
 *   <li>✅ Control Bar: 独立的工具栏组件</li>
 *   <li>✅ Context Menu Factory: 菜单工厂模式</li>
 * </ul>
 * </p>
 * 
 * @author ChronoBlocks Team
 * @version 7.4
 * @since 1.0
 */
public class TimelinePanel extends AbstractPanel 
        implements TimelineInteractionHandler.InteractionCallbacks,
                   TimelineContextMenuFactory.MenuCallbacks,
                   TimelineControlBar.Callbacks {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePanel.class);
    
    // ========== 核心组件（架构优化）==========
    private final TimelineModel model;
    private final TimelineRenderer renderer;
    private final TimelineCommandHistory commandHistory;
    private final TimelineInteractionManager interactionManager;
    private final TimelineInteractionHandler interactionHandler;
    private final TimelineContextMenuFactory menuFactory;
    private final TimelineControlBar controlBar;
    private final ShortcutManager shortcutManager;
    
    // ========== UI 状态（封装优化）==========
    private final ClipboardState clipboard = new ClipboardState();
    private final RenamingState renaming = new RenamingState();
    private TimelineContextMenu activeContextMenu = null;
    private int dropTargetTrackIndex = -1;
    private TimelineTrack hoveredTrack = null;  // 鼠标悬停的轨道
    
    // ========== 自动保存 ==========
    private long lastAutoSaveTime = System.currentTimeMillis();
    private static final long AUTO_SAVE_INTERVAL = 5000; // 5秒自动保存
    private boolean dataChanged = false; // 数据是否改变
    
    // ========== 布局常量 ==========
    // 根据轨道数量动态计算高度，最多支持40条轨道
    private static final int MAX_TRACKS = 40;
    private float getCanvasHeight() {
        int trackCount = Math.min(model.getTracks().size(), MAX_TRACKS);
        // 每条轨道高度 + 间隙，加上标尺高度
        return TimelineRenderer.getRulerHeight() + 
               trackCount * (TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap()) + 
               4; // 额外缓冲空间
    }
    
    // ========== Drag&Drop payload type ==========
    public static final String DND_PAYLOAD_ITEM = "CHRONOBLOCKS_ITEM";
    
    public TimelinePanel() {
        super();
        
        // 初始化核心组件
        this.model = new TimelineModel();
        this.renderer = new TimelineRenderer(model);
        this.commandHistory = new TimelineCommandHistory();
        this.interactionManager = new TimelineInteractionManager();
        this.interactionHandler = new TimelineInteractionHandler(model, interactionManager, commandHistory, renderer);
        this.shortcutManager = new ShortcutManager();
        
        // 配置历史管理
        commandHistory.setMaxHistorySize(100);
        
        // 直接传递 this 作为回调对象（简洁的回调注入）
        this.interactionHandler.setCallbacks(this);
        this.menuFactory = new TimelineContextMenuFactory(commandHistory, model, this);
        this.controlBar = new TimelineControlBar(model, commandHistory, this);
        
        // 注册快捷键
        registerShortcuts();
    }
    
    /**
     * 注册所有快捷键
     */
    private void registerShortcuts() {
        // 撤销/重做
        shortcutManager.registerCtrl("undo", ImGuiKey.Z,
                commandHistory::undo, "撤销");
        shortcutManager.registerCtrl("redo", ImGuiKey.Y,
                commandHistory::redo, "重做");
        
        // 删除
        shortcutManager.registerKey("delete", ImGuiKey.Delete, 
            this::deleteSelectedClip, "删除选中片段");
        
        // 剪切/复制/粘贴
        shortcutManager.registerCtrl("cut", ImGuiKey.X,
            this::cutSelectedClip, "剪切");
        shortcutManager.registerCtrl("copy", ImGuiKey.C,
            this::copySelectedClip, "复制");
        shortcutManager.registerCtrl("paste", ImGuiKey.V,
            this::pasteClip, "粘贴");
        
        // 取消
        shortcutManager.registerKey("cancel", ImGuiKey.Escape,
                interactionManager::cancelInteraction, "取消交互");
        
        // 播放控制
        shortcutManager.registerKey("togglePlay", ImGuiKey.Space,
                model::togglePlay, "播放/暂停");
        shortcutManager.registerKey("jumpStart", ImGuiKey.Home,
                model::jumpToStart, "跳到开始");
        shortcutManager.registerKey("jumpEnd", ImGuiKey.End,
                model::jumpToEnd, "跳到结束");
        
        LOGGER.debug("[Timeline] 注册了 {} 个快捷键", shortcutManager.getShortcuts().size());
    }
    
    @Override
    public String getPanelName() {
        return "Timeline";
    }
    
    @Override
    public String getWindowName() {
        return "Timeline###TimelinePanel";
    }
    
    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.NoCollapse;
    }
    
    @Override
    protected void doRender() {
        // 1. 更新播放状态（每帧调用）
        updatePlayback();
        
        // 2. 处理快捷键
        shortcutManager.handleInput(ImGui.getIO());
        
        // 3. 渲染控制栏（委托给专门的组件）
        renderControlBar();
        
        UITheme.separator();
        
        // 4. 渲染时间线画布
        renderTimelineCanvas();
        
        // 5. 渲染新建轨道菜单
        renderAddTrackMenu();
        
        // 6. 渲染活动的上下文菜单（片段、轨道、关键帧）
        if (activeContextMenu != null) {
            activeContextMenu.render();
        }
        
        // 8. 自动保存（每5秒保存一次）
        autoSaveIfNeeded();
    }
    
    /**
     * 更新播放状态（每帧调用）
     */
    private void updatePlayback() {
        if (!model.isPlaying()) return;
        
        // 计算时间增量（基于 ImGui 的帧时间）
        float deltaTime = ImGui.getIO().getDeltaTime();
        
        // 更新当前时间
        float newTime = model.getCurrentTime() + deltaTime;
        float duration = model.getDuration();
        
        // 处理超出范围
        if (newTime > duration) {
            newTime = duration; // 停在终点
            model.setPlaying(false);
            LOGGER.debug("[Timeline] 播放结束，停在终点");
        }
        
        model.setCurrentTime(newTime);
    }
    
    /**
     * 自动保存时间线数据
     */
    private void autoSaveIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (dataChanged && (currentTime - lastAutoSaveTime) >= AUTO_SAVE_INTERVAL) {
            model.save();
            dataChanged = false;
            lastAutoSaveTime = currentTime;
            LOGGER.debug("[Timeline] 自动保存时间线数据");
        }
    }
    
    /**
     * 标记数据已改变
     */
    private void markDataChanged() {
        dataChanged = true;
    }
    
    /**
     * 渲染控制栏（委托给专门的组件）
     */
    private void renderControlBar() {
        controlBar.render();
    }
    
    /**
     * 渲染时间线画布
     */
    private void renderTimelineCanvas() {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float winX = ImGui.getCursorScreenPosX();
        float winY = ImGui.getCursorScreenPosY();
        float winW = ImGui.getContentRegionAvailX();
        
        float canvasHeight = getCanvasHeight();
        
        float contentX = winX + TimelineRenderer.getLeftLabelWidth();
        float contentW = winW - TimelineRenderer.getLeftLabelWidth();
        float contentY = winY + TimelineRenderer.getRulerHeight();
        float contentH = canvasHeight - TimelineRenderer.getRulerHeight();
        
        // 使用渲染器绘制
        renderer.renderCanvas(drawList, winX, winY, winW, canvasHeight);
        
        // 渲染原位编辑输入框
        renderInlineEdit(winX, contentY);
        
        // 渲染拖放高亮
        if (dropTargetTrackIndex >= 0) {
            float y = contentY + dropTargetTrackIndex * (TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap());
            renderer.renderDropHighlight(drawList, dropTargetTrackIndex, contentX, y, contentW);
        }
        
        // 不可见按钮接收鼠标事件（必须在交互处理之前）
        ImGui.setCursorScreenPos(winX, winY);
        ImGui.invisibleButton("##TimelineCanvas", winW, canvasHeight);
        
        // 使用统一的交互处理器处理所有交互（鼠标输入、片段、关键帧、播放头、拖放）
        interactionHandler.handleCanvasInteractions(contentX, contentY, contentW, contentH, 0f);
        
        ImGui.setCursorScreenPos(winX, winY + canvasHeight);
    }
    
    
    /**
     * 双击处理关键帧添加/删除
     */
    private void handleKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
        final float clickTime = clamp(model.screenToTime(mouseX, contentX), 
            clip.getStartTime(), clip.getEndTime());
        
        Float existingKf = clip.getKeyframeAt(clickTime, 0.1f);
        
        if (existingKf != null) {
            RemoveKeyframeCommand cmd = new RemoveKeyframeCommand(clip, existingKf);
            commandHistory.executeCommand(cmd);
            markDataChanged(); // 标记数据已改变
            LOGGER.debug("[Timeline] 删除关键帧 at {:.2f}（命令）", existingKf);
        } else {
            AddKeyframeCommand cmd = new AddKeyframeCommand(clip, clickTime);
            commandHistory.executeCommand(cmd);
            markDataChanged(); // 标记数据已改变
            LOGGER.debug("[Timeline] 添加关键帧 at {:.2f}（命令）", clickTime);
        }
    }
    
    /**
     * 打开新建轨道菜单
     */
    private void openAddTrackMenu() {
        ImGui.openPopup("新建轨道##AddTrackMenu");
    }
    
    /**
     * 渲染新建轨道弹出菜单
     */
    private void renderAddTrackMenu() {
        if (ImGui.beginPopup("新建轨道##AddTrackMenu")) {
            ImGui.text("选择轨道类型：");
            ImGui.separator();
            
            for (TrackType type : TrackType.values()) {
                if (ImGui.menuItem(type.getIconAndName())) {
                    // 计算同类型轨道的数量
                    int sameTypeCount = 0;
                    for (TimelineTrack track : model.getTracks()) {
                        if (track.getType() == type) {
                            sameTypeCount++;
                        }
                    }
                    
                    // 创建新轨道（带编号）
                    String trackName = type.getDisplayName() + " " + (sameTypeCount + 1);
                    TimelineTrack newTrack = new TimelineTrack(trackName, type);
                    model.addTrack(newTrack);
                    markDataChanged(); // 标记数据已改变，触发自动保存
                    LOGGER.info("[Timeline] 创建新轨道: {} (类型: {}, 同类型第{}条)", 
                               trackName, type.getDisplayName(), sameTypeCount + 1);
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
     * 打开轨道上下文菜单（使用菜单工厂）
     */
    private void openTrackContextMenu(TimelineTrack track) {
        activeContextMenu = menuFactory.createForTrack(track);
        activeContextMenu.open();
    }
    
    /**
     * 删除轨道（支持撤销）
     */
    private void deleteTrack(TimelineTrack track) {
        if (track == null) return;
        
        // 确认删除（如果轨道有片段）
        if (!track.getClips().isEmpty()) {
            LOGGER.warn("[Timeline] 轨道 '{}' 包含 {} 个片段，确认删除", 
                       track.getName(), track.getClips().size());
        }
        
        // 使用命令模式支持撤销
        @SuppressWarnings("unchecked")
        List<Object> trackListAsObjects = (List<Object>)(List<?>) model.getTracks();
        DeleteTrackCommand cmd = new DeleteTrackCommand(track, trackListAsObjects);
        commandHistory.executeCommand(cmd);
        
        hoveredTrack = null;
        markDataChanged(); // 标记数据已改变，触发自动保存
        
        LOGGER.info("[Timeline] 已删除轨道: {} (类型: {})", 
                   track.getName(), track.getType().getDisplayName());
    }
    
    /**
     * 开始原位编辑轨道名称
     */
    private void startEditingTrack(TimelineTrack track) {
        renaming.startEditing(track);
        LOGGER.debug("[Timeline] 开始编辑轨道: '{}'", track.getName());
    }
    
    /**
     * 完成原位编辑
     */
    private void finishEditingTrack(boolean apply) {
        if (!renaming.isEditing()) return;
        
        if (apply) {
            // 获取字符串并清理
            String newName = cleanTrackName(renaming.buffer.get());
            
            if (!newName.isEmpty()) {
                String oldName = renaming.track.getName();
                renaming.track.setName(newName);
                markDataChanged();
                LOGGER.info("[Timeline] 轨道重命名: '{}' -> '{}'", oldName, newName);
            } else {
                LOGGER.warn("[Timeline] 重命名失败：名称为空");
            }
        }
        
        renaming.clear();
        LOGGER.debug("[Timeline] 完成编辑轨道");
    }
    
    /**
     * 清理轨道名称（去除空字符、不可见字符等）
     */
    private String cleanTrackName(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        // 在第一个空字符处截断（ImString 缓冲区可能包含空字符）
        int nullIndex = input.indexOf('\0');
        if (nullIndex >= 0) {
            input = input.substring(0, nullIndex);
        }
        
        // 去除所有不可打印字符（ASCII < 32 或 >= 127）
        StringBuilder cleaned = new StringBuilder();
        for (char c : input.toCharArray()) {
            // 保留可打印字符（包括中文等 Unicode 字符）
            if (c >= 32 && c != 127) {
                cleaned.append(c);
            }
        }
        
        // 去除首尾空格
        return cleaned.toString().trim();
    }
    
    /**
     * 渲染原位编辑输入框
     */
    private void renderInlineEdit(float winX, float contentY) {
        if (!renaming.isEditing()) return;
        
        // 找到正在编辑的轨道索引
        int trackIndex = model.getTracks().indexOf(renaming.track);
        if (trackIndex < 0) {
            renaming.clear();
            return;
        }
        
        // 计算轨道名称位置
        float trackY = contentY + trackIndex * (TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap());
        float nameX = winX + 28;  // 图标后面
        float nameY = trackY + 6;
        float inputWidth = TimelineRenderer.getLeftLabelWidth() - 35;  // 留出空间给图标和锁定按钮
        
        // 设置输入框位置
        ImGui.setCursorScreenPos(nameX, nameY);
        
        // 设置输入框宽度
        ImGui.pushItemWidth(inputWidth);
        
        // 自动聚焦（第一帧）
        ImGui.setKeyboardFocusHere();
        
        // 输入框（支持 Enter 确认）
        boolean enterPressed = ImGui.inputText("##TrackNameEdit", renaming.buffer, 
            ImGuiInputTextFlags.EnterReturnsTrue);
        
        ImGui.popItemWidth();
        
        // Enter 键确认
        if (enterPressed) {
            finishEditingTrack(true);
        }
        
        // ESC 键取消
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            finishEditingTrack(false);
        }
        
        // 点击其他地方完成编辑
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && !ImGui.isItemHovered()) {
            finishEditingTrack(true);
        }
    }
    
    /**
     * 复制轨道（控制器负责命名和偏移逻辑）
     */
    private void duplicateTrack(TimelineTrack track) {
        if (track == null) return;
        
        // 1. 计算新名称（控制器逻辑）
        long sameTypeCount = model.getTracks().stream()
            .filter(t -> t.getType() == track.getType())
            .count();
        String newName = track.getType().getDisplayName() + " " + (sameTypeCount + 1);
        
        // 2. 创建新轨道
        TimelineTrack newTrack = new TimelineTrack(newName, track.getType());

        // 3. 复制片段，并应用业务规则（控制器逻辑）
        for (TimelineClip clip : track.getClips()) {
            TimelineClip newClip = new TimelineClip(clip); // 使用拷贝构造函数（标准 Java 模式）
            newClip.setLabel(clip.getLabel() + " (副本)"); // 控制器负责命名
            
            // 使用 offsetTime 简化偏移逻辑（自动处理关键帧）
            newClip.offsetTime(0.5f);

            newTrack.addClip(newClip);
        }

        model.addTrack(newTrack);
        markDataChanged();
        LOGGER.info("[Timeline] 已复制轨道: {} -> {}", track.getName(), newName);
    }

    /**
     * 上移轨道（交换索引）
     */
    private void moveTrackUp(TimelineTrack track) {
        if (track == null) return;
        
        List<TimelineTrack> tracks = model.getTracks();
        int index = tracks.indexOf(track);
        
        if (index > 0) {
            // 交换位置
            Collections.swap(tracks, index, index - 1);
            markDataChanged();
            LOGGER.info("[Timeline] 上移轨道: {} (索引 {} -> {})", track.getName(), index, index - 1);
        }
    }
    
    /**
     * 下移轨道（交换索引）
     */
    private void moveTrackDown(TimelineTrack track) {
        if (track == null) return;
        
        List<TimelineTrack> tracks = model.getTracks();
        int index = tracks.indexOf(track);
        
        if (index >= 0 && index < tracks.size() - 1) {
            // 交换位置
            Collections.swap(tracks, index, index + 1);
            markDataChanged();
            LOGGER.info("[Timeline] 下移轨道: {} (索引 {} -> {})", track.getName(), index, index + 1);
        }
    }
    
    /**
     * 打开片段上下文菜单（使用菜单工厂）
     */
    private void openClipContextMenu(TimelineClip clip) {
        activeContextMenu = menuFactory.createForClip(clip);
    }
    
    /**
     * 剪切片段
     */
    private void cutClip(TimelineClip clip, TimelineTrack track) {
        if (clip == null || track == null) return;
        
        // 复制到剪贴板（使用封装的状态对象 + 拷贝构造函数）
        clipboard.set(new TimelineClip(clip), true);
        
        // 删除原片段（使用命令支持撤销）
        @SuppressWarnings("unchecked")
        List<Object> clipListAsObjects = (List<Object>)(List<?>) track.getClips();
        DeleteClipCommand cmd = new DeleteClipCommand(clip, clipListAsObjects);
        commandHistory.executeCommand(cmd);
        
        model.setSelectedClip(null);
        markDataChanged();
        LOGGER.info("[Timeline] 剪切片段: {}", clip.getLabel());
    }
    
    /**
     * 复制片段
     */
    private void copyClip(TimelineClip clip) {
        if (clip == null) return;
        
        clipboard.set(new TimelineClip(clip), false);
        LOGGER.info("[Timeline] 复制片段: {}", clip.getLabel());
    }
    
    /**
     * 粘贴片段到指定轨道
     */
    private void pasteClipToTrack(TimelineTrack track) {
        if (!clipboard.hasClip() || track == null) return;
        
        // 创建副本（使用拷贝构造函数）
        TimelineClip newClip = new TimelineClip(clipboard.clip);
        
        // 调整时间以避免重叠
        float pasteTime = model.getCurrentTime();
        newClip.setStartTime(pasteTime);
        newClip.setEndTime(pasteTime + newClip.getLength());
        
        // 添加到轨道
        track.addClip(newClip);
        model.setSelectedClip(newClip);
        
        markDataChanged();
        LOGGER.info("[Timeline] 粘贴片段: {} 到轨道 {}", newClip.getLabel(), track.getName());
        
        // 如果是剪切操作，清空剪贴板
        if (clipboard.isCut) {
            clipboard.clear();
        }
    }
    
    /**
     * 打开关键帧上下文菜单（使用菜单工厂）
     */
    private void openKeyframeContextMenu(TimelineClip clip, float keyframeTime) {
        activeContextMenu = menuFactory.createForKeyframe(clip, keyframeTime);
    }
    
    /**
     * 剪切选中的片段（快捷键）
     */
    private void cutSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        TimelineTrack track = model.findTrackForClip(selectedClip);
        cutClip(selectedClip, track);
    }
    
    /**
     * 复制选中的片段（快捷键）
     */
    private void copySelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        copyClip(selectedClip);
    }
    
    /**
     * 粘贴片段（快捷键）
     * 粘贴到当前选中片段所在的轨道，如果没有选中则粘贴到第一个轨道
     */
    private void pasteClip() {
        if (!clipboard.hasClip()) return;
        
        TimelineTrack targetTrack = null;
        
        // 优先粘贴到选中片段所在的轨道
        if (model.getSelectedClip() != null) {
            targetTrack = model.findTrackForClip(model.getSelectedClip());
        }
        
        // 如果没有选中片段，粘贴到第一个轨道
        if (targetTrack == null && !model.getTracks().isEmpty()) {
            targetTrack = model.getTracks().getFirst();
        }
        
        if (targetTrack != null) {
            pasteClipToTrack(targetTrack);
        }
    }
    
    /**
     * 适应视图：自动缩放以查看整个时间线
     */
    private void fitTimelineToView() {
        float duration = model.getDuration();
        if (duration <= 0) {
            duration = 10.0f; // 默认时长
        }
        
        // 获取可用的内容宽度（假设为窗口宽度 - 左侧标签宽度）
        float availableWidth = ImGui.getContentRegionAvailX() - TimelineRenderer.getLeftLabelWidth();
        
        // 计算需要的缩放级别（留20%的边距）
        float targetPps = (availableWidth * 0.8f) / duration;
        
        // 限制在合理范围内
        targetPps = clamp(targetPps, model.getMinPps(), model.getMaxPps());
        
        model.setZoom(targetPps);
        model.setScrollX(0); // 重置到开始位置
        
        LOGGER.info("[Timeline] 适应视图：时长={:.1f}s, 缩放={:.1f}px/s", duration, targetPps);
    }
    
    /**
     * 删除选中的片段
     */
    private void deleteSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null) {
            @SuppressWarnings("unchecked")
            List<Object> clipListAsObjects = (List<Object>)(List<?>) track.getClips();
            DeleteClipCommand cmd = new DeleteClipCommand(selectedClip, clipListAsObjects);
            commandHistory.executeCommand(cmd);
            markDataChanged(); // 标记数据已改变
            model.setSelectedClip(null);
            LOGGER.info("[Timeline] 删除片段（命令）");
        }
    }
    
    /**
     * 复制选中的片段（控制器负责命名和偏移逻辑）
     */
    private void duplicateSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        TimelineClip duplicate = new TimelineClip(selectedClip); // 使用拷贝构造函数（标准 Java 模式）
        duplicate.setLabel(selectedClip.getLabel() + " (副本)"); // 控制器负责命名
        
        // 使用 offsetTime 简化偏移逻辑（自动处理关键帧）
        float offset = selectedClip.getLength() + 0.5f;
        duplicate.offsetTime(offset);
        
        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null) {
            track.addClip(duplicate);
            model.setSelectedClip(duplicate);
            markDataChanged();
            LOGGER.info("[Timeline] 复制片段: {}", duplicate.getLabel());
        }
    }
    
    // ========== 回调接口实现（TimelineInteractionHandler.InteractionCallbacks）==========
    
    @Override
    public void onClipRightClick(TimelineClip clip) {
        openClipContextMenu(clip);
    }

    @Override
    public void onTrackRightClick(TimelineTrack track) {
        openTrackContextMenu(track);
    }

    @Override
    public void onKeyframeRightClick(TimelineClip clip, float keyframeTime) {
        openKeyframeContextMenu(clip, keyframeTime);
    }

    @Override
    public void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
        handleKeyframeDoubleClick(clip, mouseX, contentX);
    }

    @Override
    public void onClipInteractionFinished(TimelineClip clip, float originalStartTime, float originalEndTime) {
        // 使用 ClipAdapter 简化命令创建，避免冗长的匿名内部类
        MoveClipCommand cmd = new MoveClipCommand(
            new ClipAdapter(clip),
            originalStartTime,
            originalEndTime
        );
        commandHistory.executeCommand(cmd);
        model.markDurationDirty(); // 片段时间修改可能影响总时长
        markDataChanged();
        LOGGER.info("[Timeline] 完成片段移动操作（命令）");
    }

    @Override
    public void onKeyframeDragFinished(TimelineClip clip, float originalTime, float newTime) {
        // 控制器负责创建命令和管理数据修改
        // 使用 Remove + Add 命令实现关键帧移动
        commandHistory.executeCommand(new RemoveKeyframeCommand(clip, originalTime));
        commandHistory.executeCommand(new AddKeyframeCommand(clip, newTime));
        markDataChanged();
        LOGGER.info("[Timeline] 移动关键帧从 {:.2f}s 到 {:.2f}s（命令）", originalTime, newTime);
    }

    @Override
    public void onDataChanged() {
        markDataChanged();
    }

    @Override
    public void setHoveredTrack(TimelineTrack track) {
        hoveredTrack = track;
    }

    @Override
    public void setDropTargetTrackIndex(int index) {
        dropTargetTrackIndex = index;
    }

    @Override
    public boolean isMouseHoveringRect(float x1, float y1, float x2, float y2) {
        float mx = ImGui.getMousePosX();
        float my = ImGui.getMousePosY();
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }

    @Override
    public void onToggleTrackLock(TimelineTrack track) {
        if (track == null) return;
        track.setLocked(!track.isLocked());
        markDataChanged();
    }

    @Override
    public void onToggleTrackVisible(TimelineTrack track) {
        if (track == null) return;
        track.setVisible(!track.isVisible());
        markDataChanged();
    }

    @Override
    public void onContainerDropped(String containerId) {
        try {
            // 1. 根据ID获取容器
            UUID containerUuid = UUID.fromString(containerId);
            Container container = ContainerManager.getInstance().getContainer(containerUuid);
            
            if (container == null) {
                LOGGER.warn("[Timeline] 容器不存在: {}", containerId);
                return;
            }
            
            // 2. 创建容器轨道（使用UI层的TimelineTrack）
            TimelineTrack track = new TimelineTrack(container.getName());
            track.setName("Track_" + container.getName());
            
            // 3. 添加到时间线模型
            model.addTrack(track);
            
            // 4. 标记数据已改变
            markDataChanged();
            
            LOGGER.info("[Timeline] 成功创建容器轨道: {} -> {}", 
                container.getName(), track.getName());
                
        } catch (Exception e) {
            LOGGER.error("[Timeline] 处理容器拖放失败: {}", containerId, e);
        }
    }

    @Override
    public void onContainerDroppedToTrack(String containerId, int trackIndex) {
        try {
            // 1. 根据ID获取容器
            UUID containerUuid = UUID.fromString(containerId);
            Container container = ContainerManager.getInstance().getContainer(containerUuid);
            
            if (container == null) {
                LOGGER.warn("[Timeline] 容器不存在: {}", containerId);
                return;
            }
            
            // 2. 获取目标轨道
            TimelineTrack targetTrack = model.getTrackAt(trackIndex);
            if (targetTrack == null) {
                LOGGER.warn("[Timeline] 目标轨道不存在，索引: {}", trackIndex);
                return;
            }
            
            // 3. 计算鼠标位置对应的时间
            float mouseX = ImGui.getMousePosX();
            float contentX = ImGui.getCursorScreenPosX() + TimelineRenderer.getLeftLabelWidth();
            float dropTime = model.screenToTime(mouseX, contentX);
            
            // 4. 应用吸附
            if (model.isSnapEnabled()) {
                dropTime = model.snapToInterval(dropTime);
            }
            
            // 5. 确保时间在有效范围内
            dropTime = Math.max(0f, Math.min(dropTime, model.getDuration() - 1f));
            
            // 6. 创建容器片段
            TimelineClip clip = new TimelineClip(
                container.getName(), 
                dropTime, 
                dropTime + 1f, // 默认1秒长度
                0xFF4A90E2 // 蓝色
            );
            
            // 7. 添加到目标轨道
            targetTrack.addClip(clip);
            
            // 8. 标记数据已改变
            markDataChanged();
            
            LOGGER.info("[Timeline] 成功将容器 {} 添加到轨道 {} 的时间 {}s", 
                container.getName(), targetTrack.getName(), dropTime);
                
        } catch (Exception e) {
            LOGGER.error("[Timeline] 处理容器拖放到轨道失败: {} -> 轨道{}", containerId, trackIndex, e);
        }
    }

    @Override
    public void onContainerDropRejected(String containerId, int trackIndex) {
        try {
            // 1. 根据ID获取容器
            UUID containerUuid = UUID.fromString(containerId);
            Container container = ContainerManager.getInstance().getContainer(containerUuid);
            
            if (container == null) {
                LOGGER.warn("[Timeline] 容器不存在: {}", containerId);
                return;
            }
            
            // 2. 获取目标轨道
            TimelineTrack targetTrack = model.getTrackAt(trackIndex);
            if (targetTrack == null) {
                LOGGER.warn("[Timeline] 目标轨道不存在，索引: {}", trackIndex);
                return;
            }
            
            // 3. 显示错误提示
            String containerName = container.getName();
            String trackTypeName = targetTrack.getType().getDisplayName();
            
            LOGGER.warn("[Timeline] 容器拖放被拒绝: {} 不能拖放到 {} 轨道上", 
                containerName, trackTypeName);
            
            // 4. 可以在这里添加用户界面提示，比如显示一个临时的错误消息
            // 由于ImGui的限制，这里只记录日志，实际应用中可以通过其他方式显示提示
            
        } catch (Exception e) {
            LOGGER.error("[Timeline] 处理容器拖放拒绝失败: {} -> 轨道{}", containerId, trackIndex, e);
        }
    }
    
    // ========== 回调接口实现（TimelineContextMenuFactory.MenuCallbacks）==========

    @Override
    public void onCutClip(TimelineClip clip, TimelineTrack track) {
        cutClip(clip, track);
    }

    @Override
    public void onCopyClip(TimelineClip clip) {
        copyClip(clip);
    }

    @Override
    public void onPasteClip(TimelineTrack track) {
        pasteClipToTrack(track);
    }

    @Override
    public void onDeleteClip() {
        deleteSelectedClip();
    }

    @Override
    public void onDuplicateClip() {
        duplicateSelectedClip();
    }

    @Override
    public void onSetClipLength(TimelineClip clip, float length) {
        clip.setLength(length);
        markDataChanged();
    }

    @Override
    public void onDeleteTrack(TimelineTrack track) {
        deleteTrack(track);
    }

    @Override
    public void onStartEditTrack(TimelineTrack track) {
        startEditingTrack(track);
    }

    @Override
    public void onDuplicateTrack(TimelineTrack track) {
        duplicateTrack(track);
    }

    @Override
    public void onMoveTrackUp(TimelineTrack track) {
        moveTrackUp(track);
    }

    @Override
    public void onMoveTrackDown(TimelineTrack track) {
        moveTrackDown(track);
    }

    @Override
    public void onRemoveKeyframe(TimelineClip clip, float keyframeTime) {
        RemoveKeyframeCommand cmd = new RemoveKeyframeCommand(clip, keyframeTime);
        commandHistory.executeCommand(cmd);
        markDataChanged();
    }
    
    // ========== 回调接口实现（TimelineControlBar.Callbacks）==========
    
    @Override
    public void onAddTrackClicked() {
        openAddTrackMenu();
    }

    @Override
    public void onDeleteSelectedClicked() {
        deleteSelectedClip();
    }

    @Override
    public void onFitToViewClicked() {
        fitTimelineToView();
    }
    
    // ========== 工具方法 ==========
    
    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }
    
    // ========== 私有内部类：状态封装 ==========
    
    /**
     * 封装剪贴板状态
     */
    private static class ClipboardState {
        TimelineClip clip = null;
        boolean isCut = false;

        void set(TimelineClip clip, boolean isCut) {
            this.clip = clip;
            this.isCut = isCut;
        }

        void clear() {
            this.clip = null;
            this.isCut = false;
        }
        
        boolean hasClip() {
            return clip != null;
        }
    }

    /**
     * 封装轨道重命名状态
     */
    private static class RenamingState {
        TimelineTrack track = null;
        final ImString buffer = new ImString(256);

        void startEditing(TimelineTrack track) {
            this.track = track;
            this.buffer.set("");  // 先清空
            this.buffer.set(track.getName());  // 再设置
        }

        void clear() {
            this.track = null;
            this.buffer.set("");
        }
        
        boolean isEditing() {
            return track != null;
        }
    }
    
    @Override
    public int getPriority() {
        return 200;
    }
}