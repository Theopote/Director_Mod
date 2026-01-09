package com.chronoblocks.ui.di;

import com.chronoblocks.ui.panels.*;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UIConfigurationBuilder - UI配置构建器
 * <p>
 * 使用Builder模式重构UIConfiguration，提供灵活的面板配置和依赖注入。
 * 支持动态面板注册、配置文件加载和自定义面板工厂。
 * 
 * <h3>核心功能：</h3>
 * <ul>
 *   <li><strong>Builder模式</strong> - 链式调用构建复杂配置</li>
 *   <li><strong>动态配置</strong> - 支持运行时面板注册和配置</li>
 *   <li><strong>依赖注入</strong> - 正确使用MinecraftClient参数</li>
 *   <li><strong>配置验证</strong> - 完整的配置验证和错误处理</li>
 * </ul>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 基础配置
 * UIConfiguration config = UIConfigurationBuilder.builder()
 *     .withMinecraftClient(minecraftClient)
 *     .withDefaultPanels()
 *     .build();
 * 
 * // 自定义配置
 * UIConfiguration config = UIConfigurationBuilder.builder()
 *     .withMinecraftClient(minecraftClient)
 *     .addPanel(MenuBarPanel.class)
 *     .addPanel(ToolbarPanel.class)
 *     .addPanel(ContainerPanel.class)
 *     .withCustomFactory(customFactory)
 *     .build();
 * 
 * // 从配置文件加载
 * UIConfiguration config = UIConfigurationBuilder.builder()
 *     .withMinecraftClient(minecraftClient)
 *     .loadFromConfig("ui-config.json")
 *     .build();
 * }</pre>
 * 
 * @author ChronoBlocks Team
 * @version 2.1
 * @since 2.1
 */
