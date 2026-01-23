package com.chronoblocks.ui.di;

import com.chronoblocks.ui.ChronoBlocksUIManager;
import com.chronoblocks.ui.panels.*;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UIConfiguration - UI依赖注入配置中心
 * <p>
 * 这是 ChronoBlocks UI 系统的依赖注入配置中心，专注于在程序启动时创建和组装UI对象图。
 * 采用工厂模式实现依赖注入，提供统一的依赖管理，显著提升代码的可测试性、模块化和可维护性。
 * 
 * <h3>核心职责：</h3>
 * <ul>
 *   <li><strong>对象图创建</strong> - 在程序启动时创建所有UI面板实例</li>
 *   <li><strong>依赖组装</strong> - 组装面板间的依赖关系</li>
 *   <li><strong>配置验证</strong> - 验证面板配置的正确性</li>
 *   <li><strong>工厂方法</strong> - 提供创建UI管理器的工厂方法</li>
 * </ul>
 * 
 * <h3>设计原则：</h3>
 * <ul>
 *   <li><strong>单一职责</strong> - 只负责对象图的创建和组装</li>
 *   <li><strong>不可变性</strong> - 创建后配置不可修改</li>
 *   <li><strong>工厂模式</strong> - 通过工厂方法创建UI管理器</li>
 *   <li><strong>依赖注入</strong> - 管理组件间的依赖关系</li>
 * </ul>
 * 
 * <h3>职责界限：</h3>
 * <ul>
 *   <li><strong>包含</strong> - 面板创建、配置验证、UI管理器工厂</li>
 *   <li><strong>不包含</strong> - 运行时面板管理、面板生命周期管理</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 使用Builder模式（推荐）
 * UIConfiguration config = UIConfigurationBuilder.builder()
 *     .withMinecraftClient(minecraftClient)
 *     .withDefaultPanels()
 *     .build();
 * 
 * // 传统方式（向后兼容）
 * UIConfiguration config = new UIConfiguration(minecraftClient);
 * 
 * // 创建UI管理器
 * ChronoBlocksUIManager uiManager = config.createUIManager();
 * 
 * // 获取面板配置（只读）
 * List<IPanel> panels = config.getPanels();
 * }</pre>
 * 
 * @author ChronoBlocks Team
 * @version 2.1
 * @since 1.0
 * @see ChronoBlocksUIManager
 * @see IPanel
 * @see PanelValidator
 * @see UIConfigurationBuilder
 */
public class UIConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIConfiguration.class);
    
    private final List<IPanel> panels;
    private final IPanelFactory panelFactory;
    private final MinecraftClient minecraftClient;
    
    /**
     * 传统构造函数（向后兼容）
     * @param minecraftClient Minecraft客户端实例
     * @deprecated 推荐使用 UIConfigurationBuilder.builder() 创建配置
     */
    @Deprecated
    public UIConfiguration(MinecraftClient minecraftClient) {
        this.minecraftClient = minecraftClient;
        this.panelFactory = new PanelFactory();
        this.panels = new CopyOnWriteArrayList<>();
        initializePanels();
    }
    
    /**
     * 新的构造函数（支持依赖注入）
     * @param minecraftClient Minecraft客户端实例
     * @param panels 面板列表
     * @param panelFactory 面板工厂
     */
    public UIConfiguration(MinecraftClient minecraftClient, List<IPanel> panels, IPanelFactory panelFactory) {
        this.minecraftClient = minecraftClient;
        this.panels = new CopyOnWriteArrayList<>(panels);
        this.panelFactory = panelFactory;
        
        // 按优先级排序
        this.panels.sort(Comparator.comparingInt(IPanel::getPriority));
        
        LOGGER.info("[ChronoBlocks] 已初始化 {} 个面板（使用依赖注入）", panels.size());
    }
    
    /**
     * 初始化所有面板（传统方式）
     */
    private void initializePanels() {
        LOGGER.info("[ChronoBlocks] 初始化UI面板依赖注入配置（传统方式）");
        
        // 使用面板工厂创建面板实例
        try {
            panels.add(panelFactory.createPanel(MenuBarPanel.class, minecraftClient));
            panels.add(panelFactory.createPanel(ToolbarPanel.class, minecraftClient));
            panels.add(panelFactory.createPanel(ContainerPanel.class, minecraftClient));
            panels.add(panelFactory.createPanel(PropertiesPanel.class, minecraftClient));
            panels.add(panelFactory.createPanel(TimelinePanel.class, minecraftClient));
            
            // 注册新的选择系统面板
            panels.add(panelFactory.createPanel(SelectionFilterPanel.class, minecraftClient));
            
            // 注册模块化的独立面板（ToolPropertiesPanel 现在包含渲染设置）
            panels.add(panelFactory.createPanel(ToolPropertiesPanel.class, minecraftClient));
            panels.add(panelFactory.createPanel(RenderSettingsPanel.class, minecraftClient));
            panels.add(panelFactory.createPanel(SelectionHistoryPanel.class, minecraftClient));
            
            // 注册状态栏面板
            panels.add(panelFactory.createPanel(StatusBarPanel.class, minecraftClient));
            
            // 按优先级排序
            panels.sort(Comparator.comparingInt(IPanel::getPriority));
            
            LOGGER.info("[ChronoBlocks] 已初始化 {} 个面板（传统方式）", panels.size());
        } catch (Exception e) {
            LOGGER.error("[ChronoBlocks] 初始化面板失败", e);
            throw new RuntimeException("初始化面板失败", e);
        }
    }
    
    /**
     * 创建UI管理器实例
     * @return 配置好的UI管理器
     * @throws IllegalStateException 如果面板配置无效
     */
    public ChronoBlocksUIManager createUIManager() {
        validatePanelConfiguration();
        return new ChronoBlocksUIManager(panels);
    }

    /**
     * 验证面板配置的有效性
     * @throws IllegalStateException 如果面板配置无效
     */
    private void validatePanelConfiguration() {
        PanelValidator.ValidationResult result = PanelValidator.validatePanels(panels);
        
        if (!result.isValid()) {
            LOGGER.error("[ChronoBlocks] 面板配置验证失败:\n{}", result.getSummary());
            throw new IllegalStateException("面板配置验证失败: " + String.join(", ", result.getErrors()));
        }
        
        if (result.hasWarnings()) {
            LOGGER.warn("[ChronoBlocks] 面板配置验证警告:\n{}", result.getSummary());
        }
        
        LOGGER.debug("[ChronoBlocks] 面板配置验证通过: {} 个面板", panels.size());
    }
    
    /**
     * 获取所有面板（只读）
     * @return 面板列表的不可变副本
     */
    public List<IPanel> getPanels() {
        return List.copyOf(panels);
    }
    
    /**
     * 获取面板工厂
     * @return 面板工厂实例
     */
    public IPanelFactory getPanelFactory() {
        return panelFactory;
    }

    /**
     * 检查是否使用了依赖注入
     * @return true如果使用了依赖注入，false如果使用传统方式
     */
    public boolean isUsingDependencyInjection() {
        return panelFactory != null;
    }
    
    /**
     * 获取面板数量
     * @return 面板数量
     */
    public int getPanelCount() {
        return panels.size();
    }

}