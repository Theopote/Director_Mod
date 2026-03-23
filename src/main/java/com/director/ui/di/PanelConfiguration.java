package com.director.ui.di;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * PanelConfiguration - 面板配置数据类
 * <p>
 * 用于JSON配置文件的面板配置数据结构，支持灵活的面板配置和依赖注入。
 * 
 * <h3>配置示例：</h3>
 * <pre>{@code
 * {
 *   "panels": [
 *     {
 *       "type": "MenuBarPanel",
 *       "enabled": true,
 *       "priority": 10,
 *       "properties": {
 *         "title": "Director Menu",
 *         "showIcons": true
 *       }
 *     },
 *     {
 *       "type": "ToolbarPanel",
 *       "enabled": true,
 *       "priority": 20,
 *       "properties": {
 *         "orientation": "horizontal",
 *         "buttonSize": 32
 *       }
 *     }
 *   ],
 *   "settings": {
 *     "enableValidation": true,
 *     "enableLogging": true,
 *     "defaultFactory": "PanelFactory"
 *   }
 * }
 * }</pre>
 * 
 * @author Director Team
 * @version 1.0
 * @since 2.1
 */
public class PanelConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(PanelConfiguration.class);
    
    @JsonProperty("panels")
    private List<PanelDefinition> panels;
    
    @JsonProperty("settings")
    private ConfigurationSettings settings;
    
    /**
     * 默认构造函数
     */
    public PanelConfiguration() {
        this.settings = new ConfigurationSettings();
    }
    
    /**
     * 面板定义
     */
    public static class PanelDefinition {
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("enabled")
        private boolean enabled = true;
        
        @JsonProperty("priority")
        private int priority = 100;
        
        @JsonProperty("properties")
        private Map<String, Object> properties;
        
        @JsonProperty("dependencies")
        private List<String> dependencies;
        
        // 构造函数
        public PanelDefinition() {}
        
        public PanelDefinition(String type, boolean enabled, int priority) {
            this.type = type;
            this.enabled = enabled;
            this.priority = priority;
        }
        
        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        public Map<String, Object> getProperties() { return properties; }
        public void setProperties(Map<String, Object> properties) { this.properties = properties; }
        
        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
        
        @Override
        public String toString() {
            return String.format("PanelDefinition{type='%s', enabled=%s, priority=%d}", 
                type, enabled, priority);
        }
    }
    
    /**
     * 配置设置
     */
    public static class ConfigurationSettings {
        @JsonProperty("enableValidation")
        private boolean enableValidation = true;
        
        @JsonProperty("enableLogging")
        private boolean enableLogging = true;
        
        @JsonProperty("defaultFactory")
        private String defaultFactory = "PanelFactory";
        
        @JsonProperty("customFactory")
        private String customFactory;
        
        @JsonProperty("configVersion")
        private String configVersion = "2.1";
        
        // 构造函数
        public ConfigurationSettings() {}
        
        // Getters and Setters
        public boolean isEnableValidation() { return enableValidation; }
        public void setEnableValidation(boolean enableValidation) { this.enableValidation = enableValidation; }
        
        public boolean isEnableLogging() { return enableLogging; }
        public void setEnableLogging(boolean enableLogging) { this.enableLogging = enableLogging; }
        
        public String getDefaultFactory() { return defaultFactory; }
        public void setDefaultFactory(String defaultFactory) { this.defaultFactory = defaultFactory; }
        
        public String getCustomFactory() { return customFactory; }
        public void setCustomFactory(String customFactory) { this.customFactory = customFactory; }
        
        public String getConfigVersion() { return configVersion; }
        public void setConfigVersion(String configVersion) { this.configVersion = configVersion; }
        
        @Override
        public String toString() {
            return String.format("ConfigurationSettings{validation=%s, logging=%s, factory='%s'}", 
                enableValidation, enableLogging, defaultFactory);
        }
    }
    
    // Getters and Setters
    public List<PanelDefinition> getPanels() { return panels; }
    public void setPanels(List<PanelDefinition> panels) { this.panels = panels; }
    
    public ConfigurationSettings getSettings() { return settings; }
    public void setSettings(ConfigurationSettings settings) { this.settings = settings; }
    
    /**
     * 验证配置的有效性
     * @return true如果配置有效，false否则
     */
    public boolean isValid() {
        if (panels == null || panels.isEmpty()) {
            LOGGER.warn("[PanelConfiguration] 配置中没有定义面板");
            return false;
        }
        
        for (PanelDefinition panel : panels) {
            if (panel.getType() == null || panel.getType().trim().isEmpty()) {
                LOGGER.error("[PanelConfiguration] 面板类型不能为空");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 获取启用的面板数量
     * @return 启用的面板数量
     */
    public int getEnabledPanelCount() {
        if (panels == null) return 0;
        return (int) panels.stream().filter(PanelDefinition::isEnabled).count();
    }
    
    /**
     * 获取禁用的面板数量
     * @return 禁用的面板数量
     */
    public int getDisabledPanelCount() {
        if (panels == null) return 0;
        return (int) panels.stream().filter(panel -> !panel.isEnabled()).count();
    }
    
    @Override
    public String toString() {
        return String.format("PanelConfiguration{panels=%d, enabled=%d, disabled=%d, settings=%s}", 
            panels != null ? panels.size() : 0, 
            getEnabledPanelCount(), 
            getDisabledPanelCount(), 
            settings);
    }
}
