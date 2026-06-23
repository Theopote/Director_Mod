package com.director.ui;

import com.director.core.DirectorServices;
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
import com.director.ui.commands.UICommandFacade;
import com.director.ui.commands.impl.DirectorUILayoutActions;
import com.director.ui.commands.impl.UICommandFacadeImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

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
 *   <li>任何直接操作 TimelineModel、BlockAnimationEngine、PlaybackOrchestrator 的方法</li>
 * </ul>
 * <p>
 * 这些方法只能存在于：
 * </p>
 * <ul>
 *   <li>{@code com.director.ui.commands.impl.UICommandFacadeImpl}（命令门面实现）</li>
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
                    localFacade = new UICommandFacadeImpl(
                            panelRegistry,
                            panelManager,
                            layoutManager,
                            getDialogManager(),
                            layoutActions(),
                            this::openVideoExportDialog);
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

    public UIPlaybackBridge getPlaybackBridge() {
        return playbackBridge;
    }
    

    private DirectorUILayoutActions layoutActions() {
        return new DirectorUILayoutActions() {
                        @Override
            public void clearCurrentDockSpaceState() {
                DirectorUIManager.this.clearCurrentDockSpaceState();
                        }
                        
                        @Override
            public void restoreDefaultPanelVisibility() {
                DirectorUIManager.this.restoreDefaultPanelVisibility();
                        }
                        
                        @Override
            public void requestDockSpaceRelayout() {
                DirectorUIManager.this.isFirstLayout = true;
            }
        };
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
                    DirectorServices.get().containers().clear();
                    com.director.timeline.persistence.TimelineModelPersistence.resetForCurrentWorld();
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
                DirectorServices.get().containers());
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

        // 播放驱动已迁移至 GameRenderer.renderWorld(HEAD)，与世界渲染同帧同步

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
            VideoExportDialog.renderIfNeeded(this);
            videoExportProgressOverlay.render(this);
            
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
     * <p>委托 {@link com.director.export.video.VideoExportManager} 在后台线程执行
     * {@link com.director.export.video.VideoExportTask}（逐帧渲染 + FFmpeg/PNG 编码）。</p>
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
                            DirectorServices.get().imgui().checkAndUpdateIniPath();
                            // 重置布局标志，以便在新世界中检查是否需要应用默认布局
                            isFirstLayout = true;
                            com.director.core.container.persistence.ContainerPersistence.loadForCurrentWorld(
                                DirectorServices.get().containers());

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
                case "SelectionFilterPanel", "SelectionHistoryPanel", "RenderSettingsPanel",
                     "ToolPropertiesPanel", "ActorsPanel" -> false;
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
                        } catch (RuntimeException e) {
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
