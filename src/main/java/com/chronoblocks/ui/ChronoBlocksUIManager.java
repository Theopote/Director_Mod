package com.chronoblocks.ui;

import com.chronoblocks.ui.panels.IPanel;
import com.chronoblocks.ui.panels.AbstractPanel;
import com.chronoblocks.ui.panels.MenuBarPanel;
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

    // 性能监控
    private long renderCount = 0;
    private long lastPerformanceLog = 0;
    private static final long PERFORMANCE_LOG_INTERVAL = 60000; // 1分钟
    
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
            
            // 驱动播放控制器更新动画状态
            com.chronoblocks.core.playback.PlaybackController.get().update(deltaSeconds);
            
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
     */
    private void setupPanelRegistryReferences() {
        logDebug("为所有符合条件的面板设置依赖引用...");
        
        int registryAwareCount = 0;
        int uiManagerAwareCount = 0;
        
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
                
                // 注入UI管理器引用
                if (panel instanceof IUIManagerAware) {
                    try {
                        ((IUIManagerAware) panel).setUIManager(this);
                        uiManagerAwareCount++;
                        logDebug("为面板 '{}' 注入了 UIManager", panel.getPanelName());
                    } catch (Exception e) {
                        logError("为面板 '{}' 注入 UIManager 失败", e, panel.getPanelName());
                    }
                }
            }
        }
        
        logDebug("面板依赖引用设置完成 - PanelRegistry: {} 个面板, UIManager: {} 个面板", 
                registryAwareCount, uiManagerAwareCount);
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