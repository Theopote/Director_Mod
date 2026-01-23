package com.chronoblocks.ui;

import com.chronoblocks.ui.panels.IPanel;
import com.chronoblocks.ui.panels.AbstractPanel;
import com.chronoblocks.ui.panels.MenuBarPanel;
import com.chronoblocks.ui.panels.StatusBarPanel;
import com.chronoblocks.ui.panels.ToolbarPanel;
import com.chronoblocks.ui.panels.TimelinePanel;
import com.chronoblocks.ui.panels.PropertiesPanel;
import com.chronoblocks.ui.panels.ContainerPanel;
import com.chronoblocks.ui.di.UIConfiguration;
import com.chronoblocks.ui.di.IPanelRegistry;
import com.chronoblocks.ui.di.PanelRegistry;
import com.chronoblocks.ui.layout.LayoutPersistenceManager;
import com.chronoblocks.ui.aware.IPanelRegistryAware;
import com.chronoblocks.ui.aware.IUIManagerAware;
import com.chronoblocks.ui.imgui.ImGuiUtils;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.properties.PropertyAdapterBootstrap;
import com.chronoblocks.export.video.VideoExportManager;
import com.chronoblocks.export.video.VideoExportSettings;
import com.chronoblocks.export.video.VideoRenderPipeline;
import com.chronoblocks.ui.video.VideoExportDialog;
import com.chronoblocks.ui.video.VideoExportProgressOverlay;
import com.chronoblocks.ui.timeline.model.camera.CameraViewportProfile;
import com.chronoblocks.ui.timeline.model.camera.ViewportMath;
import com.chronoblocks.ui.timeline.model.TimelineTrack;
import com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController;
import com.chronoblocks.ui.timeline.render.TimelineColors;
import com.chronoblocks.ui.commands.UICommandFacade;
import com.chronoblocks.ui.commands.ProjectCommands;
import com.chronoblocks.ui.commands.TimelineCommands;
import com.chronoblocks.ui.commands.PlaybackCommands;
import com.chronoblocks.ui.commands.CameraCommands;
import com.chronoblocks.ui.commands.ViewCommands;
import com.chronoblocks.ui.commands.LayoutCommands;
import com.chronoblocks.ui.commands.HelpCommands;
import com.chronoblocks.ui.commands.ToolCommands;
import com.chronoblocks.core.playback.PlaybackController;
import com.chronoblocks.ui.timeline.model.camera.path.CameraPathType;
import com.chronoblocks.ui.timeline.model.TimelineModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * ChronoBlocks UI Manager - 核心UI管理器
 * <p>
 * 这是 ChronoBlocks 动画编辑器的核心UI管理器，负责协调和管理所有UI面板的渲染和交互。
 * 采用现代化的依赖注入架构，提供高度可扩展和可维护的UI系统。
 *
 * <h3>核心功能特性：</h3>
 * <ul>
 *   <li><strong>DockSpace布局系统</strong> - 基于ImGui的现代化停靠布局，专业建模软件风格，支持面板拖拽和停靠，防止ImGui状态泄漏</li>
 *   <li><strong>现代化IoC系统</strong> - 基于类型的面板注册表，支持类型安全的依赖注入</li>
 *   <li><strong>线程安全设计</strong> - 使用CopyOnWriteArrayList和ConcurrentHashMap确保多线程环境下的安全性</li>
 *   <li><strong>动态面板管理</strong> - 支持运行时添加、移除面板，完整的生命周期管理</li>
 *   <li><strong>智能渲染管道</strong> - 统一面板渲染循环，按优先级排序渲染，支持性能优化</li>
 *   <li><strong>完整面板生命周期管理</strong> - 自动初始化、状态验证、错误处理、增强的资源清理</li>
 *   <li><strong>分级错误处理</strong> - 关键面板缺失时抛出异常，重要面板缺失时提供降级UI</li>
 *   <li><strong>降级UI机制</strong> - 为缺失的重要面板提供友好的用户反馈和功能提示</li>
 *   <li><strong>调试和监控</strong> - 内置调试窗口、性能监控和面板状态报告功能</li>
 * </ul>
 *
 *
 * <h3>现代化IoC使用示例（推荐方式）：</h3>
 * <pre>{@code
 * // 方式1：使用UIConfiguration创建（强烈推荐）
 * UIConfiguration config = new UIConfiguration(minecraftClient);
 * ChronoBlocksUIManager uiManager = config.createUIManagerWithRegistry();
 *
 * // 方式2：直接使用面板注册表（高级用法）
 * IPanelRegistry registry = new PanelRegistry(panels);
 * ChronoBlocksUIManager uiManager = new ChronoBlocksUIManager(registry);
 *
 * // 方式3：使用面板列表（标准用法）
 * List<IPanel> panels = Arrays.asList(
 *     new MenuBarPanel(),
 *     new ToolbarPanel(),
 *     new TimelinePanel(),
 *     new PropertiesPanel(),
 *     new ContainerPanel()
 * );
 * ChronoBlocksUIManager uiManager = new ChronoBlocksUIManager(panels);
 *
 * // 类型安全的面板访问（通过面板注册表）
 * MenuBarPanel menuBar = uiManager.getPanelRegistry().getPanel(MenuBarPanel.class);
 *
 * // 在主线程中渲染
 * uiManager.render();
 *
 * // 动态面板管理（线程安全）
 * uiManager.getPanelRegistry().addPanel(new CustomPanel());
 * uiManager.getPanelRegistry().removePanel(oldPanel);
 * // 或通过类型移除
 * uiManager.getPanelRegistry().removePanel(CustomPanel.class);
 *
 * // 验证面板状态
 * PanelStatusReport report = uiManager.validatePanelStatus();
 * if (!report.isHealthy()) {
 *     LOGGER.warn("面板状态异常: {}", report.getSummary());
 *     LOGGER.debug("详细报告:\n{}", report.getDetailedReport());
 * }
 * }</pre>
 *
 * <h3>⚠️ 重要说明：</h3>
 * <ul>
 *   <li><strong>默认构造函数已移除</strong> - 使用 {@code new ChronoBlocksUIManager()} 会抛出 {@code UnsupportedOperationException}</li>
 *   <li><strong>强制依赖注入</strong> - 所有面板必须通过构造函数注入，确保类型安全和完整性</li>
 *   <li><strong>推荐使用UIConfiguration</strong> - 自动处理面板创建、配置和依赖关系</li>
 *   <li><strong>面板注册表O(1)查找</strong> - 移除私有字段缓存，直接使用 {@code panelRegistry.getPanel(Class)}</li>
 *   <li><strong>线程安全渲染</strong> - 使用CopyOnWriteArrayList防止ConcurrentModificationException</li>
 *   <li><strong>统一渲染循环</strong> - 简化面板渲染逻辑，易于添加新面板，避免重复null检查</li>
 *   <li><strong>ImGui状态管理</strong> - 使用try-finally确保样式变量和颜色正确弹出，防止状态泄漏</li>
 *   <li><strong>增强资源管理</strong> - 关键错误传播，状态一致性保证，失败率监控</li>
 * </ul>
 * 
 * <h3>🚫 架构铁律（必须遵守）：</h3>
 * <p>
 * <strong>UIManager must not execute playback or timeline commands.</strong>
 * </p>
 * <p>
 * UIManager 类本身禁止包含以下方法：
 * </p>
 * <ul>
 *   <li>{@code public void play()}</li>
 *   <li>{@code public void pause()}</li>
 *   <li>{@code public void stop()}</li>
 *   <li>任何直接操作 TimelineModel、BlockAnimationEngine、PlaybackController 的方法</li>
 * </ul>
 * <p>
 * 这些方法只能存在于：
 * </p>
 * <ul>
 *   <li>{@code UICommandFacadeImpl} 内部类（命令门面实现）</li>
 *   <li>{@code Engine / Controller} 类（业务逻辑层）</li>
 * </ul>
 * <p>
 * 这是防止架构退化的"保险丝"。
 * </p>
 *
 * <h3>DockSpace布局说明：</h3>
 * <p>
 * ChronoBlocks采用专业建模软件风格的DockSpace布局：
 * </p>
 * <ul>
 *   <li><strong>左侧容器面板</strong> - 占据屏幕宽度的15%</li>
 *   <li><strong>右侧属性面板</strong> - 占据屏幕宽度的20%</li>
 *   <li><strong>顶部工具面板</strong> - 占据屏幕高度的10%</li>
 *   <li><strong>底部时间线面板</strong> - 占据屏幕高度的15%</li>
 *   <li><strong>中央工作区</strong> - 保持65%宽度、75%高度的透明区域，直接透出Minecraft游戏画面</li>
 * </ul>
 * <p>
 * 布局创建顺序确保中央场景视图优先定义，其他面板向其停靠，提供稳定的用户体验。
 * </p>
 *
 * <h3>面板生命周期管理：</h3>
 * <p>
 * ChronoBlocks提供完整的面板生命周期管理：
 * </p>
 * <ol>
 *   <li><strong>创建阶段</strong> - UIConfiguration创建面板实例</li>
 *   <li><strong>注入阶段</strong> - ChronoBlocksUIManager接收面板列表</li>
 *   <li><strong>初始化阶段</strong> - 自动调用所有面板的initialize()方法</li>
 *   <li><strong>渲染阶段</strong> - 按优先级调用面板的render()方法</li>
 *   <li><strong>状态验证</strong> - 支持运行时面板健康状态检查</li>
 *   <li><strong>清理阶段</strong> - 自动调用所有面板的dispose()方法</li>
 * </ol>
 * <p>
 * 动态面板管理支持运行时添加/移除面板，新面板会自动初始化，移除的面板会自动清理资源。
 * </p>
 *
 * <h3>IoC系统架构：</h3>
 * <p>
 * ChronoBlocks采用现代化的依赖注入系统：
 * </p>
 * <ul>
 *   <li><strong>IPanelRegistry</strong> - 面板注册表接口，提供类型安全的面板管理</li>
 *   <li><strong>PanelRegistry</strong> - 基于ConcurrentHashMap的线程安全实现，支持动态面板管理</li>
 *   <li><strong>类型安全查找</strong> - 通过面板类型直接获取实例，避免类型转换</li>
 *   <li><strong>多态支持</strong> - 自动注册接口类型，支持多态查找</li>
 *   <li><strong>性能优化</strong> - O(1)查找性能，支持高频访问</li>
 * </ul>
 * <p>
 * 相比传统的手动面板查找，IoC系统提供了更好的类型安全性和性能。
 * </p>
 *
 * @author ChronoBlocks Team
 * @version 2.0
 * @since 1.0
 */
