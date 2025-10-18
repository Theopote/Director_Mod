package com.chronoblocks.ui.di;

import com.chronoblocks.ui.panels.IPanel;

import java.util.List;
import java.util.Optional;

/**
 * IPanelRegistry - 面板注册表接口
 * <p>
 * 提供类型安全的面板注册和查找机制，支持依赖注入模式。
 * 通过面板类型直接查找实例，避免手动类型转换和查找逻辑。
 * 
 * <h3>核心功能：</h3>
 * <ul>
 *   <li><strong>类型安全注册</strong> - 基于面板类型进行注册和查找</li>
 *   <li><strong>依赖注入支持</strong> - 支持自动依赖解析和注入</li>
 *   <li><strong>生命周期管理</strong> - 统一管理面板的创建、初始化和销毁</li>
 *   <li><strong>线程安全</strong> - 支持多线程环境下的安全访问</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 * @since 2.0
 */
public interface IPanelRegistry {
    
    /**
     * 注册面板实例
     * @param panelType 面板类型
     * @param panel 面板实例
     * @param <T> 面板类型参数
     * @throws IllegalArgumentException 如果面板类型或实例为null
     */
    <T extends IPanel> void registerPanel(Class<T> panelType, T panel);
    
    /**
     * 获取指定类型的面板实例
     * @param panelType 面板类型
     * @param <T> 面板类型参数
     * @return 面板实例，如果未找到则返回null
     */
    <T extends IPanel> T getPanel(Class<T> panelType);
    
    /**
     * 获取指定类型的面板实例，返回Optional包装
     * @param panelType 面板类型
     * @param <T> 面板类型参数
     * @return Optional包装的面板实例
     */
    <T extends IPanel> Optional<T> getPanelOptional(Class<T> panelType);
    
    /**
     * 获取指定类型的面板实例，如果不存在则抛出异常
     * @param panelType 面板类型
     * @param <T> 面板类型参数
     * @return 面板实例
     * @throws IllegalStateException 如果面板未注册
     */
    <T extends IPanel> T getRequiredPanel(Class<T> panelType);
    
    /**
     * 检查是否已注册指定类型的面板
     * @param panelType 面板类型
     * @return true如果已注册，false否则
     */
    boolean hasPanel(Class<? extends IPanel> panelType);
    
    /**
     * 获取所有已注册的面板
     * @return 面板实例列表
     */
    List<IPanel> getAllPanels();
    
    /**
     * 获取所有已注册的面板类型
     * @return 面板类型列表
     */
    List<Class<? extends IPanel>> getRegisteredPanelTypes();
    
    /**
     * 获取面板注册表的大小
     * @return 已注册面板的数量
     */
    int size();
    
    /**
     * 检查注册表是否为空
     * @return true如果为空，false否则
     */
    boolean isEmpty();
    
    /**
     * 清除所有面板注册
     */
    void clear();
    
    /**
     * 移除指定类型的面板
     * @param panelType 面板类型
     * @param <T> 面板类型参数
     * @return 被移除的面板实例，如果未找到则返回null
     */
    <T extends IPanel> T removePanel(Class<T> panelType);
    
    /**
     * 添加面板实例（动态面板管理）
     * @param panel 面板实例
     * @return true如果添加成功，false如果面板为null
     */
    boolean addPanel(IPanel panel);
    
    /**
     * 移除面板实例（动态面板管理）
     * @param panel 面板实例
     * @return true如果移除成功，false如果面板为null或未找到
     */
    boolean removePanel(IPanel panel);
    
    /**
     * 获取注册表的调试信息
     * @return 注册表状态字符串
     */
    String getDebugInfo();
}
