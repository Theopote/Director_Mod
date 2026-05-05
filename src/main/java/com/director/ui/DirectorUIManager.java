package com.director.ui;

import com.director.ui.panels.IPanel;
import com.director.ui.panels.MenuBarPanel;
import com.director.ui.panels.ToolbarPanel;
import com.director.ui.panels.TimelinePanel;
import com.director.ui.panels.PropertiesPanel;
import com.director.ui.panels.ContainerPanel;
import com.director.ui.di.UIConfiguration;
import com.director.ui.di.IPanelRegistry;
import com.director.ui.di.PanelRegistry;
import com.director.ui.layout.LayoutPersistenceManager;
import com.director.ui.coordinator.UIPanelManager;
import com.director.ui.coordinator.UIPlaybackBridge;
import com.director.ui.coordinator.UIShortcutCoordinator;
import com.director.ui.coordinator.UIDialogCoordinator;
import com.director.ui.imgui.ImGuiUtils;
import com.director.ui.properties.PropertyAdapterBootstrap;
import com.director.export.video.VideoExportManager;
import com.director.export.video.VideoExportSettings;
import com.director.export.video.VideoRenderPipeline;
import com.director.ui.video.VideoExportDialog;
import com.director.ui.video.VideoExportProgressOverlay;
import com.director.ui.timeline.model.camera.CameraViewportProfile;
import com.director.ui.timeline.model.camera.ViewportMath;
import com.director.ui.timeline.model.TimelineTrack;
import com.director.ui.timeline.playback.camera.TimelineCameraController;
import com.director.ui.timeline.render.TimelineColors;
import com.director.ui.commands.UICommandFacade;
import com.director.ui.commands.ProjectCommands;
import com.director.ui.commands.TimelineCommands;
import com.director.ui.commands.PlaybackCommands;
import com.director.ui.commands.CameraCommands;
import com.director.ui.commands.ViewCommands;
import com.director.ui.commands.LayoutCommands;
import com.director.ui.commands.HelpCommands;
import com.director.ui.commands.ToolCommands;
import com.director.ui.commands.ContainerCommands;
import com.director.ui.commands.SelectionFilterCommands;
import com.director.core.selection.block_filters.FilterIds;
import com.director.ui.timeline.model.ITimelineProject;
import com.director.ui.timeline.model.camera.path.CameraPathType;
import com.director.ui.timeline.model.TimelineModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import imgui.ImGui;

/**
 * Director UI Manager - 核心UI管理器
 * <p>
 * 这是 Director 动画编辑器的核心UI管理器，负责协调和管理所有UI面板的渲染和交互。
 * 采用现代化的依赖注入架构，提供高度可扩展和可维护的UI系统。
 *
 * <h3>职责协调器（4 个子模块）：</h3>
 * <ul>
 *   <li><strong>UIPanelManager</strong> - 面板注册、依赖注入、renderAll、show/hide/toggle</li>
 *   <li><strong>UIPlaybackBridge</strong> - UI 帧循环 ↔ PlaybackOrchestrator.tick(delta)</li>
 *   <li><strong>UIShortcutCoordinator</strong> - 持有 ShortcutDispatcher，统一快捷键入口</li>
 *   <li><strong>UIDialogCoordinator</strong> - 持有 DialogManager，统一 dialog 渲染</li>
 * </ul>
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
 * DirectorUIManager uiManager = config.createUIManagerWithRegistry();
 *
 * // 方式2：直接使用面板注册表（高级用法）
 * IPanelRegistry registry = new PanelRegistry(panels);
 * DirectorUIManager uiManager = new DirectorUIManager(registry);
 *
 * // 方式3：使用面板列表（标准用法）
 * List<IPanel> panels = Arrays.asList(
 *     new MenuBarPanel(),
 *     new ToolbarPanel(),
 *     new TimelinePanel(),
 *     new PropertiesPanel(),
 *     new ContainerPanel()
 * );
 * DirectorUIManager uiManager = new DirectorUIManager(panels);
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
 *   <li><strong>默认构造函数已移除</strong> - 使用 {@code new DirectorUIManager()} 会抛出 {@code UnsupportedOperationException}</li>
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
 * Director采用专业建模软件风格的DockSpace布局：
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
 * Director提供完整的面板生命周期管理：
 * </p>
 * <ol>
 *   <li><strong>创建阶段</strong> - UIConfiguration创建面板实例</li>
 *   <li><strong>注入阶段</strong> - DirectorUIManager接收面板列表</li>
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
 * Director采用现代化的依赖注入系统：
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
 * @author Director Team
 * @version 2.0
 * @since 1.0
 */
