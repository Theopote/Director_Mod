package com.chronoblocks.ui.di;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;

/**
 * PanelConfigurationLoader - 面板配置加载器
 * <p>
 * 负责从JSON配置文件加载面板配置，支持文件系统、类路径和默认配置加载。
 * 提供完整的错误处理和配置验证功能。
 * 
 * <h3>支持的文件格式：</h3>
 * <ul>
 *   <li><strong>JSON</strong> - 标准JSON格式配置文件</li>
 *   <li><strong>YAML</strong> - YAML格式配置文件（未来支持）</li>
 * </ul>
 * 
 * <h3>配置加载优先级：</h3>
 * <ol>
 *   <li>用户自定义配置文件</li>
 *   <li>类路径中的默认配置文件</li>
 *   <li>硬编码的默认配置</li>
 * </ol>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2.1
 */
public class PanelConfigurationLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(PanelConfigurationLoader.class);
    
    private static final String DEFAULT_CONFIG_PATH = "ui-config.json";
    private static final String DEFAULT_CONFIG_RESOURCE = "/config/ui-config.json";
    
    private final ObjectMapper objectMapper;
    
    /**
     * 创建配置加载器
     */
    public PanelConfigurationLoader() {
        this.objectMapper = new ObjectMapper();
        // 配置JSON解析器忽略未知属性
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    
    /**
     * 从文件路径加载配置
     * @param configPath 配置文件路径
     * @return 面板配置，如果加载失败则返回默认配置
     */
    public PanelConfiguration loadFromFile(String configPath) {
        if (configPath == null || configPath.trim().isEmpty()) {
            LOGGER.warn("[PanelConfigurationLoader] 配置文件路径为空，使用默认配置");
            return createDefaultConfiguration();
        }
        
        try {
            Path path = Paths.get(configPath);
            if (!Files.exists(path)) {
                LOGGER.warn("[PanelConfigurationLoader] 配置文件不存在: {}，使用默认配置", configPath);
                return createDefaultConfiguration();
            }
            
            PanelConfiguration config = objectMapper.readValue(path.toFile(), PanelConfiguration.class);
            
            if (config.isValid()) {
                LOGGER.info("[PanelConfigurationLoader] 成功从文件加载配置: {} ({} 个面板)", 
                    configPath, config.getEnabledPanelCount());
                return config;
            } else {
                LOGGER.error("[PanelConfigurationLoader] 配置文件无效: {}，使用默认配置", configPath);
                return createDefaultConfiguration();
            }
            
        } catch (IOException e) {
            LOGGER.error("[PanelConfigurationLoader] 加载配置文件失败: {}", configPath, e);
            return createDefaultConfiguration();
        }
    }
    
    /**
     * 从类路径资源加载配置
     * @param resourcePath 资源路径
     * @return 面板配置，如果加载失败则返回默认配置
     */
    public PanelConfiguration loadFromResource(String resourcePath) {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            LOGGER.warn("[PanelConfigurationLoader] 资源路径为空，使用默认配置");
            return createDefaultConfiguration();
        }
        
        try (InputStream inputStream = getClass().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                LOGGER.warn("[PanelConfigurationLoader] 资源文件不存在: {}，使用默认配置", resourcePath);
                return createDefaultConfiguration();
            }
            
            PanelConfiguration config = objectMapper.readValue(inputStream, PanelConfiguration.class);
            
            if (config.isValid()) {
                LOGGER.info("[PanelConfigurationLoader] 成功从资源加载配置: {} ({} 个面板)", 
                    resourcePath, config.getEnabledPanelCount());
                return config;
            } else {
                LOGGER.error("[PanelConfigurationLoader] 资源配置文件无效: {}，使用默认配置", resourcePath);
                return createDefaultConfiguration();
            }
            
        } catch (IOException e) {
            LOGGER.error("[PanelConfigurationLoader] 加载资源文件失败: {}", resourcePath, e);
            return createDefaultConfiguration();
        }
    }
    
    /**
     * 加载默认配置
     * @return 默认面板配置
     */
    public PanelConfiguration loadDefaultConfiguration() {
        LOGGER.info("[PanelConfigurationLoader] 加载默认配置");
        return createDefaultConfiguration();
    }
    
    /**
     * 智能加载配置（按优先级尝试多种方式）
     * @param customConfigPath 自定义配置文件路径
     * @return 面板配置
     */
    public PanelConfiguration loadConfiguration(String customConfigPath) {
        // 1. 尝试加载用户自定义配置文件
        if (customConfigPath != null && !customConfigPath.trim().isEmpty()) {
            PanelConfiguration config = loadFromFile(customConfigPath);
            if (config != null && config.isValid()) {
                return config;
            }
        }
        
        // 2. 尝试加载默认配置文件
        PanelConfiguration config = loadFromFile(DEFAULT_CONFIG_PATH);
        if (config != null && config.isValid()) {
            return config;
        }
        
        // 3. 尝试从类路径加载
        config = loadFromResource(DEFAULT_CONFIG_RESOURCE);
        if (config != null && config.isValid()) {
            return config;
        }
        
        // 4. 使用硬编码默认配置
        LOGGER.info("[PanelConfigurationLoader] 使用硬编码默认配置");
        return createDefaultConfiguration();
    }
    
    /**
     * 创建默认配置
     * @return 默认面板配置
     */
    private PanelConfiguration createDefaultConfiguration() {
        PanelConfiguration config = new PanelConfiguration();
        
        // 创建默认面板定义
        List<PanelConfiguration.PanelDefinition> panels = new ArrayList<>();
        
        // MenuBarPanel
        panels.add(new PanelConfiguration.PanelDefinition("MenuBarPanel", true, 10));
        
        // ToolbarPanel
        panels.add(new PanelConfiguration.PanelDefinition("ToolbarPanel", true, 20));
        
        // ContainerPanel
        panels.add(new PanelConfiguration.PanelDefinition("ContainerPanel", true, 30));

        // ⚠️ ActorsPanel 已隐藏，等待下一版本
        // ActorsPanel
        // panels.add(new PanelConfiguration.PanelDefinition("ActorsPanel", true, 35));
        
        // PropertiesPanel
        panels.add(new PanelConfiguration.PanelDefinition("PropertiesPanel", true, 40));
        
        // TimelinePanel
        panels.add(new PanelConfiguration.PanelDefinition("TimelinePanel", true, 50));
        
        // 添加新的模块化面板
        panels.add(new PanelConfiguration.PanelDefinition("ToolPropertiesPanel", true, 60));
        panels.add(new PanelConfiguration.PanelDefinition("SelectionRenderSettingsPanel", true, 70));
        panels.add(new PanelConfiguration.PanelDefinition("SelectionFilterPanel", true, 80));
        panels.add(new PanelConfiguration.PanelDefinition("SelectionHistoryPanel", true, 90));
        panels.add(new PanelConfiguration.PanelDefinition("HelpPanel", true, 100));
        
        // 状态栏面板（优先级 -10，永远在最底部）
        panels.add(new PanelConfiguration.PanelDefinition("StatusBarPanel", true, -10));
        
        config.setPanels(panels);
        
        // 设置默认配置
        PanelConfiguration.ConfigurationSettings settings = new PanelConfiguration.ConfigurationSettings();
        settings.setEnableValidation(true);
        settings.setEnableLogging(true);
        settings.setDefaultFactory("PanelFactory");
        settings.setConfigVersion("2.1");
        config.setSettings(settings);
        
        LOGGER.debug("[PanelConfigurationLoader] 创建默认配置: {} 个面板", panels.size());
        return config;
    }
    
    /**
     * 保存配置到文件
     * @param config 面板配置
     * @param filePath 文件路径
     * @return true如果保存成功，false否则
     */
    public boolean saveConfiguration(PanelConfiguration config, String filePath) {
        if (config == null || filePath == null || filePath.trim().isEmpty()) {
            LOGGER.error("[PanelConfigurationLoader] 配置或文件路径为空");
            return false;
        }
        
        try {
            Path path = Paths.get(filePath);
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), config);
            LOGGER.info("[PanelConfigurationLoader] 配置已保存到: {}", filePath);
            return true;
        } catch (IOException e) {
            LOGGER.error("[PanelConfigurationLoader] 保存配置失败: {}", filePath, e);
            return false;
        }
    }
    
    /**
     * 验证配置文件格式
     * @param filePath 文件路径
     * @return true如果格式有效，false否则
     */
    public boolean validateConfigurationFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return false;
            }
            
            PanelConfiguration config = objectMapper.readValue(path.toFile(), PanelConfiguration.class);
            return config.isValid();
        } catch (IOException e) {
            LOGGER.debug("[PanelConfigurationLoader] 配置文件格式验证失败: {}", filePath, e);
            return false;
        }
    }
}
