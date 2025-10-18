package com.chronoblocks.ui.di;

import com.chronoblocks.ui.panels.IPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UIConfigurationValidator - UI配置验证器
 * <p>
 * 提供全面的配置验证功能，包括面板配置验证、依赖关系验证、性能验证等。
 * 支持多层次的验证策略和详细的错误报告。
 * 
 * <h3>验证类型：</h3>
 * <ul>
 *   <li><strong>基础验证</strong> - 面板类型、优先级、属性等基础配置验证</li>
 *   <li><strong>依赖验证</strong> - 面板间依赖关系验证</li>
 *   <li><strong>性能验证</strong> - 面板数量和性能影响验证</li>
 *   <li><strong>兼容性验证</strong> - 面板工厂兼容性验证</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2.1
 */
public class UIConfigurationValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(UIConfigurationValidator.class);
    
    // 验证结果缓存
    private final Map<String, ValidationResult> validationCache = new ConcurrentHashMap<>();
    
    // 验证配置
    private final ValidationConfig config;
    
    /**
     * 创建验证器
     */
    public UIConfigurationValidator() {
        this.config = new ValidationConfig();
    }
    
    /**
     * 创建验证器（自定义配置）
     * @param config 验证配置
     */
    public UIConfigurationValidator(ValidationConfig config) {
        this.config = config != null ? config : new ValidationConfig();
    }
    
    /**
     * 验证UIConfiguration
     * @param configuration UI配置
     * @return 验证结果
     */
    public ValidationResult validateConfiguration(UIConfiguration configuration) {
        if (configuration == null) {
            return ValidationResult.error("UIConfiguration不能为null");
        }
        
        String cacheKey = generateCacheKey(configuration);
        if (config.isEnableCaching() && validationCache.containsKey(cacheKey)) {
            LOGGER.debug("[UIConfigurationValidator] 使用缓存的验证结果");
            return validationCache.get(cacheKey);
        }
        
        ValidationResult result = new ValidationResult();
        
        try {
            // 基础验证
            validateBasicConfiguration(configuration, result);
            
            // 面板验证
            validatePanels(configuration, result);
            
            // 依赖验证
            if (config.isValidateDependencies()) {
                validateDependencies(configuration, result);
            }
            
            // 性能验证
            if (config.isValidatePerformance()) {
                validatePerformance(configuration, result);
            }
            
            // 兼容性验证
            if (config.isValidateCompatibility()) {
                validateCompatibility(configuration, result);
            }
            
            // 缓存结果
            if (config.isEnableCaching()) {
                validationCache.put(cacheKey, result);
            }
            
            LOGGER.info("[UIConfigurationValidator] 配置验证完成: {} 个错误, {} 个警告", 
                result.getErrorCount(), result.getWarningCount());
            
        } catch (Exception e) {
            LOGGER.error("[UIConfigurationValidator] 验证过程中发生异常", e);
            result.addError("验证过程中发生异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 验证面板配置
     * @param configuration 面板配置
     * @return 验证结果
     */
    public ValidationResult validatePanelConfiguration(PanelConfiguration configuration) {
        if (configuration == null) {
            return ValidationResult.error("PanelConfiguration不能为null");
        }
        
        ValidationResult result = new ValidationResult();
        
        try {
            // 基础配置验证
            if (!configuration.isValid()) {
                result.addError("面板配置无效");
                return result;
            }
            
            // 面板定义验证
            validatePanelDefinitions(configuration, result);
            
            // 设置验证
            validateSettings(configuration, result);
            
            LOGGER.debug("[UIConfigurationValidator] 面板配置验证完成: {} 个错误, {} 个警告", 
                result.getErrorCount(), result.getWarningCount());
            
        } catch (Exception e) {
            LOGGER.error("[UIConfigurationValidator] 面板配置验证过程中发生异常", e);
            result.addError("面板配置验证过程中发生异常: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 基础配置验证
     */
    private void validateBasicConfiguration(UIConfiguration configuration, ValidationResult result) {
        if (configuration.getPanels() == null || configuration.getPanels().isEmpty()) {
            result.addError("配置中没有面板");
            return;
        }
        
        if (configuration.getPanelFactory() == null) {
            result.addError("面板工厂不能为null");
        }
        
        if (configuration.getPanelCount() > config.getMaxPanelCount()) {
            result.addWarning(String.format("面板数量过多: %d (建议不超过 %d)", 
                configuration.getPanelCount(), config.getMaxPanelCount()));
        }
    }
    
    /**
     * 面板验证
     */
    private void validatePanels(UIConfiguration configuration, ValidationResult result) {
        List<IPanel> panels = configuration.getPanels();
        
        for (int i = 0; i < panels.size(); i++) {
            IPanel panel = panels.get(i);
            
            try {
                // 面板基础验证
                if (panel == null) {
                    result.addError(String.format("面板[%d]为null", i));
                    continue;
                }
                
                String panelName = panel.getPanelName();
                if (panelName == null || panelName.trim().isEmpty()) {
                    result.addError(String.format("面板[%d]名称为空", i));
                }
                
                int priority = panel.getPriority();
                if (priority < 0) {
                    result.addWarning(String.format("面板[%d]优先级为负数: %d", i, priority));
                }
                
                // 面板功能验证
                validatePanelFunctionality(panel, i, result);
                
            } catch (Exception e) {
                result.addError(String.format("面板[%d]验证失败: %s", i, e.getMessage()));
            }
        }
    }
    
    /**
     * 面板功能验证
     */
    private void validatePanelFunctionality(IPanel panel, int index, ValidationResult result) {
        try {
            // 测试基础方法
            panel.isVisible();
            panel.needsUpdate();
            
            // 测试渲染方法（不实际渲染）
            // panel.render(); // 这里不调用，因为可能影响UI状态
            
        } catch (Exception e) {
            result.addError(String.format("面板[%d]功能验证失败: %s", index, e.getMessage()));
        }
    }
    
    /**
     * 依赖验证
     */
    private void validateDependencies(UIConfiguration configuration, ValidationResult result) {
        // 这里可以实现面板间依赖关系验证
        // 目前面板间没有明确的依赖关系，未来可以扩展
        LOGGER.debug("[UIConfigurationValidator] 依赖验证暂未实现");
    }
    
    /**
     * 性能验证
     */
    private void validatePerformance(UIConfiguration configuration, ValidationResult result) {
        int panelCount = configuration.getPanelCount();
        
        if (panelCount > config.getMaxPanelCount()) {
            result.addWarning(String.format("面板数量过多可能影响性能: %d (建议不超过 %d)", 
                panelCount, config.getMaxPanelCount()));
        }
        
        // 检查是否有高优先级面板过多
        long highPriorityCount = configuration.getPanels().stream()
            .filter(panel -> panel.getPriority() < 50)
            .count();
        
        if (highPriorityCount > 5) {
            result.addWarning(String.format("高优先级面板过多: %d (建议不超过 5)", highPriorityCount));
        }
    }
    
    /**
     * 兼容性验证
     */
    private void validateCompatibility(UIConfiguration configuration, ValidationResult result) {
        IPanelFactory factory = configuration.getPanelFactory();
        if (factory == null) {
            result.addError("面板工厂为null，无法进行兼容性验证");
            return;
        }
        
        // 验证面板工厂是否支持所有面板类型
        for (IPanel panel : configuration.getPanels()) {
            if (!factory.supportsPanelType(panel.getClass())) {
                result.addWarning(String.format("面板工厂不支持面板类型: %s", 
                    panel.getClass().getSimpleName()));
            }
        }
    }
    
    /**
     * 面板定义验证
     */
    private void validatePanelDefinitions(PanelConfiguration configuration, ValidationResult result) {
        List<PanelConfiguration.PanelDefinition> panels = configuration.getPanels();
        
        for (int i = 0; i < panels.size(); i++) {
            PanelConfiguration.PanelDefinition panelDef = panels.get(i);
            
            if (panelDef.getType() == null || panelDef.getType().trim().isEmpty()) {
                result.addError(String.format("面板定义[%d]类型为空", i));
            }
            
            if (panelDef.getPriority() < 0) {
                result.addWarning(String.format("面板定义[%d]优先级为负数: %d", i, panelDef.getPriority()));
            }
        }
    }
    
    /**
     * 设置验证
     */
    private void validateSettings(PanelConfiguration configuration, ValidationResult result) {
        PanelConfiguration.ConfigurationSettings settings = configuration.getSettings();
        if (settings == null) {
            result.addWarning("配置设置为空，使用默认设置");
            return;
        }
        
        if (settings.getConfigVersion() == null || settings.getConfigVersion().trim().isEmpty()) {
            result.addWarning("配置版本为空");
        }
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(UIConfiguration configuration) {
        return String.format("config_%d_%s", 
            configuration.getPanelCount(),
            configuration.isUsingDependencyInjection() ? "di" : "legacy");
    }
    
    /**
     * 清除验证缓存
     */
    public void clearCache() {
        validationCache.clear();
        LOGGER.debug("[UIConfigurationValidator] 验证缓存已清除");
    }
    
    /**
     * 获取缓存大小
     */
    public int getCacheSize() {
        return validationCache.size();
    }
    
    /**
     * 验证配置类
     */
    public static class ValidationConfig {
        private boolean enableCaching = true;
        private boolean validateDependencies = true;
        private boolean validatePerformance = true;
        private boolean validateCompatibility = true;
        private int maxPanelCount = 20;
        
        // Getters and Setters
        public boolean isEnableCaching() { return enableCaching; }
        public void setEnableCaching(boolean enableCaching) { this.enableCaching = enableCaching; }
        
        public boolean isValidateDependencies() { return validateDependencies; }
        public void setValidateDependencies(boolean validateDependencies) { this.validateDependencies = validateDependencies; }
        
        public boolean isValidatePerformance() { return validatePerformance; }
        public void setValidatePerformance(boolean validatePerformance) { this.validatePerformance = validatePerformance; }
        
        public boolean isValidateCompatibility() { return validateCompatibility; }
        public void setValidateCompatibility(boolean validateCompatibility) { this.validateCompatibility = validateCompatibility; }
        
        public int getMaxPanelCount() { return maxPanelCount; }
        public void setMaxPanelCount(int maxPanelCount) { this.maxPanelCount = maxPanelCount; }
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public int getErrorCount() {
            return errors.size();
        }
        
        public int getWarningCount() {
            return warnings.size();
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            if (!errors.isEmpty()) {
                sb.append("错误:\n");
                for (String error : errors) {
                    sb.append("  - ").append(error).append("\n");
                }
            }
            if (!warnings.isEmpty()) {
                sb.append("警告:\n");
                for (String warning : warnings) {
                    sb.append("  - ").append(warning).append("\n");
                }
            }
            return sb.toString();
        }
        
        public static ValidationResult error(String message) {
            ValidationResult result = new ValidationResult();
            result.addError(message);
            return result;
        }
        
        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, errors=%d, warnings=%d}", 
                isValid(), getErrorCount(), getWarningCount());
        }
    }
}