public class UIConfigurationBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIConfigurationBuilder.class);
    
    private MinecraftClient minecraftClient;
    private IPanelFactory panelFactory;
    private final List<Class<? extends IPanel>> panelTypes;
    private final List<IPanel> customPanels;
    private boolean useDefaultPanels = false;
    private String configFilePath;
    private boolean enableValidation = true;
    private boolean enableLogging = true;
    private PanelConfigurationLoader configLoader;
    private UIConfigurationValidator validator;
    
    /**
     * 创建构建器实例
     * @return 新的构建器实例
     */
    public static UIConfigurationBuilder builder() {
        return new UIConfigurationBuilder();
    }
    
    /**
     * 私有构造函数
     */
    private UIConfigurationBuilder() {
        this.panelTypes = new ArrayList<>();
        this.customPanels = new ArrayList<>();
    }
    
    /**
     * 设置MinecraftClient实例
     * @param minecraftClient Minecraft客户端实例
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder withMinecraftClient(MinecraftClient minecraftClient) {
        this.minecraftClient = minecraftClient;
        if (enableLogging) {
            LOGGER.debug("[UIConfigurationBuilder] 设置MinecraftClient: {}", 
                minecraftClient != null ? "已注入" : "未注入");
        }
        return this;
    }
    
    /**
     * 设置自定义面板工厂
     * @param panelFactory 面板工厂实例
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder withCustomFactory(IPanelFactory panelFactory) {
        this.panelFactory = panelFactory;
        if (enableLogging) {
            LOGGER.debug("[UIConfigurationBuilder] 设置自定义面板工厂: {}", 
                panelFactory != null ? panelFactory.getClass().getSimpleName() : "null");
        }
        return this;
    }
    
    /**
     * 启用默认面板配置
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder withDefaultPanels() {
        this.useDefaultPanels = true;
        if (enableLogging) {
            LOGGER.debug("[UIConfigurationBuilder] 启用默认面板配置");
        }
        return this;
    }
    
    /**
     * 添加面板类型
     * @param panelType 面板类型
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder addPanel(Class<? extends IPanel> panelType) {
        if (panelType != null) {
            this.panelTypes.add(panelType);
            if (enableLogging) {
                LOGGER.debug("[UIConfigurationBuilder] 添加面板类型: {}", panelType.getSimpleName());
            }
        }
        return this;
    }
    
    /**
     * 添加多个面板类型
     * @param panelTypes 面板类型数组
     * @return 构建器实例（支持链式调用）
     */
    @SafeVarargs
    public final UIConfigurationBuilder addPanels(Class<? extends IPanel>... panelTypes) {
        if (panelTypes != null) {
            for (Class<? extends IPanel> panelType : panelTypes) {
                addPanel(panelType);
            }
        }
        return this;
    }
    
    /**
     * 添加自定义面板实例
     * @param panel 面板实例
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder addCustomPanel(IPanel panel) {
        if (panel != null) {
            this.customPanels.add(panel);
            if (enableLogging) {
                LOGGER.debug("[UIConfigurationBuilder] 添加自定义面板: {}", panel.getPanelName());
            }
        }
        return this;
    }
    
    /**
     * 从配置文件加载面板配置
     * @param configFilePath 配置文件路径
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder loadFromConfig(String configFilePath) {
        this.configFilePath = configFilePath;
        if (enableLogging) {
            LOGGER.debug("[UIConfigurationBuilder] 设置配置文件路径: {}", configFilePath);
        }
        return this;
    }
    
    /**
     * 设置配置加载器
     * @param configLoader 配置加载器
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder withConfigLoader(PanelConfigurationLoader configLoader) {
        this.configLoader = configLoader;
        if (enableLogging) {
            LOGGER.debug("[UIConfigurationBuilder] 设置配置加载器: {}", 
                configLoader != null ? configLoader.getClass().getSimpleName() : "null");
        }
        return this;
    }
    
    /**
     * 设置配置验证器
     * @param validator 配置验证器
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder withValidator(UIConfigurationValidator validator) {
        this.validator = validator;
        if (enableLogging) {
            LOGGER.debug("[UIConfigurationBuilder] 设置配置验证器: {}", 
                validator != null ? validator.getClass().getSimpleName() : "null");
        }
        return this;
    }
    
    /**
     * 启用或禁用配置验证
     * @param enableValidation 是否启用验证
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder withValidation(boolean enableValidation) {
        this.enableValidation = enableValidation;
        if (enableLogging) {
            LOGGER.debug("[UIConfigurationBuilder] 设置配置验证: {}", enableValidation);
        }
        return this;
    }
    
    /**
     * 启用或禁用日志记录
     * @param enableLogging 是否启用日志
     * @return 构建器实例（支持链式调用）
     */
    public UIConfigurationBuilder withLogging(boolean enableLogging) {
        this.enableLogging = enableLogging;
        return this;
    }
    
    /**
     * 构建UIConfiguration实例
     * @return 配置好的UIConfiguration实例
     * @throws IllegalStateException 如果配置无效
     */
    public UIConfiguration build() {
        if (enableLogging) {
            LOGGER.info("[UIConfigurationBuilder] 开始构建UIConfiguration");
        }
        
        // 创建配置加载器
        PanelConfigurationLoader loader = createConfigLoader();
        
        // 加载配置文件（如果指定）
        PanelConfiguration config = loadConfiguration(loader);
        
        // 创建面板工厂
        IPanelFactory factory = createPanelFactory();
        
        // 创建面板列表
        List<IPanel> panels = createPanels(factory, config);
        
        // 创建UIConfiguration实例
        UIConfiguration configuration = new UIConfiguration(minecraftClient, panels, factory);
        
        // 验证配置
        if (enableValidation) {
            validateConfiguration(configuration);
        }
        
        if (enableLogging) {
            LOGGER.info("[UIConfigurationBuilder] 成功构建UIConfiguration，包含 {} 个面板", panels.size());
        }
        
        return configuration;
    }
    
    /**
     * 验证配置的有效性
     * @param configuration UI配置
     * @throws IllegalStateException 如果配置无效
     */
    private void validateConfiguration(UIConfiguration configuration) {
        // 基础验证
        if (!useDefaultPanels && panelTypes.isEmpty() && customPanels.isEmpty()) {
            throw new IllegalStateException("必须至少指定一种面板配置方式：默认面板、面板类型或自定义面板");
        }
        
        if (panelFactory != null && !panelTypes.isEmpty()) {
            // 验证面板工厂是否支持所有面板类型
            for (Class<? extends IPanel> panelType : panelTypes) {
                if (!panelFactory.supportsPanelType(panelType)) {
                    throw new IllegalStateException("面板工厂不支持面板类型: " + panelType.getSimpleName());
                }
            }
        }
        
        // 使用验证器进行详细验证
        if (validator != null) {
            UIConfigurationValidator.ValidationResult result = validator.validateConfiguration(configuration);
            if (!result.isValid()) {
                LOGGER.error("[UIConfigurationBuilder] 配置验证失败:\n{}", result.getSummary());
                throw new IllegalStateException("配置验证失败: " + String.join(", ", result.getErrors()));
            }
            
            if (result.hasWarnings()) {
                LOGGER.warn("[UIConfigurationBuilder] 配置验证警告:\n{}", result.getSummary());
            }
        }
    }
    
    /**
     * 创建配置加载器
     * @return 配置加载器实例
     */
    private PanelConfigurationLoader createConfigLoader() {
        if (configLoader != null) {
            return configLoader;
        }
        return new PanelConfigurationLoader();
    }
    
    /**
     * 加载配置
     * @param loader 配置加载器
     * @return 面板配置
     */
    private PanelConfiguration loadConfiguration(PanelConfigurationLoader loader) {
        if (configFilePath != null && !configFilePath.trim().isEmpty()) {
            return loader.loadConfiguration(configFilePath);
        }
        return loader.loadDefaultConfiguration();
    }
    
    /**
     * 创建面板工厂
     * @return 面板工厂实例
     */
    private IPanelFactory createPanelFactory() {
        if (panelFactory != null) {
            return panelFactory;
        }
        return new PanelFactory();
    }
    
    /**
     * 创建面板列表
     * @param factory 面板工厂
     * @param config 面板配置
     * @return 面板列表
     */
    private List<IPanel> createPanels(IPanelFactory factory, PanelConfiguration config) {

        // 添加自定义面板
        List<IPanel> panels = new CopyOnWriteArrayList<>(customPanels);
        
        // 从配置文件创建面板
        if (config != null && config.getPanels() != null) {
            addPanelsFromConfig(panels, factory, config);
        } else {
            // 添加默认面板
            if (useDefaultPanels) {
                addDefaultPanels(panels, factory);
            }
            
            // 添加指定类型的面板
            for (Class<? extends IPanel> panelType : panelTypes) {
                try {
                    IPanel panel = factory.createPanel(panelType, minecraftClient);
                    panels.add(panel);
                } catch (Exception e) {
                    LOGGER.error("[UIConfigurationBuilder] 创建面板失败: {}", panelType.getSimpleName(), e);
                    if (enableValidation) {
                        throw new RuntimeException("创建面板失败: " + panelType.getSimpleName(), e);
                    }
                }
            }
        }
        
        // 按优先级排序
        panels.sort(Comparator.comparingInt(IPanel::getPriority));
        
        return panels;
    }
    
    /**
     * 从配置文件添加面板
     * @param panels 面板列表
     * @param factory 面板工厂
     * @param config 面板配置
     */
    private void addPanelsFromConfig(List<IPanel> panels, IPanelFactory factory, PanelConfiguration config) {
        for (PanelConfiguration.PanelDefinition panelDef : config.getPanels()) {
            if (!panelDef.isEnabled()) {
                if (enableLogging) {
                    LOGGER.debug("[UIConfigurationBuilder] 跳过禁用的面板: {}", panelDef.getType());
                }
                continue;
            }
            
            try {
                Class<? extends IPanel> panelType = getPanelTypeByName(panelDef.getType());
                if (panelType == null) {
                    LOGGER.warn("[UIConfigurationBuilder] 未知的面板类型: {}", panelDef.getType());
                    continue;
                }
                
                IPanel panel = factory.createPanel(panelType, minecraftClient);
                
                // 设置面板优先级（如果支持）
                if (panel instanceof AbstractPanel) {
                    // 这里可以添加设置优先级的逻辑
                }
                
                panels.add(panel);
                
                if (enableLogging) {
                    LOGGER.debug("[UIConfigurationBuilder] 从配置创建面板: {} (优先级: {})", 
                        panelDef.getType(), panelDef.getPriority());
                }
                
            } catch (Exception e) {
                LOGGER.error("[UIConfigurationBuilder] 从配置创建面板失败: {}", panelDef.getType(), e);
                if (enableValidation) {
                    throw new RuntimeException("从配置创建面板失败: " + panelDef.getType(), e);
                }
            }
        }
    }
    
    /**
     * 根据名称获取面板类型
     * @param typeName 类型名称
     * @return 面板类型，如果未找到则返回null
     */
    private Class<? extends IPanel> getPanelTypeByName(String typeName) {
        // 这里可以使用反射或映射表来查找面板类型
        // 为了简化，我们使用硬编码映射
        return switch (typeName) {
            case "MenuBarPanel" -> MenuBarPanel.class;
            case "ToolbarPanel" -> ToolbarPanel.class;
            case "ContainerPanel" -> ContainerPanel.class;
            case "ActorsPanel" -> ActorsPanel.class;
            case "PropertiesPanel" -> PropertiesPanel.class;
            case "TimelinePanel" -> TimelinePanel.class;
            // 添加新的模块化面板
            case "ToolPropertiesPanel" -> ToolPropertiesPanel.class;
            case "SelectionRenderSettingsPanel" -> RenderSettingsPanel.class; // 向后兼容旧名称
            case "RenderSettingsPanel" -> RenderSettingsPanel.class;
            case "SelectionFilterPanel" -> SelectionFilterPanel.class;
            case "SelectionHistoryPanel" -> SelectionHistoryPanel.class;
            case "HelpPanel" -> HelpPanel.class;
            default -> null;
        };
    }
    
    /**
     * 添加默认面板
     * @param panels 面板列表
     * @param factory 面板工厂
     */
    private void addDefaultPanels(List<IPanel> panels, IPanelFactory factory) {
        // 默认面板类型（按优先级排序）
            List<Class<? extends IPanel>> defaultTypes = Arrays.asList(
            MenuBarPanel.class,
            ToolbarPanel.class,
            ContainerPanel.class,
            ActorsPanel.class,
            PropertiesPanel.class,
            TimelinePanel.class,
            // 添加新的模块化面板
            ToolPropertiesPanel.class,
            RenderSettingsPanel.class,
            SelectionFilterPanel.class,
            SelectionHistoryPanel.class,
            HelpPanel.class
        );
        
        for (Class<? extends IPanel> panelType : defaultTypes) {
            try {
                IPanel panel = factory.createPanel(panelType, minecraftClient);
                panels.add(panel);
            } catch (Exception e) {
                LOGGER.error("[UIConfigurationBuilder] 创建默认面板失败: {}", panelType.getSimpleName(), e);
                if (enableValidation) {
                    throw new RuntimeException("创建默认面板失败: " + panelType.getSimpleName(), e);
                }
            }
        }
    }
}
