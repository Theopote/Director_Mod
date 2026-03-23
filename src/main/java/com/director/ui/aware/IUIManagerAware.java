package com.director.ui.aware;

import com.director.ui.DirectorUIManager;

/**
 * UI管理器感知接口
 * <p>
 * 实现此接口的面板将自动获得 DirectorUIManager 的引用。
 * 这允许面板访问UI管理器的核心功能，如布局管理、状态控制等。
 * 
 * <h3>使用场景：</h3>
 * <ul>
 *   <li><strong>布局控制</strong> - 面板需要动态调整布局或保存/恢复布局</li>
 *   <li><strong>状态管理</strong> - 面板需要访问全局UI状态</li>
 *   <li><strong>生命周期控制</strong> - 面板需要控制其他面板的显示/隐藏</li>
 *   <li><strong>事件广播</strong> - 面板需要向整个UI系统广播事件</li>
 * </ul>
 * 
 * <h3>实现示例：</h3>
 * <pre>{@code
 * public class MenuBarPanel implements IPanel, IUIManagerAware {
 *     private DirectorUIManager uiManager;
 *     
 *     @Override
 *     public void setUIManager(DirectorUIManager uiManager) {
 *         this.uiManager = uiManager;
 *     }
 *     
 *     public void saveLayout() {
 *         if (uiManager != null) {
 *             uiManager.saveCurrentLayout();
 *         }
 *     }
 *     
 *     public void resetLayout() {
 *         if (uiManager != null) {
 *             uiManager.resetToDefaultLayout();
 *         }
 *     }
 * }
 * }</pre>
 * 
 * @author Director Team
 * @version 1.0
 * @since 1.0
 */
public interface IUIManagerAware {
    
    /**
     * 设置UI管理器引用
     * <p>
     * 此方法由 DirectorUIManager 在面板初始化时自动调用，
     * 为面板提供访问UI管理器核心功能的能力。
     * 
     * @param uiManager UI管理器实例，不能为null
     * @throws IllegalArgumentException 如果uiManager为null
     */
    void setUIManager(DirectorUIManager uiManager);
}