public class DirectorUIManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorUIManager.class);

    /**
     * 通用信息日志方法 - 使用SLF4J流畅API优化性能
     * @param format 日志消息模板
     * @param args 参数
     */
    private void logInfo(String format, Object... args) {
        LOGGER.atInfo()
              .setMessage("[Director] " + format)
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
              .setMessage("[Director] " + format)
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
              .setMessage("[Director] " + format)
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
              .setMessage("[Director] " + format)
              .addArgument(args)
              .log();
    }

    /**
     * 延迟调试日志方法 - 使用lambda表达式延迟执行复杂计算
     * @param messageSupplier 消息提供者
     */
    private void logDebugState(java.util.function.Supplier<String> messageSupplier) {
        LOGGER.atDebug().log(() -> "[Director] " + messageSupplier.get());
    }

    // 面板管理 - 使用现代化的面板注册表
    private final IPanelRegistry panelRegistry;

    // 布局状态标志 - 每次创建新的UIManager时都重置为true
    private boolean isFirstLayout = true;

    // 状态管理
    private boolean isDisposed = false;
    
    // 布局持久化管理器
    private final LayoutPersistenceManager layoutManager;

    // ========== 职责协调器（4 个子模块）==========
    private final UIDialogCoordinator dialogCoordinator;
    private final UIPanelManager panelManager;
    private final UIPlaybackBridge playbackBridge;
    private final UIShortcutCoordinator shortcutCoordinator;

    // 性能监控
    private long renderCount = 0;
    private long lastPerformanceLog = 0;
    private static final long PERFORMANCE_LOG_INTERVAL = 60000; // 1分钟

    // ========== 视频导出 UI（v5.2）==========
    private final VideoExportProgressOverlay videoExportProgressOverlay = new VideoExportProgressOverlay();

    // DockSpace相关常量
    private static final int DOCKSPACE_FLAGS = imgui.flag.ImGuiWindowFlags.MenuBar |
                                             imgui.flag.ImGuiWindowFlags.NoDocking |
                                             imgui.flag.ImGuiWindowFlags.NoTitleBar |
                                             imgui.flag.ImGuiWindowFlags.NoCollapse |
                                             imgui.flag.ImGuiWindowFlags.NoResize |
                                             imgui.flag.ImGuiWindowFlags.NoMove |
                                             imgui.flag.ImGuiWindowFlags.NoBringToFrontOnFocus |
                                             imgui.flag.ImGuiWindowFlags.NoNavFocus |
                                             imgui.flag.ImGuiWindowFlags.NoBackground;

    private static final String DOCKSPACE_WINDOW_NAME = "DockSpace";
    /**
     * DockSpace 持久化 ID。
     * <p>
     * 使用与宿主窗口同名的稳定键，避免历史布局里出现多个 DockSpace 根节点
     * （表现为“窗口位置正确但不在当前 DockSpace 停靠树中”）。
     */
    private static final String DOCKSPACE_ID = DOCKSPACE_WINDOW_NAME;

    /**
     * 默认构造函数 - 已移除
     * <p>
     * <strong>⚠️ 此构造函数已被移除，请使用依赖注入构造函数！</strong><br>
     * 使用 {@link UIConfiguration} 创建面板列表，然后调用 {@link #DirectorUIManager(List)} 构造函数。
     *
     * @deprecated 此构造函数已被移除，请使用依赖注入构造函数
     * @throws UnsupportedOperationException 始终抛出，强制使用依赖注入
     * @see #DirectorUIManager(List) 推荐的依赖注入构造函数
     * @see UIConfiguration 面板配置创建器
     */
    @Deprecated(since = "2.0", forRemoval = true)
    public DirectorUIManager() {
        throw new UnsupportedOperationException(
                """
                        默认构造函数已被移除。请使用依赖注入构造函数：
                        1. UIConfiguration config = new UIConfiguration(minecraftClient);
                        2. DirectorUIManager uiManager = config.createUIManagerWithRegistry();
                        或：DirectorUIManager uiManager = new DirectorUIManager(config.getPanels());"""
        );
    }


    /**
     * 依赖注入构造函数 - 推荐使用
     * @param panels 面板列表
     * @throws IllegalArgumentException 如果面板列表为null或包含null元素
     */
    public DirectorUIManager(List<IPanel> panels) {
        this(createValidatedRegistry(panels), LayoutInitPolicy.RESET_DOCKSPACE_STATE);
    }

    /**
     * 获取面板注册表（供外部在关闭/保存时访问关键面板）。
     */
    public IPanelRegistry getPanelRegistry() {
        return panelRegistry;
    }

    /**
     * 获取 DialogManager（供 CommandFacade、Layout 等配置与调用）。
     * 由 UIDialogCoordinator 持有，此处统一接入。
     */
    private com.director.ui.dialogs.DialogManager getDialogManager() {
        return dialogCoordinator.getDialogManager();
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
    
    private volatile UICommandFacade commandFacade;

    /**
     * 延迟初始化命令门面（懒加载模式，确保面板已完全初始化）
     */
    private UICommandFacade getOrCreateCommandFacade() {
        UICommandFacade localFacade = commandFacade;
        if (localFacade == null) {
            synchronized (this) {
                localFacade = commandFacade;
                if (localFacade == null) {
                    localFacade = new UICommandFacadeImpl();
                    var d = new com.director.ui.input.ShortcutDispatcher(localFacade, panelRegistry);
                    shortcutCoordinator.setShortcutDispatcher(d);
                    commandFacade = localFacade;
                    LOGGER.info("[DirectorUIManager] ShortcutDispatcher 已初始化");
                }
            }
        }
        return localFacade;
    }
        private enum LayoutInitPolicy {
            RESET_DOCKSPACE_STATE,
            RELOAD_CURRENT_WORLD
        }

        private static IPanelRegistry createValidatedRegistry(List<IPanel> panels) {
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
            return new PanelRegistry(panels);
        }

    
    /**
     * 获取快捷键分发器
     * 
     * @return ShortcutDispatcher 实例
     */
    public com.director.ui.input.ShortcutDispatcher getShortcutDispatcher() {
        getOrCreateCommandFacade();
        return shortcutCoordinator.getShortcutDispatcher();
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
        private final ContainerCommands containerCommands;
        private final SelectionFilterCommands selectionFilterCommands;
        
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
                this.timelineModel = com.director.ui.timeline.container.ContainerTimelineService.getInstance().getTimelineModel();
                if (this.timelineModel == null) {
                    LOGGER.warn("[Commands] TimelineModel not available, some commands may not work");
                }
            }
            
            // 初始化命令实现（传入依赖）
            this.projectCommands = new ProjectCommandsImpl(timelineModel, menuBarPanel);
            this.timelineCommands = new TimelineCommandsImpl(timelinePanel, menuBarPanel, timelineModel);
            this.playbackCommands = new PlaybackCommandsImpl(timelineModel);
            this.cameraCommands = new CameraCommandsImpl(timelineModel);
            this.viewCommands = new ViewCommandsImpl(DirectorUIManager.this.panelManager);
            com.director.ui.properties.PropertyAdapterContext.setShowFilterPanelRunnable(
                () -> viewCommands.showPanel(com.director.ui.PanelId.SELECTION_FILTER));
            this.layoutCommands = new LayoutCommandsImpl(layoutManager);
            this.helpCommands = new HelpCommandsImpl(menuBarPanel);
            this.toolCommands = new ToolCommandsImpl();
            this.containerCommands = new ContainerCommandsImpl(DirectorUIManager.this.panelRegistry);
            this.selectionFilterCommands = new SelectionFilterCommandsImpl();
            
            // 设置时间线设置访问器（用于时间线设置对话框）
            if (timelineModel != null) {
                // 设置时间线设置访问器
                DirectorUIManager.this.getDialogManager().setTimelineSettingsAccessor(
                    new com.director.ui.dialogs.DialogManager.TimelineSettingsAccessor() {
                        @Override
                        public com.director.ui.timeline.snapping.TimelineSnapSettings getSnapSettings() {
                            return timelineModel.getSnapSettings();
                        }
                        
                        @Override
                        public float getFrameRate() {
                            // 尝试多种方式获取项目对象
                            ITimelineProject project = timelineModel;

                            // 使用与 TimelineProjectPropertyAdapter 相同的方式
                            float fps = project.getFrameRate();
                            LOGGER.debug("[TimelineSettings] 获取帧率: {} (项目对象: {})", fps, project.getClass().getSimpleName());
                            return fps;
                        }
                        
                        @Override
                        public boolean setFrameRate(float fps) {

                            // 使用与 TimelineProjectPropertyAdapter 相同的方式
                            timelineModel.setFrameRate(fps);
                            boolean success = true;
                            LOGGER.debug("[TimelineSettings] 帧率已设置为: {}", fps);
                            return success;
                        }
                        
                        @Override
                        public int getTimeUnit() {

                            return timelineModel.getTimeUnit();
                        }
                        
                        @Override
                        public boolean setTimeUnit(int unit) {

                            timelineModel.setTimeUnit(unit);
                            return true;
                        }
                    }
                );
            }
            
            // 设置布局访问器（用于保存/加载布局预设对话框）
            DirectorUIManager.this.getDialogManager().setLayoutAccessor(
                    new com.director.ui.dialogs.DialogManager.LayoutAccessor() {
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
        
        @Override
        public ContainerCommands container() {
            return containerCommands;
        }
        
        @Override
        public SelectionFilterCommands selectionFilter() {
            return selectionFilterCommands;
        }
    }
    
    /**
     * 选择过滤器命令实现：唯一允许操作全局 BlockFilterPipeline 的入口，Panel 不得直接碰 SelectionManager/Pipeline。
     */
    private static final class SelectionFilterCommandsImpl implements SelectionFilterCommands {
        private static com.director.core.selection.block_filters.BlockFilterPipeline getPipeline() {
            return com.director.core.selection.SelectionManager.get().getPipeline();
        }
        
        @Override
        public java.util.List<com.director.core.selection.block_filters.BlockFilter> getFilters() {
            return getPipeline().getFilters();
        }
        
        @Override
        public boolean isEmpty() { return getPipeline().isEmpty(); }
        
        @Override
        public int size() { return getPipeline().size(); }
        
        @Override
        public void clear() { getPipeline().clear(); }
        
        @Override
        public void replaceAll(java.util.List<com.director.core.selection.block_filters.BlockFilter> filters) {
            var p = getPipeline();
            p.clear();
            if (filters != null) p.addAll(filters);
        }
        
        @Override
        public void appendFilter(com.director.core.selection.block_filters.BlockFilter filter) {
            if (filter == null) return;
            String id = com.director.core.selection.block_filters.BlockFilterRegistry.getFilterId(filter);
            if (id != null && pipelineContains(id)) return; // 避免重复添加同一规则
            getPipeline().add(filter);
        }
        
        @Override
        public void removeFilterAt(int index) {
            var list = new java.util.ArrayList<>(getPipeline().getFilters());
            if (index >= 0 && index < list.size()) {
                list.remove(index);
                replaceAll(list);
            }
        }
        
        @Override
        public void applyQuickFilter(String id) {
            var f = com.director.core.selection.block_filters.BlockFilterRegistry.getFilter(id);
            if (f != null) {
                replaceAll(java.util.List.of(f));
                return;
            }
            var preset = com.director.core.selection.block_filters.BlockFilterPresetManager.getPreset(id);
            if (preset != null) replaceAll(preset.getFilters());
        }

        @Override
        public boolean isFilterEnabled(String id) {
            if (id == null) return false;
            return pipelineContains(id);
        }

        @Override
        public void setFilterEnabled(String id, boolean enabled) {
            if (id == null) return;
            String c = com.director.core.selection.block_filters.BlockFilterRegistry.resolveFilterId(id);
            if (enabled) {
                if (FilterIds.EXCLUDE_AIR.equals(c)) removeFilterById(FilterIds.ALL);
                if (!pipelineContains(id)) {
                    var f = com.director.core.selection.block_filters.BlockFilterRegistry.getFilter(id);
                    if (f != null) {
                        // 默认「忽略空气」：往空管道加其他规则时，先加 exclude_air 再追加当前（排除正在加的就是 exclude_air）
                        if (isEmpty() && !FilterIds.EXCLUDE_AIR.equals(c)) {
                            var air = com.director.core.selection.block_filters.BlockFilterRegistry.getFilter(FilterIds.EXCLUDE_AIR);
                            if (air != null) appendFilter(air);
                        }
                        appendFilter(f);
                    }
                }
            } else {
                removeFilterById(id);
            }
        }

        private boolean pipelineContains(String id) {
            String c = com.director.core.selection.block_filters.BlockFilterRegistry.resolveFilterId(id);
            if (c == null) return false;
            for (var f : getFilters())
                if (c.equals(com.director.core.selection.block_filters.BlockFilterRegistry.getFilterId(f)))
                    return true;
            return false;
        }

        private void removeFilterById(String id) {
            String c = com.director.core.selection.block_filters.BlockFilterRegistry.resolveFilterId(id);
            if (c == null) return;
            var list = new java.util.ArrayList<>(getFilters());
            for (int i = 0; i < list.size(); i++) {
                if (c.equals(com.director.core.selection.block_filters.BlockFilterRegistry.getFilterId(list.get(i)))) {
                    removeFilterAt(i);
                    return;
                }
            }
        }
        
        @Override
        public void loadPreset(String id) {
            var preset = com.director.core.selection.block_filters.BlockFilterPresetManager.getPreset(id);
            if (preset != null) replaceAll(preset.getFilters());
        }
        
        @Override
        public void savePreset(String name) {
            if (name == null || name.isBlank()) return;
            com.director.core.selection.block_filters.BlockFilterPresetManager.savePreset(name, getPipeline());
        }
        
        @Override
        public void deletePreset(String id) {
            com.director.core.selection.block_filters.BlockFilterPresetManager.deletePreset(id);
        }
        
        @Override
        public java.util.Set<String> getPresetIds() {
            return com.director.core.selection.block_filters.BlockFilterPresetManager.getPresetIds();
        }
        
        @Override
        public java.util.Set<String> getFilterIds() {
            return com.director.core.selection.block_filters.BlockFilterRegistry.getAllFilterIds();
        }
        
        @Override
        public com.director.core.selection.block_filters.BlockFilter getFilter(String id) {
            return com.director.core.selection.block_filters.BlockFilterRegistry.getFilter(id);
        }
        
        @Override
        public String getFilterId(com.director.core.selection.block_filters.BlockFilter filter) {
            return com.director.core.selection.block_filters.BlockFilterRegistry.getFilterId(filter);
        }
        
        @Override
        public void enableProfiling(boolean enable) { getPipeline().enableProfiling(enable); }
        
        @Override
        public void resetStats() { getPipeline().resetStats(); }
        
        @Override
        public long getLastNanos() { return getPipeline().getLastNanos(); }
        
        @Override
        public long getTotalChecked() { return getPipeline().getTotalChecked(); }
    }
    
    /**
     * 容器命令实现（从 panelRegistry 取 ContainerPanel，委托 requestDeleteSelected）
     */
    private class ContainerCommandsImpl implements ContainerCommands {
        private final IPanelRegistry registry;
        ContainerCommandsImpl(IPanelRegistry registry) {
            this.registry = registry;
        }
        @Override
        public void requestDeleteSelected() {
            ContainerPanel panel = registry.getPanel(ContainerPanel.class);
            if (panel != null) {
                panel.requestDeleteSelected();
            }
        }
    }
    
    /**
     * 项目命令实现（优化：直接调用，减少间接调用）
     */
    private class ProjectCommandsImpl implements ProjectCommands {
        
        private final TimelineModel timelineModel;

        public ProjectCommandsImpl(TimelineModel timelineModel, MenuBarPanel menuBarPanel) {
            this.timelineModel = timelineModel;
        }
        
        @Override
        public void newProject() {
            // 显示确认对话框
            DirectorUIManager.this.getDialogManager().showNewAnimationConfirm();
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
            DirectorUIManager.this.openVideoExportDialog();
        }
        
        @Override
        public void exitApplication() {
            try {
                // 退出前保存当前项目
                if (timelineModel != null) {
                    timelineModel.save();
                    LOGGER.info("[Project] 退出前已保存项目");
                }
                
                // 关闭 Director 窗口
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && client.currentScreen instanceof com.director.ui.DirectorScreen screen) {
                    screen.close();
                    client.setScreen(null);
                    LOGGER.info("[Project] Director 窗口已关闭");
                } else {
                    // 兜底：直接设置屏幕为 null
                    if (client != null) {
                        client.setScreen(null);
                    }
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
        private final TimelineModel timelineModel;
        
        public TimelineCommandsImpl(TimelinePanel timelinePanel, MenuBarPanel menuBarPanel, TimelineModel timelineModel) {
            this.timelinePanel = timelinePanel;
            this.timelineModel = timelineModel;
        }
        
        @Override
        public void openTimelineSettings() {
            DirectorUIManager.this.getDialogManager().showTimelineSettingsDialog();
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
                        .filter(t -> t.getType() == com.director.ui.timeline.model.TrackType.CAMERA)
                        .count();
                
                String trackName = "摄像机轨道 " + (sameTypeCount + 1);
                com.director.ui.timeline.model.TimelineTrack newTrack = 
                        new com.director.ui.timeline.model.TimelineTrack(trackName, 
                                com.director.ui.timeline.model.TrackType.CAMERA);
                
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
                        .filter(t -> t.getType() == com.director.ui.timeline.model.TrackType.ENTITY)
                        .count();
                
                String trackName = "实体轨道 " + (sameTypeCount + 1);
                com.director.ui.timeline.model.TimelineTrack newTrack = 
                        new com.director.ui.timeline.model.TimelineTrack(trackName, 
                                com.director.ui.timeline.model.TrackType.ENTITY);
                
                timelineModel.addTrack(newTrack);
                
                // 如果是第一条实体轨道，自动激活
                if (sameTypeCount == 0) {
                    com.director.ui.timeline.playback.entity.TimelineEntityController
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
                        .filter(t -> t.getType() == com.director.ui.timeline.model.TrackType.WEATHER)
                        .count();
                
                String trackName = "天气轨道 " + (sameTypeCount + 1);
                com.director.ui.timeline.model.TimelineTrack newTrack = 
                        new com.director.ui.timeline.model.TimelineTrack(trackName, 
                                com.director.ui.timeline.model.TrackType.WEATHER);
                
                timelineModel.addTrack(newTrack);
                
                // 如果是第一条天气轨道，自动激活
                if (sameTypeCount == 0) {
                    com.director.ui.timeline.playback.weather.TimelineWeatherController.getInstance()
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
                        .filter(t -> t.getType() == com.director.ui.timeline.model.TrackType.AUDIO)
                        .count();
                
                String trackName = "音频轨道 " + (sameTypeCount + 1);
                // 音频轨道使用 AudioTrack 类
                com.director.ui.timeline.model.audio.AudioTrack newTrack = 
                        new com.director.ui.timeline.model.audio.AudioTrack(trackName);
                
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
        * 播放命令实现（统一由 TimelineModel/PlaybackOrchestrator 驱动）
     */
    private class PlaybackCommandsImpl implements PlaybackCommands {
        
        private final TimelineModel timelineModel;
        
        public PlaybackCommandsImpl(TimelineModel timelineModel) {
            this.timelineModel = timelineModel;
        }

        private TimelineModel resolvePlaybackModel() {
            TimelineModel boundModel = com.director.ui.timeline.playback.PlaybackOrchestrator.get().getBoundModel();
            return boundModel != null ? boundModel : timelineModel;
        }

        private void withPlaybackModel(Consumer<TimelineModel> action, String actionName) {
            TimelineModel model = resolvePlaybackModel();
            if (model == null) {
                LOGGER.warn("[PlaybackCommands] {} skipped: TimelineModel unavailable", actionName);
                return;
            }
            action.accept(model);
        }
        
        @Override
        public void play() {
            withPlaybackModel(model -> {
                float start = model.getPlaybackRangeStart();
                float end = model.getPlaybackRangeEnd();
                float current = model.getCurrentTime();
                if (current < start || current > end) {
                    model.setCurrentTime(start);
                }
                model.setPlaying(true);
            }, "play");
        }
        
        @Override
        public void pause() {
            withPlaybackModel(model -> model.setPlaying(false), "pause");
        }
        
        @Override
        public void stop() {
            withPlaybackModel(model -> {
                model.setPlaying(false);
                model.setCurrentTime(model.getPlaybackRangeStart());
            }, "stop");
        }
        
        @Override
        public void togglePlayPause() {
            TimelineModel model = resolvePlaybackModel();
            if (model != null) {
                boolean wasPlaying = model.isPlaying();
                model.togglePlay();
                boolean isPlaying = model.isPlaying();
                LOGGER.info("[PlaybackCommands] togglePlayPause: {} -> {}", wasPlaying, isPlaying);
            } else {
                LOGGER.warn("[PlaybackCommands] togglePlayPause skipped: TimelineModel unavailable");
            }
        }
        
        @Override
        public void setInPoint() {
            withPlaybackModel(model -> {
                float current = model.getCurrentTime();
                model.setInPoint(current);
                LOGGER.info("[PlaybackCommands] 设置入点: {:.2f}s", model.getInPoint());
            }, "setInPoint");
        }
        
        @Override
        public void setOutPoint() {
            withPlaybackModel(model -> {
                float current = model.getCurrentTime();
                model.setOutPoint(current);
                LOGGER.info("[PlaybackCommands] 设置出点: {:.2f}s", model.getOutPoint());
            }, "setOutPoint");
        }
        
        @Override
        public void clearInOut() {
            withPlaybackModel(model -> {
                model.clearInOut();
                LOGGER.info("[PlaybackCommands] 已清除入/出点");
            }, "clearInOut");
        }

        @Override
        public boolean hasInPoint() {
            TimelineModel model = resolvePlaybackModel();
            return model != null && model.hasInPoint();
        }

        @Override
        public boolean hasOutPoint() {
            TimelineModel model = resolvePlaybackModel();
            return model != null && model.hasOutPoint();
        }

        @Override
        public float getInPoint() {
            TimelineModel model = resolvePlaybackModel();
            return model != null ? model.getInPoint() : 0.0f;
        }

        @Override
        public float getOutPoint() {
            TimelineModel model = resolvePlaybackModel();
            return model != null ? model.getOutPoint() : getTotalDuration();
        }
        
        @Override
        public void setPlaybackSpeed(float speed) {
            withPlaybackModel(model -> model.setPlaybackSpeed(speed), "setPlaybackSpeed");
        }
        
        @Override
        public void toggleLoop() {
            withPlaybackModel(model -> model.setLoopEnabled(!model.isLoopEnabled()), "toggleLoop");
        }
        
        @Override
        public void goToStart() {
            withPlaybackModel(model -> model.setCurrentTime(model.getPlaybackRangeStart()), "goToStart");
        }
        
        @Override
        public void goToEnd() {
            withPlaybackModel(model -> model.setCurrentTime(model.getPlaybackRangeEnd()), "goToEnd");
        }
        
        @Override
        public boolean isPlaying() {
            TimelineModel model = resolvePlaybackModel();
            return model != null && model.isPlaying();
        }
        
        @Override
        public boolean isLooping() {
            TimelineModel model = resolvePlaybackModel();
            return model != null && model.isLoopEnabled();
        }
        
        @Override
        public float getPlaybackSpeed() {
            TimelineModel model = resolvePlaybackModel();
            return model != null ? model.getPlaybackSpeed() : 1.0f;
        }
        
        @Override
        public float getCurrentTime() {
            TimelineModel model = resolvePlaybackModel();
            return model != null ? model.getCurrentTime() : 0.0f;
        }
        
        @Override
        public float getTotalDuration() {
            TimelineModel model = resolvePlaybackModel();
            return model != null ? model.getDuration() : 0.0f;
        }
        
        @Override
        public void seek(float time) {
            TimelineModel model = resolvePlaybackModel();
            if (model != null) {
                float clampedTime = Math.max(0f, Math.min(model.getDuration(), time));
                model.setCurrentTime(clampedTime);
                
                // ⭐ 立即采样（seek 是离散命令，需要立即反馈）
                // 季节轨道系统：跳转时间时更新季节采样
                try {
                    com.director.ui.timeline.playback.season.TimelineSeasonController.getInstance().onTimelineTick(clampedTime);
                } catch (Exception e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[PlaybackCommands] 季节采样失败", e);
                    }
                }
                
                // ⭐ 天气轨道系统：跳转时间时更新天气采样（预览模式）
                try {
                    com.director.ui.timeline.playback.weather.TimelineWeatherController.getInstance().previewAt(clampedTime);
                } catch (Exception e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[PlaybackCommands] 天气采样失败", e);
                    }
                }
                
                // ⭐ 摄像机控制器：跳转到指定时间并保持玩家态
                try {
                    com.director.ui.timeline.playback.camera.TimelineCameraController.getInstance().jumpToTimeAsPlayer(clampedTime);
                } catch (Exception e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[PlaybackCommands] 摄像机跳转失败", e);
                    }
                }
                
                // ⭐ 更新片段方块显示状态
                try {
                    com.director.ui.timeline.playback.TimelineClipBlockController.getInstance().update();
                } catch (Exception e) {
                    if (LOGGER.isTraceEnabled()) {
                        LOGGER.trace("[PlaybackCommands] 片段方块更新失败", e);
                    }
                }
                
                LOGGER.debug("[PlaybackCommands] Seek to {:.2f}s", clampedTime);
            } else {
                LOGGER.warn("[PlaybackCommands] seek skipped: TimelineModel unavailable");
            }
        }
        
        @Override
        public void tick(float deltaTime) {
            // ⚠️ 播放驱动已迁移到 UIPlaybackBridge（在 UIManager.render() 中每帧调用 playbackBridge.tick()）
            // Facade 的 tick() 方法保留用于向后兼容；实际驱动由 UIPlaybackBridge → PlaybackOrchestrator 统一负责
            if (timelineModel != null) {
                // 委托给 Orchestrator（确保统一驱动）
                com.director.ui.timeline.playback.PlaybackOrchestrator.get().bindModel(timelineModel);
                com.director.ui.timeline.playback.PlaybackOrchestrator.get().tick(deltaTime);
            }
        }
    }
    
    /**
     * 摄像机命令实现（当前阶段：fail-fast，避免 TODO 静默成功）
     */
    private class CameraCommandsImpl implements CameraCommands {

        private final TimelineModel timelineModel;

        public CameraCommandsImpl(TimelineModel timelineModel) {
            this.timelineModel = timelineModel;
        }

        private UnsupportedOperationException unsupportedCameraCommand(String commandName) {
            String modelState = (timelineModel != null) ? "model-bound" : "model-missing";
            return new UnsupportedOperationException("Camera command not implemented yet: " + commandName + " (" + modelState + ")");
        }
        
        @Override
        public void createCameraPath(CameraPathType type) {
            throw unsupportedCameraCommand("createCameraPath(" + type + ")");
        }
        
        @Override
        public void alignCameraToCurrentView() {
            throw unsupportedCameraCommand("alignCameraToCurrentView");
        }
        
        @Override
        public void createShotFromCurrentView() {
            throw unsupportedCameraCommand("createShotFromCurrentView");
        }
        
        @Override
        public void setDefaultCameraView() {
            throw unsupportedCameraCommand("setDefaultCameraView");
        }
    }
    
    /**
     * 视图命令实现（委托 UIPanelManager）
     */
    private class ViewCommandsImpl implements ViewCommands {
        
        private final UIPanelManager panelManager;
        
        public ViewCommandsImpl(UIPanelManager panelManager) {
            this.panelManager = panelManager;
        }
        
        @Override
        public void showPanel(String panelId) {
            panelManager.showPanel(panelId);
        }
        
        @Override
        public void hidePanel(String panelId) {
            panelManager.hidePanel(panelId);
        }
        
        @Override
        public void togglePanel(String panelId) {
            panelManager.togglePanel(panelId);
        }
        
        @Override
        public void showAllPanels() {
            panelManager.showAllPanels();
        }
        
        @Override
        public void hideAllPanels() {
            panelManager.hideAllPanels();
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
            DirectorUIManager.this.getDialogManager().showSaveLayoutDialog();
        }
        
        @Override
        public void showLoadLayoutDialog() {
            DirectorUIManager.this.getDialogManager().showLoadLayoutDialog();
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
                    com.director.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                    
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
                DirectorUIManager.this.clearCurrentDockSpaceState();
                DirectorUIManager.this.restoreDefaultPanelVisibility();
                DirectorUIManager.this.isFirstLayout = true;
                com.director.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                
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
                com.director.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
                
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
                // 主题由 DirectorStyleScope 每帧应用，此处无需操作
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

        public HelpCommandsImpl(MenuBarPanel menuBarPanel) {
        }
        
        @Override
        public void showShortcutHelp() {
            DirectorUIManager.this.getDialogManager().showShortcutsDialog();
        }
        
        @Override
        public void openUserManual() {
            // TODO: 打开用户手册
            LOGGER.info("[Help] Open user manual");
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://director.github.io/docs"));
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
            DirectorUIManager.this.getDialogManager().showAboutDialog();
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
            com.director.ui.tools.ToolRegistry.get().activate(toolId);
            com.director.ui.properties.PropertyContextService.get().onToolActivated(toolId);
        }
        
        @Override
        public void switchToNextTool() {
            var world = net.minecraft.client.MinecraftClient.getInstance().world;
            if (world != null) {
                com.director.core.selection.SelectionManager.get().switchToNextTool(world);
                var id = com.director.core.selection.SelectionManager.get().getCurrentToolId();
                if (id != null) com.director.ui.properties.PropertyContextService.get().onToolActivated(id);
                LOGGER.debug("[ToolCommands] 切换到下一个工具");
            }
        }
        
        @Override
        public void switchToPreviousTool() {
            var world = net.minecraft.client.MinecraftClient.getInstance().world;
            if (world != null) {
                com.director.core.selection.SelectionManager.get().switchToPreviousTool(world);
                var id = com.director.core.selection.SelectionManager.get().getCurrentToolId();
                if (id != null) com.director.ui.properties.PropertyContextService.get().onToolActivated(id);
                LOGGER.debug("[ToolCommands] 切换到上一个工具");
            }
        }
        
        @Override
        public void setSelectionMode(com.director.core.selection.SelectionOperation operation) {
            var currentTool = com.director.core.selection.SelectionManager.get().getCurrentTool();
            if (currentTool instanceof com.director.core.selection.SelectionOperationSupport operationSupport) {
                operationSupport.setOperation(operation);
                LOGGER.debug("[ToolCommands] 设置选择操作模式: {}", operation.getDisplayName());
            } else {
                LOGGER.debug("[ToolCommands] 当前工具不支持操作模式切换: {}", 
                    currentTool != null ? currentTool.getId() : "null");
            }
        }
        
        @Override
        public void clearSelection() {
            com.director.core.selection.SelectionManager.get().clearSelection();
            LOGGER.debug("[ToolCommands] 清除选择");
        }
        
        @Override
        public String getActiveToolId() {
            return com.director.ui.tools.ToolRegistry.get().getActiveToolId();
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
     * 高级依赖注入构造函数 - 直接使用面板注册表
     * @param panelRegistry 面板注册表
     * @throws IllegalArgumentException 如果面板注册表为null或为空
     */
    public DirectorUIManager(IPanelRegistry panelRegistry) {
        this(panelRegistry, LayoutInitPolicy.RELOAD_CURRENT_WORLD);
    }

    private DirectorUIManager(IPanelRegistry panelRegistry, LayoutInitPolicy layoutInitPolicy) {
        if (panelRegistry == null) {
            throw new IllegalArgumentException("面板注册表不能为 null");
        }
        if (panelRegistry.isEmpty()) {
            throw new IllegalArgumentException("面板注册表不能为空");
        }

        this.panelRegistry = panelRegistry;

        // 初始化布局持久化管理器
        this.layoutManager = LayoutPersistenceManager.getInstance();

        // 创建职责协调器
        this.dialogCoordinator = new UIDialogCoordinator();
        getDialogManager().setNewAnimationConfirmCallback(() -> {
            try {
                TimelinePanel timelinePanel = panelRegistry.getPanel(TimelinePanel.class);
                if (timelinePanel != null) {
                    timelinePanel.resetWorkspace(true);
                } else {
                    Objects.requireNonNull(com.director.core.container.ContainerManager.getInstance()).clear();
                    com.director.ui.timeline.persistence.TimelineModelPersistence.resetForCurrentWorld();
                }
            } catch (Exception e) {
                LOGGER.error("[UIManager] 新建动画重置失败: {}", e.getMessage(), e);
            }
        });
        this.panelManager = new UIPanelManager(panelRegistry, this, this::getOrCreateCommandFacade);
        this.playbackBridge = new UIPlaybackBridge();
        this.shortcutCoordinator = new UIShortcutCoordinator();

        init(layoutInitPolicy);
    }

    /**
     * 静态工厂方法 - 从面板列表创建UI管理器
     * @param panels 面板列表
     * @return 完全初始化的UI管理器实例
     * @throws IllegalArgumentException 如果面板列表无效
     */
    public static DirectorUIManager create(List<IPanel> panels) {
        return new DirectorUIManager(panels);
    }

    /**
     * 静态工厂方法 - 从面板注册表创建UI管理器
     * @param panelRegistry 面板注册表
     * @return 完全初始化的UI管理器实例
     * @throws IllegalArgumentException 如果面板注册表无效
     */
    public static DirectorUIManager create(IPanelRegistry panelRegistry) {
        return new DirectorUIManager(panelRegistry);
    }

    /**
     * 私有初始化方法 - 封装完整的初始化流程
     */
    private void init(LayoutInitPolicy layoutInitPolicy) {
        // 样式由 DirectorStyleScope 在每帧渲染时临时 push/pop，不在此处永久修改，避免影响 Treefactory 等模组

        // 初始化属性系统（PropertyAdapterBootstrap + ui.properties.PropertyAdapterRegistry）
        PropertyAdapterBootstrap.initialize();
        LOGGER.info("[Director] 属性系统已初始化");

        initializeLayout(layoutInitPolicy);

        // 验证必需的面板是否存在
        validateRequiredPanels();

        // 初始化所有面板 & 设置依赖引用（UIPanelManager）
        panelManager.initializeAll();
        panelManager.setupReferences();
        
        // 根据布局还原面板可见性
        restorePanelVisibilityFromLayout();

        // 强制同步当前存档的项目数据，避免跨存档内存残留（容器/时间线/Actor）。
        synchronizeWorldScopedState();

        logInfo("UI Manager initialized with {} panels using modern IoC", panelRegistry.size());
        logDebugState(() -> "Panel registry: " + panelRegistry.getDebugInfo());
    }

    private void synchronizeWorldScopedState() {
        try {
            com.director.core.container.persistence.ContainerPersistence.loadForCurrentWorld(
                com.director.core.container.ContainerManager.getInstance());
        } catch (Exception e) {
            LOGGER.warn("[Director] 同步当前存档容器数据失败 (source={}): {}", "DirectorUIManager.init", e.getMessage(), e);
        }

        try {
            TimelinePanel timelinePanel = panelRegistry.getPanel(TimelinePanel.class);
            if (timelinePanel != null) {
                timelinePanel.reloadForCurrentWorld();
            }
        } catch (Exception e) {
            LOGGER.warn("[Director] 同步当前存档时间线数据失败 (source={}): {}", "DirectorUIManager.init", e.getMessage(), e);
        }
    }

    private void initializeLayout(LayoutInitPolicy layoutInitPolicy) {
        if (layoutInitPolicy == LayoutInitPolicy.RESET_DOCKSPACE_STATE) {
            com.director.ui.layout.DockSpaceLayoutBuilder.resetLayoutState();
            LOGGER.info("[Director] DockSpace布局状态已重置，准备应用布局");
            return;
        }

        // 检测并加载当前世界的布局（如果是新世界则使用默认布局，如果是同一世界则加载保存的布局）
        try {
            this.layoutManager.reloadForCurrentWorld("DirectorUIManager.constructor");
            String currentWorldId = com.director.util.WorldIdentifier.getCurrentWorldId();
            LOGGER.info("[Director] 已加载世界 {} 的布局配置", currentWorldId);
        } catch (Exception e) {
            LOGGER.warn("[Director] 加载世界布局失败: {}", e.getMessage(), e);
            try {
                this.layoutManager.resetToDefault();
                LOGGER.info("[Director] 已创建默认布局");
            } catch (Exception resetException) {
                LOGGER.warn("[Director] 无法创建默认布局: {}", resetException.getMessage(), resetException);
            }
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

        // 🔄 系统级更新循环 - 驱动动画播放（UIPlaybackBridge）
        playbackBridge.tick();

        // 🎥 视频导出：
        // 目前的抓帧与缩放发生在 GameRenderer.renderWorld 的 HEAD/Tail 钩子中，
        // 这里保留 no-op 调用用于向后兼容（不会触发重复渲染）。
        VideoRenderPipeline.getInstance().processPendingCaptures();

        // 预检查：由 PanelManager 判定是否满足最小条件，Shell 只调用不决定
        if (!panelManager.hasMinimumPanels()) {
            panelManager.renderEmptyState();
            return;
        }

        try {
            // ImGui状态检查
            com.director.ui.imgui.ImGuiStateChecker.beforeRender("DirectorUIManager");
            
            // 性能监控：递增渲染计数
            renderCount++;

            // 定期性能日志（仅在debug模式下且间隔足够长）
            long currentTime = System.currentTimeMillis();
            if (LOGGER.isDebugEnabled() && currentTime - lastPerformanceLog > PERFORMANCE_LOG_INTERVAL) {
                int panelCount = panelRegistry.size();
                LOGGER.debug("[Director] 性能统计 - 渲染次数: {}, 面板数量: {}", renderCount, panelCount);
                lastPerformanceLog = currentTime;
            }

            // 0) 摄像机相关 Overlay 已迁移到 Minecraft HUD 渲染通道（避免 External Window 干扰）

            // 1. 渲染统一宿主窗口和 DockSpace
            int dockspaceId = renderDockSpaceWithPanels();
            if (dockspaceId == -1) {
                logWarn("DockSpace 未就绪，跳过本帧面板渲染");
                return;
            }

            // 2. 除菜单栏外的所有面板（UIPanelManager）
            panelManager.renderPanels();

            // 3) 全局弹窗/Overlay（不属于某个 Dock 面板）
            panelManager.renderPanels();
            panelManager.renderPanels();
            VideoExportDialog.renderIfNeeded(this);
            videoExportProgressOverlay.render(this);
            dialogCoordinator.render();
            
            // 4) 渲染对话框（集中管理）
            dialogCoordinator.render();
            
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
            com.director.ui.imgui.ImGuiStateChecker.forceCleanupStack("DirectorUIManager异常恢复");
        } finally {
            // 渲染后状态检查
            com.director.ui.imgui.ImGuiStateChecker.afterRender("DirectorUIManager");
            
            // 检查ImGui调用栈状态，确保所有Begin/End调用都匹配
            com.director.ui.imgui.ImGuiStateChecker.checkStackState("DirectorUIManager渲染结束");
            
            // 如果栈不为空，记录详细信息用于调试
            int stackDepth = com.director.ui.imgui.ImGuiStateChecker.getStackDepth();
            if (stackDepth > 0) {
                logWarn("⚠️ ImGui调用栈仍有 " + stackDepth + " 个未匹配的调用");
                if (LOGGER.isDebugEnabled()) {
                    java.util.List<String> stackCopy = com.director.ui.imgui.ImGuiStateChecker.getStackCopy();
                    logDebug("未匹配的调用栈: " + stackCopy);
                }
            }
        }
    }

    /**
     * 在主 viewport 中渲染与 Minecraft 画面绑定的 Overlay。
     */
    private void renderMainViewportOverlays() {
        imgui.ImGuiViewport mainViewport = ImGui.getMainViewport();
        if (mainViewport == null) {
            return;
        }

        float viewportX = mainViewport.getPosX();
        float viewportY = mainViewport.getPosY();
        float viewportW = mainViewport.getSizeX();
        float viewportH = mainViewport.getSizeY();
        if (viewportW <= 0.0f || viewportH <= 0.0f) {
            return;
        }

        ImGui.setNextWindowPos(viewportX, viewportY, imgui.flag.ImGuiCond.Always);
        ImGui.setNextWindowSize(viewportW, viewportH, imgui.flag.ImGuiCond.Always);
        ImGui.setNextWindowViewport(mainViewport.getID());

        int flags = imgui.flag.ImGuiWindowFlags.NoDecoration
                | imgui.flag.ImGuiWindowFlags.NoMove
                | imgui.flag.ImGuiWindowFlags.NoSavedSettings
                | imgui.flag.ImGuiWindowFlags.NoNav
                | imgui.flag.ImGuiWindowFlags.NoBringToFrontOnFocus
                | imgui.flag.ImGuiWindowFlags.NoFocusOnAppearing
                | imgui.flag.ImGuiWindowFlags.NoInputs;

        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowPadding, 0.0f, 0.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowBorderSize, 0.0f);
        ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.WindowRounding, 0.0f);
        ImGui.pushStyleColor(imgui.flag.ImGuiCol.WindowBg, 0.0f, 0.0f, 0.0f, 0.0f);

        if (ImGui.begin("##DirectorMainViewportOverlay", flags)) {
            imgui.ImDrawList dl = ImGui.getWindowDrawList();
            if (dl != null) {
                dl.pushClipRect(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH, true);
                renderCameraViewportOverlay(dl, viewportX, viewportY, viewportW, viewportH);
                com.director.ui.camera.path.CameraPathEditController.getInstance()
                        .renderOverlay(viewportX, viewportY, viewportW, viewportH);
                dl.popClipRect();
            }
        }
        ImGui.end();

        ImGui.popStyleColor();
        ImGui.popStyleVar(3);
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

        // 使用一个统一的宿主窗口承载菜单栏和整个 DockSpace
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
                        com.director.ui.imgui.ImGuiStateChecker.recordBegin("DockSpace", DOCKSPACE_WINDOW_NAME);
                        panelManager.renderMenuBar();
                        
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
                                LOGGER.info("[Director] 世界 {} 未找到保存的布局，应用默认布局", 
                                    com.director.util.WorldIdentifier.getCurrentWorldId());
                                com.director.ui.layout.DockSpaceLayoutBuilder.buildDefaultLayout(dockspaceId);
                            } else {
                                LOGGER.info("[Director] 已加载世界 {} 的保存布局", 
                                    com.director.util.WorldIdentifier.getCurrentWorldId());
                            }
                            
                            isFirstLayout = false;
                        }
                        
                        // 检查世界是否切换（每次渲染时检查）
                        if (com.director.util.WorldIdentifier.hasWorldChanged()) {
                            LOGGER.info("[Director] 检测到世界切换，重新加载布局");
                            // 使用 reloadForCurrentWorld 自动处理保存和加载
                            layoutManager.reloadForCurrentWorld("render()");
                            // 更新 ImGui INI 路径
                            com.director.ui.imgui.ImGuiRenderer.getInstance().checkAndUpdateIniPath();
                            // 重置布局标志，以便在新世界中检查是否需要应用默认布局
                            isFirstLayout = true;
                            com.director.core.container.persistence.ContainerPersistence.loadForCurrentWorld(
                                com.director.core.container.ContainerManager.getInstance());

                            // ⭐ 同步时间线：切档时不能把上一个存档的轨道/关键帧带过来
                            try {
                                TimelinePanel timelinePanel = panelRegistry.getPanel(TimelinePanel.class);
                                if (timelinePanel != null) {
                                    timelinePanel.reloadForCurrentWorld();
                                    LOGGER.info("[Director] 已按新世界重新加载时间线数据");
                                }
                            } catch (Exception e) {
                                LOGGER.warn("[Director] 切档时重新加载时间线失败: {}", e.getMessage(), e);
                            }
                        }
                        
                        // 注意：面板渲染应该在 DockSpace 外部进行，而不是在内部
                        // 这样可以确保中央区域保持空白，不被面板占满
                        
                        com.director.ui.imgui.ImGuiStateChecker.recordEnd("DockSpace", DOCKSPACE_WINDOW_NAME);
                    } catch (Exception e) {
                        logError("DockSpace创建失败", e);
                        com.director.ui.imgui.ImGuiStateChecker.recordError("DockSpace", "dockSpace创建", e.getMessage());
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
            StringBuilder status = new StringBuilder("[Director] Panel Status:\n");
            
            panelRegistry.getAllPanels().forEach(panel -> {
                if (panel != null) {
                    String panelName = panel.getClass().getSimpleName();
                    boolean visible = panel.isVisible();
                    boolean shouldRender;
                    boolean isHidden;
                    boolean shouldReserveSpace;
                    
                    isHidden = panel.isHidden();
                    shouldReserveSpace = panel.shouldReserveDockSpaceWhenHidden();
                    shouldRender = visible || (isHidden && shouldReserveSpace);
                    
                    status.append(String.format("  %s: visible=%s, hidden=%s, reserveSpace=%s, shouldRender=%s\n",
                        panelName, visible, isHidden, shouldReserveSpace, shouldRender));
                }
            });
            
            LOGGER.debug(status.toString());
        } catch (Exception e) {
            LOGGER.debug("[Director] Failed to log panel status: {}", e.getMessage());
        }
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
     * 验证必需的面板是否存在（用于构造函数初始化）
     * @throws IllegalStateException 如果关键面板缺失且无法提供降级方案
     */
    private void restoreDefaultPanelVisibility() {
        if (panelRegistry == null || panelRegistry.isEmpty()) {
            return;
        }

        panelRegistry.getAllPanels().forEach(panel -> {
            if (panel == null) {
                return;
            }

            boolean visibleByDefault = switch (panel.getClass().getSimpleName()) {
                case "SelectionFilterPanel", "SelectionHistoryPanel", "RenderSettingsPanel", "ToolPropertiesPanel" -> false;
                default -> true;
            };

            try {
                panel.setVisible(visibleByDefault);
                logDebug("面板 {} 已重置为默认可见性: {}", panel.getPanelName(), visibleByDefault);
            } catch (Exception e) {
                logWarn("面板 {} 重置默认可见性失败: {}", panel.getPanelName(), e.getMessage());
            }
        });
    }

    private void clearCurrentDockSpaceState() {
        try {
            int dockspaceId = ImGui.getID(DOCKSPACE_ID);
            if (dockspaceId != 0) {
                imgui.internal.ImGui.dockBuilderRemoveNode(dockspaceId);
            }
        } catch (Exception e) {
            logWarn("清理当前 DockSpace 状态失败: {}", e.getMessage());
        }
    }

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
    private void renderCameraViewportOverlay(imgui.ImDrawList dl, float screenX, float screenY, float screenW, float screenH) {
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
            
            if (dl == null || screenW <= 0.0f || screenH <= 0.0f) {
                return;
            }
            
            // 计算导出画幅区域（Fit 或 Fill）
            ViewportMath.ViewportRects viewport;
            CameraViewportProfile.PreviewScalePolicy scalePolicy = profile.getPreviewScalePolicy();
            float targetAspect = profile.getAspectRatio();
            
            if (scalePolicy == CameraViewportProfile.PreviewScalePolicy.FIT) {
                viewport = ViewportMath.computeFit(screenX, screenY, screenW, screenH, targetAspect);
            } else {
                viewport = ViewportMath.computeFill(screenX, screenY, screenW, screenH, targetAspect);
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
            LOGGER.debug("[DirectorUIManager] 渲染摄像机视口 Overlay 时出错: {}", e.getMessage(), e);
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
            // 关闭路径避免调用 DockBuilder 原生接口：
            // 在某些关闭时序（尤其 detached window 系统关闭）下，
            // 这里的 native 调用可能导致 JVM 级崩溃。
            // 布局状态重置由 DirectorScreen.performCloseCleanup() 中的
            // DockSpaceLayoutBuilder.resetLayoutState() 负责。
            logDebug("跳过 dispose 阶段 DockBuilder 原生清理，使用布局状态重置替代");

            // 释放所有面板
            if (panelRegistry != null && !panelRegistry.isEmpty()) {
                totalPanels = panelRegistry.size();
                logDebug("释放 {} 个面板", totalPanels);

                for (IPanel panel : panelRegistry.getAllPanels()) {
                    if (panel != null) {
                        try {
                            logInfo("释放面板资源: {}", panel.getPanelName());
                            panel.dispose();
                        } catch (Throwable e) {
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
                LOGGER.warn("[Director] UI Manager资源释放完成，但有 {} 个面板释放失败", panelDisposalErrors);
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
            LOGGER.warn("[Director] 面板释放失败率过高: {}/{}，可能存在严重问题", panelDisposalErrors, totalPanels);
        }
    }
}
