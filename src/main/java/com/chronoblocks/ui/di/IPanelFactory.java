package com.chronoblocks.ui.di;

import com.chronoblocks.ui.panels.IPanel;
import net.minecraft.client.MinecraftClient;

/**
 * IPanelFactory - 面板工厂接口
 * <p>
 * 定义面板创建的统一接口，支持依赖注入和动态配置。
 * 通过工厂模式实现面板的灵活创建和配置管理。
 * 
 * <h3>核心功能：</h3>
 * <ul>
 *   <li><strong>动态创建</strong> - 根据配置动态创建面板实例</li>
 *   <li><strong>依赖注入</strong> - 支持MinecraftClient等依赖注入</li>
 *   <li><strong>配置驱动</strong> - 基于配置信息创建面板</li>
 *   <li><strong>类型安全</strong> - 泛型设计确保类型安全</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 2.1
 */
public interface IPanelFactory {
    
    /**
     * 创建指定类型的面板实例
     * @param panelType 面板类型
     * @param minecraftClient Minecraft客户端实例
     * @param <T> 面板类型参数
     * @return 面板实例
     * @throws IllegalArgumentException 如果面板类型不支持或参数无效
     */
    <T extends IPanel> T createPanel(Class<T> panelType, MinecraftClient minecraftClient);
    
    /**
     * 创建指定类型的面板实例（使用默认MinecraftClient）
     * @param panelType 面板类型
     * @param <T> 面板类型参数
     * @return 面板实例
     * @throws IllegalArgumentException 如果面板类型不支持
     */
    <T extends IPanel> T createPanel(Class<T> panelType);
    
    /**
     * 检查是否支持创建指定类型的面板
     * @param panelType 面板类型
     * @return true如果支持，false否则
     */
    boolean supportsPanelType(Class<? extends IPanel> panelType);
    
    /**
     * 获取所有支持的面板类型
     * @return 支持的面板类型列表
     */
    Class<? extends IPanel>[] getSupportedPanelTypes();
    
    /**
     * 注册自定义面板创建器
     * @param panelType 面板类型
     * @param creator 面板创建器
     * @param <T> 面板类型参数
     */
    <T extends IPanel> void registerPanelCreator(Class<T> panelType, PanelCreator<T> creator);
    
    /**
     * 面板创建器函数式接口
     * @param <T> 面板类型
     */
    @FunctionalInterface
    interface PanelCreator<T extends IPanel> {
        T create(MinecraftClient minecraftClient);
    }
}