public class ChronoBlocksUIManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChronoBlocksUIManager.class);

    /**
     * 通用信息日志方法 - 使用SLF4J流畅API优化性能
     * @param format 日志消息模板
     * @param args 参数
     */
    private void logInfo(String format, Object... args) {
        LOGGER.atInfo()
              .setMessage("[ChronoBlocks] " + format)
              .addArgument(args)
              .log();
    }

    /**
     * 通用警告日志方法 - 使用SLF4J流畅API优化性能
     * @param format 警告消息模板
     * @param args 参数
     */
    private void logWarn(String format, Object... args) {
        LOGGER.atWarn()
              .setMessage("[ChronoBlocks] " + format)
              .addArgument(args)
              .log();
    }

    /**
     * 通用错误日志方法 - 使用SLF4J流畅API优化性能
     * @param format 错误消息模板
     * @param throwable 异常对象
     * @param args 参数
     */
    private void logError(String format, Throwable throwable, Object... args) {
        LOGGER.atError()
              .setMessage("[ChronoBlocks] " + format)
              .addArgument(args)
              .setCause(throwable)
              .log();
    }

    /**
     * 通用调试日志方法 - 使用SLF4J流畅API优化性能
     * @param format 调试消息模板
     * @param args 参数
     */
    private void logDebug(String format, Object... args) {
        LOGGER.atDebug()
              .setMessage("[ChronoBlocks] " + format)
              .addArgument(args)
              .log();
    }

    /**
     * 延迟调试日志方法 - 使用lambda表达式延迟执行复杂计算
     * @param messageSupplier 消息提供者
     */
    private void logDebugState(java.util.function.Supplier<String> messageSupplier) {
        LOGGER.atDebug().log(() -> "[ChronoBlocks] " + messageSupplier.get());
    }

    // 面板管理 - 使用现代化的面板注册表
    private final IPanelRegistry panelRegistry;

    // 布局状态标志 - 每次创建新的UIManager时都重置为true
    private boolean isFirstLayout = true;

    // 状态管理
    private boolean isDisposed = false;
    
    // 布局持久化管理器
    private final LayoutPersistenceManager layoutManager;
    
    // 对话框管理器
    private final com.chronoblocks.ui.dialogs.DialogManager dialogManager;

    // 性能监控
    private long renderCount = 0;
    private long lastPerformanceLog = 0;
    private static final long PERFORMANCE_LOG_INTERVAL = 60000; // 1分钟

    // ========== 视频导出 UI（v5.2）==========
    private final VideoExportProgressOverlay videoExportProgressOverlay = new VideoExportProgressOverlay();
    
    // ========== 动画系统更新（v4.5）==========
    private long lastUpdateTime = System.currentTimeMillis();

    // DockSpace相关常量
    private static final int DOCKSPACE_FLAGS = imgui.flag.ImGuiWindowFlags.NoTitleBar |
                                             imgui.flag.ImGuiWindowFlags.NoCollapse |
                                             imgui.flag.ImGuiWindowFlags.NoBringToFrontOnFocus |
                                             imgui.flag.ImGuiWindowFlags.NoNavFocus |
                                             imgui.flag.ImGuiWindowFlags.NoBackground;

    private static final String DOCKSPACE_WINDOW_NAME = "DockSpace";
    private static final String DOCKSPACE_ID = "ChronoBlocksDockSpace";

    /**
     * 默认构造函数 - 已移除
     * <p>
     * <strong>⚠️ 此构造函数已被移除，请使用依赖注入构造函数！</strong><br>
     * 使用 {@link UIConfiguration} 创建面板列表，然后调用 {@link #ChronoBlocksUIManager(List)} 构造函数。
     *
     * @deprecated 此构造函数已被移除，请使用依赖注入构造函数
     * @throws UnsupportedOperationException 始终抛出，强制使用依赖注入
     * @see #ChronoBlocksUIManager(List) 推荐的依赖注入构造函数
     * @see UIConfiguration 面板配置创建器
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public ChronoBlocksUIManager() {
        throw new UnsupportedOperationException(
                """
                        默认构造函数已被移除。请使用依赖注入构造函数：
                        1. UIConfiguration config = new UIConfiguration(minecraftClient);
                        2. ChronoBlocksUIManager uiManager = config.createUIManagerWithRegistry();
                        或：ChronoBlocksUIManager uiManager = new ChronoBlocksUIManager(config.getPanels());"""
        );
    }


    /**
     * 依赖注入构造函数 - 推荐使用
     * @param panels 面板列表
     * @throws IllegalArgumentException 如果面板列表为null或包含null元素
     */
    public ChronoBlocksUIManager(List<IPanel> panels) {
        if (panels == null) {
            throw new IllegalArgumentException("面板列表不能为 null");
        }
        if (panels.isEmpty()) {
            throw new IllegalArgumentException("面板列表不能为空");
        }
        long nullCount = panels.stream().filter(Objects::isNull).count();
        if (nullCount > 0) {
            throw new IllegalArgumentException("面板列表包含 " + nullCount + " 个 null 元素");
        }

        // 创建面板注册表
        this.panelRegistry = new PanelRegistry(panels);
        
        // 初始化布局持久化管理器
        this.layoutManager = LayoutPersistenceManager.getInstance();
        
        // 初始化对话框管理器
        this.dialogManager = new com.chronoblocks.ui.dialogs.DialogManager();
        // 设置新建动画确认回调
        this.dialogManager.setNewAnimationConfirmCallback(() -> {
            try {
                TimelinePanel timelinePanel = panelRegistry.getPanel(TimelinePanel.class);
                if (timelinePanel != null) {
                    timelinePanel.resetWorkspace(true);
                } else {
                    // 兜底：清空容器并删除时间线存档
                    Objects.requireNonNull(com.chronoblocks.core.container.ContainerManager.getInstance()).clear();
                    com.chronoblocks.ui.timeline.persistence.TimelineModelPersistence.resetForCurrentWorld();
                }
            } catch (Exception e) {
                LOGGER.error("[UIManager] 新建动画重置失败: {}", e.getMessage(), e);
            }
        });

        // 重置DockSpace布局构建器状态，以便首次打开时应用默认布局
        com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
        LOGGER.info("[ChronoBlocks] DockSpace布局状态已重置，准备应用布局");

        // 验证必需的面板是否存在
        validateRequiredPanels();

        // 应用UI主题 v4.5 🎨
        UITheme.applyDarkTheme();
        LOGGER.info("[ChronoBlocks] UI主题已应用");
        
        // 初始化旧属性系统 v4.9 📋（兼容性）
        com.chronoblocks.ui.properties.PropertyAdapterBootstrap.initialize();
        LOGGER.info("[ChronoBlocks] 旧属性系统已初始化");
        
        // 初始化新属性系统 v4.5 ⭐（现代化）
        com.chronoblocks.ui.adapters.PropertyAdapterModule.initialize();
        LOGGER.info("[ChronoBlocks] 新属性系统已初始化");
        
        // 初始化所有面板
        initializeAllPanels();
        
        // 设置面板注册表引用（用于面板间通信）
        setupPanelRegistryReferences();
        
        // 根据当前世界的布局还原面板可见性
        restorePanelVisibilityFromLayout();

        logInfo("UI Manager initialized with {} injected panels using modern IoC", panelRegistry.size());
        logDebugState(() -> "Panel registry: " + panelRegistry.getDebugInfo());
    }

    /**
     * 获取面板注册表（供外部在关闭/保存时访问关键面板）。
     */
    public IPanelRegistry getPanelRegistry() {
        return panelRegistry;
    }
    
    /**
     * 获取命令门面（供 Menu / Toolbar / Shortcut 使用）
     * 
     * <p>将用户意图翻译成系统命令，而不是直接操作系统状态。
     * 
     * @return UI 命令门面实例
     */
    public UICommandFacade commands() {
        return getOrCreateCommandFacade();
    }
    
    // ==================== 命令门面实现 ====================
    
    private UICommandFacade commandFacade;
    private final UILayoutFacade layoutFacade = new UILayoutFacadeImpl();
    private com.chronoblocks.ui.input.ShortcutDispatcher shortcutDispatcher;
    
    /**
     * 延迟初始化命令门面（懒加载模式，确保面板已完全初始化）
     */
    private UICommandFacade getOrCreateCommandFacade() {
        if (commandFacade == null) {
            commandFacade = new UICommandFacadeImpl();
            // 初始化 ShortcutDispatcher（传入 panelRegistry 用于面板焦点检测）
            shortcutDispatcher = new com.chronoblocks.ui.input.ShortcutDispatcher(commandFacade, panelRegistry);
            LOGGER.info("[ChronoBlocksUIManager] ShortcutDispatcher 已初始化");
        }
        return commandFacade;
    }
    
    /**
     * 获取快捷键分发器
     * 
     * @return ShortcutDispatcher 实例
     */
    public com.chronoblocks.ui.input.ShortcutDispatcher getShortcutDispatcher() {
        // 确保 Facade 已初始化（这会同时初始化 ShortcutDispatcher）
        getOrCreateCommandFacade();
        return shortcutDispatcher;
    }
    
    /**
     * UI 命令门面实现 - 分组结构（优化依赖注入）
     * 
     * <p><strong>架构原则：</strong>
     * <br>Facade is the only place allowed to execute system commands.
     * <br>This is the single entry point for all user actions from UI panels.
     */
    private class UICommandFacadeImpl implements UICommandFacade {
        
        // 直接注入的关键依赖（避免动态查找）
        private final TimelinePanel timelinePanel;
        private final TimelineModel timelineModel;
        private final MenuBarPanel menuBarPanel;
        
        // 命令实现实例
        private final ProjectCommands projectCommands;
        private final TimelineCommands timelineCommands;
        private final PlaybackCommands playbackCommands;
        private final CameraCommands cameraCommands;
        private final ViewCommands viewCommands;
        private final LayoutCommands layoutCommands;
        private final HelpCommands helpCommands;
        private final ToolCommands toolCommands;
        
        /**
         * 构造函数 - 初始化时注入依赖（优化：直接注入，避免重复查找）
         */
        public UICommandFacadeImpl() {
            // 从 panelRegistry 获取关键面板（初始化时获取一次，避免重复查找）
            this.timelinePanel = panelRegistry.getPanel(TimelinePanel.class);
            this.menuBarPanel = panelRegistry.getPanel(MenuBarPanel.class);
            
            // 从 TimelinePanel 直接获取 TimelineModel
            if (timelinePanel != null) {
                this.timelineModel = timelinePanel.getModel();
            } else {
                // 降级方案：通过 ContainerTimelineService 获取
                this.timelineModel = com.chronoblocks.ui.timeline.container.ContainerTimelineService.getInstance().getTimelineModel();
                if (this.timelineModel == null) {
                    LOGGER.warn("[Commands] TimelineModel not available, some commands may not work");
                }
            }
            
            // 初始化命令实现（传入依赖）
            this.projectCommands = new ProjectCommandsImpl(timelineModel, menuBarPanel);
            this.timelineCommands = new TimelineCommandsImpl(timelinePanel, menuBarPanel, timelineModel);
            this.playbackCommands = new PlaybackCommandsImpl(timelineModel);
            this.cameraCommands = new CameraCommandsImpl(timelineModel);
            this.viewCommands = new ViewCommandsImpl(panelRegistry);
            this.layoutCommands = new LayoutCommandsImpl(layoutManager);
            this.helpCommands = new HelpCommandsImpl(menuBarPanel);
            this.toolCommands = new ToolCommandsImpl();
            
            // 设置时间线设置访问器（用于时间线设置对话框）
            if (timelineModel != null && ChronoBlocksUIManager.this.dialogManager != null) {
                // 设置时间线设置访问器
                ChronoBlocksUIManager.this.dialogManager.setTimelineSettingsAccessor(
                    new com.chronoblocks.ui.dialogs.DialogManager.TimelineSettingsAccessor() {
                        @Override
                        public com.chronoblocks.ui.timeline.snapping.TimelineSnapSettings getSnapSettings() {
                            return timelineModel.getSnapSettings();
                        }
                        
                        @Override
                        public float getFrameRate() {
                            // 尝试多种方式获取项目对象
                            Object project = getProjectObject();
                            if (project == null) {
                                // 如果项目对象不存在，返回默认值
                                // 这可能是因为项目系统尚未完全实现
                                return 30.0f; // 默认值
                            }
                            
                            // 使用与 TimelineProjectPropertyAdapter 相同的方式
                            float fps = getFloatProperty(project, "frameRate", "getFrameRate", 30.0f);
                            LOGGER.debug("[TimelineSettings] 获取帧率: {} (项目对象: {})", fps, project.getClass().getSimpleName());
                            return fps;
                        }
                        
                        @Override
                        public boolean setFrameRate(float fps) {
                            Object project = getProjectObject();
                            if (project == null) {
                                // 如果项目对象不存在，记录警告但不阻止操作
                                // 这可能是因为项目系统尚未完全实现
                                LOGGER.warn("[TimelineSettings] 无法获取项目对象，帧率设置可能无效");
                                return false;
                            }
                            
                            // 使用与 TimelineProjectPropertyAdapter 相同的方式
                            boolean success = setFloatProperty(project, fps);
                            if (success) {
                                LOGGER.debug("[TimelineSettings] 帧率已设置为: {}", fps);
                            } else {
                                LOGGER.warn("[TimelineSettings] 设置帧率失败: {}", fps);
                            }
                            return success;
                        }
                        
                        @Override
                        public int getTimeUnit() {
                            Object project = getProjectObject();
                            if (project == null) {
                                return 0; // 默认：秒
                            }
                            
                            return getIntProperty(project, "timeUnit", "getTimeUnit", 0);
                        }
                        
                        @Override
                        public boolean setTimeUnit(int unit) {
                            Object project = getProjectObject();
                            if (project == null) {
                                return false;
                            }
                            
                            return setIntProperty(project, "timeUnit", "setTimeUnit", unit);
                        }
                        
                        /**
                         * 获取项目对象（尝试多种方式）
                         */
                        private Object getProjectObject() {
                            // 方式1: 通过 SelectionManager 获取当前选择
                            com.chronoblocks.ui.selection.SelectionManager selectionManager = 
                                    com.chronoblocks.ui.selection.SelectionManager.get();
                            Object selection = selectionManager.getCurrentSelection();
                            
                            // 方式2: 检查 SelectionType，如果是 PROJECT 类型，尝试获取项目对象
                            com.chronoblocks.ui.selection.SelectionType selectionType = selectionManager.getSelectionType();
                            if (selectionType == com.chronoblocks.ui.selection.SelectionType.PROJECT) {
                                // 如果是 PROJECT 类型，即使 selection 为 null，也可能有项目对象
                                // 尝试通过 PropertiesPanel 的方式：使用 PropertyAdapterRegistry
                                try {
                                    // 创建一个标记对象来查找项目适配器
                                    String projectMarker = "TimelineProject";
                                    com.chronoblocks.ui.properties.IPropertyAdapter<?> adapter = 
                                            com.chronoblocks.ui.properties.PropertyAdapterRegistry.getAdapterFor(projectMarker);
                                    
                                    // 如果适配器存在，说明项目系统可用
                                    // 但我们需要实际的项目对象，而不是适配器
                                    // 所以继续尝试其他方式
                                } catch (Exception e) {
                                    // 忽略
                                }
                                
                                // 如果 selection 不为 null，尝试使用它
                                if (selection != null) {
                                    String className = selection.getClass().getName().toLowerCase();
                                    if (className.contains("timelineproject") || className.contains("project")) {
                                        return selection;
                                    }
                                }
                            }
                            
                            // 方式3: 如果 selection 不为 null，检查是否是项目对象
                            if (selection != null) {
                                String className = selection.getClass().getName().toLowerCase();
                                if (className.contains("timelineproject") || className.contains("project")) {
                                    return selection;
                                }
                            }
                            
                            // 方式4: 尝试通过反射查找项目对象（如果项目对象是单例或全局对象）
                            // 这里我们无法直接访问，所以返回 null
                            // 注意：如果项目对象不存在，帧率设置将无法工作
                            return null;
                        }
                        
                        /**
                         * 获取浮点属性（与 TimelineProjectPropertyAdapter 相同）
                         */
                        private float getFloatProperty(Object obj, String fieldName, String getterName, float defaultValue) {
                            try {
                                java.lang.reflect.Method method = obj.getClass().getMethod(getterName);
                                Object result = method.invoke(obj);
                                if (result instanceof Float) return (Float) result;
                                if (result instanceof Double) return ((Double) result).floatValue();
                                return defaultValue;
                            } catch (Exception e) {
                                try {
                                    java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                                    field.setAccessible(true);
                                    Object result = field.get(obj);
                                    if (result instanceof Float) return (Float) result;
                                    if (result instanceof Double) return ((Double) result).floatValue();
                                    return defaultValue;
                                } catch (Exception ex) {
                                    return defaultValue;
                                }
                            }
                        }
                        
                        /**
                         * 设置浮点属性（与 TimelineProjectPropertyAdapter 相同）
                         */
                        private boolean setFloatProperty(Object obj, float value) {
                            try {
                                java.lang.reflect.Method method = obj.getClass().getMethod("setFrameRate", float.class);
                                method.invoke(obj, value);
                                return true;
                            } catch (Exception e) {
                                try {
                                    java.lang.reflect.Field field = obj.getClass().getDeclaredField("frameRate");
                                    field.setAccessible(true);
                                    field.set(obj, value);
                                    return true;
                                } catch (Exception ex) {
                                    return false;
                                }
                            }
                        }
                        
                        /**
                         * 获取整数属性
                         */
                        private int getIntProperty(Object obj, String fieldName, String getterName, int defaultValue) {
                            try {
                                java.lang.reflect.Method method = obj.getClass().getMethod(getterName);
                                Object result = method.invoke(obj);
                                if (result instanceof Integer) return (Integer) result;
                                if (result instanceof Number) return ((Number) result).intValue();
                                return defaultValue;
                            } catch (Exception e) {
                                try {
                                    java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                                    field.setAccessible(true);
                                    Object result = field.get(obj);
                                    if (result instanceof Integer) return (Integer) result;
                                    if (result instanceof Number) return ((Number) result).intValue();
                                    return defaultValue;
                                } catch (Exception ex) {
                                    return defaultValue;
                                }
                            }
                        }
                        
                        /**
                         * 设置整数属性
                         */
                        private boolean setIntProperty(Object obj, String fieldName, String setterName, int value) {
                            try {
                                java.lang.reflect.Method method = obj.getClass().getMethod(setterName, int.class);
                                method.invoke(obj, value);
                                return true;
                            } catch (Exception e) {
                                try {
                                    java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                                    field.setAccessible(true);
                                    field.set(obj, value);
                                    return true;
                                } catch (Exception ex) {
                                    return false;
                                }
                            }
                        }
                    }
                );
            }
            
            // 设置布局访问器（用于保存/加载布局预设对话框）
            if (ChronoBlocksUIManager.this.dialogManager != null) {
                ChronoBlocksUIManager.this.dialogManager.setLayoutAccessor(
                    new com.chronoblocks.ui.dialogs.DialogManager.LayoutAccessor() {
                        @Override
                        public boolean saveLayoutPreset(String presetName) {
                            return layoutCommands.saveLayoutPreset(presetName);
                        }
                        
                        @Override
                        public java.util.List<String> listLayoutPresets() {
                            return layoutCommands.listLayoutPresets();
                        }
                        
                        @Override
                        public boolean loadLayoutPreset(String presetName) {
                            return layoutCommands.loadLayoutPreset(presetName);
                        }
                        
                        @Override
                        public boolean deleteLayoutPreset(String presetName) {
                            return layoutCommands.deleteLayoutPreset(presetName);
                        }
                    }
                );
            }
        }
        
        @Override
        public ProjectCommands project() {
            return projectCommands;
        }
        
        @Override
        public TimelineCommands timeline() {
            return timelineCommands;
        }
        
        @Override
        public PlaybackCommands playback() {
            return playbackCommands;
        }
        
        @Override
        public CameraCommands camera() {
            return cameraCommands;
        }
        
        @Override
        public ViewCommands view() {
            return viewCommands;
        }
        
        @Override
        public LayoutCommands layout() {
            return layoutCommands;
        }
        
        @Override
        public HelpCommands help() {
            return helpCommands;
        }
        
        @Override
        public ToolCommands tools() {
            return toolCommands;
        }
    }
    
    /**
     * 项目命令实现（优化：直接调用，减少间接调用）
     */
    private class ProjectCommandsImpl implements ProjectCommands {
        
        private final TimelineModel timelineModel;
        private final MenuBarPanel menuBarPanel;
        
        public ProjectCommandsImpl(TimelineModel timelineModel, MenuBarPanel menuBarPanel) {
            this.timelineModel = timelineModel;
            this.menuBarPanel = menuBarPanel;
        }
        
        @Override
        public void newProject() {
            // 显示确认对话框
            ChronoBlocksUIManager.this.dialogManager.showNewAnimationConfirm();
        }
        
        @Override
        public void openProject() {
            if (timelineModel != null) {
                try {
                    // 重新加载当前世界的时间线数据
                    timelineModel.reload();
                    LOGGER.info("[Project] 项目已重新加载");
                } catch (Exception e) {
                    LOGGER.error("[Project] 打开项目失败: {}", e.getMessage(), e);
                }
            } else {
                LOGGER.warn("[Project] TimelineModel 不可用，无法打开项目");
            }
        }
        
        @Override
        public void saveProject() {
            if (timelineModel != null) {
                try {
                    timelineModel.save();
                    LOGGER.info("[Project] 项目已保存");
                } catch (Exception e) {
                    LOGGER.error("[Project] 保存项目失败: {}", e.getMessage(), e);
                }
            } else {
                LOGGER.warn("[Project] TimelineModel 不可用，无法保存项目");
            }
        }
        
        @Override
        public void saveProjectAs() {
            // 当前系统按世界自动保存，Save As 功能暂时与 Save 相同
            // 未来可以扩展为选择不同的保存位置
            saveProject();
            LOGGER.info("[Project] 项目已另存为（当前按世界自动保存）");
        }
        
        @Override
        public void exportVideo() {
            ChronoBlocksUIManager.this.openVideoExportDialog();
        }
        
        @Override
        public void exitApplication() {
            try {
                // 退出前保存当前项目
                if (timelineModel != null) {
                    timelineModel.save();
                    LOGGER.info("[Project] 退出前已保存项目");
                }
                
                // 关闭 ChronoBlocks 窗口
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.currentScreen instanceof com.chronoblocks.ui.ChronoBlocksScreen screen) {
                    screen.close();
                    client.setScreen(null);
                    LOGGER.info("[Project] ChronoBlocks 窗口已关闭");
                } else {
                    // 兜底：直接设置屏幕为 null
                    client.setScreen(null);
                }
            } catch (Exception e) {
                LOGGER.error("[Project] 退出应用失败: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 时间线命令实现（优化：直接调用 TimelinePanel 和 TimelineModel）
     */
    private class TimelineCommandsImpl implements TimelineCommands {
        
        private final TimelinePanel timelinePanel;
        private final MenuBarPanel menuBarPanel;
        private final TimelineModel timelineModel;
        
        public TimelineCommandsImpl(TimelinePanel timelinePanel, MenuBarPanel menuBarPanel, TimelineModel timelineModel) {
            this.timelinePanel = timelinePanel;
            this.menuBarPanel = menuBarPanel;
            this.timelineModel = timelineModel;
        }
        
        @Override
        public void openTimelineSettings() {
            ChronoBlocksUIManager.this.dialogManager.showTimelineSettingsDialog();
        }
        
        @Override
        public void zoomToFit() {
            if (timelinePanel != null) {
                timelinePanel.zoomToFit();
            }
        }
        
        @Override
        public void scrollToPlayhead() {
            if (timelinePanel != null) {
                timelinePanel.scrollToPlayhead();
            }
        }
        
        @Override
        public void addCameraTrack(CameraPathType type) {
            if (timelineModel == null) {
                LOGGER.warn("[Commands] TimelineModel 不可用，无法添加摄像机轨道");
                return;
            }
            
            try {
                // 计算同类型轨道数量，用于生成名称
                long sameTypeCount = timelineModel.getTracks().stream()
                        .filter(t -> t.getType() == com.chronoblocks.ui.timeline.model.TrackType.CAMERA)
                        .count();
                
                String trackName = "摄像机轨道 " + (sameTypeCount + 1);
                com.chronoblocks.ui.timeline.model.TimelineTrack newTrack = 
                        new com.chronoblocks.ui.timeline.model.TimelineTrack(trackName, 
                                com.chronoblocks.ui.timeline.model.TrackType.CAMERA);
                
                timelineModel.addTrack(newTrack);
                LOGGER.info("[Commands] 已添加摄像机轨道: {}", trackName);
            } catch (Exception e) {
                LOGGER.error("[Commands] 添加摄像机轨道失败: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void addActorTrack() {
            if (timelineModel == null) {
                LOGGER.warn("[Commands] TimelineModel 不可用，无法添加演员轨道");
                return;
            }
            
            try {
                // 计算同类型轨道数量，用于生成名称
                long sameTypeCount = timelineModel.getTracks().stream()
                        .filter(t -> t.getType() == com.chronoblocks.ui.timeline.model.TrackType.ENTITY)
                        .count();
                
                String trackName = "实体轨道 " + (sameTypeCount + 1);
                com.chronoblocks.ui.timeline.model.TimelineTrack newTrack = 
                        new com.chronoblocks.ui.timeline.model.TimelineTrack(trackName, 
                                com.chronoblocks.ui.timeline.model.TrackType.ENTITY);
                
                timelineModel.addTrack(newTrack);
                
                // 如果是第一条实体轨道，自动激活
                if (sameTypeCount == 0) {
                    com.chronoblocks.ui.timeline.playback.entity.TimelineEntityController
                            .getInstance().bindActiveTrack(newTrack);
                    LOGGER.info("[Commands] 创建第一条实体轨道，自动激活: {}", trackName);
                }
                
                LOGGER.info("[Commands] 已添加实体轨道: {}", trackName);
            } catch (Exception e) {
                LOGGER.error("[Commands] 添加实体轨道失败: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void addWeatherTrack() {
            if (timelineModel == null) {
                LOGGER.warn("[Commands] TimelineModel 不可用，无法添加天气轨道");
                return;
            }
            
            try {
                // 计算同类型轨道数量，用于生成名称
                long sameTypeCount = timelineModel.getTracks().stream()
                        .filter(t -> t.getType() == com.chronoblocks.ui.timeline.model.TrackType.WEATHER)
                        .count();
                
                String trackName = "天气轨道 " + (sameTypeCount + 1);
                com.chronoblocks.ui.timeline.model.TimelineTrack newTrack = 
                        new com.chronoblocks.ui.timeline.model.TimelineTrack(trackName, 
                                com.chronoblocks.ui.timeline.model.TrackType.WEATHER);
                
                timelineModel.addTrack(newTrack);
                
                // 如果是第一条天气轨道，自动激活
                if (sameTypeCount == 0) {
                    com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance()
                            .bindActiveTrack(newTrack);
                    LOGGER.info("[Commands] 创建第一条天气轨道，自动激活: {}", trackName);
                }
                
                LOGGER.info("[Commands] 已添加天气轨道: {}", trackName);
            } catch (Exception e) {
                LOGGER.error("[Commands] 添加天气轨道失败: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void addAudioTrack() {
            if (timelineModel == null) {
                LOGGER.warn("[Commands] TimelineModel 不可用，无法添加音频轨道");
                return;
            }
            
            try {
                // 计算同类型轨道数量，用于生成名称
                long sameTypeCount = timelineModel.getTracks().stream()
                        .filter(t -> t.getType() == com.chronoblocks.ui.timeline.model.TrackType.AUDIO)
                        .count();
                
                String trackName = "音频轨道 " + (sameTypeCount + 1);
                // 音频轨道使用 AudioTrack 类
                com.chronoblocks.ui.timeline.model.audio.AudioTrack newTrack = 
                        new com.chronoblocks.ui.timeline.model.audio.AudioTrack(trackName);
                
                timelineModel.addTrack(newTrack);
                LOGGER.info("[Commands] 已添加音频轨道: {}", trackName);
            } catch (Exception e) {
                LOGGER.error("[Commands] 添加音频轨道失败: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void undo() {
            if (timelinePanel != null) {
                timelinePanel.undo();
                LOGGER.debug("[TimelineCommands] 撤销操作");
            } else {
                LOGGER.warn("[TimelineCommands] TimelinePanel 不可用，无法撤销");
            }
        }
        
        @Override
        public void redo() {
            if (timelinePanel != null) {
                timelinePanel.redo();
                LOGGER.debug("[TimelineCommands] 重做操作");
            } else {
                LOGGER.warn("[TimelineCommands] TimelinePanel 不可用，无法重做");
            }
        }
        
        @Override
        public void deleteSelected() {
            if (timelinePanel != null) {
                timelinePanel.deleteSelected();
                LOGGER.debug("[TimelineCommands] 删除选中项");
            } else {
                LOGGER.warn("[TimelineCommands] TimelinePanel 不可用，无法删除");
            }
        }
    }
    
    /**
     * 播放命令实现（优化：直接调用 PlaybackController，补充 In/Out 点逻辑）
     */
    private class PlaybackCommandsImpl implements PlaybackCommands {
        
        private final TimelineModel timelineModel;
        
        public PlaybackCommandsImpl(TimelineModel timelineModel) {
            this.timelineModel = timelineModel;
        }
        
        @Override
        public void play() {
            // ⭐ v1.2 改进：Facade 只改 Model，不直接管 tick
            // Orchestrator 会在下一帧检测到状态变化并触发生命周期钩子
            com.chronoblocks.ui.timeline.model.TimelineModel model = 
                com.chronoblocks.ui.timeline.playback.PlaybackOrchestrator.get().getBoundModel();
            if (model != null) {
                model.setPlaying(true);
            } else {
                // 降级方案：使用旧的 timelineModel（向后兼容）
                if (timelineModel != null) {
                    timelineModel.setPlaying(true);
                }
                // 同时更新旧的 PlaybackController（向后兼容）
                PlaybackController.get().play();
            }
        }
        
        @Override
        public void pause() {
            // ⭐ v1.2 改进：Facade 只改 Model，不直接管 tick
            com.chronoblocks.ui.timeline.model.TimelineModel model = 
                com.chronoblocks.ui.timeline.playback.PlaybackOrchestrator.get().getBoundModel();
            if (model != null) {
                model.setPlaying(false);
            } else {
                // 降级方案：使用旧的 timelineModel（向后兼容）
                if (timelineModel != null) {
                    timelineModel.setPlaying(false);
                }
                // 同时更新旧的 PlaybackController（向后兼容）
                PlaybackController.get().pause();
            }
        }
        
        @Override
        public void stop() {
            // ⭐ v1.2 改进：Facade 只改 Model，不直接管 tick
            com.chronoblocks.ui.timeline.model.TimelineModel model = 
                com.chronoblocks.ui.timeline.playback.PlaybackOrchestrator.get().getBoundModel();
            if (model != null) {
                model.setPlaying(false);
                model.setCurrentTime(0f);
            } else {
                // 降级方案：使用旧的 timelineModel（向后兼容）
                if (timelineModel != null) {
                    timelineModel.jumpToStart();
                    timelineModel.setPlaying(false);
                }
                // 同时更新旧的 PlaybackController（向后兼容）
                PlaybackController.get().stop();
            }
        }
        
        @Override
        public void togglePlayPause() {
            if (timelineModel != null) {
                boolean wasPlaying = timelineModel.isPlaying();
                timelineModel.togglePlay();
                boolean isPlaying = timelineModel.isPlaying();
                LOGGER.info("[PlaybackCommands] togglePlayPause: {} -> {}", wasPlaying, isPlaying);
            } else {
                // 降级方案：使用 PlaybackController
                PlaybackController pc = PlaybackController.get();
                boolean wasPlaying = pc.isPlaying();
                if (pc.isPlaying()) {
                    pause();
                } else {
                    play();
                }
                boolean isPlaying = pc.isPlaying();
                LOGGER.info("[PlaybackCommands] togglePlayPause (fallback): {} -> {}", wasPlaying, isPlaying);
            }
        }
        
        @Override
        public void setInPoint() {
            // TODO: 实现设置入点
            // timelineModel.setInPoint(PlaybackController.get().getCurrentTime());
            LOGGER.info("[Commands] Set in point");
        }
        
        @Override
        public void setOutPoint() {
            // TODO: 实现设置出点
            // timelineModel.setOutPoint(PlaybackController.get().getCurrentTime());
            LOGGER.info("[Commands] Set out point");
        }
        
        @Override
        public void clearInOut() {
            // TODO: 实现清除入出点
            // timelineModel.clearInOut();
            LOGGER.info("[Commands] Clear in/out points");
        }
        
        @Override
        public void setPlaybackSpeed(float speed) {
            if (timelineModel != null) {
                timelineModel.setPlaybackSpeed(speed);
            }
            // 同时更新旧的 PlaybackController（向后兼容）
            PlaybackController.get().setSpeed(speed);
        }
        
        @Override
        public void toggleLoop() {
            if (timelineModel != null) {
                timelineModel.setLoopEnabled(!timelineModel.isLoopEnabled());
            }
            // 同时更新旧的 PlaybackController（向后兼容）
            PlaybackController.get().toggleLoop();
        }
        
        @Override
        public void goToStart() {
            if (timelineModel != null) {
                timelineModel.jumpToStart();
            }
            // 同时更新旧的 PlaybackController（向后兼容）
            PlaybackController.get().seek(0f);
        }
        
        @Override
        public void goToEnd() {
            if (timelineModel != null) {
                timelineModel.jumpToEnd();
            } else {
                // 降级方案：使用 PlaybackController
                PlaybackController pc = PlaybackController.get();
                float endTime = pc.getDuration();
                pc.seek(endTime);
            }
        }
        
        @Override
        public boolean isPlaying() {
            // 优先使用 TimelineModel 的状态
            if (timelineModel != null) {
                return timelineModel.isPlaying();
            }
            // 降级方案：使用 PlaybackController
            return PlaybackController.get().isPlaying();
        }
        
        @Override
        public boolean isLooping() {
            // 优先使用 TimelineModel 的状态
            if (timelineModel != null) {
                return timelineModel.isLoopEnabled();
            }
            // 降级方案：使用 PlaybackController
            return PlaybackController.get().isLoop();
        }
        
        @Override
        public float getPlaybackSpeed() {
            // 优先使用 TimelineModel 的状态
            if (timelineModel != null) {
                return timelineModel.getPlaybackSpeed();
            }
            // 降级方案：使用 PlaybackController
            return PlaybackController.get().getSpeed();
        }
        
        @Override
        public float getCurrentTime() {
            // 优先使用 TimelineModel 的状态
            if (timelineModel != null) {
                return timelineModel.getCurrentTime();
            }
            // 降级方案：使用 PlaybackController
            return PlaybackController.get().getCurrentTime();
        }
        
        @Override
        public float getTotalDuration() {
            // 优先使用 TimelineModel 的状态
            if (timelineModel != null) {
                return timelineModel.getDuration();
            }
            // 降级方案：使用 PlaybackController
            return PlaybackController.get().getDuration();
        }
        
        @Override
        public void seek(float time) {
            // ⭐ v1.2 改进：Facade 只改 Model，Orchestrator 在下一帧自然采样
            com.chronoblocks.ui.timeline.model.TimelineModel model = 
                com.chronoblocks.ui.timeline.playback.PlaybackOrchestrator.get().getBoundModel();
            if (model != null) {
                float clampedTime = Math.max(0f, Math.min(model.getDuration(), time));
                model.setCurrentTime(clampedTime);
                
                // ⭐ 立即采样（seek 是离散命令，需要立即反馈）
                // 季节轨道系统：跳转时间时更新季节采样
                try {
                    com.chronoblocks.ui.timeline.playback.season.TimelineSeasonController.getInstance().onTimelineTick(clampedTime);
                } catch (Exception e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[PlaybackCommands] 季节采样失败", e);
                    }
                }
                
                // ⭐ 天气轨道系统：跳转时间时更新天气采样（预览模式）
                try {
                    com.chronoblocks.ui.timeline.playback.weather.TimelineWeatherController.getInstance().previewAt(clampedTime);
                } catch (Exception e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[PlaybackCommands] 天气采样失败", e);
                    }
                }
                
                // ⭐ 摄像机控制器：跳转到指定时间并保持玩家态
                try {
                    com.chronoblocks.ui.timeline.playback.camera.TimelineCameraController.getInstance().jumpToTimeAsPlayer(clampedTime);
                } catch (Exception e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[PlaybackCommands] 摄像机跳转失败", e);
                    }
                }
                
                // ⭐ 更新片段方块显示状态
                try {
                    com.chronoblocks.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
                } catch (Exception e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[PlaybackCommands] 片段方块更新失败", e);
                    }
                }
                
                LOGGER.debug("[PlaybackCommands] Seek to {:.2f}s", clampedTime);
            } else {
                // 降级方案：使用旧的 timelineModel（向后兼容）
                if (timelineModel != null) {
                    float clampedTime = Math.max(0f, Math.min(timelineModel.getDuration(), time));
                    timelineModel.setCurrentTime(clampedTime);
                } else {
                    // 降级方案：使用 PlaybackController
                    PlaybackController.get().seek(time);
                }
            }
        }
        
        @Override
        public void tick(float deltaTime) {
            // ⚠️ 播放驱动已迁移到 PlaybackOrchestrator（在 UIManager.updateAnimationSystem() 中调用）
            // Facade 的 tick() 方法保留用于向后兼容，但实际驱动由 Orchestrator 负责
            // 这样可以确保播放系统在任何情况下（面板隐藏/失焦）都保持一致
            // 
            // 注意：如果外部代码直接调用 commands.playback().tick()，会触发 Orchestrator
            // 但通常应该通过 UIManager.updateAnimationSystem() 统一驱动
            if (timelineModel != null) {
                // 委托给 Orchestrator（确保统一驱动）
                com.chronoblocks.ui.timeline.playback.PlaybackOrchestrator.get().bindModel(timelineModel);
                com.chronoblocks.ui.timeline.playback.PlaybackOrchestrator.get().tick(deltaTime);
            }
        }
    }
    
    /**
     * 摄像机命令实现（优化：直接调用 TimelineModel 和摄像机控制器）
     */
    private class CameraCommandsImpl implements CameraCommands {
        
        private final TimelineModel timelineModel;
        
        public CameraCommandsImpl(TimelineModel timelineModel) {
            this.timelineModel = timelineModel;
        }
        
        @Override
        public void createCameraPath(CameraPathType type) {
            // TODO: 实现创建摄像机路径
            // timelineModel.createCameraPathClip(type);
            LOGGER.info("[Commands] Create camera path: {}", type);
        }
        
        @Override
        public void alignCameraToCurrentView() {
            // TODO: 实现对齐摄像机到当前视角
            // 通过 TimelineCameraController 对齐
            LOGGER.info("[Commands] Align camera to current view");
        }
        
        @Override
        public void createShotFromCurrentView() {
            // TODO: 实现从当前视角创建镜头
            // timelineModel.createCameraClipFromCurrentView();
            LOGGER.info("[Commands] Create shot from current view");
        }
        
        @Override
        public void setDefaultCameraView() {
            // TODO: 实现设置默认摄像机视角
            LOGGER.info("[Commands] Set default camera view");
        }
    }
    
    /**
     * 视图命令实现（优化：直接使用 panelRegistry）
     */
    private class ViewCommandsImpl implements ViewCommands {
        
        private final IPanelRegistry panelRegistry;
        
        public ViewCommandsImpl(IPanelRegistry panelRegistry) {
            this.panelRegistry = panelRegistry;
        }
        
        @Override
        public void showPanel(String panelId) {
            IPanel panel = findPanelById(panelId);
            if (panel != null) {
                panel.setVisible(true);
                LOGGER.debug("[ViewCommands] 显示面板: {}", panelId);
            } else {
                LOGGER.warn("[ViewCommands] 未找到面板: {}", panelId);
            }
        }
        
        @Override
        public void hidePanel(String panelId) {
            IPanel panel = findPanelById(panelId);
            if (panel != null) {
                panel.setVisible(false);
                LOGGER.debug("[ViewCommands] 隐藏面板: {}", panelId);
            } else {
                LOGGER.warn("[ViewCommands] 未找到面板: {}", panelId);
            }
        }
        
        @Override
        public void togglePanel(String panelId) {
            IPanel panel = findPanelById(panelId);
            if (panel != null) {
                panel.setVisible(!panel.isVisible());
                LOGGER.debug("[ViewCommands] 切换面板可见性: {} -> {}", panelId, panel.isVisible());
            } else {
                LOGGER.warn("[ViewCommands] 未找到面板: {}", panelId);
            }
        }
        
        @Override
        public void showAllPanels() {
            int count = 0;
            for (IPanel panel : panelRegistry.getAllPanels()) {
                panel.setVisible(true);
                count++;
            }
            LOGGER.debug("[ViewCommands] 显示所有面板: {} 个", count);
        }
        
        @Override
        public void hideAllPanels() {
            int count = 0;
            for (IPanel panel : panelRegistry.getAllPanels()) {
                // 不隐藏 MenuBar 和 StatusBar（这些是固定面板）
                if (!(panel instanceof MenuBarPanel) && !(panel instanceof com.chronoblocks.ui.panels.StatusBarPanel)) {
                    panel.setVisible(false);
                    count++;
                }
            }
            LOGGER.debug("[ViewCommands] 隐藏所有面板: {} 个", count);
        }
        
        /**
         * 根据面板ID查找面板实例
         * @param panelId 面板ID（PanelId常量或面板名称）
         * @return 面板实例，如果未找到则返回 null
         */
        private IPanel findPanelById(String panelId) {
            // PanelId 常量的值就是面板名称，所以可以直接使用 panelId 作为面板名称
            // 但需要处理一些特殊情况（如 MENU_BAR -> "MenuBar"）
            String actualName = switch (panelId) {
                case com.chronoblocks.ui.PanelId.MENU_BAR -> "MenuBar";
                case com.chronoblocks.ui.PanelId.STATUS_BAR -> "StatusBar";
                case com.chronoblocks.ui.PanelId.TOOL_PROPERTIES -> "ToolProperties";
                case com.chronoblocks.ui.PanelId.SELECTION_FILTER -> "SelectionFilter";
                case com.chronoblocks.ui.PanelId.SELECTION_HISTORY -> "SelectionHistory";
                case com.chronoblocks.ui.PanelId.RENDER_SETTINGS -> "RenderSettings";
                default -> panelId; // 大多数 PanelId 常量的值就是面板名称
            };
            
            // 遍历所有面板，查找匹配的面板名称
            for (IPanel panel : panelRegistry.getAllPanels()) {
                if (actualName.equals(panel.getPanelName())) {
                    return panel;
                }
            }
            
            // 如果没找到，记录警告（但不在每次调用时都记录，避免刷屏）
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("[ViewCommands] 未找到面板: {} (映射后: {})", panelId, actualName);
            }
            return null;
        }
    }
    
    /**
     * 布局命令实现（优化：直接使用 layoutManager）
     */
    private class LayoutCommandsImpl implements LayoutCommands {
        
        private final LayoutPersistenceManager layoutManager;
        
        public LayoutCommandsImpl(LayoutPersistenceManager layoutManager) {
            this.layoutManager = layoutManager;
        }
        
        @Override
        public void saveCurrentLayout() {
            try {
                layoutManager.saveLayout();
                LOGGER.info("[Layout] Current layout saved");
            } catch (Exception e) {
                LOGGER.error("[Layout] Failed to save layout: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void showSaveLayoutDialog() {
            if (ChronoBlocksUIManager.this.dialogManager != null) {
                ChronoBlocksUIManager.this.dialogManager.showSaveLayoutDialog();
            }
        }
        
        @Override
        public void showLoadLayoutDialog() {
            if (ChronoBlocksUIManager.this.dialogManager != null) {
                ChronoBlocksUIManager.this.dialogManager.showLoadLayoutDialog();
            }
        }
        
        @Override
        public boolean saveLayoutPreset(String presetName) {
            return layoutManager.saveLayoutPreset(presetName);
        }
        
        @Override
        public java.util.List<String> listLayoutPresets() {
            return layoutManager.listLayoutPresets();
        }
        
        @Override
        public boolean loadLayoutPreset(String presetName) {
            try {
                boolean success = layoutManager.loadLayoutPreset(presetName);
                if (success) {
                    // 强制重新加载布局（包括 ImGui INI 文件）
                    layoutManager.forceReloadLayout();
                    
                    // 重置 DockSpace 布局状态，强制重新应用布局
                    com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                    
                    LOGGER.info("[Layout] Layout preset '{}' loaded and applied", presetName);
                }
                return success;
            } catch (Exception e) {
                LOGGER.error("[Layout] Failed to load layout preset: {}", e.getMessage(), e);
                return false;
            }
        }
        
        @Override
        public boolean deleteLayoutPreset(String presetName) {
            return layoutManager.deleteLayoutPreset(presetName);
        }
        
        @Override
        public void restoreDefaultLayout() {
            try {
                // 1. 删除布局 JSON 文件
                java.nio.file.Path layoutFile = layoutManager.getLayoutFile();
                if (layoutFile != null && java.nio.file.Files.exists(layoutFile)) {
                    java.nio.file.Files.delete(layoutFile);
                    LOGGER.info("[Layout] Deleted layout file: {}", layoutFile);
                }
                
                // 2. 删除 ImGui INI 文件
                java.nio.file.Path iniPath = layoutManager.getImGuiIniPath();
                if (iniPath != null && java.nio.file.Files.exists(iniPath)) {
                    java.nio.file.Files.delete(iniPath);
                    LOGGER.info("[Layout] Deleted ImGui INI file: {}", iniPath);
                }
                
                // 3. 重置内存中的布局数据为默认值（不保存，因为文件已删除）
                // 注意：这里不调用 resetToDefault()，因为它会保存文件
                // 我们直接创建新的默认布局数据，然后强制重新加载
                layoutManager.resetLayoutDataToDefault();
                
                // 4. 强制重新加载布局（因为文件不存在，会保持默认布局数据）
                layoutManager.forceReloadLayout();
                
                // 5. 重置 DockSpace 布局状态，强制重新应用默认布局
                com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                
                LOGGER.info("[Layout] Layout restored to default");
            } catch (Exception e) {
                LOGGER.error("[Layout] Failed to restore default layout: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void reloadLayout() {
            try {
                // 先保存当前布局（如果有未保存的更改）
                layoutManager.saveLayout();
                layoutManager.saveImGuiIni();
                
                // 强制重新加载布局（包括布局数据和 ImGui INI 文件）
                layoutManager.forceReloadLayout();
                
                // 重置 DockSpace 布局状态，强制重新应用布局
                com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                
                LOGGER.info("[Layout] Layout reloaded for current world");
            } catch (Exception e) {
                LOGGER.error("[Layout] Failed to reload layout: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void setUIScale(float scale) {
            // TODO: 实现 UI 缩放
            LOGGER.info("[Layout] Set UI scale: {}", scale);
        }
        
        @Override
        public void setTheme(String theme) {
            if ("Dark".equals(theme)) {
                UITheme.applyDarkTheme();
            } else if ("Light".equals(theme)) {
                // TODO: 实现浅色主题
                LOGGER.warn("[Layout] Light theme not implemented yet");
            }
        }
    }
    
    /**
     * 帮助命令实现（优化：直接调用 MenuBarPanel）
     */
    private class HelpCommandsImpl implements HelpCommands {
        
        private final MenuBarPanel menuBarPanel;
        
        public HelpCommandsImpl(MenuBarPanel menuBarPanel) {
            this.menuBarPanel = menuBarPanel;
        }
        
        @Override
        public void showShortcutHelp() {
            dialogManager.showShortcutsDialog();
        }
        
        @Override
        public void openUserManual() {
            // TODO: 打开用户手册
            LOGGER.info("[Help] Open user manual");
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://chronoblocks.github.io/docs"));
            } catch (Exception e) {
                LOGGER.error("[Help] 打开用户手册失败: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void openVideoTutorial() {
            LOGGER.info("[Help] Open video tutorial");
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://www.youtube.com/@theopote"));
            } catch (Exception e) {
                LOGGER.error("[Help] 打开视频教程失败: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void reportBug() {
            // TODO: 打开反馈页面
            LOGGER.info("[Help] Report bug");
        }
        
        @Override
        public void showAboutDialog() {
            dialogManager.showAboutDialog();
        }
    }
    
    /**
     * 工具命令实现
     */
    private class ToolCommandsImpl implements ToolCommands {
        
        @Override
        public void activateTool(String toolId) {
            if (toolId == null || toolId.isBlank()) {
                return;
            }
            com.chronoblocks.ui.tools.ToolRegistry.get().activate(toolId);
        }
        
        @Override
        public void switchToNextTool() {
            var world = net.minecraft.client.MinecraftClient.getInstance().world;
            if (world != null) {
                com.chronoblocks.core.selection.SelectionManager.get().switchToNextTool(world);
                LOGGER.debug("[ToolCommands] 切换到下一个工具");
            }
        }
        
        @Override
        public void switchToPreviousTool() {
            var world = net.minecraft.client.MinecraftClient.getInstance().world;
            if (world != null) {
                com.chronoblocks.core.selection.SelectionManager.get().switchToPreviousTool(world);
                LOGGER.debug("[ToolCommands] 切换到上一个工具");
            }
        }
        
        @Override
        public void setSelectionMode(com.chronoblocks.core.selection.SelectionOperation operation) {
            var currentTool = com.chronoblocks.core.selection.SelectionManager.get().getCurrentTool();
            if (currentTool instanceof com.chronoblocks.core.selection.SelectionOperationSupport operationSupport) {
                operationSupport.setOperation(operation);
                LOGGER.debug("[ToolCommands] 设置选择操作模式: {}", operation.getDisplayName());
            } else {
                LOGGER.debug("[ToolCommands] 当前工具不支持操作模式切换: {}", 
                    currentTool != null ? currentTool.getId() : "null");
            }
        }
        
        @Override
        public void clearSelection() {
            com.chronoblocks.core.selection.SelectionManager.get().clearSelection();
            LOGGER.debug("[ToolCommands] 清除选择");
        }
        
        @Override
        public String getActiveToolId() {
            return com.chronoblocks.ui.tools.ToolRegistry.get().getActiveToolId();
        }
        
        @Override
        public boolean isToolActive(String toolId) {
            if (toolId == null || toolId.isBlank()) {
                return false;
            }
            String activeId = getActiveToolId();
            return toolId.equals(activeId);
        }
    }
    
    /**
     * 布局操作门面接口
     */
    public interface UILayoutFacade {
        void showAll();
        void hideAll();
        void toggle(String panelName);
        void saveCurrent();
        void restoreDefault();
    }
    
    /**
     * 布局操作门面实现
     */
    private class UILayoutFacadeImpl implements UILayoutFacade {
        
        @Override
        public void showAll() {
            for (IPanel panel : panelRegistry.getAllPanels()) {
                panel.setVisible(true);
            }
        }
        
        @Override
        public void hideAll() {
            for (IPanel panel : panelRegistry.getAllPanels()) {
                if (!(panel instanceof MenuBarPanel)) { // 不隐藏 MenuBar
                    panel.setVisible(false);
                }
            }
        }
        
        @Override
        public void toggle(String panelName) {
            IPanel panel = findPanelByName(panelName);
            if (panel != null) {
                panel.setVisible(!panel.isVisible());
            }
        }
        
        @Override
        public void saveCurrent() {
            try {
                layoutManager.saveLayout();
                LOGGER.info("[Layout] Current layout saved");
            } catch (Exception e) {
                LOGGER.error("[Layout] Failed to save layout: {}", e.getMessage(), e);
            }
        }
        
        @Override
        public void restoreDefault() {
            try {
                java.nio.file.Path iniPath = layoutManager.getImGuiIniPath();
                if (java.nio.file.Files.exists(iniPath)) {
                    java.nio.file.Files.delete(iniPath);
                }
                layoutManager.resetToDefault();
                com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                LOGGER.info("[Layout] Layout restored to default");
            } catch (Exception e) {
                LOGGER.error("[Layout] Failed to restore default layout: {}", e.getMessage(), e);
            }
        }
        
        private IPanel findPanelByName(String panelName) {
            // 面板名称映射（处理显示名称和实际名称的差异）
            String actualName = switch (panelName) {
                case "Timeline" -> "Timeline";
                case "Tool Properties" -> "ToolProperties";
                case "Container" -> "Container";
                case "RenderSettings" -> "RenderSettings";
                case "Toolbar" -> "Toolbar";
                case "SelectionFiler" -> "SelectionFilter";
                case "Properties" -> "Properties";
                case "SelectionHistory" -> "SelectionHistory";
                default -> panelName;
            };
            
            for (IPanel panel : panelRegistry.getAllPanels()) {
                if (actualName.equals(panel.getPanelName())) {
                    return panel;
                }
            }
            return null;
        }
    }

    /**
     * 高级依赖注入构造函数 - 直接使用面板注册表
     * @param panelRegistry 面板注册表
     * @throws IllegalArgumentException 如果面板注册表为null或为空
     */
    public ChronoBlocksUIManager(IPanelRegistry panelRegistry) {
        if (panelRegistry == null) {
            throw new IllegalArgumentException("面板注册表不能为 null");
        }
        if (panelRegistry.isEmpty()) {
            throw new IllegalArgumentException("面板注册表不能为空");
        }

        this.panelRegistry = panelRegistry;
        
        // 应用UI主题 v4.5 🎨
        UITheme.applyDarkTheme();
        LOGGER.info("[ChronoBlocks] UI主题已应用");
        
        // 初始化属性系统 v4.5 📋
        PropertyAdapterBootstrap.initialize();
        LOGGER.info("[ChronoBlocks] 属性系统已初始化");
        
        // 初始化布局持久化管理器
        this.layoutManager = LayoutPersistenceManager.getInstance();
        
        // 初始化对话框管理器
        this.dialogManager = new com.chronoblocks.ui.dialogs.DialogManager();
        // 设置新建动画确认回调
        this.dialogManager.setNewAnimationConfirmCallback(() -> {
            try {
                TimelinePanel timelinePanel = panelRegistry.getPanel(TimelinePanel.class);
                if (timelinePanel != null) {
                    timelinePanel.resetWorkspace(true);
                } else {
                    // 兜底：清空容器并删除时间线存档
                    Objects.requireNonNull(com.chronoblocks.core.container.ContainerManager.getInstance()).clear();
                    com.chronoblocks.ui.timeline.persistence.TimelineModelPersistence.resetForCurrentWorld();
                }
            } catch (Exception e) {
                LOGGER.error("[UIManager] 新建动画重置失败: {}", e.getMessage(), e);
            }
        });

        // 检测并加载当前世界的布局（如果是新世界则使用默认布局，如果是同一世界则加载保存的布局）
        try {
            this.layoutManager.reloadForCurrentWorld("ChronoBlocksUIManager.constructor");
            String currentWorldId = com.chronoblocks.util.WorldIdentifier.getCurrentWorldId();
            LOGGER.info("[ChronoBlocks] 已加载世界 {} 的布局配置", currentWorldId);
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 加载世界布局失败: {}", e.getMessage(), e);
            // 如果加载失败，创建默认布局
            try {
                this.layoutManager.resetToDefault();
                LOGGER.info("[ChronoBlocks] 已创建默认布局");
            } catch (Exception resetException) {
                LOGGER.warn("[ChronoBlocks] 无法创建默认布局: {}", resetException.getMessage(), resetException);
            }
        }

        // 验证必需的面板是否存在
        validateRequiredPanels();

        // 初始化所有面板
        initializeAllPanels();
        
        // 设置面板注册表引用（用于面板间通信）
        setupPanelRegistryReferences();
        
        // 根据布局还原面板可见性
        restorePanelVisibilityFromLayout();

        logInfo("UI Manager initialized with {} panels using advanced IoC", panelRegistry.size());
        logDebugState(() -> "Panel registry: " + panelRegistry.getDebugInfo());
    }

    /**
     * 静态工厂方法 - 从面板列表创建UI管理器
     * @param panels 面板列表
     * @return 完全初始化的UI管理器实例
     * @throws IllegalArgumentException 如果面板列表无效
     */
    public static ChronoBlocksUIManager create(List<IPanel> panels) {
        if (panels == null) {
            throw new IllegalArgumentException("面板列表不能为 null");
        }
        if (panels.isEmpty()) {
            throw new IllegalArgumentException("面板列表不能为空");
        }
        long nullCount = panels.stream().filter(Objects::isNull).count();
        if (nullCount > 0) {
            throw new IllegalArgumentException("面板列表包含 " + nullCount + " 个 null 元素");
        }

        IPanelRegistry registry = new PanelRegistry(panels);
        return create(registry);
    }

    /**
     * 静态工厂方法 - 从面板注册表创建UI管理器
     * @param panelRegistry 面板注册表
     * @return 完全初始化的UI管理器实例
     * @throws IllegalArgumentException 如果面板注册表无效
     */
    public static ChronoBlocksUIManager create(IPanelRegistry panelRegistry) {
        ChronoBlocksUIManager manager = new ChronoBlocksUIManager(panelRegistry);
        manager.initialize();
        return manager;
    }

    /**
     * 私有初始化方法 - 封装完整的初始化流程
     */
    private void initialize() {
        // 强制使用默认布局配置
        try {
            this.layoutManager.resetToDefault();
            LOGGER.info("[ChronoBlocks] 强制使用默认布局配置");
        } catch (Exception e) {
            LOGGER.warn("[ChronoBlocks] 无法重置为默认布局: {}", e.getMessage(), e);
        }

        // 验证必需的面板是否存在
        validateRequiredPanels();

        // 初始化所有面板
        initializeAllPanels();
        
        // 设置面板注册表引用（用于面板间通信）
        setupPanelRegistryReferences();
        
        // 根据布局还原面板可见性
        restorePanelVisibilityFromLayout();

        logInfo("UI Manager initialized with {} panels using modern IoC", panelRegistry.size());
        logDebugState(() -> "Panel registry: " + panelRegistry.getDebugInfo());
    }


    /**
     * 更新动画系统（v4.5 一体化交互系统）
     * <p>
     * 统一的动画更新循环，每帧调用：
     * - 计算帧间隔时间
     * - 驱动 PlaybackController 更新
     * - 同步时间线求值结果
     */
    private void updateAnimationSystem() {
        try {
            // 计算时间增量（deltaTime）
            long currentTime = System.currentTimeMillis();
            float deltaSeconds = (currentTime - lastUpdateTime) / 1000.0f;
            lastUpdateTime = currentTime;
            
            // 限制最大帧间隔，避免长时间暂停后的巨大跳跃
            deltaSeconds = Math.min(deltaSeconds, 0.1f); // 最大100ms
            
            // ⭐ v1.2 改进：不再每帧 bindModel，模型绑定已在生命周期节点完成
            // ✅ 统一播放驱动（替代 TimelinePanel 的 updatePlayback + 一堆 controller tick）
            // 这确保了播放系统在任何情况下（面板隐藏/失焦）都保持一致
            com.chronoblocks.ui.timeline.playback.PlaybackOrchestrator.get().tick(deltaSeconds);
            
        } catch (Exception e) {
            // 静默处理异常，避免中断UI渲染
            if (renderCount % 60 == 0) { // 每秒最多记录一次
                logError("动画系统更新失败", e);
            }
        }
    }

    /**
     * 安全渲染单个面板 - 封装try-catch逻辑
     * @param panel 要渲染的面板
     */
    private void renderPanelSafe(IPanel panel) {
        try {
            panel.render();
        } catch (Exception e) {
            logError("面板 {} 渲染失败", e, panel.getPanelName());
            com.chronoblocks.ui.imgui.ImGuiStateChecker.recordError("UIManager", panel.getPanelName(), e.getMessage());
        }
    }

    /**
     * 渲染UI - 主渲染入口（v4.5 一体化交互系统）
     */
    public void render() {
        if (isDisposed) {
            logWarn("UI Manager已释放，跳过渲染");
            return;
        }

        // 🔄 系统级更新循环 - 驱动动画播放
        updateAnimationSystem();

        // 🎥 视频导出：
        // 目前的抓帧与缩放发生在 GameRenderer.renderWorld 的 HEAD/Tail 钩子中，
        // 这里保留 no-op 调用用于向后兼容（不会触发重复渲染）。
        VideoRenderPipeline.getInstance().processPendingCaptures();

        // 预检查：验证必需的面板是否存在
        if (!validateRequiredPanelsForRender()) {
            logWarn("⚠️ 必需面板缺失，渲染降级UI提示");
            renderEmptyStateUI();
            return;
        }

        try {
            // ImGui状态检查
            com.chronoblocks.ui.imgui.ImGuiStateChecker.beforeRender("ChronoBlocksUIManager");
            
            // 性能监控：递增渲染计数
            renderCount++;

            // 定期性能日志（仅在debug模式下且间隔足够长）
            long currentTime = System.currentTimeMillis();
            if (LOGGER.isDebugEnabled() && currentTime - lastPerformanceLog > PERFORMANCE_LOG_INTERVAL) {
                int panelCount = panelRegistry.size();
                LOGGER.debug("[ChronoBlocks] 性能统计 - 渲染次数: {}, 面板数量: {}", renderCount, panelCount);
                lastPerformanceLog = currentTime;
            }

            // 0) 摄像机视口 Overlay（在所有面板之前渲染，使其被面板遮挡）
            renderCameraViewportOverlay();

            // 1. 统一处理菜单栏（如果有的话）
            panelRegistry.getPanelOptional(MenuBarPanel.class)
                .filter(panel -> {
                    // 菜单栏：渲染可见的，或者需要占位的隐藏菜单栏
                    if (panel.isVisible()) {
                        return true;
                    }
                    return panel.isHidden() && panel.shouldReserveDockSpaceWhenHidden();
                })
                .ifPresent(this::renderPanelSafe);

            // 2. 渲染DockSpace布局 - 为其他面板提供停靠区域
            int dockspaceId = renderDockSpaceWithPanels();
            if (dockspaceId == -1) {
                // DockSpace 未就绪时，跳过本帧的面板渲染，避免 Begin/End 失配
                logWarn("DockSpace 未就绪，跳过本帧面板渲染");
                return;
            }

            // *** 暂时禁用自动布局，让面板独立渲染 ***
            // 先让面板能够正常显示，再解决布局问题

            // 3. 一次循环渲染所有非菜单栏的面板（包括需要占位的隐藏面板）
            panelRegistry.getAllPanels().stream()
                .filter(Objects::nonNull)
                .filter(panel -> !(panel instanceof MenuBarPanel))
                .filter(panel -> {
                    // 渲染可见面板，或者需要DockSpace占位的隐藏面板
                    if (panel.isVisible()) {
                        return true;
                    }
                    // 检查是否是需要占位的隐藏面板
                    if (panel instanceof AbstractPanel abstractPanel) {
                        return abstractPanel.isHidden() && abstractPanel.shouldReserveDockSpaceWhenHidden();
                    }
                    return false;
                })
                .forEach(this::renderPanelSafe);

            // 4) 全局弹窗/Overlay（不属于某个 Dock 面板）
            VideoExportDialog.renderIfNeeded(this);
            videoExportProgressOverlay.render(this);
            
            // 5) 渲染对话框（集中管理）
            dialogManager.renderDialogs();
            
            // ❌ 暂时禁用中央保护区域渲染 - 这会导致ImGui认为鼠标在UI上
            // renderCentralProtectionArea();
            
            // 调试：记录面板渲染状态
            if (LOGGER.isDebugEnabled() && renderCount % 300 == 0) { // 每5秒记录一次(60fps)
                logPanelRenderingStatus();
            }
            
            // ImGui 会自动保存布局到 imgui.ini 文件，无需手动管理

        } catch (Exception e) {
            // 特别处理并发修改异常
            if (e instanceof java.util.ConcurrentModificationException) {
                logWarn("⚠️ 检测到并发修改异常，跳过本次渲染");
                logDebug("并发修改异常详情: {}", e.getMessage());
            } else {
                logError("UI渲染过程中发生错误", e);
            }
            
            // 在异常情况下强制清理ImGui状态
            com.chronoblocks.ui.imgui.ImGuiStateChecker.forceCleanupStack("ChronoBlocksUIManager异常恢复");
        } finally {
            // 渲染后状态检查
            com.chronoblocks.ui.imgui.ImGuiStateChecker.afterRender("ChronoBlocksUIManager");
            
            // 检查ImGui调用栈状态，确保所有Begin/End调用都匹配
            com.chronoblocks.ui.imgui.ImGuiStateChecker.checkStackState("ChronoBlocksUIManager渲染结束");
            
            // 如果栈不为空，记录详细信息用于调试
            int stackDepth = com.chronoblocks.ui.imgui.ImGuiStateChecker.getStackDepth();
            if (stackDepth > 0) {
                logWarn("⚠️ ImGui调用栈仍有 " + stackDepth + " 个未匹配的调用");
                if (LOGGER.isDebugEnabled()) {
                    java.util.List<String> stackCopy = com.chronoblocks.ui.imgui.ImGuiStateChecker.getStackCopy();
                    logDebug("未匹配的调用栈: " + stackCopy);
                }
            }
        }
    }

    // =========================================================
    // Video Export API（给 MenuBarPanel / Dialog 调用）
    // =========================================================

    /**
     * 打开“视频导出”对话框（由 MenuBarPanel 调用）。
     */
    public void openVideoExportDialog() {
        VideoExportDialog.open();
    }

    /**
     * 启动导出任务（由 VideoExportDialog 调用）。
     *
     * <p>当前为任务骨架：先跑通 UI + 进度回调，后续替换为真实离屏渲染与编码器。</p>
     */
    public void startVideoExport(VideoExportSettings settings) {
        if (settings == null) {
            return;
        }

        videoExportProgressOverlay.show();
        videoExportProgressOverlay.setProgress(0f, "已提交导出任务，准备中...");
        LOGGER.info("[VideoExport] 用户请求开始导出：{}", settings.outputPath);

        VideoExportManager.getInstance().start(settings, new VideoExportManager.ProgressListener() {
            @Override
            public void onProgress(float progress01, String message) {
                videoExportProgressOverlay.setProgress(progress01, message);
            }

            @Override
            public void onCompleted(boolean success, String errorMessage) {
                if (success) {
                    videoExportProgressOverlay.setFinished(true, "导出完成");
                    LOGGER.info("[VideoExport] 导出完成: {}", settings.outputPath);
                } else {
                    String msg = errorMessage != null ? errorMessage : "导出失败";
                    // 失败时不要立刻 hide，否则用户会感觉“点了没反应”
                    videoExportProgressOverlay.setFinished(false, msg);
                    LOGGER.warn("[VideoExport] {}", msg);
                }
            }
        });
    }

    /**
     * 请求取消导出（由 Overlay 的“取消导出”按钮调用）。
     */
    public void requestCancelVideoExport() {
        VideoExportManager.getInstance().requestCancel();
    }

    /**
     * 渲染DockSpace布局并包含面板 - 完全透明版本
     * <p>
     * 使用全屏DockSpace提供完整的停靠区域，但保持完全透明背景。
     * 在DockSpace内部渲染所有面板，确保它们正确停靠。
     * @return DockSpace ID，如果创建失败返回-1
     */
    private int renderDockSpaceWithPanels() {
        imgui.ImGuiViewport viewport = imgui.ImGui.getMainViewport();

        // 设置DockSpace覆盖整个工作区域，菜单栏独立渲染
        imgui.ImGui.setNextWindowPos(viewport.getWorkPosX(), viewport.getWorkPosY());
        imgui.ImGui.setNextWindowSize(viewport.getWorkSizeX(), viewport.getWorkSizeY());
        imgui.ImGui.setNextWindowViewport(viewport.getID());

        int dockspaceId = -1;
        
        // 使用try-with-resources管理样式
        try (var style = ImGuiUtils.pushStyleWithCounter()) {
            // 设置样式变量 - 完全透明
            style.setVar(imgui.flag.ImGuiStyleVar.WindowRounding, 0.0f);
            style.setVar(imgui.flag.ImGuiStyleVar.WindowBorderSize, 0.0f);
            style.setVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);

            // 设置背景透明颜色，但保留停靠视觉反馈
            style.setColor(imgui.flag.ImGuiCol.WindowBg, 0.0f, 0.0f, 0.0f, 0.0f);
            style.setColor(imgui.flag.ImGuiCol.ChildBg, 0.0f, 0.0f, 0.0f, 0.0f);
            style.setColor(imgui.flag.ImGuiCol.DockingEmptyBg, 0.0f, 0.0f, 0.0f, 0.0f);
            style.setColor(imgui.flag.ImGuiCol.DockingPreview, 0.2f, 0.6f, 1.0f, 0.3f);
            style.setColor(imgui.flag.ImGuiCol.HeaderHovered, 0.0f, 0.8f, 0.4f, 0.4f);

            // 使用try-with-resources管理窗口
            try (var window = ImGuiUtils.beginWindow(DOCKSPACE_WINDOW_NAME, DOCKSPACE_FLAGS)) {
                if (window != null) {
                    try {
                        com.chronoblocks.ui.imgui.ImGuiStateChecker.recordBegin("DockSpace", DOCKSPACE_WINDOW_NAME);
                        
                        // 创建DockSpace - 全屏透明，基础停靠功能
                        dockspaceId = imgui.ImGui.getID(DOCKSPACE_ID);
                        // 🔧 使用PassthruCentralNode标志让中央空白区域不捕获鼠标
                        // 这样ImGui只会在面板区域捕获鼠标，中央区域完全透明
                        imgui.ImGui.dockSpace(dockspaceId, 0, 0, imgui.internal.flag.ImGuiDockNodeFlags.PassthruCentralNode);
                        
                        // 应用默认布局（仅在首次打开且无保存的布局时）
                        if (dockspaceId != -1 && isFirstLayout) {
                            // 检查是否存在保存的布局文件（按世界区分）
                            java.nio.file.Path iniPath = layoutManager.getImGuiIniPath();
                            boolean hasCustomLayout = java.nio.file.Files.exists(iniPath);
                            
                            if (!hasCustomLayout) {
                                // 没有自定义布局，应用默认布局
                                LOGGER.info("[ChronoBlocks] 世界 {} 未找到保存的布局，应用默认布局", 
                                    com.chronoblocks.util.WorldIdentifier.getCurrentWorldId());
                                com.chronoblocks.ui.layout.DockSpaceLayoutBuilder.buildDefaultLayout(dockspaceId);
                            } else {
                                LOGGER.info("[ChronoBlocks] 已加载世界 {} 的保存布局", 
                                    com.chronoblocks.util.WorldIdentifier.getCurrentWorldId());
                            }
                            
                            isFirstLayout = false;
                        }
                        
                        // 检查世界是否切换（每次渲染时检查）
                        if (com.chronoblocks.util.WorldIdentifier.hasWorldChanged()) {
                            LOGGER.info("[ChronoBlocks] 检测到世界切换，重新加载布局");
                            // 使用 reloadForCurrentWorld 自动处理保存和加载
                            layoutManager.reloadForCurrentWorld("render()");
                            // 更新 ImGui INI 路径
                            com.chronoblocks.ui.imgui.ImGuiRenderer.getInstance().checkAndUpdateIniPath();
                            // 重置布局标志，以便在新世界中检查是否需要应用默认布局
                            isFirstLayout = true;
                            com.chronoblocks.core.container.persistence.ContainerPersistence.loadForCurrentWorld(
                                com.chronoblocks.core.container.ContainerManager.getInstance());

                            // ⭐ 同步时间线：切档时不能把上一个存档的轨道/关键帧带过来
                            try {
                                TimelinePanel timelinePanel = panelRegistry.getPanel(TimelinePanel.class);
                                if (timelinePanel != null) {
                                    timelinePanel.reloadForCurrentWorld();
                                    LOGGER.info("[ChronoBlocks] 已按新世界重新加载时间线数据");
                                }
                            } catch (Exception e) {
                                LOGGER.warn("[ChronoBlocks] 切档时重新加载时间线失败: {}", e.getMessage(), e);
                            }
                        }
                        
                        // 注意：面板渲染应该在 DockSpace 外部进行，而不是在内部
                        // 这样可以确保中央区域保持空白，不被面板占满
                        
                        com.chronoblocks.ui.imgui.ImGuiStateChecker.recordEnd("DockSpace", DOCKSPACE_WINDOW_NAME);
                    } catch (Exception e) {
                        logError("DockSpace创建失败", e);
                        com.chronoblocks.ui.imgui.ImGuiStateChecker.recordError("DockSpace", "dockSpace创建", e.getMessage());
                        dockspaceId = -1;
                    }
                } else {
                    logWarn("DockSpace窗口未能开始，可能被其他窗口遮挡");
                }
            } catch (Exception e) {
                logError("DockSpace渲染过程中发生错误", e);
            }
        } catch (Exception e) {
            logError("DockSpace样式管理过程中发生错误", e);
        }
        
        return dockspaceId;
    }

    /**
     * 记录面板渲染状态用于调试
     */
    private void logPanelRenderingStatus() {
        try {
            StringBuilder status = new StringBuilder("[ChronoBlocks] Panel Status:\n");
            
            panelRegistry.getAllPanels().forEach(panel -> {
                if (panel != null) {
                    String panelName = panel.getClass().getSimpleName();
                    boolean visible = panel.isVisible();
                    boolean shouldRender = visible;
                    boolean isHidden = false;
                    boolean shouldReserveSpace = false;
                    
                    if (panel instanceof AbstractPanel abstractPanel) {
                        isHidden = abstractPanel.isHidden();
                        shouldReserveSpace = abstractPanel.shouldReserveDockSpaceWhenHidden();
                        shouldRender = visible || (isHidden && shouldReserveSpace);
                    }
                    
                    status.append(String.format("  %s: visible=%s, hidden=%s, reserveSpace=%s, shouldRender=%s\n",
                        panelName, visible, isHidden, shouldReserveSpace, shouldRender));
                }
            });
            
            LOGGER.debug(status.toString());
        } catch (Exception e) {
            LOGGER.debug("[ChronoBlocks] Failed to log panel status: {}", e.getMessage());
        }
    }


    /**
     * 渲染空状态UI - 当面板列表为空时显示
     */
    private void renderEmptyStateUI() {
        try {
            LOGGER.debug("[ChronoBlocks] 渲染空状态UI提示");

            // 显示简洁的错误提示，不显示详细的调试信息
            if (ImGui.begin("ChronoBlocks", ImGuiWindowFlags.AlwaysAutoResize)) {
                ImGui.textColored(0.8f, 0.2f, 0.2f, 1.0f, "ChronoBlocks UI 初始化失败");
                ImGui.separator();
                ImGui.text("面板状态: 无可用面板");
                ImGui.text("请检查面板配置和初始化");
                ImGui.separator();
                ImGui.text("如需详细调试信息，请查看控制台日志");
            }
            ImGui.end();

        } catch (Exception e) {
            logError("渲染空状态UI失败", e);
        }
    }

    /**
     * 初始化所有面板 - 在UIConfiguration注入后立即调用
     * 确保所有面板在渲染前完成初始化
     */
    private void initializeAllPanels() {
        logDebug("开始初始化所有面板");

        int successCount = 0;
        int failureCount = 0;

        for (IPanel panel : panelRegistry.getAllPanels()) {
            try {
                if (panel != null) {
                    logDebug("初始化面板: {}", panel.getPanelName());
                    panel.initialize();
                    successCount++;
                }
            } catch (Exception e) {
                failureCount++;
                logError("初始化面板 {} 失败", e, panel.getPanelName());
            }
        }

        logInfo("面板初始化完成 - 成功: {}, 失败: {}", successCount, failureCount);

        if (failureCount > 0) {
            logWarn("有 {} 个面板初始化失败，可能影响UI功能", failureCount);
        }
    }
    
    /**
     * 设置面板注册表引用（用于面板间通信）
     * <p>
     * 使用Aware接口设计模式，自动为实现了特定接口的面板注入依赖。
     * 这遵循开闭原则，未来新增面板只需实现对应接口即可获得依赖注入。
     * 
     * <p>优化：为 MenuBarPanel 注入 UICommandFacade 而不是 ChronoBlocksUIManager
     */
    private void setupPanelRegistryReferences() {
        logDebug("为所有符合条件的面板设置依赖引用...");
        
        int registryAwareCount = 0;
        int commandFacadeCount = 0;
        
        for (IPanel panel : panelRegistry.getAllPanels()) {
            if (panel != null) {
                // 注入面板注册表引用
                if (panel instanceof IPanelRegistryAware) {
                    try {
                        ((IPanelRegistryAware) panel).setPanelRegistry(panelRegistry);
                        registryAwareCount++;
                        logDebug("为面板 '{}' 注入了 PanelRegistry", panel.getPanelName());
                    } catch (Exception e) {
                        logError("为面板 '{}' 注入 PanelRegistry 失败", e, panel.getPanelName());
                    }
                }
                
                // 为 MenuBarPanel 和 StatusBarPanel 注入 UICommandFacade
                if (panel instanceof MenuBarPanel) {
                    try {
                        ((MenuBarPanel) panel).setCommands(getOrCreateCommandFacade());
                        commandFacadeCount++;
                        logDebug("为面板 '{}' 注入了 UICommandFacade", panel.getPanelName());
                    } catch (Exception e) {
                        logError("为面板 '{}' 注入 UICommandFacade 失败", e, panel.getPanelName());
                    }
                }
                
                if (panel instanceof StatusBarPanel) {
                    try {
                        ((StatusBarPanel) panel).setCommands(getOrCreateCommandFacade());
                        commandFacadeCount++;
                        logDebug("为面板 '{}' 注入了 UICommandFacade", panel.getPanelName());
                    } catch (Exception e) {
                        logError("为面板 '{}' 注入 UICommandFacade 失败", e, panel.getPanelName());
                    }
                }
                
                if (panel instanceof ToolbarPanel) {
                    try {
                        ((ToolbarPanel) panel).setCommands(getOrCreateCommandFacade());
                        commandFacadeCount++;
                        logDebug("为面板 '{}' 注入了 UICommandFacade", panel.getPanelName());
                    } catch (Exception e) {
                        logError("为面板 '{}' 注入 UICommandFacade 失败", e, panel.getPanelName());
                    }
                }
                
                // ⭐ TimelinePanel：注入 UICommandFacade（用于播放控制等）
                if (panel instanceof TimelinePanel) {
                    try {
                        TimelinePanel timelinePanel = (TimelinePanel) panel;
                        timelinePanel.setCommands(getOrCreateCommandFacade());
                        commandFacadeCount++;
                        
                        // ⭐ v1.2 改进：在面板注册时绑定 Orchestrator（生命周期节点）
                        // 这确保了模型绑定只在生命周期节点发生，而不是每帧
                        com.chronoblocks.ui.timeline.model.TimelineModel model = timelinePanel.getModel();
                        if (model != null) {
                            com.chronoblocks.ui.timeline.playback.PlaybackOrchestrator.get().bindModel(model);
                            logDebug("为面板 '{}' 绑定了 PlaybackOrchestrator", panel.getPanelName());
                        }
                        
                        logDebug("为面板 '{}' 注入了 UICommandFacade", panel.getPanelName());
                    } catch (Exception e) {
                        logError("为面板 '{}' 注入 UICommandFacade 失败", e, panel.getPanelName());
                    }
                }
                
                // 注入UI管理器引用（其他面板仍可使用）
                if (panel instanceof IUIManagerAware && !(panel instanceof MenuBarPanel)) {
                    try {
                        ((IUIManagerAware) panel).setUIManager(this);
                        logDebug("为面板 '{}' 注入了 UIManager", panel.getPanelName());
                    } catch (Exception e) {
                        logError("为面板 '{}' 注入 UIManager 失败", e, panel.getPanelName());
                    }
                }
            }
        }
        
        logDebug("面板依赖引用设置完成 - PanelRegistry: {} 个面板, UICommandFacade: {} 个面板", 
                registryAwareCount, commandFacadeCount);
    }
    
    /**
     * 根据布局配置还原面板可见性
     */
    private void restorePanelVisibilityFromLayout() {
        if (layoutManager == null || panelRegistry == null || panelRegistry.isEmpty()) {
            return;
        }
        
        panelRegistry.getAllPanels().forEach(panel -> {
            if (panel == null) {
                return;
            }
            Boolean storedVisibility = layoutManager.getStoredPanelVisibility(panel.getPanelName());
            if (storedVisibility != null) {
                try {
                    panel.setVisible(storedVisibility);
                    logDebug("面板 {} 应用可见性: {}", panel.getPanelName(), storedVisibility);
                } catch (Exception e) {
                    logWarn("面板 {} 应用可见性失败: {}", panel.getPanelName(), e.getMessage());
                }
            }
        });
    }

    /**
     * 验证必需的面板是否存在（用于渲染前的预检查）
     * @return true如果所有必需面板都存在，false如果关键面板缺失
     */
    private boolean validateRequiredPanelsForRender() {
        if (panelRegistry == null || panelRegistry.isEmpty()) {
            return false;
        }

        // 检查关键面板是否存在
        MenuBarPanel menuBarPanel = panelRegistry.getPanel(MenuBarPanel.class);
        return menuBarPanel != null;
    }

    /**
     * 验证必需的面板是否存在（用于构造函数初始化）
     * @throws IllegalStateException 如果关键面板缺失且无法提供降级方案
     */
    private void validateRequiredPanels() {
        boolean hasCriticalErrors = false;
        StringBuilder errorMessage = new StringBuilder("关键面板缺失: ");

        // 关键面板检查 - 这些面板缺失会导致UI功能严重不完整
        MenuBarPanel menuBarPanel = panelRegistry.getPanel(MenuBarPanel.class);
        if (menuBarPanel == null) {
            logWarn("关键面板缺失: 菜单栏面板");
            errorMessage.append("菜单栏 ");
            hasCriticalErrors = true;
        }


        // 重要面板检查 - 这些面板缺失会影响用户体验但不致命
        ToolbarPanel toolbarPanel = panelRegistry.getPanel(ToolbarPanel.class);
        if (toolbarPanel == null) {
            logWarn("重要面板缺失: 工具栏面板 - 将提供降级UI提示");
        }

        TimelinePanel timelinePanel = panelRegistry.getPanel(TimelinePanel.class);
        if (timelinePanel == null) {
            logWarn("重要面板缺失: 时间轴面板 - 将提供降级UI提示");
        }

        PropertiesPanel propertiesPanel = panelRegistry.getPanel(PropertiesPanel.class);
        if (propertiesPanel == null) {
            logWarn("重要面板缺失: 属性面板 - 将提供降级UI提示");
        }

        ContainerPanel containerPanel = panelRegistry.getPanel(ContainerPanel.class);
        if (containerPanel == null) {
            logWarn("重要面板缺失: 容器面板 - 将提供降级UI提示");
        }

        // 如果关键面板缺失，抛出异常
        if (hasCriticalErrors) {
            errorMessage.append("- UI无法正常初始化");
            throw new IllegalStateException(errorMessage.toString());
        }
    }

    /**
     * 🎥 渲染摄像机视口 Overlay（导出画幅框、安全区、参考线等）
     * 
     * <p>在屏幕上叠加显示摄像机导出画幅区域，不裁切 Minecraft 世界渲染。
     * 这是一个"导演监视器"功能，帮助用户在编辑时看到最终导出会被裁切的区域。
     * 
     * <p>渲染内容：
     * <ul>
     *   <li>导出画幅边框 - 显示未来会被导出的区域</li>
     *   <li>Letterbox 遮罩 - 显示会被裁切的黑边区域</li>
     *   <li>安全区 - Title Safe / Action Safe 参考线</li>
     *   <li>参考线 - 中心十字、三分法网格</li>
     * </ul>
     * 
     * <p>设计原则：
     * <ul>
     *   <li>✅ 全屏照常渲染 Minecraft 世界</li>
     *   <li>✅ 在背景层叠加显示导出区域框（会被面板遮挡）</li>
     *   <li>❌ 不裁切 Minecraft 渲染本身</li>
     *   <li>❌ 不影响鼠标操作和调试</li>
     * </ul>
     */
    private void renderCameraViewportOverlay() {
        try {
            // 获取激活的摄像机轨道
            TimelineCameraController cameraController = TimelineCameraController.getInstance();
            TimelineTrack activeCameraTrack = cameraController.getActiveCameraTrack();
            
            if (activeCameraTrack == null) {
                // 没有激活的摄像机轨道，不显示任何 Overlay
                return;
            }
            
            // 获取摄像机视口配置
            CameraViewportProfile profile = activeCameraTrack.getCameraViewportProfile();
            if (profile == null) {
                return;
            }
            
            // 使用 ImGui Background DrawList（背景层绘制，会被面板遮挡）
            imgui.ImDrawList dl = ImGui.getBackgroundDrawList();
            if (dl == null) {
                return;
            }
            
            // 获取屏幕尺寸
            imgui.ImGuiIO io = ImGui.getIO();
            float screenW = io.getDisplaySizeX();
            float screenH = io.getDisplaySizeY();
            
            // 计算导出画幅区域（Fit 或 Fill）
            ViewportMath.ViewportRects viewport;
            CameraViewportProfile.PreviewScalePolicy scalePolicy = profile.getPreviewScalePolicy();
            float targetAspect = profile.getAspectRatio();
            
            if (scalePolicy == CameraViewportProfile.PreviewScalePolicy.FIT) {
                viewport = ViewportMath.computeFit(0, 0, screenW, screenH, targetAspect);
            } else {
                viewport = ViewportMath.computeFill(0, 0, screenW, screenH, targetAspect);
            }
            
            // 1️⃣ Letterbox 遮罩（先画，盖在世界上面）
            if (profile.isShowLetterboxMask()) {
                drawLetterboxMask(dl, viewport, profile.getLetterboxAlpha());
            }
            
            // 2️⃣ 导出画幅边框（非常重要）
            int borderColor = TimelineColors.rgba(255, 255, 255, 255); // 白色
            float borderThickness = 2.0f;
            dl.addRect(
                viewport.innerX, viewport.innerY,
                viewport.innerX2(), viewport.innerY2(),
                borderColor, 0f, 0, borderThickness
            );
            
            // 3️⃣ 安全区 / 构图辅助
            if (profile.isShowSafeAreas()) {
                drawSafeAreas(dl, viewport, profile.getActionSafe(), profile.getTitleSafe());
            }
            
            // 4️⃣ 参考线
            if (profile.isShowRuleOfThirds()) {
                drawRuleOfThirds(dl, viewport);
            }
            
            if (profile.isShowCenterCross()) {
                drawCenterCross(dl, viewport);
            }
            
        } catch (Exception e) {
            LOGGER.debug("[ChronoBlocksUIManager] 渲染摄像机视口 Overlay 时出错: {}", e.getMessage(), e);
            // 不抛出异常，避免影响其他 UI 渲染
        }
    }
    
    /**
     * 绘制 Letterbox 遮罩（黑边）
     */
    private void drawLetterboxMask(imgui.ImDrawList dl, ViewportMath.ViewportRects viewport, float alpha) {
        // 使用 TimelineColors.rgba() 创建 ARGB 格式颜色
        int maskColor = TimelineColors.rgba(0, 0, 0, (int)(alpha * 255)); // 黑色，带透明度
        
        // 上黑边
        if (viewport.innerY > viewport.outerY) {
            dl.addRectFilled(
                viewport.outerX, viewport.outerY,
                viewport.outerX + viewport.outerW, viewport.innerY,
                maskColor
            );
        }
        
        // 下黑边
        float bottomY = viewport.outerY + viewport.outerH;
        if (viewport.innerY2() < bottomY) {
            dl.addRectFilled(
                viewport.outerX, viewport.innerY2(),
                viewport.outerX + viewport.outerW, bottomY,
                maskColor
            );
        }
        
        // 左黑边
        if (viewport.innerX > viewport.outerX) {
            dl.addRectFilled(
                viewport.outerX, viewport.innerY,
                viewport.innerX, viewport.innerY2(),
                maskColor
            );
        }
        
        // 右黑边
        float rightX = viewport.outerX + viewport.outerW;
        if (viewport.innerX2() < rightX) {
            dl.addRectFilled(
                viewport.innerX2(), viewport.innerY,
                rightX, viewport.innerY2(),
                maskColor
            );
        }
    }
    
    /**
     * 绘制安全区（Title Safe / Action Safe）
     */
    private void drawSafeAreas(imgui.ImDrawList dl, ViewportMath.ViewportRects viewport, 
                                float actionSafe, float titleSafe) {
        // Action Safe（动作安全区）
        float actionW = viewport.innerW * actionSafe;
        float actionH = viewport.innerH * actionSafe;
        float actionX = viewport.innerX + (viewport.innerW - actionW) * 0.5f;
        float actionY = viewport.innerY + (viewport.innerH - actionH) * 0.5f;
        
        int actionColor = TimelineColors.rgba(0, 255, 0, 200); // 绿色，半透明
        dl.addRect(actionX, actionY, actionX + actionW, actionY + actionH, actionColor, 0f, 0, 1.0f);
        
        // Title Safe（标题安全区）
        float titleW = viewport.innerW * titleSafe;
        float titleH = viewport.innerH * titleSafe;
        float titleX = viewport.innerX + (viewport.innerW - titleW) * 0.5f;
        float titleY = viewport.innerY + (viewport.innerH - titleH) * 0.5f;
        
        int titleColor = TimelineColors.rgba(255, 0, 0, 200); // 红色，半透明
        dl.addRect(titleX, titleY, titleX + titleW, titleY + titleH, titleColor, 0f, 0, 1.0f);
    }
    
    /**
     * 绘制三分法网格
     */
    private void drawRuleOfThirds(imgui.ImDrawList dl, ViewportMath.ViewportRects viewport) {
        int lineColor = TimelineColors.rgba(255, 255, 255, 136); // 半透明白色 (136/255 ≈ 0.53)
        
        // 垂直三等分线
        float thirdW = viewport.innerW / 3.0f;
        float v1 = viewport.innerX + thirdW;
        float v2 = viewport.innerX + thirdW * 2.0f;
        dl.addLine(v1, viewport.innerY, v1, viewport.innerY2(), lineColor, 1.0f);
        dl.addLine(v2, viewport.innerY, v2, viewport.innerY2(), lineColor, 1.0f);
        
        // 水平三等分线
        float thirdH = viewport.innerH / 3.0f;
        float h1 = viewport.innerY + thirdH;
        float h2 = viewport.innerY + thirdH * 2.0f;
        dl.addLine(viewport.innerX, h1, viewport.innerX2(), h1, lineColor, 1.0f);
        dl.addLine(viewport.innerX, h2, viewport.innerX2(), h2, lineColor, 1.0f);
    }
    
    /**
     * 绘制中心十字参考线
     */
    private void drawCenterCross(imgui.ImDrawList dl, ViewportMath.ViewportRects viewport) {
        int lineColor = TimelineColors.rgba(255, 255, 255, 136); // 半透明白色 (136/255 ≈ 0.53)
        
        float centerX = viewport.innerX + viewport.innerW * 0.5f;
        float centerY = viewport.innerY + viewport.innerH * 0.5f;
        
        // 垂直中心线
        dl.addLine(centerX, viewport.innerY, centerX, viewport.innerY2(), lineColor, 1.0f);
        
        // 水平中心线
        dl.addLine(viewport.innerX, centerY, viewport.innerX2(), centerY, lineColor, 1.0f);
    }

    /**
     * 释放资源 - 增强版本
     * <p>
     * 提供更好的错误处理和资源管理：
     * - 使用finally块确保isDisposed状态正确设置
     * - 对关键清理失败抛出RuntimeException
     * - 区分关键错误和非关键错误
     * - 确保资源完全释放
     */
    public void dispose() {
        if (isDisposed) {
            logDebug("UI Manager已释放，跳过重复释放");
            return;
        }

        logInfo("开始释放UI Manager资源");

        // 用于跟踪清理过程中的错误
        RuntimeException criticalError = null;
        int panelDisposalErrors = 0;
        int totalPanels = 0;

        try {
            // 清理ImGui DockSpace状态，防止第二次打开时的冲突
            try {
                logDebug("清理ImGui DockSpace状态");
                // 获取DockSpace ID并清理
                int dockspaceId = ImGui.getID(DOCKSPACE_ID);
                if (dockspaceId != 0) {
                    imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
                    logDebug("DockSpace节点已清理");
                }
            } catch (Exception e) {
                logError("清理DockSpace状态时发生错误", e);
                // DockSpace清理失败不是关键错误，继续处理
            }

            // 释放所有面板
            if (panelRegistry != null && !panelRegistry.isEmpty()) {
                totalPanels = panelRegistry.size();
                logDebug("释放 {} 个面板", totalPanels);

                for (IPanel panel : panelRegistry.getAllPanels()) {
                    if (panel != null) {
                        try {
                            panel.dispose();
                        } catch (Exception e) {
                            panelDisposalErrors++;
                            logError("释放面板 {} 时发生错误", e);
                            // 面板释放失败不是关键错误，继续处理其他面板
                        }
                    }
                }

                // 清空面板注册表，防止重复释放
                try {
                    panelRegistry.clear();
                    logDebug("面板列表已清空");
                } catch (Exception e) {
                    // 面板注册表清理失败是关键错误
                    criticalError = new RuntimeException("面板注册表清理失败", e);
                    logError("面板注册表清理失败", e);
                }
            } else {
                logDebug("面板列表为空，无需释放");
            }

        } catch (Exception e) {
            // 其他未预期的错误
            criticalError = new RuntimeException("UI Manager资源释放过程中发生未预期错误", e);
            logError("UI Manager资源释放过程中发生未预期错误", e);
        } finally {
            // 确保无论是否异常都会标记为已释放
            isDisposed = true;
            
            // 释放布局管理器
            if (layoutManager != null) {
                try {
                    layoutManager.dispose();
                } catch (Exception e) {
                    logError("释放布局管理器时发生错误", e);
                }
            }

            // 记录释放结果
            if (criticalError != null) {
                logWarn("UI Manager资源释放完成，但有关键错误");
            } else if (panelDisposalErrors > 0) {
                LOGGER.warn("[ChronoBlocks] UI Manager资源释放完成，但有 {} 个面板释放失败", panelDisposalErrors);
            } else {
                logInfo("UI Manager资源释放完成 - 成功释放 {} 个面板", totalPanels);
            }
        }

        // 如果有关键错误，在finally块后抛出，确保isDisposed已设置
        if (criticalError != null) {
            throw criticalError;
        }

        // 如果面板释放失败率过高，发出警告
        if (totalPanels > 0 && (double) panelDisposalErrors / totalPanels > 0.5) {
            LOGGER.warn("[ChronoBlocks] 面板释放失败率过高: {}/{}，可能存在严重问题", panelDisposalErrors, totalPanels);
        }
    }
}