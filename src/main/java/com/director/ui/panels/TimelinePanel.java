package com.director.ui.panels;

import com.director.ui.PanelId;
import com.director.ui.PanelWindows;
import com.director.ui.timeline.commands.*;
import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.ui.timeline.dialog.*;
import com.director.ui.timeline.interaction.*;
import com.director.ui.timeline.menu.TimelineContextMenu;
import com.director.ui.timeline.menu.TimelineContextMenuFactory;
import com.director.core.DirectorServices;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TimelineInterpolation;
import com.director.timeline.model.TimelineModel;
import com.director.timeline.model.TimelineSelectable;
import com.director.timeline.model.TimelineTrack;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.TimelineViewportState;
import com.director.timeline.model.audio.AudioClipKeyframe;
import com.director.timeline.model.camera.CameraKeyframe;
import com.director.timeline.model.entity.EntityClipKeyframe;
import com.director.timeline.model.time.TimeKeyframe;
import com.director.timeline.model.weather.WeatherKeyframe;
import com.director.ui.timeline.render.TimelineColors;
import com.director.ui.timeline.render.TimelineRenderer;
import com.director.ui.timeline.audio.TimelineAudioService;
import com.director.ui.timeline.state.*;
import com.director.ui.timeline.ui.TimelineControlBar;
import com.director.timeline.model.season.SeasonKeyframe;
import com.director.timeline.playback.camera.CameraRuntime;
import com.director.timeline.playback.camera.TimelineCameraController;
import com.director.timeline.playback.season.TimelineSeasonController;
import com.director.timeline.playback.time.TimelineWorldController;
import com.director.timeline.playback.weather.TimelineWeatherController;
import com.director.timeline.model.camera.path.CustomPathClip;
import net.minecraft.client.MinecraftClient;
import imgui.ImGui;
import imgui.ImDrawList;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiFocusedFlags;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ⚠️ GLFW 导入已不再使用（时间线快捷键已迁移到 ShortcutDispatcher）

/**
 * Director 专业级时间轴面板（架构重构版 v8.0）
 * <p>
 * 基于完全分离的 MVC 架构设计，
 * <ul>
 *   <li>✅ Model: {@link TimelineModel} - 时间数据模型</li>
 *   <li>✅ View: {@link TimelineRenderer} - 时间线渲染逻辑</li>
 *   <li>✅ Controller: {@link TimelinePanel} - 中央协调器</li>
 *   <li>⚙ Command Pattern: 完整的撤销/重做</li>
 *   <li>⚙ Interaction Handler: 专职交互处理器</li>
 *   <li>✅ Control Bar: 独立的工具栏组件</li>
 *   <li>⚙ Context Menu Factory: 菜单工厂模式</li>
 *   <li>✅ Drag & Drop Handler: 专职的拖放处理器</li>
 *   <li>✅ Navigation Handler: 专职的导航缩放处理器</li>
 *   <li>✅ Track Controller: 专职的轨道管理控制器</li>
 * </ul>
 * </p>
 *
 * <p>
 * 拆分原则，
 * <ul>
 *   <li>{@link TimelineDragAndDropHandler} - 处理拖放选段和标签管理</li>
 *   <li>{@link TimelineNavigationHandler} - 处理视图缩放、滚动和平移</li>
 *   <li>{@link TimelineTrackController} - 处理轨道的 CRUD 操作和菜单项</li>
 * </ul>
 * </p>
 *
 * @author Director Team
 * @version 8.0 - 职责拆分优化
 * @since 1.0
 */
