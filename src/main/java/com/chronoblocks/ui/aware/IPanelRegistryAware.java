package com.chronoblocks.ui.aware;

import com.chronoblocks.ui.di.IPanelRegistry;

/**
 * 面板注册表感知接口
 * <p>
 * 实现此接口的面板将自动获得 IPanelRegistry 的引用。
 * 这允许面板在运行时访问其他面板，实现面板间的通信和协作。
 * 
 * <h3>使用场景：</h3>
 * <ul>
 *   <li><strong>面板间通信</strong> - 一个面板需要通知或控制另一个面板</li>
 *   <li><strong>状态同步</strong> - 多个面板需要共享状态信息</li>
 *   <li><strong>事件传播</strong> - 面板需要向其他面板广播事件</li>
 *   <li><strong>依赖查找</strong> - 面板需要获取特定类型的其他面板实例</li>
 * </ul>
 * 
 * <h3>实现示例：</h3>
 * <pre>{@code
 * public class MenuBarPanel implements IPanel, IPanelRegistryAware {
 *     private IPanelRegistry panelRegistry;
 *     
 *     @Override
 *     public void setPanelRegistry(IPanelRegistry panelRegistry) {
 *         this.panelRegistry = panelRegistry;
 *     }
 *     
 *     public void notifyOtherPanels() {
 *         // 通过面板注册表访问其他面板
 *         ToolbarPanel toolbar = panelRegistry.getPanel(ToolbarPanel.class);
 *         if (toolbar != null) {
 *             toolbar.updateState();
 *         }
 *     }
 * }
 * }</pre>
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 * @since 1.0
 */
public interface IPanelRegistryAware {
    
    /**
     * 设置面板注册表引用
     * <p>
     * 此方法由 ChronoBlocksUIManager 在面板初始化时自动调用，
     * 为面板提供访问其他面板的能力。
     * 
     * @param panelRegistry 面板注册表实例，不能为null
     * @throws IllegalArgumentException 如果panelRegistry为null
     */
    void setPanelRegistry(IPanelRegistry panelRegistry);
}
