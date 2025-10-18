package com.chronoblocks.core.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 容器管理器（v4.5 一体化交互系统）
 * 
 * 负责管理所有容器的生命周期和关系：
 * - 容器创建/删除
 * - 层级关系管理
 * - 选择状态管理
 * - 事件通知
 * - 动画应用集成
 * 
 * @author ChronoBlocks Team
 * @version 4.5
 */
public class ContainerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerManager.class);
    private static ContainerManager INSTANCE = null;
    
    // ========== 容器存储 ==========
    private final Map<UUID, Container> containers = new LinkedHashMap<>();
    private final List<Container> rootContainers = new ArrayList<>();
    
    // ========== 选中状态 ==========
    private Container selectedContainer = null;
    private final Set<Container> selectedContainers = new LinkedHashSet<>();
    
    // ========== 事件监听器 ==========
    private final List<ContainerListener> listeners = new ArrayList<>();
    
    public ContainerManager() {
        createDefaultContainers();
        
        // 注册到动画应用器
        com.chronoblocks.core.animation.AnimationApplicator.get()
            .setContainerManager(this);
        
        LOGGER.info("[ContainerManager] Initialized and registered with AnimationApplicator");
    }
    
    // ========== 单例访问（v4.5）==========
    
    /**
     * 获取全局容器管理器实例
     * @return 容器管理器实例，如果未创建则返回 null
     */
    public static ContainerManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 设置全局容器管理器实例
     * 通常在 UI 初始化时调用
     */
    public static void setInstance(ContainerManager instance) {
        INSTANCE = instance;
        if (instance != null) {
            LOGGER.info("[ContainerManager] Global instance set");
        }
    }
    
    // ========== 容器创建 ==========
    
    public Container createContainer(String name) {
        return createContainer(name, ContainerType.GROUP);
    }
    
    public Container createContainer(String name, ContainerType type) {
        Container container = new Container(name, type);
        addContainer(container);
        LOGGER.info("[ContainerManager] 创建容器: {} ({})", name, type);
        return container;
    }
    
    public void addContainer(Container container) {
        containers.put(container.getId(), container);
        if (container.getParent() == null) {
            rootContainers.add(container);
        }
        fireContainerAdded(container);
    }
    
    public void removeContainer(Container container) {
        if (container == null) return;
        
        // 递归删除所有子容器
        List<Container> children = new ArrayList<>(container.getChildren());
        for (Container child : children) {
            removeContainer(child);
        }
        
        // 从父容器中移除
        container.removeFromParent();
        
        // 从根列表中移除
        rootContainers.remove(container);
        
        // 从总容器列表中移除
        containers.remove(container.getId());
        
        // 更新选中状态
        if (selectedContainer == container) {
            selectedContainer = null;
        }
        selectedContainers.remove(container);
        
        fireContainerRemoved(container);
        LOGGER.info("[ContainerManager] 删除容器: {}", container.getName());
    }
    
    // ========== 容器查找 ==========
    
    public Container getContainer(UUID id) {
        return containers.get(id);
    }
    
    public Container getContainer(String name) {
        return containers.values().stream()
            .filter(c -> c.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    public List<Container> getAllContainers() {
        return new ArrayList<>(containers.values());
    }
    
    public List<Container> getRootContainers() {
        return new ArrayList<>(rootContainers);
    }
    
    public List<Container> getContainersByType(ContainerType type) {
        return containers.values().stream()
            .filter(c -> c.getType() == type)
            .collect(Collectors.toList());
    }
    
    public int getContainerCount() {
        return containers.size();
    }
    
    // ========== 容器层级操作 ==========
    
    public void moveContainerToParent(Container container, Container newParent) {
        if (container == null) return;
        
        // 移除旧的父子关系
        container.removeFromParent();
        rootContainers.remove(container);
        
        // 建立新的父子关系
        if (newParent != null) {
            newParent.addChild(container);
        } else {
            rootContainers.add(container);
        }
        
        fireContainerMoved(container, newParent);
    }
    
    public void moveContainerUp(Container container) {
        if (container == null) return;
        
        Container parent = container.getParent();
        List<Container> siblings = parent != null ? 
            new ArrayList<>(parent.getChildren()) : rootContainers;
        
        int index = siblings.indexOf(container);
        if (index > 0) {
            Collections.swap(siblings, index, index - 1);
            fireContainerMoved(container, parent);
        }
    }
    
    public void moveContainerDown(Container container) {
        if (container == null) return;
        
        Container parent = container.getParent();
        List<Container> siblings = parent != null ? 
            new ArrayList<>(parent.getChildren()) : rootContainers;
        
        int index = siblings.indexOf(container);
        if (index < siblings.size() - 1 && index >= 0) {
            Collections.swap(siblings, index, index + 1);
            fireContainerMoved(container, parent);
        }
    }
    
    // ========== 选中管理 ==========
    
    public void selectContainer(Container container) {
        selectContainer(container, false);
    }
    
    public void selectContainer(Container container, boolean multiSelect) {
        if (!multiSelect) {
            selectedContainers.clear();
        }
        
        if (container != null) {
            selectedContainers.add(container);
            selectedContainer = container;
        } else {
            selectedContainer = null;
        }
        
        fireSelectionChanged();
    }
    
    public void deselectContainer(Container container) {
        selectedContainers.remove(container);
        if (selectedContainer == container) {
            selectedContainer = selectedContainers.isEmpty() ? 
                null : selectedContainers.iterator().next();
        }
        fireSelectionChanged();
    }
    
    public void clearSelection() {
        selectedContainers.clear();
        selectedContainer = null;
        fireSelectionChanged();
    }
    
    public Container getSelectedContainer() {
        return selectedContainer;
    }
    
    public Set<Container> getSelectedContainers() {
        return new LinkedHashSet<>(selectedContainers);
    }
    
    public boolean isSelected(Container container) {
        return selectedContainers.contains(container);
    }
    
    // ========== 批量操作 ==========
    
    public void deleteSelectedContainers() {
        List<Container> toDelete = new ArrayList<>(selectedContainers);
        for (Container container : toDelete) {
            removeContainer(container);
        }
    }
    
    public List<Container> duplicateSelectedContainers() {
        List<Container> duplicates = new ArrayList<>();
        for (Container container : selectedContainers) {
            Container duplicate = container.clone();
            addContainer(duplicate);
            duplicates.add(duplicate);
        }
        return duplicates;
    }
    
    // ========== 工具方法 ==========
    
    public void clear() {
        containers.clear();
        rootContainers.clear();
        selectedContainers.clear();
        selectedContainer = null;
        LOGGER.info("[ContainerManager] 清空所有容器");
    }
    
    private void createDefaultContainers() {
        Container cityCore = createContainer("City Core", ContainerType.BUILDING);
        Container residential = createContainer("Residential Zone", ContainerType.REGION);
        Container trees = createContainer("Tree Cluster", ContainerType.GROUP);
        
        LOGGER.info("[ContainerManager] 创建默认容器");
    }
    
    // ========== 事件系统 ==========
    
    public void addListener(ContainerListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(ContainerListener listener) {
        listeners.remove(listener);
    }
    
    private void fireContainerAdded(Container container) {
        for (ContainerListener listener : listeners) {
            listener.onContainerAdded(container);
        }
    }
    
    private void fireContainerRemoved(Container container) {
        for (ContainerListener listener : listeners) {
            listener.onContainerRemoved(container);
        }
    }
    
    private void fireContainerMoved(Container container, Container newParent) {
        for (ContainerListener listener : listeners) {
            listener.onContainerMoved(container, newParent);
        }
    }
    
    private void fireSelectionChanged() {
        for (ContainerListener listener : listeners) {
            listener.onSelectionChanged(getSelectedContainers());
        }
    }
    
    // ========== 监听器接口 ==========
    
    public interface ContainerListener {
        default void onContainerAdded(Container container) {}
        default void onContainerRemoved(Container container) {}
        default void onContainerMoved(Container container, Container newParent) {}
        default void onSelectionChanged(Set<Container> selected) {}
    }
}
