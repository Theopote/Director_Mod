package com.chronoblocks.ui.panels;

import com.chronoblocks.ui.timeline.commands.*;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.ui.timeline.dialog.*;
import com.chronoblocks.ui.timeline.interaction.*;
import com.chronoblocks.ui.timeline.menu.TimelineContextMenu;
import com.chronoblocks.ui.timeline.menu.TimelineContextMenuFactory;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.timeline.model.TimelineModel;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.model.TimelineViewportState;
import com.chronoblocks.ui.timeline.render.TimelineRenderer;
import com.chronoblocks.ui.timeline.state.*;
import com.chronoblocks.ui.timeline.ui.TimelineControlBar;
import com.chronoblocks.ui.timeline.shortcuts.TimelineShortcutManager;
import com.chronoblocks.ui.theme.UITheme;
import imgui.ImGui;
import imgui.ImDrawList;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiInputTextFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * ChronoBlocks 专业级时间轴面板（架构重构版 v8.0）
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
 *   <li>✅ Drag & Drop Handler: 专门的拖放处理器</li>
 *   <li>✅ Navigation Handler: 专门的导航/缩放处理器</li>
 *   <li>✅ Track Controller: 专门的轨道管理控制器</li>
 * </ul>
 * </p>
 * 
 * <p>
 * 拆分原则：
 * <ul>
 *   <li>{@link TimelineDragAndDropHandler} - 处理拖放逻辑和坐标计算</li>
 *   <li>{@link TimelineNavigationHandler} - 处理视图缩放、滚动和平移</li>
 *   <li>{@link TimelineTrackController} - 处理轨道的 CRUD 操作和菜单渲染</li>
 * </ul>
 * </p>
 * 
 * @author ChronoBlocks Team
 * @version 8.0 - 职责拆分优化
 * @since 1.0
 */
