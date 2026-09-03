package com.director.ui.di;

import com.director.core.DirectorServices;
import com.director.ui.panels.*;
import com.director.core.container.ContainerManager;
import com.director.ui.container.ContainerCommandBridge;
import com.director.ui.timeline.commands.TimelineCommandHistory;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PanelFactory - 面板工厂实现
 * <p>
 * 基于反射和注册机制的面板工厂，支持动态面板创建和依赖注入。
 * 提供高性能的面板创建和类型安全保证。
 * 
 * <h3>设计特点：</h3>
 * <ul>
 *   <li><strong>高性能</strong> - 使用缓存避免重复反射操作</li>
 *   <li><strong>类型安全</strong> - 泛型设计确保编译时类型检查</li>
 *   <li><strong>可扩展</strong> - 支持自定义面板创建器注册</li>
 *   <li><strong>线程安全</strong> - 使用ConcurrentHashMap确保并发安全</li>
 * </ul>
 * 
 * @author Director Team
 * @version 1.0
 * @since 2.1
 */
public class PanelFactory implements IPanelFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(PanelFactory.class);
    
    // 面板类型到创建器的映射
    private final Map<Class<? extends IPanel>, PanelCreator<? extends IPanel>> creators;
    private final TimelineCommandHistory sharedCommandHistory = new TimelineCommandHistory();
    
    // 默认面板类型映射
    private static final Map<Class<? extends IPanel>, Class<? extends IPanel>> DEFAULT_PANEL_TYPES = new HashMap<>();
    
    static {
        // 注册默认面板类型
        DEFAULT_PANEL_TYPES.put(MenuBarPanel.class, MenuBarPanel.class);
        DEFAULT_PANEL_TYPES.put(ToolbarPanel.class, ToolbarPanel.class);
        DEFAULT_PANEL_TYPES.put(ContainerPanel.class, ContainerPanel.class);
        DEFAULT_PANEL_TYPES.put(ActorsPanel.class, ActorsPanel.class);
        DEFAULT_PANEL_TYPES.put(PropertiesPanel.class, PropertiesPanel.class);
        DEFAULT_PANEL_TYPES.put(TimelinePanel.class, TimelinePanel.class);
    }
    
    /**
     * 创建面板工厂实例
     */
    public PanelFactory() {
        this.creators = new ConcurrentHashMap<>();
        registerDefaultCreators();
    }
    
    /**
     * 注册默认面板创建器
     */
    private void registerDefaultCreators() {
        // 注册MenuBarPanel创建器
        registerPanelCreator(MenuBarPanel.class, minecraftClient -> {
            // 目前面板类不支持MinecraftClient参数，使用无参构造函数
            // 未来可以扩展面板类以支持MinecraftClient依赖注入
            LOGGER.debug("[PanelFactory] 创建MenuBarPanel (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            return new MenuBarPanel();
        });
        
        // 注册ToolbarPanel创建器
        registerPanelCreator(ToolbarPanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建ToolbarPanel (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            return new ToolbarPanel();
        });
        
        // 注册ContainerPanel创建器 - 使用依赖注入
        registerPanelCreator(ContainerPanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建ContainerPanel with Dependency Injection (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            
            // 创建依赖对象
            ContainerManager containerManager = DirectorServices.get().containers();
            TimelineCommandHistory commandHistory = sharedCommandHistory;
            ContainerCommandBridge.bindHistory(commandHistory);
            
            // 使用依赖注入构造函数（v5.0: 移除了WorldBlockCollector参数，propertiesPanel 在内部创建）
            return new ContainerPanel(containerManager, commandHistory);
        });

        registerPanelCreator(ActorsPanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建ActorsPanel (MinecraftClient: {})",
                minecraftClient != null ? "已注入" : "未注入");
            return new ActorsPanel();
        });
        
        // 注册PropertiesPanel创建器
        registerPanelCreator(PropertiesPanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建PropertiesPanel (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            return new PropertiesPanel();
        });
        
        // 注册TimelinePanel创建器
        registerPanelCreator(TimelinePanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建TimelinePanel (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            return new TimelinePanel(sharedCommandHistory);
        });

        // 注册SelectionFilterPanel创建器
        registerPanelCreator(SelectionFilterPanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建SelectionFilterPanel (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            return new SelectionFilterPanel();
        });
        
        // 注册ToolPropertiesPanel创建器（现在包含SelectionRenderSettingsPanel的功能）
        registerPanelCreator(ToolPropertiesPanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建ToolPropertiesPanel (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            return new ToolPropertiesPanel();
        });
        
        // 注册RenderSettingsPanel创建器（原 SelectionRenderSettingsPanel）
        registerPanelCreator(RenderSettingsPanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建RenderSettingsPanel (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            return new RenderSettingsPanel();
        });
        
        // 注册SelectionHistoryPanel创建器
        registerPanelCreator(SelectionHistoryPanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建SelectionHistoryPanel (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            return new SelectionHistoryPanel();
        });
        
        // 注册StatusBarPanel创建器
        registerPanelCreator(StatusBarPanel.class, minecraftClient -> {
            LOGGER.debug("[PanelFactory] 创建StatusBarPanel (MinecraftClient: {})", 
                minecraftClient != null ? "已注入" : "未注入");
            return new StatusBarPanel();
        });
        
        
        LOGGER.debug("[PanelFactory] 已注册 {} 个默认面板创建器", creators.size());
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T extends IPanel> T createPanel(Class<T> panelType, MinecraftClient minecraftClient) {
        if (panelType == null) {
            throw new IllegalArgumentException("面板类型不能为null");
        }
        
        PanelCreator<? extends IPanel> creator = creators.get(panelType);
        if (creator == null) {
            throw new IllegalArgumentException("不支持的面板类型: " + panelType.getSimpleName());
        }
        
        try {
            T panel = (T) creator.create(minecraftClient);
            LOGGER.debug("[PanelFactory] 创建面板: {} (MinecraftClient: {})", 
                panelType.getSimpleName(), minecraftClient != null ? "已注入" : "未注入");
            return panel;
        } catch (Exception e) {
            LOGGER.error("[PanelFactory] 创建面板失败: {}", panelType.getSimpleName(), e);
            throw new RuntimeException("创建面板失败: " + panelType.getSimpleName(), e);
        }
    }
    
    @Override
    public <T extends IPanel> T createPanel(Class<T> panelType) {
        return createPanel(panelType, null);
    }
    
    @Override
    public boolean supportsPanelType(Class<? extends IPanel> panelType) {
        return panelType != null && creators.containsKey(panelType);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends IPanel>[] getSupportedPanelTypes() {
        return creators.keySet().toArray(new Class[0]);
    }
    
    @Override
    public <T extends IPanel> void registerPanelCreator(Class<T> panelType, PanelCreator<T> creator) {
        if (panelType == null) {
            throw new IllegalArgumentException("面板类型不能为null");
        }
        if (creator == null) {
            throw new IllegalArgumentException("面板创建器不能为null");
        }
        
        creators.put(panelType, creator);
        LOGGER.debug("[PanelFactory] 注册自定义面板创建器: {}", panelType.getSimpleName());
    }
    
    /**
     * 获取已注册的面板创建器数量
     * @return 创建器数量
     */
    public int getCreatorCount() {
        return creators.size();
    }
    
    /**
     * 清除所有自定义创建器（保留默认创建器）
     */
    public void clearCustomCreators() {
        creators.clear();
        registerDefaultCreators();
        LOGGER.info("[PanelFactory] 已清除所有自定义创建器，恢复默认配置");
    }
}
