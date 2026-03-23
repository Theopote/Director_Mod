package com.director.ui.di;

import com.director.ui.panels.IPanel;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * PanelValidator - 面板验证工具类
 * <p>
 * 提供面板配置的验证功能，确保面板配置的正确性和一致性。
 * 包括面板名称唯一性、优先级冲突、必需面板检查等验证。
 * 
 * @author Director Team
 * @version 1.0
 */
public class PanelValidator {
    
    /**
     * 验证面板配置
     * @param panels 面板列表
     * @return 验证结果
     */
    public static ValidationResult validatePanels(List<IPanel> panels) {
        ValidationResult result = new ValidationResult();
        
        if (panels == null) {
            result.addError("面板列表不能为空");
            return result;
        }
        
        if (panels.isEmpty()) {
            result.addError("面板列表不能为空");
            return result;
        }
        
        // 检查面板名称唯一性
        validatePanelNameUniqueness(panels, result);
        
        // 检查面板优先级冲突
        validatePanelPriorityConflicts(panels, result);
        
        // 检查面板实现完整性
        validatePanelImplementation(panels, result);
        
        // 检查必需面板
        validateRequiredPanels(panels, result);
        
        return result;
    }
    
    /**
     * 验证面板名称唯一性
     */
    private static void validatePanelNameUniqueness(List<IPanel> panels, ValidationResult result) {
        Map<String, Integer> nameCount = new HashMap<>();
        
        for (IPanel panel : panels) {
            String name = panel.getPanelName();
            if (name == null || name.trim().isEmpty()) {
                result.addError("发现空的面板名称");
                continue;
            }
            
            nameCount.put(name, nameCount.getOrDefault(name, 0) + 1);
        }
        
        for (Map.Entry<String, Integer> entry : nameCount.entrySet()) {
            if (entry.getValue() > 1) {
                result.addError(String.format("面板名称重复: %s (出现 %d 次)", 
                        entry.getKey(), entry.getValue()));
            }
        }
    }
    
    /**
     * 验证面板优先级冲突
     */
    private static void validatePanelPriorityConflicts(List<IPanel> panels, ValidationResult result) {
        Map<Integer, List<String>> priorityMap = new HashMap<>();
        
        for (IPanel panel : panels) {
            int priority = panel.getPriority();
            priorityMap.computeIfAbsent(priority, k -> new ArrayList<>()).add(panel.getPanelName());
        }
        
        for (Map.Entry<Integer, List<String>> entry : priorityMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                result.addWarning(String.format("面板优先级冲突: 优先级 %d 被 %s 共享", 
                        entry.getKey(), String.join(", ", entry.getValue())));
            }
        }
    }
    
    /**
     * 验证面板实现完整性
     */
    private static void validatePanelImplementation(List<IPanel> panels, ValidationResult result) {
        for (IPanel panel : panels) {
            try {
                // 检查基本方法是否正常
                String name = panel.getPanelName();
                if (name == null || name.trim().isEmpty()) {
                    result.addError("面板名称不能为空");
                }
                
                int priority = panel.getPriority();
                if (priority < 0) {
                    result.addWarning(String.format("面板 %s 的优先级为负数: %d", name, priority));
                }
                
                // 检查可见性状态（用于未来扩展）
                panel.isVisible();
                // 这里可以添加更多检查
                
            } catch (Exception e) {
                result.addError(String.format("面板 %s 实现检查失败: %s", 
                        panel.getPanelName(), e.getMessage()));
            }
        }
    }
    
    /**
     * 验证必需面板
     */
    private static void validateRequiredPanels(List<IPanel> panels, ValidationResult result) {
        // 检查是否有菜单栏面板
        boolean hasMenuBar = panels.stream()
                .anyMatch(panel -> panel.getPanelName().equals("MenuBar"));
        
        if (!hasMenuBar) {
            result.addWarning("未找到菜单栏面板，某些功能可能不可用");
        } else {
            result.addInfo("菜单栏面板检查通过");
        }
        
        // 检查是否有工具栏面板
        boolean hasToolbar = panels.stream()
                .anyMatch(panel -> panel.getPanelName().equals("Toolbar"));
        
        if (!hasToolbar) {
            result.addWarning("未找到工具栏面板，某些功能可能不可用");
        } else {
            result.addInfo("工具栏面板检查通过");
        }
        
        // 调试信息：列出所有面板名称
        StringBuilder panelNames = new StringBuilder("当前面板: ");
        for (IPanel panel : panels) {
            panelNames.append(panel.getPanelName()).append(", ");
        }
        result.addInfo(panelNames.toString());
    }
    
    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<String> infos = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
        }
        
        public void addInfo(String info) {
            infos.add(info);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
        
        public List<String> getInfos() {
            return new ArrayList<>(infos);
        }
        
        public String getSummary() {
            StringBuilder summary = new StringBuilder();
            
            if (isValid()) {
                summary.append("面板配置验证通过");
            } else {
                summary.append("面板配置验证失败");
            }
            
            if (hasWarnings()) {
                summary.append(" (有警告)");
            }
            
            summary.append("\n");
            
            if (!errors.isEmpty()) {
                summary.append("错误:\n");
                for (String error : errors) {
                    summary.append("  - ").append(error).append("\n");
                }
            }
            
            if (!warnings.isEmpty()) {
                summary.append("警告:\n");
                for (String warning : warnings) {
                    summary.append("  - ").append(warning).append("\n");
                }
            }
            
            if (!infos.isEmpty()) {
                summary.append("信息:\n");
                for (String info : infos) {
                    summary.append("  - ").append(info).append("\n");
                }
            }
            
            return summary.toString();
        }
        
        @Override
        public String toString() {
            return getSummary();
        }
    }
}