public class TimelinePanel extends AbstractPanel 
        implements TimelineInteractionHandler.InteractionCallbacks,
                   TimelineControlBar.Callbacks {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePanel.class);
    
    // ========== UI 布局常量（v9.0 优化：提取魔法数字）==========
    /** 轨道名称编辑框的 X 偏移量（相对于窗口左侧） */
    private static final float TRACK_EDIT_OFFSET_X = 28f;
    /** 轨道名称编辑框的 Y 偏移量（相对于轨道顶部） */
    private static final float TRACK_EDIT_OFFSET_Y = 6f;
    /** 轨道名称编辑框宽度需要减去的值（为右侧控件留出空间） */
    private static final float TRACK_EDIT_WIDTH_SUBTRACT = 35f;
    /** 轨道名称编辑框的最小宽度（防止过小无法输入） */
    private static final float TRACK_EDIT_MIN_WIDTH = 20f;
    /** 刻度尺交互的最小宽度（小于此值不处理交互，避免布局异常） */
    private static final float RULER_INTERACTION_MIN_WIDTH = 10f;
    /** 虚拟滚动条底部填充（像素），用于改善滚动体验 */
    private static final float DUMMY_SCROLL_PADDING = 50.0f;
    
    // ========== 核心组件（架构优化）==========
    private final TimelineModel model;
    private final TimelineViewportState viewportState;
    private final TimelineRenderer renderer;
    private final TimelineCommandHistory commandHistory;
    private final TimelineInteractionManager interactionManager;
    private final TimelineInteractionHandler interactionHandler;
    private final TimelineControlBar controlBar;
    private final TimelineShortcutManager shortcutManager;
    
    // ========== 新的拆分组件 (Controllers/Handlers) ==========
    private final TimelineDragAndDropHandler dragDropHandler;
    private final TimelineNavigationHandler navigationHandler;
    private final TimelineTrackController trackController;
    
    // ========== UI 状态（封装优化）==========
    private final ClipboardManager clipboard = ClipboardManager.getInstance();
    private final AutoSaveManager autoSave;
    private int dropTargetTrackIndex = -1;
    
    // ========== 性能优化：基于时间的计时器 ==========
    // 使用时间戳代替帧计数，避免受 FPS 波动影响
    private long lastAutoSaveTime = 0;
    
    // ========== 常量定义 ==========
    private static final int MAX_HISTORY_SIZE = 100;
    private static final float KEYFRAME_TOLERANCE = 0.1f;
    @SuppressWarnings("unused")
    private static final float CLIP_DUPLICATE_OFFSET = 0.5f; // 预留：片段复制时的偏移量
    @SuppressWarnings("unused")
    private static final float FIXED_DELTA_TIME = 1.0f / 60.0f; // 预留：60 FPS 固定时间步长（可在updatePlayback中使用）
    private static final long AUTO_SAVE_INTERVAL_MS = 5000; // 5秒（基于时间，不受FPS影响）

    public TimelinePanel() {
        super();
        
        // 1. 初始化核心组件
        this.viewportState = new TimelineViewportState();
        this.model = new TimelineModel(viewportState);
        this.renderer = new TimelineRenderer(model, viewportState);
        this.commandHistory = new TimelineCommandHistory();
        this.interactionManager = new TimelineInteractionManager();
        this.interactionHandler = new TimelineInteractionHandler(model, interactionManager, commandHistory, renderer);
        
        // 配置历史管理
        commandHistory.setMaxHistorySize(MAX_HISTORY_SIZE);
        // 设置模型引用，使命令历史自动触发模型更新
        commandHistory.setModel(model);
        
        // 2. 初始化拆分出来的处理器
        this.dragDropHandler = new TimelineDragAndDropHandler(model, this::markDataChanged);
        this.navigationHandler = new TimelineNavigationHandler(model, viewportState);
        this.trackController = new TimelineTrackController(model, commandHistory, this::markDataChanged);
        
        // 3. 直接传递 this 作为回调对象（简洁的回调注入）
        this.interactionHandler.setCallbacks(this);
        TimelineContextMenuFactory menuFactory = TimelineContextMenuFactory.forPanel(this, model, commandHistory);
        this.interactionHandler.setMenuFactory(menuFactory);
        this.controlBar = new TimelineControlBar(model, viewportState, commandHistory, this, navigationHandler);
        
        // 4. 初始化自动保存管理器
        this.autoSave = new AutoSaveManager(model);
        
        // 初始化自动保存时间戳
        this.lastAutoSaveTime = System.currentTimeMillis();
        
        // 初始化对话框
        ClipRenameDialog.initialize(model, commandHistory);
        ClipLengthDialog.initialize(commandHistory, model);
        ClipBindingDialog.initialize(model, commandHistory, this::markDataChanged);
        TrackRenameDialog.initialize(model, commandHistory, this::markDataChanged);
        
        // 初始化快捷键管理器
        this.shortcutManager = new TimelineShortcutManager(
            model, commandHistory, interactionManager,
            new TimelineShortcutManager.Callbacks() {
                @Override
                public void cutSelectedClip() {
                    TimelinePanel.this.cutSelectedClip();
                }
                
                @Override
                public void copySelectedClip() {
                    TimelinePanel.this.copySelectedClip();
                }
                
                @Override
                public void pasteClip() {
                    TimelinePanel.this.pasteClip();
                }
                
                @Override
                public void deleteSelectedClip() {
                    TimelinePanel.this.deleteSelectedClip();
                }
            }
        );
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
        shortcutManager.handle();
        
        // 3. 渲染控制栏（委托给专门的组件）
        renderControlBar();
        
        // 控制栏与分割线之间的间距（等于按钮与按钮之间的间距，4像素）
        // 注意：控制栏设置了 ItemSpacing=4px，控制栏结束后会自动添加 4px 间距
        // 所以控制栏到分割线之间已经有 4px 间距了，我们不需要再手动添加
        // 但需要临时清除 ItemSpacing 以避免 separator 也自动添加间距
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        ImGui.separator();
        // 分割线与时间刻度条之间的间距（等于按钮与按钮之间的间距，4像素）
        // 使用固定的像素间距，确保 Header 和 Separator 不会太挤
        ImGui.dummy(0f, UITheme.Spacing.ELEMENT_SPACING); // 4px: 分割线到时间刻度的间距
        ImGui.popStyleVar(); // 恢复 ItemSpacing
        
        // 4. 渲染时间线画布
        renderTimelineCanvas();
        
        // 5. 渲染新建轨道菜单（委托给 TrackController）
        trackController.renderAddTrackMenu();
        
        // 6. 上下文菜单现在在子窗口内渲染（见 renderTimelineCanvas 方法）
        // 这样可以确保 popup 在正确的窗口上下文中显示
        
        // 7. 自动保存（性能优化：基于时间的检查，不受FPS波动影响）
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoSaveTime >= AUTO_SAVE_INTERVAL_MS) {
            autoSave.tick();
            lastAutoSaveTime = currentTime;
        }
    }
    
    /**
     * 更新播放状态（每帧调用）
     * 
     * <p>性能优化：
     * <ul>
     *   <li>早期返回：不播放时直接返回</li>
     *   <li>固定时间步长：使用固定时间步长避免帧率波动导致时间不准</li>
     * </ul>
     * 
     * <p>自动跟随：
     * <ul>
     *   <li>播放时自动调整视图，确保播放头始终在可见区域内</li>
     *   <li>参考 Premiere Pro 等专业视频编辑软件的行为</li>
     * </ul>
     */
    private void updatePlayback() {
        // 早期返回：不播放时直接返回，避免不必要的计算
        if (!model.isPlaying()) {
            return;
        }
        
        // 使用固定时间步长，避免帧率波动导致时间不准
        // 实际应用中可以根据需要选择使用固定时间步长或实时时间
        float deltaTime = ImGui.getIO().getDeltaTime();
        // 可选：使用固定时间步长
        // float deltaTime = FIXED_DELTA_TIME;
        
        // 更新当前时间
        float newTime = model.getCurrentTime() + deltaTime;
        float duration = model.getDuration();
        
        // 处理超出范围
        if (newTime > duration) {
            newTime = duration; // 停在终点
            // ★ 优化：只在状态真正改变时记录日志，避免频繁触发时刷屏
            // 检查是否从播放状态变为停止状态
            boolean wasPlaying = model.isPlaying();
            model.setPlaying(false);
            if (wasPlaying && LOGGER.isTraceEnabled()) {
                // 使用 trace 级别，因为这是高频事件，debug 级别可能过于频繁
                LOGGER.trace("[TimelinePanel] 播放结束，停在终点 (时间: {:.2f}s)", newTime);
            }
        }
        
        model.setCurrentTime(newTime);
        
        // ★ 自动跟随：确保播放头始终在可见区域内
        // 在播放时，如果播放头超出可见范围，自动调整 scrollX 使播放头保持在可见区域内
        // 这确保播放时游标始终可见，用户可以随时看到当前播放位置
        ensurePlayheadVisible();
    }
    
    /**
     * 缓存的可视区域参数（用于自动跟随功能）
     * 这些值在 renderTimelineCanvas() 中更新，在 updatePlayback() 中使用
     */
    private float cachedContentX = 0f;
    private float cachedContentW = 0f;
    private static final float PLAYHEAD_MARGIN = 50f; // 播放头距离可见区域边缘的最小距离（像素）
    
    /**
     * 确保播放头始终在可见区域内（自动跟随功能）
     * 
     * <p>类似 Premiere Pro 等专业视频编辑软件，播放时游标永远不会跑出屏幕外。
     * 如果播放头超出可见范围，自动调整 scrollX 使播放头保持在可见区域内。
     * 
     * <p>v9.0 优化：防止与用户手动导航冲突
     * <ul>
     *   <li>如果用户正在拖拽滚动条、平移视图或进行其他交互，暂停自动跟随</li>
     *   <li>避免用户在查看其他区域时被强制拉回播放头位置，导致画面抖动或操作失效</li>
     * </ul>
     */
    private void ensurePlayheadVisible() {
        // 只有在有效区域时才执行自动跟随
        if (cachedContentW <= 0) {
            return;
        }
        
        // ★ v9.0 优化：防止与用户手动导航冲突
        // 如果用户按下了鼠标（可能在拖拽滚动条、平移视图或进行其他交互），暂时禁用自动跟随
        // 这样用户可以在播放过程中自由查看其他区域，不会被强制拉回播放头位置
        if (ImGui.isMouseDown(ImGuiMouseButton.Left) || 
            ImGui.isMouseDown(ImGuiMouseButton.Middle) ||
            ImGui.isMouseDown(ImGuiMouseButton.Right)) {
            return;
        }
        
        // 检查是否有任何活跃的 ImGui 控件（例如滑块、输入框等）
        // 如果用户正在与 UI 交互，暂停自动跟随
        if (ImGui.isAnyItemActive() || ImGui.isAnyItemHovered()) {
            return;
        }
        // 这样用户可以自由查看时间线的其他区域，而不会被强制拉回播放头位置
        if (ImGui.isMouseDown(ImGuiMouseButton.Left) || 
            ImGui.isMouseDown(ImGuiMouseButton.Middle) ||
            ImGui.isMouseDown(ImGuiMouseButton.Right)) {
            return;
        }
        
        // 委托给 NavigationHandler 处理
        navigationHandler.ensurePlayheadVisible(cachedContentX, cachedContentW, PLAYHEAD_MARGIN);
    }

    /**
     * 标记数据已改变（委托给 AutoSaveManager）
     */
    private void markDataChanged() {
        autoSave.markDirty();
    }
    
    /**
     * 渲染控制栏（委托给专门的组件）
     */
    private void renderControlBar() {
        controlBar.render();
    }
    
    /**
     * 安全地推入裁剪区域（防止边缘情况崩溃）
     * 
     * <p>性能优化：如果用户将窗口调整得非常小（例如高度小于 Header 高度），
     * trackViewportHeight 可能接近 0。虽然做了 Math.max(10f, ...) 的保护，
     * 但 pushClipRect 如果接收到 min > max 的坐标（例如在极端的布局计算错误
     * 或窗口最小化瞬间），会导致 ImGui 内部断言失败或渲染异常。
     * 
     * @param drawList ImGui 绘制列表
     * @param x1 裁剪区域左上角 X
     * @param y1 裁剪区域左上角 Y
     * @param x2 裁剪区域右下角 X
     * @param y2 裁剪区域右下角 Y
     */
    private void pushClipRectSafe(ImDrawList drawList, float x1, float y1, float x2, float y2) {
        if (x2 <= x1 || y2 <= y1) {
            // 如果区域无效，推入一个空的裁剪区（裁剪掉所有内容），防止 ImGui 报错
            // 或者推入一个 1x1 的安全区域
            drawList.pushClipRect(x1, y1, x1 + 1f, y1 + 1f);
        } else {
            drawList.pushClipRect(x1, y1, x2, y2);
        }
    }
    
    /**
     * 新版时间线画布（v8.0）：
     * - 上方：时间标尺 + 轨道标题区域（固定）
     * - 下方：轨道区域子窗口（垂直滚动）
     * - Ctrl + 滚轮：缩放（围绕鼠标）
     * - Shift + 滚轮：水平滚动（scrollX）
     */
    private void renderTimelineCanvas() {
        ImDrawList drawList = ImGui.getWindowDrawList();

        float winX = ImGui.getCursorScreenPosX();
        float winY = ImGui.getCursorScreenPosY();
        float winW = ImGui.getContentRegionAvailX();
        float availH = ImGui.getContentRegionAvailY();

        // ============================================================
        // ★ v9.0 修复 1：先处理布局调整逻辑
        // ============================================================
        // 必须在计算 contentX 之前调用，这样如果用户正在拖动，
        // leftLabelWidth 会更新，后续计算的 contentX 才是当前帧正确的数值。
        renderer.updateLeftSectionWidth(winX, winY, availH);

        float headerHeight = TimelineRenderer.getHeaderHeight();
        // 确保高度不为负，最小高度为 10px 以避免渲染问题
        float trackViewportHeight = Math.max(10f, availH - headerHeight);

        // ============================================================
        // ★ v9.0 修复 2：基于更新后的宽度计算坐标
        // ============================================================
        // 此时 TimelineRenderer.getLeftLabelWidth() 返回的是最新值
        float contentX = winX + TimelineRenderer.getLeftLabelWidth();
        float contentW = winW - TimelineRenderer.getLeftLabelWidth();
        float trackRegionTop = winY + headerHeight;

        // ★ 缓存可视区域参数（用于自动跟随功能）
        // 确保使用的是最新值
        cachedContentX = contentX;
        cachedContentW = contentW;

        // 1) 告诉 renderer 当前视口参数（用于坐标转换）
        // 这一步非常重要，它会将 contentX 同步给 Renderer 内部的 Viewport 对象
        renderer.beginFrame(winX, winY, winW, availH);
        
        // ★ 优化 A：显式裁剪 Header 区域，防止标尺绘制到外面
        pushClipRectSafe(drawList, winX, winY, winX + winW, winY + headerHeight);
        renderer.renderTimelineHeader(drawList, winX, winY, winW);
        drawList.popClipRect();

        // 2.5) 处理刻度尺区域交互（点击跳转播放头，拖动播放头）
        handleRulerInteractions(winX, winY, headerHeight, contentX, contentW);

        // 3) 处理滚轮：缩放 / 水平滚动（委托给 NavigationHandler）
        navigationHandler.handleMouseWheel(winX, winY, winW, headerHeight, trackViewportHeight);

        if (trackViewportHeight <= 0f) {
            // 空高度就不渲染轨道了
            ImGui.setCursorScreenPos(winX, winY + headerHeight);
            return;
        }

        // 4) 计算轨道总高度（用于滚动条）
        int trackCount = model.getTrackCount();
        float trackHeight = TimelineRenderer.getTrackHeight();
        float trackGap = TimelineRenderer.getTrackGap();
        float rowHeight = trackHeight + trackGap;
        float totalTracksHeight = trackCount > 0 ? 
            (trackCount * trackHeight + (trackCount - 1) * trackGap) : 0f;
        
        // 轨道区域子窗口：支持垂直滚动
        // 如果轨道总高度超过视口高度，ImGui 会自动显示垂直滚动条
        ImGui.setCursorScreenPos(winX, trackRegionTop);
        
        // ★ 优化 B：移除 HorizontalScrollbar 标志
        // 我们只需要垂直滚动条，水平方向由 Virtual ScrollX 控制
        // 使用 None 标志，ImGui 会自动处理垂直滚动条（当内容超出时）
        ImGui.beginChild("##TimelineTrackRegion",
                winW, trackViewportHeight,
                false,
                ImGuiWindowFlags.None); // 移除 HorizontalScrollbar，ImGui会自动处理垂直滚动
        
        // 计算虚拟高度，撑开垂直滚动条
        // ★ v9.0 优化：使用常量替换魔法数字，改善滚动体验
        float totalTracksHeightWithPadding = totalTracksHeight + DUMMY_SCROLL_PADDING;
        
        if (totalTracksHeightWithPadding > trackViewportHeight) {
            // 在内容结束位置创建虚拟控件，撑开滚动条
            ImGui.setCursorPos(0, totalTracksHeightWithPadding);
            ImGui.dummy(1.0f, 1.0f); // 撑开滚动条
            // 归位到开始位置
            ImGui.setCursorPos(0, 0);
        }

        float scrollY = ImGui.getScrollY();
        viewportState.setScrollY(scrollY);

        // ★ 优化 A：显式裁剪轨道区域
        // 裁剪整个轨道区域（防止内容在滚动时溢出子窗口边界）
        pushClipRectSafe(drawList, winX, trackRegionTop, winX + winW, trackRegionTop + trackViewportHeight);
        
        // 在主窗口坐标系里画（所以传 winX / trackRegionTop），scrollY 从 viewportState 同步
        // 注意：renderTrackArea 会渲染整个区域（包括左侧标签和右侧内容）
        // 但片段渲染在 renderClip 中已经被限制在内容区域内（通过 clampedX1/clampedX2）
        renderer.renderTrackArea(drawList, winX, trackRegionTop, winW, trackViewportHeight);
        
        // 6) Drop 高亮（在裁剪区域内）
        if (dropTargetTrackIndex >= 0) {
            // 使用已计算的 rowHeight
            float y = trackRegionTop - scrollY + dropTargetTrackIndex * rowHeight;
            renderer.renderDropHighlight(drawList, contentX, y, contentW);
        }
        
        // 恢复轨道区域裁剪
        drawList.popClipRect();

        // 5) 渲染原位编辑和弹窗（这些是标准 ImGui 控件，自带裁剪，不需要 pushClipRect）
        // 注意：Clip 相关的 Popup 应该在裁剪区之外渲染，Dialog 会新建 Layer，所以没问题
        // 但 InlineEdit (InputText) 必须在 Child 内部渲染以跟随滚动
        renderInlineEdit(winX, trackRegionTop, scrollY);
        
        // 这些 Dialog 渲染逻辑是安全的，因为它们内部使用 beginPopup / beginTooltip
        TrackRenameDialog.renderPopupIfNeeded();
        ClipRenameDialog.renderIfNeeded();
        ClipBindingDialog.renderIfNeeded();
        ClipLengthDialog.renderIfNeeded();

        // 7) InvisibleButton 用于交互
        // ★ 关键修正：InvisibleButton 在 Child 窗口中的定位
        // 
        // 问题分析：
        // - 在 Child 窗口中，(0,0) 是内容区域的左上角（相对于 Child 窗口）
        // - 当 scrollY > 0 时，(0,0) 会滚动到视口上方
        // - 如果使用 setCursorScreenPos，坐标会与 Child 窗口的坐标系不匹配
        //
        // 解决方案：
        // - 使用 setCursorPos(0, 0) 在 Child 窗口的内容坐标系中定位
        // - 创建一个覆盖整个内容区域（包括滚动部分）的按钮
        // - ImGui 的 invisibleButton 会自动处理滚动，只要在正确的 Child 窗口上下文中创建即可
        //
        ImGui.setCursorPos(0, 0);
        // 创建一个覆盖整个内容区域（包括所有可能滚动的轨道）的按钮
        // 宽度使用 winW，高度使用总轨道高度（加一些边距），这样无论滚动到哪里都能捕获点击
        float buttonHeight = Math.max(trackViewportHeight, totalTracksHeight + 100f); // 添加边距确保覆盖
        ImGui.invisibleButton("##TimelineCanvas", winW, buttonHeight);
        
        // 检查 invisibleButton 是否被右键点击（用于上下文菜单）
        // 注意：invisibleButton 会拦截右键事件，所以我们需要在这里检查
        // ★ 优化 D：确保 InvisibleButton 的 ID 唯一性，并利用其状态
        boolean rightClickedOnCanvas = ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Right);

        // 7.5) 设置容器拖放目标（委托给 DragDropHandler）
        // 使用主窗口的 contentX（在子窗口之前保存的值）
        this.dropTargetTrackIndex = dragDropHandler.handleDragDropTarget(trackRegionTop, scrollY, contentX);

        // 8) 将几何信息传给交互处理器（命中检测 / 拖动 / 上下文菜单）
        interactionHandler.handleCanvasInteractions(contentX, trackRegionTop, contentW, trackViewportHeight, scrollY, rightClickedOnCanvas);
        
        // 8.5) 在子窗口内渲染活动的上下文菜单（必须在 endChild 之前）
        // 注意：ImGui popup 必须在调用 openPopup 的同一窗口上下文中渲染
        TimelineContextMenu menu = interactionHandler.getActiveContextMenu();
        if (menu != null) {
            menu.render();
        }

        ImGui.endChild();

        // ============================================================
        // ★ 修复：在 EndChild 之后渲染播放头（v8.1 关键修复）
        // ============================================================
        // 
        // 问题分析：
        // - 子窗口（Child Window）有默认的裁剪区域（ClipRect）
        // - 如果播放头在子窗口内部渲染，标尺上的三角形手柄会被子窗口顶部边界裁剪掉
        // - 用户只能看到轨道上的红线，看不到标尺上的手柄
        //
        // 解决方案：
        // - 将 renderPlayhead 移到 endChild() 之后，在主窗口层级渲染
        // - 这样播放头会覆盖在所有内容之上，且不会被子窗口裁剪
        // - 使用单独的裁剪区域确保播放头只显示在内容区域内
        //
        // ============================================================
        
        // 使用主窗口的 DrawList（此时已经在 Child 作用域外）
        // ★ v9.0 更新：手柄现在在刻度尺内部（顶部与刻度尺顶部对齐），不需要扩展裁剪区域
        float trackAreaBottom = trackRegionTop + trackViewportHeight;
        
        // 设置裁剪区域：从刻度尺顶部一直到轨道区域底部
        // 手柄在刻度尺内部，所以不需要向上扩展裁剪区域
        pushClipRectSafe(drawList, contentX, winY, contentX + contentW, trackAreaBottom);
        
        // 注意参数调整：
        // - contentY 传 winY（Header 顶部），以便 renderer 计算正确的手柄位置
        // - contentH 传 availH（总高度），以便播放头线画到底部
        renderer.renderPlayhead(drawList, contentX, winY, contentW, availH);
        
        drawList.popClipRect();

        // 9) 把光标移动到子窗口下方，避免下一个控件覆盖
        ImGui.setCursorScreenPos(winX, trackRegionTop + trackViewportHeight);
    }

    /**
     * 处理刻度尺区域交互（点击跳转播放头，拖动播放头）
     * 
     * <p>功能：
     * <ul>
     *   <li>在刻度尺上点击：播放头跳转到点击位置</li>
     *   <li>点击播放头手柄：开始拖动播放头</li>
     *   <li>拖动播放头：实时更新播放头位置</li>
     * </ul>
     * 
     * @param winX 窗口 X 坐标
     * @param winY 窗口 Y 坐标
     * @param headerHeight Header 高度（包含刻度尺）
     * @param contentX 内容区域 X 起点（刻度尺起点）
     * @param contentW 内容区域宽度
     */
    private void handleRulerInteractions(float winX, float winY, float headerHeight, float contentX, float contentW) {
        // ★ v9.0 优化：添加可见性检查，防止布局异常
        // 如果内容区域宽度过小（可能窗口被压缩得很窄），不处理交互
        if (contentW < RULER_INTERACTION_MIN_WIDTH) {
            return;
        }
        
        float rulerHeight = TimelineRenderer.getRulerHeight();
        float rulerBottom = winY + rulerHeight;

        // 刻度尺区域（只包含内容区域，不包括左侧标签）
        float rulerX2 = contentX + contentW;
        
        // ★ v9.0 更新：手柄现在在刻度尺内部（顶部与刻度尺顶部对齐），不需要扩展交互区域
        ImGui.setCursorScreenPos(contentX, winY);
        // ★ 优化：添加实例哈希值避免 ID 冲突（虽然当前是单例，但为未来扩展预留）
        ImGui.invisibleButton("##TimelineRuler" + System.identityHashCode(this), contentW, rulerHeight);
        
        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();
        
        // 检查鼠标是否在刻度尺区域内
        boolean isInRulerArea = mouseX >= contentX && mouseX <= rulerX2 &&
                               mouseY >= winY && mouseY <= rulerBottom;
        
        if (!isInRulerArea && !ImGui.isItemHovered()) {
            return;
        }
        
        // 检查是否点击在播放头手柄上
        boolean isClickingPlayheadHandle = isPointInPlayheadHandle(mouseX, mouseY, winY, rulerHeight, contentX);
        
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left)) {
            // 无论是点击播放头手柄还是刻度尺其他位置，都统一调用 startPlayheadDrag
            // startPlayheadDrag 内部会处理位置更新和拖动开始
            interactionHandler.startPlayheadDrag(mouseX, contentX);
            
            if (isClickingPlayheadHandle) {
                LOGGER.debug("[TimelinePanel] 开始拖动播放头手柄");
            } else {
                LOGGER.debug("[TimelinePanel] 刻度尺点击跳转到时间: {:.2f}s", renderer.xToTime(mouseX, contentX));
            }
        }
        
        // 处理拖动（由 InteractionHandler 统一管理）
        // 这里不需要额外处理，因为拖动状态已经在 InteractionHandler 中管理
    }
    
    /**
     * 检查点是否在播放头手柄区域内（性能优化版）
     * 
     * <p>优化说明：
     * <ul>
     *   <li>移除了昂贵的重心坐标计算，改用简单的 AABB（轴对齐包围盒）检测</li>
     *   <li>对于 UI 交互，矩形判定比精确的三角形判定体验更好（更大的点击容错率），且性能更高</li>
     *   <li>避免了大量的浮点乘除运算，提升了高频交互场景下的性能</li>
     * </ul>
     * 
     * <p>v9.0 更新：手柄现在在刻度尺内部，顶部与刻度尺顶部对齐
     * 
     * @param mouseX 鼠标 X 坐标
     * @param mouseY 鼠标 Y 坐标
     * @param rulerTop 刻度尺顶部 Y 坐标
     * @param rulerHeight 刻度尺高度（未使用，保留以保持 API 一致性）
     * @param contentX 内容区域 X 起点
     * @return 是否在播放头手柄内
     */
    private boolean isPointInPlayheadHandle(float mouseX, float mouseY, float rulerTop, float rulerHeight, float contentX) {
        float currentTime = model.getCurrentTime();
        float playheadX = renderer.timeToX(currentTime, contentX);
        
        // ★ v9.0 更新：播放头手柄参数（与渲染代码保持一致）
        // 手柄现在变小了（6.0f），顶部与刻度尺顶部对齐
        float handleSize = 6.0f;
        float handleHeight = handleSize * 1.5f; // 9.0f
        // 手柄顶部位置：与刻度尺顶部对齐（三角形的顶部点在刻度尺上边缘）
        float handleBottom = rulerTop + handleHeight;

        // 简单的矩形包围盒检测（AABB）
        // X 轴范围：播放头中心左右各 handleSize
        boolean insideX = (mouseX >= playheadX - handleSize) && (mouseX <= playheadX + handleSize);
        
        // Y 轴范围：从刻度尺顶部到手柄底部（在刻度尺内部）
        boolean insideY = (mouseY >= rulerTop) && (mouseY <= handleBottom);
        
        return insideX && insideY;
    }

    /**
     * 双击处理关键帧添加/删除
     * 
     * <p>边界检查：
     * <ul>
     *   <li>检查轨道是否锁定</li>
     *   <li>检查片段是否有效</li>
     * </ul>
     */
    private void handleKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
        if (clip == null) {
            LOGGER.warn("[TimelinePanel] 尝试在空片段上添加关键帧");
            return;
        }
        
        // 检查轨道是否锁定
        TimelineTrack track = model.findTrackForClip(clip);
        if (track != null && track.isLocked()) {
            LOGGER.debug("[TimelinePanel] 轨道已锁定，无法修改关键帧: {}", track.getName());
            return;
        }
        
        final float clickTime = clamp(model.screenToTime(mouseX, contentX), 
            clip.getStartTime(), clip.getEndTime());
        
        Float existingKf = clip.getKeyframeAt(clickTime, KEYFRAME_TOLERANCE);
        
        if (existingKf != null) {
            RemoveKeyframeCommand cmd = new RemoveKeyframeCommand(model, clip, existingKf);
            commandHistory.executeCommand(cmd);
            markDataChanged();
            LOGGER.debug("[TimelinePanel] 删除关键帧 at {}s (片段: {})", 
                String.format("%.2f", existingKf), clip.getLabel());
        } else {
            AddKeyframeCommand cmd = new AddKeyframeCommand(model, clip, clickTime);
            commandHistory.executeCommand(cmd);
            markDataChanged();
            LOGGER.debug("[TimelinePanel] 添加关键帧 at {}s (片段: {})", 
                String.format("%.2f", clickTime), clip.getLabel());
        }
    }

    /**
     * 原位编辑轨道名称（已兼容 scrollY）
     */
    private void renderInlineEdit(float winX, float trackRegionTop, float scrollY) {
        if (!TrackRenameDialog.isEditingAny()) {
            return;
        }

        TimelineTrack editingTrack = TrackRenameDialog.getEditingTrack();
        if (editingTrack == null) {
            return;
        }

        int trackIndex = model.getTracks().indexOf(editingTrack);
        if (trackIndex < 0) {
            TrackRenameDialog.finishInlineEdit(false);
            return;
        }

        float rowHeight = TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        float trackY = trackRegionTop - scrollY + trackIndex * rowHeight;

        // ★ v9.0 优化：使用常量替换魔法数字，方便后期 UI 微调
        float nameX = winX + TRACK_EDIT_OFFSET_X;
        float nameY = trackY + TRACK_EDIT_OFFSET_Y;
        float inputWidth = TimelineRenderer.getLeftLabelWidth() - TRACK_EDIT_WIDTH_SUBTRACT;

        // 确保输入框不会过小无法输入
        if (inputWidth < TRACK_EDIT_MIN_WIDTH) {
            return;
        }

        ImGui.setCursorScreenPos(nameX, nameY);
        ImGui.pushItemWidth(inputWidth);

        ImGui.setKeyboardFocusHere();
        boolean enterPressed = ImGui.inputText("##TrackNameEdit", TrackRenameDialog.getInlineBuffer(),
                ImGuiInputTextFlags.EnterReturnsTrue);
        ImGui.popItemWidth();
        
        // Enter 键确认
        if (enterPressed) {
            TrackRenameDialog.finishInlineEdit(true);
        }
        
        // ESC 键取消
        if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
            TrackRenameDialog.finishInlineEdit(false);
        }
        
        // 点击其他地方完成编辑
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && !ImGui.isItemHovered()) {
            TrackRenameDialog.finishInlineEdit(true);
        }
    }

    /**
     * 剪切选中的片段（快捷键）
     */
    private void cutSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null) {
            clipboard.cut(selectedClip, track, model, commandHistory);
            markDataChanged();
        }
    }
    
    /**
     * 复制选中的片段（快捷键）
     */
    private void copySelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        clipboard.copy(selectedClip);
    }
    
    /**
     * 粘贴片段（快捷键）
     */
    private void pasteClip() {
        clipboard.paste(model);
        markDataChanged();
    }

    /**
     * 删除选中的片段
     */
    private void deleteSelectedClip() {
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;
        
        // ★ 轨道锁定功能：如果轨道锁定，禁止删除片段
        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null && track.isLocked()) {
            LOGGER.info("[TimelinePanel] 轨道已锁定，禁止删除片段 (轨道: {})", track.getName());
            return;
        }
        
        clipboard.deleteSelected(model, commandHistory);
        markDataChanged();
    }
    
    
    // ========== 回调接口实现（TimelineInteractionHandler.InteractionCallbacks）==========
    
    @Override
    public void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
        handleKeyframeDoubleClick(clip, mouseX, contentX);
    }

    @Override
    public void onClipInteractionFinished(TimelineClip clip, float originalStartTime, float originalEndTime) {
        // ★ 修复：使用显式传递 old 值的构造函数
        // originalStartTime/originalEndTime 是拖动开始时的值（旧值）
        // clip.getStartTime()/getEndTime() 是拖动结束时的值（新值）
        float currentStartTime = clip.getStartTime();
        float currentEndTime = clip.getEndTime();
        
        // 只有当位置真正改变时才创建命令
        if (Math.abs(currentStartTime - originalStartTime) > 0.001f || 
            Math.abs(currentEndTime - originalEndTime) > 0.001f) {
            // ★ 修复：使用显式传递 old 值的构造函数，避免从已修改的 clip 中读取错误的值
            MoveClipCommand cmd = new MoveClipCommand(
                model,
                new ClipAdapter(clip),
                originalStartTime,  // old 值：拖动开始时的位置
                originalEndTime,     // old 值：拖动开始时的位置
                currentStartTime,    // new 值：拖动结束时的位置（当前值）
                currentEndTime       // new 值：拖动结束时的位置（当前值）
            );
            commandHistory.executeCommand(cmd);
            model.markDurationDirty(); // 片段时间修改可能影响总时长
            markDataChanged();
            LOGGER.info("[TimelinePanel] 完成片段移动操作 (片段: {}, 时间: {:.2f}s-{:.2f}s -> {:.2f}s-{:.2f}s)", 
                clip.getLabel(), originalStartTime, originalEndTime, currentStartTime, currentEndTime);
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[TimelinePanel] 片段位置未改变，跳过命令创建");
            }
        }
    }

    @Override
    public void onKeyframeDragFinished(TimelineClip clip, float originalTime, float newTime) {
        // 控制器负责创建命令和管理数据修改
        // 使用 Remove + Add 命令实现关键帧移动
        commandHistory.executeCommand(new RemoveKeyframeCommand(model, clip, originalTime));
        commandHistory.executeCommand(new AddKeyframeCommand(model, clip, newTime));
        markDataChanged();
        LOGGER.info("[TimelinePanel] 移动关键帧从 {}s 到 {}s (片段: {})", 
            String.format("%.2f", originalTime), String.format("%.2f", newTime), clip.getLabel());
    }

    @Override
    public void onDataChanged() {
        markDataChanged();
    }

    @Override
    public void setHoveredTrack(TimelineTrack track) {
        // 保留接口实现，但不再存储状态（由 InteractionHandler 内部管理）
    }

    public void requestClipRename(TimelineClip clip) {
        ClipRenameDialog.open(clip);
    }

    public void requestClipLength(TimelineClip clip) {
        ClipLengthDialog.open(clip);
    }

    public void requestClipContainerBinding(TimelineClip clip) {
        ClipBindingDialog.open(clip);
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
        if (containerId == null || containerId.isEmpty()) {
            LOGGER.warn("[TimelinePanel] 容器ID为空，拒绝拖放");
            return;
        }
        
        try {
            // 1. 验证并解析容器ID
            UUID containerUuid;
            try {
                containerUuid = UUID.fromString(containerId);
            } catch (IllegalArgumentException e) {
                LOGGER.error("[TimelinePanel] 无效的容器ID格式: {}", containerId, e);
                return;
            }
            
            // 2. 获取容器
            Container container = ContainerManager.getInstance().getContainer(containerUuid);
            if (container == null) {
                LOGGER.warn("[TimelinePanel] 容器不存在: {}", containerId);
                return;
            }
            
            // 3. 创建容器轨道（使用UI层的TimelineTrack）
            TimelineTrack track = new TimelineTrack(container.getName());
            track.setName("Track_" + container.getName());
            
            // 4. 添加到时间线模型
            model.addTrack(track);
            
            // 5. 标记数据已改变
            markDataChanged();
            
            LOGGER.info("[TimelinePanel] 成功创建容器轨道: {} -> {}", 
                container.getName(), track.getName());
                
        } catch (Exception e) {
            LOGGER.error("[TimelinePanel] 处理容器拖放失败: containerId={}", containerId, e);
            // 可以考虑在这里添加用户通知（如 ImGui 弹窗）
        }
    }

    @Override
    public void onContainerDroppedToTrack(String containerId, int trackIndex) {
        // 注意：此回调现在由 TimelineDragAndDropHandler 内部处理
        // 保留此方法以兼容接口，但实际逻辑已在 handleDragDropTarget 中实现
        LOGGER.debug("[TimelinePanel] onContainerDroppedToTrack 回调被调用，但逻辑已由 DragDropHandler 处理");
    }


    @Override
    public void onContainerDropRejected(String containerId, int trackIndex) {
        if (containerId == null || containerId.isEmpty()) {
            LOGGER.warn("[TimelinePanel] 容器ID为空，拒绝拖放");
            return;
        }
        
        try {
            // 1. 验证并解析容器ID
            UUID containerUuid;
            try {
                containerUuid = UUID.fromString(containerId);
            } catch (IllegalArgumentException e) {
                LOGGER.error("[TimelinePanel] 无效的容器ID格式: {}", containerId, e);
                return;
            }
            
            // 2. 获取容器
            Container container = ContainerManager.getInstance().getContainer(containerUuid);
            if (container == null) {
                LOGGER.warn("[TimelinePanel] 容器不存在: {}", containerId);
                return;
            }
            
            // 3. 获取目标轨道
            if (trackIndex < 0 || trackIndex >= model.getTrackCount()) {
                LOGGER.warn("[TimelinePanel] 目标轨道索引无效: {} (总轨道数: {})", 
                    trackIndex, model.getTrackCount());
                return;
            }
            
            TimelineTrack targetTrack = model.getTrackAt(trackIndex);
            if (targetTrack == null) {
                LOGGER.warn("[TimelinePanel] 目标轨道不存在，索引: {}", trackIndex);
                return;
            }
            
            // 4. 记录拒绝原因
            String containerName = container.getName();
            String trackTypeName = targetTrack.getType().getDisplayName();
            
            LOGGER.warn("[TimelinePanel] 容器拖放被拒绝: {} 不能拖放到 {} 轨道上 (轨道索引: {})", 
                containerName, trackTypeName, trackIndex);
            
            // 5. 可以在这里添加用户界面提示，比如显示一个临时的错误消息
            // 由于ImGui的限制，这里只记录日志，实际应用中可以通过其他方式显示提示
            
        } catch (Exception e) {
            LOGGER.error("[TimelinePanel] 处理容器拖放拒绝失败: containerId={}, trackIndex={}", 
                containerId, trackIndex, e);
        }
    }

    // ========== 回调接口实现（TimelineControlBar.Callbacks）==========
    
    @Override
    public void onAddTrackClicked() {
        // 委托给 TrackController
        trackController.openMenu();
    }

    @Override
    public void onDeleteSelectedClicked() {
        deleteSelectedClip();
    }

    @Override
    public void onFitToViewClicked() {
        // 委托给 NavigationHandler
        float availableWidth = ImGui.getContentRegionAvailX() - TimelineRenderer.getLeftLabelWidth();
        navigationHandler.fitToView(availableWidth);
    }
    
    // ========== 工具方法 ==========
    
    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    @Override
    public int getPriority() {
        return 200;
    }
}