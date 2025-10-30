package com.chronoblocks.core.selection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🔍 SelectionToolDebugger - 选择工具系统调试器
 * 
 * 用于调试和验证选择工具系统的状态
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class SelectionToolDebugger {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionToolDebugger.class);
    
    /**
     * 打印选择工具系统状态
     */
    public static void printSystemStatus() {
        LOGGER.info("=== ChronoBlocks 选择工具系统状态 ===");
        
        try {
            SelectionManager manager = SelectionManager.get();
            LOGGER.info("选择管理器: {}", manager != null ? "已初始化" : "未初始化");
            
            if (manager != null) {
                LOGGER.info("当前工具: {}", manager.getCurrentToolDisplayName());
                LOGGER.info("是否有工具: {}", manager.hasCurrentTool());
                LOGGER.info("注册的工具数量: {}", manager.getRegistry().size());
                
                // 打印所有注册的工具
                for (SelectionTool tool : manager.getRegistry().getAllTools()) {
                    LOGGER.info("  - {} ({})", tool.getDisplayName(), tool.getId());
                }
                
                SelectionState selection = manager.getCurrentSelection();
                LOGGER.info("当前选择: {} 个方块", selection.size());
            }
            
            SelectionToolIntegration integration = SelectionToolIntegration.get();
            LOGGER.info("集成状态: {}", integration.isEnabled() ? "已启用" : "已禁用");
            
        } catch (Exception e) {
            LOGGER.error("获取系统状态失败", e);
        }
        
        LOGGER.info("=== 状态检查完成 ===");
    }
    
    /**
     * 测试工具注册
     */
    public static void testToolRegistration() {
        LOGGER.info("=== 测试工具注册 ===");
        
        try {
            SelectionManager manager = SelectionManager.get();
            SelectionRegistry registry = manager.getRegistry();
            
            LOGGER.info("注册的工具:");
            for (SelectionTool tool : registry.getAllTools()) {
                LOGGER.info("  - {}: {} ({})", tool.getId(), tool.getDisplayName(), tool.getDescription());
            }
            
            // 测试工具查找
            SelectionTool clickTool = registry.getTool("click");
            LOGGER.info("点击工具查找: {}", clickTool != null ? "成功" : "失败");
            
            SelectionTool boxTool = registry.getTool("box");
            LOGGER.info("框选工具查找: {}", boxTool != null ? "成功" : "失败");
            
            SelectionTool wandTool = registry.getTool("wand");
            LOGGER.info("魔棒工具查找: {}", wandTool != null ? "成功" : "失败");
            
        } catch (Exception e) {
            LOGGER.error("测试工具注册失败", e);
        }
        
        LOGGER.info("=== 工具注册测试完成 ===");
    }
    
    /**
     * 测试工具切换
     */
    public static void testToolSwitching() {
        LOGGER.info("=== 测试工具切换 ===");
        
        try {
            SelectionManager manager = SelectionManager.get();
            
            // 测试切换到点击工具
            manager.setCurrentTool("click", null);
            LOGGER.info("切换到点击工具: {}", manager.getCurrentToolDisplayName());
            
            // 测试切换到框选工具
            manager.setCurrentTool("box", null);
            LOGGER.info("切换到框选工具: {}", manager.getCurrentToolDisplayName());
            
            // 测试切换到魔棒工具
            manager.setCurrentTool("wand", null);
            LOGGER.info("切换到魔棒工具: {}", manager.getCurrentToolDisplayName());
            
        } catch (Exception e) {
            LOGGER.error("测试工具切换失败", e);
        }
        
        LOGGER.info("=== 工具切换测试完成 ===");
    }
}