public class TimelinePanel extends AbstractPanel
        implements TimelineInteractionHandler.InteractionCallbacks,
        TimelineControlBar.Callbacks,
        ContainerManager.ContainerListener,
        com.director.ui.aware.ICommandsAware,
        com.director.ui.aware.IPlaybackModelProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelinePanel.class);

    // ========== UI 布局常量（v9.0 优化：提取常量数值）==========
    /** 轨道名称编辑框的 X 偏移量（相对于窗口左侧） */
    private static final float TRACK_EDIT_OFFSET_X = 28f;
    /** 轨道名称编辑框的 Y 偏移量（相对于轨道顶部） */
    private static final float TRACK_EDIT_OFFSET_Y = 6f;
    /** 轨道名称编辑框宽度需要减去的值（为右侧控件留出空间） */
    private static final float TRACK_EDIT_WIDTH_SUBTRACT = 65f;
    /** 轨道名称编辑框的最小宽度（防止过小无法输入） */
    private static final float TRACK_EDIT_MIN_WIDTH = 20f;
    /** 刻度尺最小交互的宽度（小于此值不处理交互，避免布局异常） */
    private static final float RULER_INTERACTION_MIN_WIDTH = 10f;
    /** 虚拟滚动条底部填充（像素），用于改善滚动体验 */
    private static final float DUMMY_SCROLL_PADDING = 50.0f;
    /** In/Out 标签宽度 */
    private static final float RANGE_TAG_WIDTH = 28.0f;
    /** In/Out 标签高度 */
    private static final float RANGE_TAG_HEIGHT = 14.0f;
    /** In/Out 标签与线的水平间距 */
    private static final float RANGE_TAG_GAP = 4.0f;

    // ========== 核心组件（框架优化）==========
    private final TimelineModel model;
    private final TimelineViewportState viewportState;
    private final TimelineRenderer renderer;
    private final TimelineCommandHistory commandHistory;
    private final TimelineInteractionManager interactionManager;
    private final TimelineInteractionHandler interactionHandler;
    private final TimelineControlBar controlBar;
    private final TimelineAudioService audioService = TimelineAudioService.getInstance();
    
    // ⭐ Facade 架构：命令门面（用于控制命令，如播放/暂停/跳转）
    private com.director.ui.commands.UICommandFacade commands;
    
    // ⭐ v1.1：焦点状态追踪（用于快捷键上下文检测）
    private static volatile boolean isTimelinePanelFocused = false;
    
    /**
     * 检查 TimelinePanel 是否聚焦（供 ShortcutContext 使用）
     * 
     * @return true 如果 TimelinePanel 聚焦，false 如果不聚焦
     */
    public static boolean isFocused() {
        return isTimelinePanelFocused;
    }

    // ========== 新的拆分组件 (Controllers/Handlers) ==========
    private final TimelineDragAndDropHandler dragDropHandler;
    private final TimelineNavigationHandler navigationHandler;
    private final TimelineTrackController trackController;

    // ========== UI 状态（封装优化） ==========
    private final ClipboardManager clipboard = ClipboardManager.getInstance();
    private final AutoSaveManager autoSave;
    private int dropTargetTrackIndex = -1;
    
    // ⭐ 时间标尺交互状态跟踪（用于区分点击和拖动）
    private float rulerClickStartX = -1f;
    private float rulerClickStartY = -1f;
    private static final float DRAG_THRESHOLD = 5f; // 拖动阈值（像素）
    
    // ⭐ 标记拖动状态跟踪（右键拖动标记）
    private com.director.timeline.model.TimelineMarker draggedMarker = null;
    private float markerDragStartX = -1f;

    // ========== 性能优化：基于时间的定时器 ==========
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
        this(new TimelineCommandHistory());
    }

    public TimelinePanel(TimelineCommandHistory sharedCommandHistory) {
        super();

        // 1. 初始化核心组件
        this.viewportState = new TimelineViewportState();
        this.model = new TimelineModel(viewportState);
        com.director.actor.model.texture.CustomModelResourceWarmup.scheduleForCurrentWorld();
        this.renderer = new TimelineRenderer(model, viewportState);
        this.commandHistory = sharedCommandHistory != null ? sharedCommandHistory : new TimelineCommandHistory();
        com.director.ui.timeline.commands.TimelineCommandBridge.bind(this.commandHistory);
        this.interactionManager = new TimelineInteractionManager();
        this.interactionHandler = new TimelineInteractionHandler(model, interactionManager, commandHistory, renderer);
        
        // ⭐ 设置拖动状态查询器（用于实时显示拖动中的关键帧位置）
        renderer.setDragStateQuery(new TimelineRenderer.DragStateQuery() {
            @Override
            public float getDraggedAutomationKeyframeTime(TimelineSelectable keyframe) {
                return interactionHandler.getDraggedAutomationKeyframeTime(keyframe);
            }
            
            @Override
            public float getDraggedClipKeyframeTime(TimelineClip clip, float keyframeTime) {
                return interactionHandler.getDraggedClipKeyframeTime(clip, keyframeTime);
            }
            
            @Override
            public float getDraggedMarkerTime(com.director.timeline.model.TimelineMarker marker) {
                return interactionHandler.getDraggedMarkerTime(marker);
            }
            
            @Override
            public float getDraggedPathClipStartTime(com.director.timeline.model.camera.path.CameraPathClip clip) {
                return interactionHandler.getDraggedPathClipStartTime(clip);
            }
            
            @Override
            public float getDraggedPathClipEndTime(com.director.timeline.model.camera.path.CameraPathClip clip) {
                return interactionHandler.getDraggedPathClipEndTime(clip);
            }

            @Override
            public boolean isSnapGuideActive() {
                return interactionHandler.isSnapGuideActive();
            }

            @Override
            public float getSnapGuideTime() {
                return interactionHandler.getSnapGuideTime();
            }

            @Override
            public boolean isSnapGuideToGrid() {
                return interactionHandler.isSnapGuideToGrid();
            }

            @Override
            public boolean isSnapGuideToClip() {
                return interactionHandler.isSnapGuideToClip();
            }

            @Override
            public boolean isSnapGuideToKeyframe() {
                return interactionHandler.isSnapGuideToKeyframe();
            }
        });

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

        // 初始化自动保存时钟
        this.lastAutoSaveTime = System.currentTimeMillis();

        // 注册容器-时间线关联服务
        DirectorServices.get().containerTimeline().setTimelineModel(model);
        DirectorServices.get().containerTimeline().setAudioSync(audioService);

        model.setAudioLoadHook(audioService);
        model.setClipSoundCollector(audioService);
        model.setTrackPlaybackListener(
                com.director.ui.timeline.playback.TimelineAudioController.getInstance());
        model.setPlaybackLifecycle(TimelineCameraController.getInstance()::onTimelineUiClosed);
        
        // 初始化音频服务（自动收集声音事件）
        audioService.setTimelineModel(model);
        
        // 初始化音频控制器（播放时触发声音）
        com.director.ui.timeline.playback.TimelineAudioController.getInstance().setTimelineModel(model);

        // 注册片段方块控制器，根据播放头位置控制片段方块显示
        com.director.timeline.playback.block.TimelineClipBlockController.getInstance().setTimelineModel(model);
        TimelineCameraController.getInstance().init(model);
        // ⭐ 注册时间轨道控制器，控制世界时间和太阳角度
        TimelineWorldController.getInstance().init(model);
        // ⭐ 注册季节轨道控制器，控制季节颜色和气候
        com.director.timeline.playback.season.TimelineSeasonController.getInstance().init(model);
        // ⭐ 演员轨道控制器 v2：绑定 TimelineModel 和 ActorRegistry
        com.director.actor.ActorRegistry actorRegistry = 
            com.director.actor.ActorRegistryManager.getInstance();
        com.director.actor.runtime.ActorRuntimeController.getInstance().bind(model, actorRegistry);

        // ⭐ 天气轨道控制器：v1.0 不再需要 init()，改为通过 bindActiveTrack() 绑定轨道
        // 注册模型变更监听器，用于自动激活唯一的天气轨道
        model.addModelChangeListener(changeType -> com.director.timeline.playback.weather.TimelineWeatherController.getInstance().checkAndAutoActivate());
        // 初始化时检查并自动激活唯一的天气轨道
        com.director.timeline.playback.weather.TimelineWeatherController.getInstance().checkAndAutoActivate();

        // 注册容器监听器，用于同步容器名称变更到时间线片段
        DirectorServices.get().containers().addListener(this);

        // 初始化对话框
        ClipRenameDialog.initialize(model, commandHistory);
        ClipLengthDialog.initialize(commandHistory, model);
        ClipBindingDialog.initialize(model, commandHistory, this::markDataChanged);
        TrackRenameDialog.initialize(model, commandHistory, this::markDataChanged);

        // ⭐ v1.2 改进：在 TimelinePanel 初始化时绑定 Orchestrator（生命周期节点）
        // 这避免了每帧 bindModel，确保模型绑定只在生命周期节点发生
        com.director.ui.timeline.playback.PlaybackOrchestrator.get().bindModel(model);
    }

    /**
     * 是否有进行中的时间线交互（拖动片段、框选等），供 ShortcutDispatcher 路由 Esc。
     */
    public boolean isInteractionActive() {
        return interactionManager.isInteracting() || renderer.isActorKeyInteractionActive();
    }

    /**
     * 取消进行中的时间线交互（Esc 快捷键）。
     */
    public void cancelInteraction() {
        renderer.cancelActorKeyInteraction();
        interactionHandler.cancelAllInteractionsSilently();
        autoSave.markDirty();
    }

    @Override
    public String getPanelName() {
        return PanelId.TIMELINE;
    }

    @Override
    public String getWindowName() {
        return PanelWindows.timeline();
    }

    /**
     * ⭐ UI 生命周期管理：重写 setVisible 以在 UI 关闭时清理状态
     */
    @Override
    public void setVisible(boolean visible) {
        boolean wasVisible = isVisible();
        super.setVisible(visible);
        
        // UI 关闭时清理状态（但不通知 CameraController，由 DirectorScreen 统一处理）
        if (wasVisible && !visible) {
            dispose();
        }
    }
    
    /**
     * ⭐ UI 生命周期管理：清理面板资源
     * 在 UI 关闭时调用，静默取消所有交互，不触发摄像机回调
     */
    @Override
    protected void onContentUnavailable() {
        com.director.ui.actor.pose.ActorAnimationEditingLifecycle.cancelTransientWork();
    }

    @Override
    public void dispose() {
        com.director.ui.actor.pose.ActorAnimationEditingLifecycle.cancelTransientWork();
        if (disposed) {
            return;
        }
        disposed = true;

        LOGGER.debug("[TimelinePanel] 开始清理面板资源");

        // 1. 静默取消所有交互（不通知 CameraController）
        interactionHandler.cancelAllInteractionsSilently();

        // 2. 强制 UI 状态归零
        model.setScrubbing(false);
        // 不改 isPlaying，由上层控制

        // 3. 清理监听器，防止内存泄漏
        try {
            DirectorServices.get().containers().removeListener(this);
            LOGGER.debug("[TimelinePanel] 已移除容器监听器");
        } catch (Exception e) {
            LOGGER.warn("[TimelinePanel] 移除容器监听器失败: {}", e.getMessage());
        }

        // 4. 清理音频服务资源
        if (audioService != null) {
            try {
                audioService.dispose();
                LOGGER.debug("[TimelinePanel] 已清理音频服务");
            } catch (Exception e) {
                LOGGER.warn("[TimelinePanel] 清理音频服务失败: {}", e.getMessage());
            }
        }

        LOGGER.debug("[TimelinePanel] 面板资源清理完成");
    }
    
    private boolean disposed = false;

    @Override
    protected int getWindowFlags() {
        return ImGuiWindowFlags.NoCollapse;
    }

    @Override
    protected void doRender() {
        // ✅ 只有时间线面板聚焦时才启用时间线快捷键上下文，避免影响其他面板输入
        // 注意：ImGui.isWindowFocused() 必须在 ImGui.begin() 之后调用（已在 AbstractPanel.render() 中确保）
        boolean focused = ImGui.isWindowFocused(ImGuiFocusedFlags.RootAndChildWindows);
        
        // ⭐ v1.1：更新焦点状态（供 ShortcutContext / ShortcutDispatcher 使用，如 Delete、播放等键盘快捷键）
        // 仅用 focused：键盘快捷键在窗口（或其子窗口）有焦点时即可生效，不要求鼠标悬停；
        // 否则选中片段/关键帧后鼠标移开再按 Delete 会无反应。
        isTimelinePanelFocused = focused;
        
        // 调试日志（仅在 trace 级别）
        if (LOGGER.isTraceEnabled() && focused) {
            LOGGER.trace("[TimelinePanel] 窗口聚焦状态已更新: focused={}", true);
        }

        // ⚠️ 播放子系统 tick 已迁移到 DirectorScreen.tick()（Facade 架构）
        // 所有播放相关的 Controller 更新现在通过 commands.playback().tick(deltaTime) 统一处理
        // 这确保了播放逻辑不依赖于 TimelinePanel 的可见性，与 Menu/Toolbar/Shortcut 的行为一致
        
        // 3. 渲染控制栏（优先给专职的组件）
        renderControlBar();


        // 控制栏与分隔线之间的间距（等于按钮与按钮之间的间距，4像素）
        // 注意：控制栏设置了 ItemSpacing=4px，控制栏结束后会自动添加 4px 间距
        // 所以控制栏到 separator 之间已经有 4px 间距了，我们不需要再手动添加
        // 但需要临时清除 ItemSpacing 以避免 separator 也自动添加间距
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f);
        //ImGui.separator();
        // 分隔线与时间刻度栏之间的间距（等于按钮与按钮之间的间距，4像素）
        // 使用固定的像素间距，确保 Header 与 Separator 不太紧
        //ImGui.dummy(0f, UITheme.Spacing.ELEMENT_SPACING); // 4px: 分隔线到时间刻度栏的间距
        ImGui.popStyleVar(); // 恢复 ItemSpacing

        // 4. 渲染时间线画布
        renderTimelineCanvas();

        // 5. 渲染新建轨道菜单（委托给 TrackController）
        trackController.renderAddTrackMenu();

        // 6. 上下文菜单现在在子窗口内渲染（见 renderTimelineCanvas 方法）
        // 这样可以确保 popup 在正确的窗口上下文中间显示

        // 7. 自动保存（性能优化：基于时间的检查，不受FPS波动影响）
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAutoSaveTime >= AUTO_SAVE_INTERVAL_MS) {
            autoSave.tick();
            lastAutoSaveTime = currentTime;
        }
    }

    // ⚠️ updatePlayback() 方法已迁移到 PlaybackCommandsImpl.tick()（Facade 架构）
    // 播放系统 tick 现在通过 commands.playback().tick(deltaTime) 统一处理
    // 这确保了播放逻辑不依赖于 TimelinePanel 的可见性，与 Menu/Toolbar/Shortcut 的行为一致
    // 
    // 自动跟随功能（ensurePlayheadVisible）保留在 TimelinePanel 中，因为它是 UI 视图相关的功能
    // 需要在播放 tick 后调用，这可以通过监听播放状态变化来实现

    /**
     * 存储的可视区域参数（用于自动跟随功能）
     * 这些值在 renderTimelineCanvas() 中更新，用于自动跟随功能
     */
    private float cachedContentX = 0f;
    private float cachedContentW = 0f;
    private static final float PLAYHEAD_MARGIN = 50f; // 播放头距离可见区域边缘的最小距离（像素）

    /**
     * 确保播放头始终在可见区域内（自动跟随功能）
     *
     * <p>类似于 Premiere Pro 等专业视频编辑软件，播放时标记始终不跑出屏幕外。
     * 如果播放头超出可见范围，自动调整 scrollX 使播放头保持在可见区域内。
     *
     * <p>v9.0 优化：防止与用户手动导航冲突
     * <ul>
     *   <li>如果用户正在拖动滚动条、平移视图或进行其他交互，暂停自动跟随</li>
     *   <li>避免用户在查看其他区域时被强制拉回播放头位置，导致画面抖动或操作失效</li>
     * </ul>
     */
    private void ensurePlayheadVisible() {
        // 只有在有效区域时才执行自动跟随
        if (cachedContentW <= 0) {
            return;
        }

        // ⭐ v9.0 优化：防止与用户手动导航冲突
        // 如果用户按下了鼠标（可能在拖动滚动条、平移视图或进行其他交互），临时禁用自动跟随
        // 这允许用户在播放过程中自由查看其他区域，而不会被强制拉回播放头位置
        if (ImGui.isMouseDown(ImGuiMouseButton.Left) ||
                ImGui.isMouseDown(ImGuiMouseButton.Middle) ||
                ImGui.isMouseDown(ImGuiMouseButton.Right)) {
            return;
        }

        // 检查是否有任何活跃的 ImGui 控件（如滑块、输入框等）。
        // 普通 hover 不应暂停自动跟随，否则鼠标停在时间线面板上时播放头会跑出视野。
        if (ImGui.isAnyItemActive()) {
            return;
        }

        // 委托给 NavigationHandler 处理
        navigationHandler.ensurePlayheadVisible(cachedContentX, cachedContentW, PLAYHEAD_MARGIN);
    }

    private boolean isPlaybackActive() {
        if (commands != null && commands.playback() != null) {
            return commands.playback().isPlaying();
        }
        return model != null && model.isPlaying();
    }

    /**
     * 标记数据已修改（委托给 AutoSaveManager）
     */
    private void markDataChanged() {
        autoSave.markDirty();
    }

    /**
     * 重置时间线与容器关联（供菜单项“新建动画”调用）
     */
    public void resetWorkspace(boolean clearContainers) {
        clearWorkspaceEditingState();
        model.resetToEmpty();
        if (clearContainers) {
            DirectorServices.get().containers().clear();
        }
        autoSave.markDirty();
    }

    /**
     * 存档切换时调用：清空并加载“当前存档”的时间线数据（如无存档则保持空白）。
     */
    private void clearWorkspaceEditingState() {
        cancelInteraction();
        commandHistory.clear();
        clipboard.clear();
        com.director.ui.actor.selection.ActorSelectionHub.clearSelection();
        com.director.ui.actor.pose.ActorAnimationEditingLifecycle.cancelTransientWork();
    }

    public void reloadForCurrentWorld() {
        clearWorkspaceEditingState();
        com.director.ui.actor.selection.ActorSelectionHub.clearSelection();
        com.director.ui.actor.pose.ActorAnimationEditingLifecycle.cancelTransientWork();
        var runtime=com.director.actor.runtime.ActorRuntimeController.getInstance();
        boolean enabled=runtime.isEnabled();
        runtime.setEnabled(false);
        try {
            model.reloadForCurrentWorld();
        } finally {
            runtime.bind(model,com.director.actor.ActorRegistryManager.getInstance());
            runtime.setEnabled(enabled);
        }
        com.director.actor.model.texture.CustomModelResourceWarmup.scheduleForCurrentWorld();
    }

    /**
     * 立即落盘一次（用于关闭 Director 界面前的兜底保存）。
     *
     * @return 是否成功写入存档
     */
    public boolean saveNow() {
        cancelInteraction();
        autoSave.flushNow();
        boolean saved = model.save();
        return com.director.actor.ActorRegistryManager.saveImmediately() && saved;
    }

    /**
     * 渲染控制栏（优先给专职的组件）
     */
    private void renderControlBar() {
        controlBar.render();
    }

    /**
     * 安全地推送剪裁区域（防止边缘情况崩溃）
     *
     * <p>性能优化：如果用户将窗口调整得非常小（如高度小于 Header 高度），
     * trackViewportHeight 可能接近 0。虽然做 Math.max(10f, ...) 的保护，
     * 但 pushClipRect 如果接收到 min > max 的坐标（如在极端布局计算错误
     * 或窗口最小化闪屏间），会导致 ImGui 内部崩溃或画布异常。
     *
     * @param drawList ImGui 绘制列表
     * @param x1 剪裁区域左上角 X
     * @param y1 剪裁区域左上角 Y
     * @param x2 剪裁区域右下角 X
     * @param y2 剪裁区域右下角 Y
     */
    private void pushClipRectSafe(ImDrawList drawList, float x1, float y1, float x2, float y2) {
        if (x2 <= x1 || y2 <= y1) {
            // 如果区域无效，推送一个空的剪裁区域（剪裁掉所有内容），防止 ImGui 报错
            // 或者推送一个 1x1 的安全区域
            drawList.pushClipRect(x1, y1, x1 + 1f, y1 + 1f);
        } else {
            drawList.pushClipRect(x1, y1, x2, y2);
        }
    }

    /**
     * 新版时间线画布（v8.0）：
     * - 上方：时间标记 + 轨道标题区域（固定）
     * - 下方：轨道区域子窗口（垂直滚动）
     * - Ctrl + 滚动：缩放（垂直鼠标）
     * - Shift + 滚动：水平滚动（scrollX）
     */
    private record TimelineCanvasLayout(
            float winX,
            float winY,
            float winW,
            float availH,
            float headerHeight,
            float trackViewportHeight,
            float contentX,
            float contentW,
            float trackRegionTop
    ) {
    }

    private void renderTimelineCanvas() {
        ImDrawList drawList = ImGui.getWindowDrawList();
        TimelineCanvasLayout layout = computeTimelineCanvasLayout();
        boolean skipDividerDrag = shouldSkipDividerDragForKeyframes(layout);

        prepareTimelineCanvasFrame(layout, skipDividerDrag);
        renderTimelineCanvasHeader(layout, drawList);
        handleRulerInteractions(layout.winY(), layout.contentX(), layout.contentW());
        navigationHandler.handleMouseWheel(
                layout.winX(), layout.winY(), layout.winW(),
                layout.headerHeight(), layout.trackViewportHeight()
        );

        if (layout.trackViewportHeight() <= 0f) {
            ImGui.setCursorScreenPos(layout.winX(), layout.winY() + layout.headerHeight());
            return;
        }

        renderTimelineTrackRegion(layout, drawList);
        renderTimelinePlayheadOverlay(layout, drawList);
        ImGui.setCursorScreenPos(layout.winX(), layout.trackRegionTop() + layout.trackViewportHeight());
    }

    private TimelineCanvasLayout computeTimelineCanvasLayout() {
        float winX = ImGui.getCursorScreenPosX();
        float winY = ImGui.getCursorScreenPosY();
        float winW = ImGui.getContentRegionAvailX();
        float availH = ImGui.getContentRegionAvailY();
        float headerHeight = TimelineRenderer.getHeaderHeight();
        float trackViewportHeight = Math.max(10f, availH - headerHeight);
        float contentX = winX + TimelineRenderer.getLeftLabelWidth();
        float contentW = winW - TimelineRenderer.getLeftLabelWidth();
        float trackRegionTop = winY + headerHeight;
        return new TimelineCanvasLayout(
                winX, winY, winW, availH,
                headerHeight, trackViewportHeight,
                contentX, contentW, trackRegionTop
        );
    }

    private boolean shouldSkipDividerDragForKeyframes(TimelineCanvasLayout layout) {
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        float dividerX = layout.winX() + TimelineRenderer.getLeftLabelWidth();
        float dividerHitWidth = 4f;

        if (mouseX < dividerX - dividerHitWidth || mouseX > dividerX + dividerHitWidth) {
            return false;
        }
        if (mouseY < layout.trackRegionTop() || mouseY > layout.trackRegionTop() + layout.trackViewportHeight()) {
            return false;
        }

        TimelineHitResult quickHit = TimelineHitTest.compute(
                renderer, model,
                mouseX, mouseY,
                layout.contentX(), layout.trackRegionTop(),
                layout.contentW(), layout.trackViewportHeight(),
                viewportState.getScrollY(),
                null
        );
        return quickHit.type() == TimelineHitResult.HitType.KEYFRAME
                || quickHit.type() == TimelineHitResult.HitType.AUTOMATION_KEYFRAME;
    }

    private void prepareTimelineCanvasFrame(TimelineCanvasLayout layout, boolean skipDividerDrag) {
        renderer.updateLeftSectionWidth(layout.winX(), layout.winY(), layout.availH(), skipDividerDrag);
        cachedContentX = layout.contentX();
        cachedContentW = layout.contentW();
        if (isPlaybackActive()) {
            ensurePlayheadVisible();
        }
        renderer.beginFrame(layout.winX(), layout.winY(), layout.winW(), layout.availH());
    }

    private void renderTimelineCanvasHeader(TimelineCanvasLayout layout, ImDrawList drawList) {
        pushClipRectSafe(
                drawList,
                layout.winX(), layout.winY(),
                layout.winX() + layout.winW(), layout.winY() + layout.headerHeight()
        );
        renderer.renderTimelineHeader(drawList, layout.winX(), layout.winY(), layout.winW());
        drawList.popClipRect();
    }

    private void renderTimelineTrackRegion(TimelineCanvasLayout layout, ImDrawList drawList) {
        int trackCount = model.getTrackCount();
        float trackHeight = TimelineRenderer.getTrackHeight();
        float trackGap = TimelineRenderer.getTrackGap();
        float rowHeight = trackHeight + trackGap;
        float totalTracksHeight = renderer.prepareTrackRows();

        ImGui.setCursorScreenPos(layout.winX(), layout.trackRegionTop());
        ImGui.beginChild("##TimelineTrackRegion", layout.winW(), layout.trackViewportHeight(), false, ImGuiWindowFlags.None);

        float totalTracksHeightWithPadding = totalTracksHeight + DUMMY_SCROLL_PADDING;
        if (totalTracksHeightWithPadding > layout.trackViewportHeight()) {
            ImGui.setCursorPos(0, totalTracksHeightWithPadding);
            ImGui.dummy(1.0f, 1.0f);
            ImGui.setCursorPos(0, 0);
        }

        float scrollY = ImGui.getScrollY();
        viewportState.setScrollY(scrollY);

        pushClipRectSafe(
                drawList,
                layout.winX(), layout.trackRegionTop(),
                layout.winX() + layout.winW(), layout.trackRegionTop() + layout.trackViewportHeight()
        );
        renderer.renderTrackArea(
                drawList, layout.winX(), layout.trackRegionTop(), layout.winW(), layout.trackViewportHeight()
        );
        renderTimelineTrackOverlays(layout, drawList, scrollY, rowHeight);
        drawList.popClipRect();

        renderTimelineTrackEditors(layout, scrollY);
        renderTimelineTrackDialogs();
        renderTimelineTrackInteractions(layout, scrollY, totalTracksHeight);

        ImGui.endChild();
    }

    private void renderTimelineTrackOverlays(
            TimelineCanvasLayout layout,
            ImDrawList drawList,
            float scrollY,
            float rowHeight
    ) {
        if (dropTargetTrackIndex >= 0) {
            float y = layout.trackRegionTop() - scrollY + renderer.getMath().trackContentY(dropTargetTrackIndex);
            renderer.renderDropHighlight(drawList, layout.contentX(), y, layout.contentW());
        }
        renderer.renderSnapGuide(
                drawList,
                layout.contentX(), layout.trackRegionTop(),
                layout.contentW(), layout.trackViewportHeight()
        );
    }

    private void renderTimelineTrackEditors(TimelineCanvasLayout layout, float scrollY) {
        renderInlineEdit(layout.winX(), layout.trackRegionTop(), scrollY);
    }

    private void renderTimelineTrackDialogs() {
        TrackRenameDialog.renderPopupIfNeeded();
        ClipRenameDialog.renderIfNeeded();
        ClipBindingDialog.renderIfNeeded();
        ClipLengthDialog.renderIfNeeded();
    }

    private void renderTimelineTrackInteractions(
            TimelineCanvasLayout layout,
            float scrollY,
            float totalTracksHeight
    ) {
        ImGui.setCursorPos(0, 0);
        float buttonHeight = Math.max(layout.trackViewportHeight(), totalTracksHeight + 100f);
        ImGui.invisibleButton("##TimelineCanvas", layout.winW(), buttonHeight);
        boolean rightClickedOnCanvas = ImGui.isItemHovered() && ImGui.isMouseClicked(ImGuiMouseButton.Right);

        dropTargetTrackIndex = dragDropHandler.handleDragDropTarget(
                layout.trackRegionTop(), scrollY, layout.contentX(), renderer.getMath()
        );
        boolean actorRowClick = renderer.handleActorRowClick(layout.winX(), layout.contentX(),
                layout.winX() + layout.winW(), layout.trackRegionTop(), layout.trackViewportHeight(), scrollY);
        if (!actorRowClick) interactionHandler.handleCanvasInteractions(
                layout.contentX(), layout.trackRegionTop(),
                layout.contentW(), layout.trackViewportHeight(),
                scrollY, rightClickedOnCanvas
        );

        ImDrawList drawList = ImGui.getWindowDrawList();
        pushClipRectSafe(
                drawList,
                layout.winX(), layout.trackRegionTop(),
                layout.winX() + layout.winW(), layout.trackRegionTop() + layout.trackViewportHeight()
        );
        interactionHandler.renderMarqueeOverlay(drawList);
        drawList.popClipRect();

        interactionHandler.updateMenuState();
        TimelineContextMenu activeMenu = interactionHandler.getActiveContextMenu();
        if (activeMenu != null) {
            activeMenu.render();
        }
    }

    private void renderTimelinePlayheadOverlay(TimelineCanvasLayout layout, ImDrawList drawList) {
        float trackAreaBottom = layout.trackRegionTop() + layout.trackViewportHeight();
        pushClipRectSafe(
                drawList,
                layout.contentX(), layout.winY(),
                layout.contentX() + layout.contentW(), trackAreaBottom
        );
        renderPlaybackRangeGuides(
                drawList, layout.contentX(), layout.winY(), layout.contentW(), trackAreaBottom
        );
        renderer.renderPlayhead(
                drawList, layout.contentX(), layout.winY(), layout.contentW(), layout.availH()
        );
        drawList.popClipRect();
    }

    private void renderPlaybackRangeGuides(
            ImDrawList drawList,
            float contentX,
            float rulerTop,
            float contentW,
            float trackAreaBottom
    ) {
        if (model == null || contentW <= 0f) {
            return;
        }

        boolean hasIn = model.hasInPoint();
        boolean hasOut = model.hasOutPoint();
        if (!hasIn && !hasOut) {
            return;
        }

        float duration = model.getDuration();
        if (duration <= 0f) {
            return;
        }

        float inPoint = model.getInPoint();
        float outPoint = model.getOutPoint();
        boolean overlap = hasIn && hasOut && Math.abs(inPoint - outPoint) < 0.001f;

        if (hasIn) {
            renderPlaybackRangeGuide(
                    drawList,
                    contentX,
                    contentW,
                    rulerTop,
                    trackAreaBottom,
                    inPoint,
                    "IN",
                    true,
                    TimelineColors.rgba(80, 210, 120, 210),
                    0f
            );
        }

        if (hasOut) {
            renderPlaybackRangeGuide(
                    drawList,
                    contentX,
                    contentW,
                    rulerTop,
                    trackAreaBottom,
                    outPoint,
                    "OUT",
                    false,
                    TimelineColors.rgba(255, 185, 70, 210),
                    overlap ? (RANGE_TAG_HEIGHT + 2f) : 0f
            );
        }
    }

    private void renderPlaybackRangeGuide(
            ImDrawList drawList,
            float contentX,
            float contentW,
            float rulerTop,
            float trackAreaBottom,
            float time,
            String label,
            boolean placeRight,
            int lineColor,
            float tagYOffset
    ) {
        float x = renderer.timeToX(time, contentX);
        float maxX = contentX + contentW;
        if (x < contentX - 2f || x > maxX + 2f) {
            return;
        }

        float xAligned = (float) Math.floor(x) + 0.5f;

        // 竖线（阴影 + 主线），与播放头/吸附线风格一致。
        drawList.addRectFilled(xAligned - 1.5f, rulerTop, xAligned + 1.5f, trackAreaBottom, TimelineColors.rgba(0, 0, 0, 70));
        drawList.addRectFilled(xAligned - 0.5f, rulerTop, xAligned + 0.5f, trackAreaBottom, lineColor);

        float tagTop = rulerTop + 2f + tagYOffset;
        float tagBottom = tagTop + RANGE_TAG_HEIGHT;
        float tagLeft = placeRight
                ? xAligned + RANGE_TAG_GAP
                : xAligned - RANGE_TAG_GAP - RANGE_TAG_WIDTH;
        tagLeft = clamp(tagLeft, contentX, maxX - RANGE_TAG_WIDTH);
        float tagRight = tagLeft + RANGE_TAG_WIDTH;

        int tagBg = TimelineColors.adjustAlpha(lineColor, 55);
        int tagBorder = TimelineColors.adjustAlpha(lineColor, 200);
        drawList.addRectFilled(tagLeft, tagTop, tagRight, tagBottom, tagBg, 2.0f);
        drawList.addRect(tagLeft, tagTop, tagRight, tagBottom, tagBorder, 2.0f, 0, 1.0f);
        drawList.addText(tagLeft + 5f, tagTop + 2f, TimelineColors.COLOR_TEXT_LIGHT, label);
    }

    /**
     * 处理刻度尺区域交互（点击跳转播放头，拖动播放头）
     *
     * <p>功能：
     * <ul>
     *   <li>在底部三角上点击：播放头跳转到点击位置</li>
     *   <li>点击播放头手柄：开始拖动播放头</li>
     *   <li>拖动播放头：实时更新播放头位置</li>
     * </ul>
     *
     * <p>⭐ 关键修复 (v3.2)：
     * <ul>
     *   <li>左键按下时：通知 TimelineCameraController.beginScrubbing()</li>
     *   <li>左键松开时：通知 TimelineCameraController.endScrubbing()</li>
     *   <li>让"拖动播放头"状态与相机状态机同步，避免 PREVIEW/GAME 来回切换</li>
     * </ul>
     *
     * @param winY 窗口 Y 坐标
     * @param contentX 内容区域 X 起点（底部三角起点）
     * @param contentW 内容区域宽度
     */
    private void handleRulerInteractions(float winY, float contentX, float contentW) {
        // ⭐ v9.0 优化：添加可见性检查，防止布局异常
        // 如果内容区域宽度过小（可能窗口被压缩得很窄），不处理交互
        if (contentW < RULER_INTERACTION_MIN_WIDTH) {
            return;
        }

        float rulerHeight = TimelineRenderer.getRulerHeight();
        float rulerBottom = winY + rulerHeight;

        // 刻度尺区域（只包含内容区域，不包含左侧标签）
        float rulerX2 = contentX + contentW;

        // ⭐ v9.0 更新：手柄现在在刻度尺内（顶部与底部三角对齐），不需要扩展交互区域
        ImGui.setCursorScreenPos(contentX, winY);
        // ⭐ 优化：添加实例哈希避免 ID 冲突（虽然当前是单例，但为未来扩展预留）
        ImGui.invisibleButton("##TimelineRuler" + System.identityHashCode(this), contentW, rulerHeight);

        float mouseX = ImGui.getIO().getMousePosX();
        float mouseY = ImGui.getIO().getMousePosY();

        // 检查鼠标是否在刻度尺区域内
        boolean isInRulerArea = mouseX >= contentX && mouseX <= rulerX2 &&
                mouseY >= winY && mouseY <= rulerBottom;

        boolean hovered = ImGui.isItemHovered();

        // ⭐ 修复：区分点击时间标尺和拖动播放头
        if (ImGui.isMouseClicked(ImGuiMouseButton.Left) && (isInRulerArea || hovered)) {
            // 记录点击位置，用于后续判断是否真的拖动
            rulerClickStartX = mouseX;
            rulerClickStartY = mouseY;
            
            // 如果正在播放，先暂停（Facade 架构）
            // 通过 Facade 查询播放状态，然后通过 Facade 暂停
            if (commands != null && commands.playback() != null && commands.playback().isPlaying()) {
                commands.playback().pause();
            }
        }
        
        // 检测是否真的拖动（鼠标移动超过阈值）
        if (ImGui.isMouseDown(ImGuiMouseButton.Left) && rulerClickStartX >= 0f) {
            float deltaX = Math.abs(mouseX - rulerClickStartX);
            float deltaY = Math.abs(mouseY - rulerClickStartY);
            
            // 如果移动距离超过阈值，认为是拖动
            if (deltaX > DRAG_THRESHOLD || deltaY > DRAG_THRESHOLD) {
                // 开始拖动播放头（scrubbing）
                if (!model.isScrubbing()) {
                    interactionHandler.startPlayheadDrag(rulerClickStartX, contentX);
                }
                // 已经在拖动时，位置更新由 TimelineInteractionHandler.handleDrag() 处理
                rulerClickStartX = -1f; // 标记已进入拖动模式
            }
        }
        
        // 左键松开时处理
        if (ImGui.isMouseReleased(ImGuiMouseButton.Left)) {
            if (rulerClickStartX >= 0f) {
                // 如果点击位置还在，说明是点击而不是拖动
                float clickTime = renderer.xToTime(rulerClickStartX, contentX);
                // ⭐ Facade 架构：通过 Facade 统一处理 seek（包括时间更新、季节/天气/相机采样一致化）
                if (commands != null && commands.playback() != null) {
                    commands.playback().seek(clickTime);
                    LOGGER.debug("[TimelinePanel] 点击时间标尺，跳转到 {:.2f}s (通过 Facade)", clickTime);
                } else {
                    // 降级方案：使用原有的 handler（向后兼容）
                    interactionHandler.clickTimeRuler(rulerClickStartX, contentX);
                    LOGGER.debug("[TimelinePanel] 点击时间标尺，跳转到 {:.2f}s (降级方案)", clickTime);
                }
            }
            // 如果已经在拖动，endInteractionIfNeeded 会处理结束逻辑
            rulerClickStartX = -1f;
            rulerClickStartY = -1f;
        }
        
        // ⭐ 右键点击时间标尺：检测标记或打开空白菜单
        if (ImGui.isMouseClicked(ImGuiMouseButton.Right) && (isInRulerArea || hovered)) {
            // 先检测是否点击了标记
            com.director.timeline.model.TimelineMarker clickedMarker = hitTestMarker(mouseX, mouseY, contentX, winY, rulerHeight);
            if (clickedMarker != null) {
                // 点击了标记：开始拖动（右键按住拖动）
                draggedMarker = clickedMarker;
                markerDragStartX = mouseX;
                // 选择标记
                model.selectItem(clickedMarker, false);  // 单选标记
                // 同步到属性面板
                DirectorServices.get().uiSelection().setCurrentSelection(clickedMarker);
                LOGGER.debug("[TimelinePanel] 右键点击标记，开始拖动: {}", clickedMarker.getName());
            } else {
                // 没有点击标记：打开空白菜单（可以添加标记）
                float clickTime = renderer.xToTime(mouseX, contentX);
                // 创建并打开空白菜单
                TimelineContextMenuFactory menuFactory = TimelineContextMenuFactory.forPanel(this, model, commandHistory);
                TimelineContextMenu menu = menuFactory.createEmptyAreaMenu(clickTime);
                if (menu != null) {
                    menu.open();
                }
            }
        }
        
        // ⭐ 右键拖动标记
        if (ImGui.isMouseDown(ImGuiMouseButton.Right) && draggedMarker != null) {
            // 检测是否真的拖动（移动距离超过阈值）
            float deltaX = Math.abs(mouseX - markerDragStartX);
            if (deltaX > DRAG_THRESHOLD) {
                // 开始拖动标记
                if (!interactionHandler.isDraggingMarker()) {
                    interactionHandler.startMarkerDrag(draggedMarker, markerDragStartX, contentX);
                }
                // 更新拖动位置
                interactionHandler.updateMarkerDrag(mouseX, contentX);
            }
        }
        
        // ⭐ 右键松开：结束拖动或打开属性面板
        if (ImGui.isMouseReleased(ImGuiMouseButton.Right)) {
            if (draggedMarker != null) {
                float deltaX = Math.abs(mouseX - markerDragStartX);
                if (deltaX > DRAG_THRESHOLD) {
                    // 是拖动：结束拖动
                    interactionHandler.endMarkerDrag();
                } else {
                    // 是点击：打开属性面板（已在点击时处理）
                }
                draggedMarker = null;
                markerDragStartX = -1f;
            }
        }
    }
    
    /**
     * 检测鼠标是否点击了标记
     */
    private com.director.timeline.model.TimelineMarker hitTestMarker(
            float mouseX, float mouseY, float contentX, float rulerTop, float rulerHeight) {
        if (model == null) {
            return null;
        }
        
        float centerY = rulerTop + rulerHeight * 0.5f;
        float hitRadius = 6.0f;  // 6px 半径
        
        java.util.List<com.director.timeline.model.TimelineMarker> markers = model.getMarkers();
        if (markers == null || markers.isEmpty()) {
            return null;
        }
        
        for (com.director.timeline.model.TimelineMarker marker : markers) {
            if (marker == null) continue;
            
            float markerTime = marker.getTime();
            float mx = renderer.timeToX(markerTime, contentX);  // ⭐ 修复：应该是 timeToX 而不是 xToTime
            float mxAligned = (float) Math.floor(mx) + 0.5f;
            
            // 计算距离
            float dx = mouseX - mxAligned;
            float dy = mouseY - centerY;
            float distSq = dx * dx + dy * dy;
            
            if (distSq <= hitRadius * hitRadius) {
                return marker;  // 命中标记
            }
        }
        
        return null;
    }

    /**
     * 双击处理关键帧添加/删除
     *
     * <p>边缘检查：
     * <ul>
     *   <li>检查轨道是否锁定</li>
     *   <li>检查片段是否有值</li>
     * </ul>
     */
    private void handleKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
        if (clip == null) {
            LOGGER.warn("[TimelinePanel] ????????????????????????????????????");
            return;
        }

        TimelineTrack track = model.findTrackForClip(clip);
        if (track != null && track.isLocked()) {
            LOGGER.debug("[TimelinePanel] ??????????????????????????????????????? {}", track.getName());
            return;
        }

        final float clickTime = clamp(model.screenToTime(mouseX, contentX),
                clip.getStartTime(), clip.getEndTime());

        if (clip instanceof com.director.lighting.LightTimelineClip) {
            com.director.ui.lighting.PointLightParameterEditing.finish();
            var existing=clip.findKeyframeAt(clickTime,KEYFRAME_TOLERANCE);
            if(existing!=null) commandHistory.executeCommand(new RemoveClipKeyframeCommand(model,track,clip,existing));
            else {
                var command=new AddKeyframeCommand(model,clip,clickTime);commandHistory.executeCommand(command);
                var key=command.getLightKeyframe();
                if(key!=null) {
                    model.setPlaying(false);model.setCurrentTime(key.getTime());model.selectItem(key,false);
                    com.director.core.DirectorServices.get().uiSelection().setSelection(com.director.ui.selection.SelectionType.KEYFRAME,key);
                }
            }
            markDataChanged();return;
        }

        if (track != null && track.getType() == TrackType.AUDIO) {
            com.director.timeline.model.clip.ClipKeyframe existing =
                    clip.findKeyframeAt(clickTime, KEYFRAME_TOLERANCE);
            if (existing != null) {
                RemoveClipKeyframeCommand cmd = new RemoveClipKeyframeCommand(model, track, clip, existing);
                commandHistory.executeCommand(cmd);
                markDataChanged();
                LOGGER.debug("[TimelinePanel] ????????????????????? at {}s (??????: {})",
                        String.format("%.2f", existing.getTime()), clip.getLabel());
            } else {
                float volume = com.director.ui.timeline.audio.TimelineAudioService.getInstance()
                        .getVolumeAtTimeForClip(clip, clickTime);
                AudioClipKeyframe audioKeyframe = new AudioClipKeyframe(clickTime, TimelineInterpolation.LINEAR, volume);
                AddClipKeyframeCommand cmd = new AddClipKeyframeCommand(model, track, clip, audioKeyframe);
                commandHistory.executeCommand(cmd);
                markDataChanged();
                LOGGER.debug("[TimelinePanel] ????????????????????? at {}s (??????: {})",
                        String.format("%.2f", clickTime), clip.getLabel());
            }
            return;
        }

        // ⭐ 特殊处理 EntityTimelineClip：创建 EntityClipKeyframe
        if (clip instanceof com.director.timeline.model.entity.EntityTimelineClip entityTimelineClip) {
            com.director.timeline.model.entity.EntityClip entityClip = entityTimelineClip.getEntityClip();
            
            // 查找是否已存在关键帧
            com.director.timeline.model.entity.EntityClipKeyframe existing = 
                entityClip.findKeyframeAt(clickTime, KEYFRAME_TOLERANCE);
            
            if (existing != null) {
                // 删除关键帧
                entityClip.removeKeyframe(existing.getId());
                markDataChanged();
                LOGGER.debug("[TimelinePanel] 删除 EntityClipKeyframe at {}s (片段: {})",
                        String.format("%.2f", clickTime), clip.getLabel());
            } else {
                // 创建 EntityClipKeyframe
                EntityClipKeyframe keyframe = getEntityClipKeyframe(clickTime, track);

                entityClip.addKeyframe(keyframe);
                markDataChanged();
                LOGGER.debug("[TimelinePanel] 添加 EntityClipKeyframe at {}s (片段: {})",
                        String.format("%.2f", clickTime), clip.getLabel());
            }
            return;
        }

        Float existingKf = clip.getKeyframeAt(clickTime, KEYFRAME_TOLERANCE);

        if (existingKf != null) {
            RemoveKeyframeCommand cmd = new RemoveKeyframeCommand(model, clip, existingKf);
            commandHistory.executeCommand(cmd);
            markDataChanged();
            LOGGER.debug("[TimelinePanel] ???????????????at {}s (??????: {})",
                    String.format("%.2f", existingKf), clip.getLabel());
        } else {
            AddKeyframeCommand cmd = new AddKeyframeCommand(model, clip, clickTime);
            commandHistory.executeCommand(cmd);
            markDataChanged();
            LOGGER.debug("[TimelinePanel] ???????????????at {}s (??????: {})",
                    String.format("%.2f", clickTime), clip.getLabel());
        }
    }

    private static @NotNull EntityClipKeyframe getEntityClipKeyframe(float clickTime, TimelineTrack track) {
        EntityClipKeyframe keyframe =
            new EntityClipKeyframe(clickTime);

        // 采样当前状态作为初始值
        if (track != null && track.getType() == TrackType.ACTOR) {
            com.director.timeline.model.entity.ActorTrack actorTrack =
                track.getActorTrackPayload();
            if (actorTrack != null) {
                com.director.timeline.playback.entity.EntityClipSampler sampler =
                    new com.director.timeline.playback.entity.EntityClipSampler(actorTrack);
                com.director.timeline.playback.entity.EntitySample sample =
                    sampler.sampleAt(clickTime);
                if (sample != null && sample.enabled) {
                    keyframe.setPosition(sample.position.x, sample.position.y, sample.position.z);
                    keyframe.setRotation(sample.rotation.x, sample.rotation.y, sample.rotation.z);
                    keyframe.setScale(sample.scale.x, sample.scale.y, sample.scale.z);
                }
            }
        }
        return keyframe;
    }

    private void renderInlineEdit(float winX, float trackRegionTop, float scrollY) {
        if (!TrackRenameDialog.isEditingAny()) {
            return;
        }

        TimelineTrack editingTrack = TrackRenameDialog.getEditingTrack();
        if (editingTrack == null) {
            TrackRenameDialog.finishInlineEdit(false);
            return;
        }

        int trackIndex = model.getTracks().indexOf(editingTrack);
        if (trackIndex < 0) {
            TrackRenameDialog.finishInlineEdit(false);
            return;
        }

        float rowHeight = TimelineRenderer.getTrackHeight() + TimelineRenderer.getTrackGap();
        float trackY = trackRegionTop - scrollY + renderer.getMath().trackContentY(trackIndex);

        // ⭐ v9.0 优化：使用常量代替魔法数值，便于后期 UI 微调
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
     * 剪切选中的片段（快捷键，供 Facade 调用）
     */
    public void cutSelected() {
        if(renderer.handleActorKeyCommand("cut")) return;
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;

        TimelineTrack track = model.findTrackForClip(selectedClip);
        if (track != null) {
            clipboard.cut(selectedClip, track, model, commandHistory);
            markDataChanged();
        }
    }

    /**
     * 复制选中的片段（快捷键，供 Facade 调用）
     */
    public void copySelected() {
        if(renderer.handleActorKeyCommand("copy")) return;
        TimelineClip selectedClip = model.getSelectedClip();
        if (selectedClip == null) return;

        clipboard.copy(selectedClip);
    }

    /**
     * 粘贴片段（快捷键，供 Facade 调用）
     */
    public void paste() {
        if(renderer.handleActorKeyCommand("paste")) return;
        clipboard.paste(model);
        markDataChanged();
    }

    /**
     * 删除选中的项（公共方法，供 Facade 调用）
     */
    public void deleteSelected() {
        if(renderer.handleActorKeyCommand("delete")) return;
        java.util.List<Object> items = model.getSelectedItems();
        if (items == null || items.isEmpty()) {
            return;
        }

        // ✅ 一次性删除应该只产生一条历史记录，撤销也只需一步
        commandHistory.executeCommand(new com.director.ui.timeline.commands.BatchDeleteSelectionCommand(model, items));

        model.clearSelection();
        DirectorServices.get().uiSelection().clearSelection();
        markDataChanged();
    }

    /**
     * 执行时间线命令（公共方法，供 Facade 调用）。
     */
    public void executeCommand(TimelineCommand command) {
        if (command == null) {
            return;
        }
        commandHistory.executeCommand(command);
        markDataChanged();
    }
    
    /**
     * 撤销最近一次操作（公共方法，供 Facade 调用）
     */
    public void undo() {
        commandHistory.undo();
    }
    
    /**
     * 重做上一次撤销的操作（公共方法，供 Facade 调用）
     */
    public void redo() {
        commandHistory.redo();
    }


    // ========== 回调接口实现（TimelineInteractionHandler.InteractionCallbacks） ==========

    @Override
    public void onKeyframeDoubleClick(TimelineClip clip, float mouseX, float contentX) {
        handleKeyframeDoubleClick(clip, mouseX, contentX);
    }

    @Override
    public void onClipInteractionFinished(TimelineClip clip, float originalStartTime, float originalEndTime) {
        // ⭐ 修复：使用显式传参 old 值构造函数
        // originalStartTime/originalEndTime 是拖动开始时的值（旧值）
        // clip.getStartTime()/getEndTime() 是拖动结束时的值（新值）
        float currentStartTime = clip.getStartTime();
        float currentEndTime = clip.getEndTime();

        // 只有位置真正改变时才创建命令
        if (Math.abs(currentStartTime - originalStartTime) > 0.001f ||
                Math.abs(currentEndTime - originalEndTime) > 0.001f) {
            // ⭐ 修复：使用显式传参 old 值构造函数，避免从已修改的 clip 中读取错的值
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
                LOGGER.debug("[TimelinePanel] 片段位置未修改，跳过命令创建");
            }
        }
    }

    @Override
    public void onDataChanged() {
        markDataChanged();
    }

    @Override
    public void setHoveredTrack(TimelineTrack track) {
        // 留空接口实现，但不再存储状态（由 InteractionHandler 内部管理）
    }

    public void requestClipRename(TimelineClip clip) {
        ClipRenameDialog.open(clip);
    }

    public void requestClipContainerBinding(TimelineClip clip) {
        ClipBindingDialog.open(clip);
    }


    @Override
    public void onAddKeyframeToTrack(TimelineTrack track) {
        if (track == null) {
            return;
        }

        TrackType type = track.getType();
        if (type == TrackType.CONTAINER) {
            LOGGER.warn("[TimelinePanel] 容器轨道不支持轨道级关键帧，请使用片段内的动画配置 (轨道: {})", track.getName());
            return;
        }

        float time = clamp(model.getCurrentTime(), 0f, model.getDuration());
        String timeLabel = String.format("%.2f", time);

        if (tryAddCameraKeyframe(track, time, timeLabel)
                || tryAddWeatherKeyframe(track, time, timeLabel)
                || tryAddTimeKeyframe(track, time, timeLabel)
                || tryAddEntityKeyframe(track, time, timeLabel)
                || tryAddSeasonKeyframe(track, time, timeLabel)) {
            return;
        }

        addClipKeyframeAtPlayhead(track, time);
        markDataChanged();
    }

    private void executeAutomationKeyframeAdd(
            TimelineTrack track,
            TimelineSelectable keyframe,
            Runnable rebuildSampler
    ) {
        commandHistory.executeCommand(new AddAutomationKeyframeCommand(model, track, keyframe));
        rebuildSampler.run();
        markDataChanged();
    }

    private boolean tryAddCameraKeyframe(TimelineTrack track, float time, String timeLabel) {
        if (!track.getType().supportsCameraPath()) {
            return false;
        }

        TimelineCameraController cameraController = TimelineCameraController.getInstance();
        if (!cameraController.isAddKeyframeTargetValid(track)) {
            return true;
        }
        if (!CameraRuntime.getInstance().isPlayerOwner()) {
            LOGGER.warn("[TimelinePanel] 当前非玩家模式，跳过摄像机关键帧捕捉");
            return true;
        }

        CustomPathClip customPathClip = track.findCustomPathClipAt(time);
        if (customPathClip == null) {
            LOGGER.warn(
                    "[TimelinePanel] 摄像机关键帧只能添加到自定义路径片段内，请先创建自定义路径片段 (轨道: {})",
                    track.getName()
            );
            return true;
        }

        CameraKeyframe keyframe = new CameraKeyframe("Camera @" + timeLabel, time);
        if (!cameraController.captureFromCurrentCamera(keyframe)) {
            LOGGER.warn("[TimelinePanel] 摄像机状态捕获失败，取消添加关键帧");
            return true;
        }

        commandHistory.executeCommand(new AddCameraPathKeyframeCommand(model, customPathClip, keyframe));
        markDataChanged();
        return true;
    }

    private boolean tryAddWeatherKeyframe(TimelineTrack track, float time, String timeLabel) {
        if (!track.getType().supportsWeatherControl()) {
            return false;
        }

        TimelineWeatherController weatherController = TimelineWeatherController.getInstance();
        if (!weatherController.isAddKeyframeTargetValid(track)) {
            return true;
        }

        executeAutomationKeyframeAdd(
                track,
                new WeatherKeyframe("Weather @" + timeLabel, time),
                weatherController::rebuildFromActiveTrack
        );
        return true;
    }

    private boolean tryAddTimeKeyframe(TimelineTrack track, float time, String timeLabel) {
        if (!track.getType().supportsTimeControl()) {
            return false;
        }

        TimelineWorldController timeController = TimelineWorldController.getInstance();
        if (!timeController.isAddKeyframeTargetValid(track)) {
            return true;
        }

        executeAutomationKeyframeAdd(
                track,
                getTimeKeyframe(timeLabel, time),
                timeController::rebuildFromActiveTrack
        );
        return true;
    }

    private boolean tryAddEntityKeyframe(TimelineTrack track, float time, String timeLabel) {
        if (track.getType() != TrackType.ACTOR) return false;
        var key = com.director.ui.actor.pose.ActorRootKeyframeEditing.insertAtPlayhead(model, track);
        if (key != null) markDataChanged();
        else LOGGER.debug("[TimelinePanel] 当前演员片段不可写入整体关键帧，请检查播放状态、锁定和路径控制");
        return true;
    }

    private boolean tryAddSeasonKeyframe(TimelineTrack track, float time, String timeLabel) {
        if (!track.getType().supportsSeasonControl()) {
            return false;
        }

        TimelineSeasonController seasonController = TimelineSeasonController.getInstance();
        if (!seasonController.isAddKeyframeTargetValid(track)) {
            return true;
        }

        executeAutomationKeyframeAdd(
                track,
                new SeasonKeyframe("Season @" + timeLabel, time),
                seasonController::rebuildFromActiveTrack
        );
        return true;
    }

    private void addClipKeyframeAtPlayhead(TimelineTrack track, float time) {
        TimelineClip targetClip = track.findClipAtTime(time);
        if (targetClip == null) {
            TimelineClip selected = model.getSelectedClip();
            if (selected != null && model.findTrackForClip(selected) == track) {
                targetClip = selected;
            }
        }

        if (targetClip == null) {
            LOGGER.warn(
                    "[TimelinePanel] Failed to add keyframe: track {} has no editable clip at current time",
                    track.getName()
            );
            return;
        }

        float clampedTime = clamp(time, targetClip.getStartTime(), targetClip.getEndTime());
        if(targetClip instanceof com.director.lighting.LightTimelineClip) {
            if(time<targetClip.getStartTime() || time>targetClip.getEndTime()) return;
            com.director.ui.lighting.PointLightParameterEditing.finish();
            var command=new AddKeyframeCommand(model,targetClip,clampedTime);commandHistory.executeCommand(command);
            var key=targetClip.findKeyframeAt(clampedTime,1e-4f);
            if(key!=null) {
                model.setPlaying(false);model.selectItem(key,false);
                com.director.core.DirectorServices.get().uiSelection().setSelection(com.director.ui.selection.SelectionType.KEYFRAME,key);
            }
        } else commandHistory.executeCommand(new AddKeyframeCommand(model, targetClip, clampedTime));
    }

    private static @NotNull TimeKeyframe getTimeKeyframe(String timeLabel, float time) {
        MinecraftClient client = MinecraftClient.getInstance();
        long currentWorldTime = 6000L; // 默认正午
        if (client != null && client.world != null) {
            currentWorldTime = client.world.getTimeOfDay();
        }

        TimeKeyframe timeKeyframe =
            new TimeKeyframe("Time @" + timeLabel, time);
        // 只记录时间-of-day（0~23999），避免保存世界总 tick 导致插值 diff 巨大引发闪烁
        timeKeyframe.setWorldTimeTicks(currentWorldTime % 24000L);
        return timeKeyframe;
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


    // ========== 回调接口实现（TimelineControlBar.Callbacks） ==========

    @Override
    public void onAddTrackClicked() {
        // 委托给 TrackController
        trackController.openMenu();
    }

    @Override
    public void onDeleteSelectedClicked() {
        deleteSelected();
    }

    @Override
    public void onFitToViewClicked() {
        // 委托给 NavigationHandler
        float availableWidth = ImGui.getContentRegionAvailX() - TimelineRenderer.getLeftLabelWidth();
        navigationHandler.fitToView(availableWidth);
    }
    
    /**
     * 设置命令门面（供依赖注入使用）
     */
    @Override
    public void setCommands(com.director.ui.commands.UICommandFacade commands) {
        this.commands = commands;
        // 同时传递给 TimelineControlBar（如果已创建）
        if (controlBar != null) {
            controlBar.setCommands(commands);
        }
    }
    
    /**
     * 滚动到播放头位置（不缩放，只调整滚动位置）
     * 供命令门面调用
     */
    public void scrollToPlayhead() {
        if (navigationHandler == null) {
            return;
        }
        
        // 动态计算内容区域（如果缓存值无效，使用当前窗口尺寸）
        float contentX;
        float contentW;
        
        if (cachedContentW > 0) {
            // 使用缓存值（更准确，因为是在渲染时计算的）
            contentX = cachedContentX;
            contentW = cachedContentW;
        } else {
            // 兜底：使用当前窗口尺寸
            float winW = ImGui.getContentRegionAvailX();
            contentX = TimelineRenderer.getLeftLabelWidth();
            contentW = winW - TimelineRenderer.getLeftLabelWidth();
        }
        
        if (contentW > 0) {
            navigationHandler.ensurePlayheadVisible(contentX, contentW, PLAYHEAD_MARGIN);
        }
    }
    
    /**
     * 缩放到适应视图（显示所有内容）
     * 供命令门面调用
     */
    public void zoomToFit() {
        if (navigationHandler != null) {
            float availableWidth = ImGui.getContentRegionAvailX() - TimelineRenderer.getLeftLabelWidth();
            navigationHandler.fitToView(availableWidth);
        }
    }

    // ========== 工具方法 ==========

    private float clamp(float v, float a, float b) {
        return Math.max(a, Math.min(b, v));
    }

    @Override
    public int getPriority() {
        return 200;
    }
    
    /**
     * 获取 TimelineModel（供命令门面使用）
     * 
     * @return TimelineModel 实例
     */
    @Override
    public TimelineModel getModel() {
        return model;
    }
    
    /**
     * 获取 TimelineModel（别名方法，供外部统一访问）
     * 与 getModel() 等价，提供更明确的语义
     */
    public TimelineModel getTimelineModel() {
        return model;
    }

    // ========== 容器监听器接口实现 ===========

    @Override
    public void onContainerAdded(Container container) {
        // 容器添加时不需要特殊处理
    }

    @Override
    public void onContainerRemoved(Container container) {
        // 容器删除时已在DeleteContainersCommand中删除了对应的片段
    }

    @Override
    public void onContainerMoved(Container container, Container newParent) {
        // 容器移动时不需要特殊处理
    }

    @Override
    public void onSelectionChanged(java.util.Set<Container> selected) {
        // 容器选择变更时不需要特殊处理
    }

    @Override
    public void onContainerRenamed(Container container, String oldName, String newName) {
        // ⭐ 功能：容器重命名时，同步到时间线上的片段
        if (container == null || newName == null) {
            return;
        }

        var timelineService = DirectorServices.get().containerTimeline();
        timelineService.updateClipNamesForContainer(container.getId(), newName);
        markDataChanged();

        LOGGER.info("[TimelinePanel] 容器 {} 重命名已同步到时间线片段: {} -> {}",
                container.getName(), oldName, newName);
    }

    @Override
    public void onActiveContainerChanged(Container oldActive, Container newActive) {
        // 激活容器变更时不需要特殊处理
    }
}
