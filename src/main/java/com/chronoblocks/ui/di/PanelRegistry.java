package com.chronoblocks.ui.di;

import com.chronoblocks.ui.panels.IPanel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PanelRegistry - 面板注册表实现
 * <p>
 * 基于ConcurrentHashMap的线程安全面板注册表，提供类型安全的面板注册和查找机制。
 * 支持依赖注入模式，通过面板类型直接查找实例，避免手动类型转换。
 * 
 * <h3>设计特点：</h3>
 * <ul>
 *   <li><strong>线程安全</strong> - 使用ConcurrentHashMap确保并发安全</li>
 *   <li><strong>类型安全</strong> - 泛型设计避免类型转换错误</li>
 *   <li><strong>性能优化</strong> - O(1)查找性能，支持高频访问</li>
 *   <li><strong>内存高效</strong> - 基于引用的存储，无额外开销</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 * @since 2.0
 */
public class PanelRegistry implements IPanelRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PanelRegistry.class);
    
    // 面板类型到实例的映射
    private final Map<Class<? extends IPanel>, IPanel> panelMap;
    
    /**
     * 创建新的面板注册表
     */
    public PanelRegistry() {
        this.panelMap = new ConcurrentHashMap<>();
        LOGGER.debug("[PanelRegistry] 创建新的面板注册表");
    }
    
    /**
     * 从现有面板列表创建注册表
     * @param panels 面板列表
     */
    public PanelRegistry(List<IPanel> panels) {
        this();
        if (panels != null) {
            for (IPanel panel : panels) {
                if (panel != null) {
                    registerPanelByInstance(panel);
                }
            }
            LOGGER.info("[PanelRegistry] 从面板列表创建注册表，注册了 {} 个面板", panelMap.size());
        }
    }
    
    @Override
    public <T extends IPanel> void registerPanel(Class<T> panelType, T panel) {
        if (panelType == null) {
            throw new IllegalArgumentException("面板类型不能为null");
        }
        if (panel == null) {
            throw new IllegalArgumentException("面板实例不能为null");
        }
        
        IPanel existing = panelMap.put(panelType, panel);
        if (existing != null) {
            LOGGER.warn("[PanelRegistry] 覆盖已存在的面板: {} -> {}", 
                panelType.getSimpleName(), panel.getPanelName());
        } else {
            LOGGER.debug("[PanelRegistry] 注册面板: {} -> {}", 
                panelType.getSimpleName(), panel.getPanelName());
        }
    }
    
    @Override
    public <T extends IPanel> T getPanel(Class<T> panelType) {
        if (panelType == null) {
            return null;
        }
        
        IPanel panel = panelMap.get(panelType);
        if (panel != null) {
            try {
                return panelType.cast(panel);
            } catch (ClassCastException e) {
                LOGGER.error("[PanelRegistry] 面板类型转换失败: {} -> {}", 
                    panelType.getSimpleName(), panel.getClass().getSimpleName(), e);
                return null;
            }
        }
        
        return null;
    }
    
    @Override
    public <T extends IPanel> Optional<T> getPanelOptional(Class<T> panelType) {
        return Optional.ofNullable(getPanel(panelType));
    }
    
    @Override
    public <T extends IPanel> T getRequiredPanel(Class<T> panelType) {
        T panel = getPanel(panelType);
        if (panel == null) {
            throw new IllegalStateException("未找到必需的面板: " + panelType.getSimpleName());
        }
        return panel;
    }
    
    /**
     * 通过面板名称获取面板实例
     * @param panelName 面板名称
     * @return 面板实例，如果未找到则返回null
     */
    public IPanel getPanelByName(String panelName) {
        if (panelName == null || panelName.trim().isEmpty()) {
            return null;
        }
        
        for (IPanel panel : panelMap.values()) {
            if (panelName.equals(panel.getPanelName())) {
                return panel;
            }
        }
        
        return null;
    }
    
    @Override
    public boolean hasPanel(Class<? extends IPanel> panelType) {
        return panelType != null && panelMap.containsKey(panelType);
    }
    
    @Override
    public List<IPanel> getAllPanels() {
        return new ArrayList<>(panelMap.values());
    }
    
    @Override
    public List<Class<? extends IPanel>> getRegisteredPanelTypes() {
        return new ArrayList<>(panelMap.keySet());
    }
    
    @Override
    public int size() {
        return panelMap.size();
    }
    
    @Override
    public boolean isEmpty() {
        return panelMap.isEmpty();
    }
    
    @Override
    public void clear() {
        int size = panelMap.size();
        panelMap.clear();
        LOGGER.info("[PanelRegistry] 清除注册表，移除了 {} 个面板", size);
    }
    
    @Override
    public <T extends IPanel> T removePanel(Class<T> panelType) {
        if (panelType == null) {
            return null;
        }
        
        IPanel removed = panelMap.remove(panelType);
        if (removed != null) {
            LOGGER.debug("[PanelRegistry] 移除面板: {} -> {}", 
                panelType.getSimpleName(), removed.getPanelName());
            try {
                return panelType.cast(removed);
            } catch (ClassCastException e) {
                LOGGER.error("[PanelRegistry] 移除面板时类型转换失败: {} -> {}", 
                    panelType.getSimpleName(), removed.getClass().getSimpleName(), e);
                return null;
            }
        }
        
        return null;
    }
    
    @Override
    public boolean addPanel(IPanel panel) {
        if (panel == null) {
            LOGGER.warn("[PanelRegistry] 尝试添加null面板，忽略操作");
            return false;
        }
        
        registerPanelByInstance(panel);
        LOGGER.info("[PanelRegistry] 动态添加面板: {} -> {}", 
            panel.getClass().getSimpleName(), panel.getPanelName());
        return true;
    }
    
    @Override
    public boolean removePanel(IPanel panel) {
        if (panel == null) {
            LOGGER.warn("[PanelRegistry] 尝试移除null面板，忽略操作");
            return false;
        }
        
        Class<? extends IPanel> panelType = panel.getClass();
        IPanel removed = panelMap.remove(panelType);
        
        if (removed != null) {
            LOGGER.info("[PanelRegistry] 动态移除面板: {} -> {}", 
                panelType.getSimpleName(), panel.getPanelName());
            return true;
        }
        
        LOGGER.warn("[PanelRegistry] 尝试移除未注册的面板: {}", panel.getPanelName());
        return false;
    }
    
    /**
     * 通过面板实例自动注册（根据实例的实际类型）
     * @param panel 面板实例
     */
    public void registerPanelByInstance(IPanel panel) {
        // 获取面板的实际类型（包括继承层次）
        Class<? extends IPanel> panelType = panel.getClass();
        
        // 直接使用Map存储，避免泛型类型问题
        IPanel existing = panelMap.put(panelType, panel);
        if (existing != null) {
            LOGGER.warn("[PanelRegistry] 覆盖已存在的面板: {} -> {}", 
                panelType.getSimpleName(), panel.getPanelName());
        } else {
            LOGGER.debug("[PanelRegistry] 注册面板: {} -> {}", 
                panelType.getSimpleName(), panel.getPanelName());
        }
        
        // 注释掉接口类型注册，避免重复注册导致的问题
        // registerInterfaceTypes(panel);
    }
    
    /**
     * 注册面板的所有接口类型，支持多态查找
     * @param panel 面板实例
     */
    @SuppressWarnings("unchecked")
    private void registerInterfaceTypes(IPanel panel) {
        Class<?> currentClass = panel.getClass();
        
        // 遍历继承层次，注册所有IPanel的子接口
        while (currentClass != null && IPanel.class.isAssignableFrom(currentClass)) {
            Class<?>[] interfaces = currentClass.getInterfaces();
            for (Class<?> interfaceClass : interfaces) {
                if (IPanel.class.isAssignableFrom(interfaceClass)) {
                    Class<? extends IPanel> interfaceType = (Class<? extends IPanel>) interfaceClass;
                    if (!panelMap.containsKey(interfaceType)) {
                        panelMap.put(interfaceType, panel);
                        LOGGER.debug("[PanelRegistry] 注册接口类型: {} -> {}", 
                            interfaceType.getSimpleName(), panel.getPanelName());
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }
    
    /**
     * 获取注册表的调试信息
     * @return 注册表状态字符串
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("PanelRegistry{size=").append(size()).append(", panels=[");
        
        List<String> panelInfos = panelMap.entrySet().stream()
            .map(entry -> entry.getKey().getSimpleName() + "->" + entry.getValue().getPanelName())
            .collect(Collectors.toList());
        
        sb.append(String.join(", ", panelInfos));
        sb.append("]}");
        
        return sb.toString();
    }
}
