package com.director.core.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import com.director.core.container.persistence.ContainerPersistence;

/**
 * 容器管理器（v4.5 一体化交互系统）
 * <p>
 * 负责管理所有容器的生命周期和关系：
 * - 容器创建/删除
 * - 层级关系管理
 * - 选择状态管理
 * - 事件通知
 * - 动画应用集成
 * 
 * @author Director Team
 * @version 4.5
 */
public class ContainerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerManager.class);
    private static final AtomicReference<ContainerManager> GLOBAL = new AtomicReference<>();
    private static volatile boolean initializing = false;  // 初始化标志，防止递归
    
    // ========== 容器存储 ==========
    private final Map<UUID, Container> containers = new LinkedHashMap<>();
    private final Map<String, Container> containersByName = new HashMap<>();
    private final List<Container> rootContainers = new ArrayList<>();
    // 读写锁：读多写少，提升并发性能
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final java.util.concurrent.locks.Lock readLock = lock.readLock();
    private final java.util.concurrent.locks.Lock writeLock = lock.writeLock();
    
    // ========== 选中状态 ==========
    private Container selectedContainer = null;
    private final Set<Container> selectedContainers = new LinkedHashSet<>();
    private Container lastSelectedContainer = null;  // 用于 Shift 范围选择
    
    // ========== 激活容器（v5.0 - 用于实时接收模式） ==========
    private Container activeContainer = null;
    
    // ========== 事件监听器 ==========
    private final List<ContainerListener> listeners = new CopyOnWriteArrayList<>();
    
    public ContainerManager() {
        this(true);
    }

    private ContainerManager(boolean registerGlobal) {
        initializing = true;  // 设置初始化标志
        try {
            createDefaultContainers();
            if (registerGlobal) {
                registerGlobalInstance(this);
            }
            // 加载当前存档的容器数据
            ContainerPersistence.loadForCurrentWorld(this);
            
            LOGGER.info("[ContainerManager] Initialized");
        } finally {
            initializing = false;  // 清除初始化标志
        }
    }
    
    private static void registerGlobalInstance(ContainerManager manager) {
        if (GLOBAL.compareAndSet(null, manager)) {
            LOGGER.info("[ContainerManager] Global instance set");
        }
    }
    
    public static ContainerManager getInstance() {
        ContainerManager existing = GLOBAL.get();
        if (existing != null) {
            return existing;
        }
        // 如果正在初始化，等待初始化完成
        if (initializing) {
            // 等待一小段时间，让初始化完成
            int retries = 0;
            while (initializing && retries < 100) {
                try {
                    Thread.sleep(10);
                    retries++;
                    existing = GLOBAL.get();
                    if (existing != null) {
                        return existing;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            // 如果等待后仍然没有实例，返回null而不是创建新实例
            existing = GLOBAL.get();
            if (existing != null) {
                return existing;
            }
            LOGGER.warn("[ContainerManager] 初始化超时，返回null");
            return null;
        }
        ContainerManager created = new ContainerManager(false);
        if (GLOBAL.compareAndSet(null, created)) {
            LOGGER.info("[ContainerManager] Global fallback instance created");
            return created;
        }
        return GLOBAL.get();
    }
    
    /**
     * 检查是否正在初始化
     */
    public static boolean isInitializing() {
        return initializing;
    }
    
    public Container createContainer(String name, ContainerType type) {
        Container container = new Container(name, type);
        // 组默认隐藏，子容器跟随（由 Container.setState 级联处理）
        if (type == ContainerType.GROUP) {
            container.setState(com.director.core.container.ContainerState.HIDDEN);
        }
        addContainer(container);
        LOGGER.info("[ContainerManager] 创建容器: {} ({})", name, type);
        return container;
    }

    /**
     * 生成在当前管理器中唯一的容器名称。
     *
     * @param baseName 名称前缀，若为空则使用 "Container"
     * @return 不与现有容器冲突的名称
     */
    public String generateUniqueName(String baseName) {
        String prefix = (baseName == null || baseName.isBlank()) ? "Container" : baseName.trim();
        readLock.lock();
        try {
            String candidate = prefix;
            int index = 1;
            while (containersByName.containsKey(candidate)) {
                candidate = prefix + "_" + index++;
            }
            return candidate;
        } finally {
            readLock.unlock();
        }
    }
    
    public void addContainer(Container container) {
        if (container == null) return;
        writeLock.lock();
        try {
            containers.put(container.getId(), container);
            Container previous = containersByName.put(container.getName(), container);
            if (previous != null && previous != container) {
                LOGGER.warn("[ContainerManager] 覆盖重复名称容器: {}", container.getName());
            }
            if (container.getParent() == null) {
                rootContainers.add(container);
            }
        } finally {
            writeLock.unlock();
        }
        
        // v5.2: 不再使用HiddenBlockRegistry，可见性由ContainerVisibilityService管理
        // 如果容器是隐藏状态，Container.setState()会自动调用ContainerVisibilityService.hideContainer()
        
        fireContainerAdded(container);
        ContainerPersistence.requestAutoSave();
    }

    /**
     * 批量添加容器（一次加锁）。对于无父级的容器会暂时追加到根列表。
     */
    public void addContainers(java.util.Collection<Container> list) {
        if (list == null || list.isEmpty()) return;
        writeLock.lock();
        try {
            for (Container container : list) {
                if (container == null) continue;
                containers.put(container.getId(), container);
                Container previous = containersByName.put(container.getName(), container);
                if (previous != null && previous != container) {
                    LOGGER.warn("[ContainerManager] 覆盖重复名称容器: {}", container.getName());
                }
                if (container.getParent() == null) {
                    rootContainers.add(container);
                } else {
                    // 已有父级的，默认挂到父级末尾
                    container.getParent().addChildAt(container, container.getParent().getChildCount());
                }
            }
        } finally {
            writeLock.unlock();
        }
        for (Container c : list) {
            if (c != null) {
                fireContainerAdded(c);
            }
        }
        ContainerPersistence.requestAutoSave();
    }

    /**
     * 批量恢复整棵层级（一次加锁，不触发中间事件）。
     * 适用于 ClearAll 撤销、从快照恢复等场景。
     */
    public void restoreHierarchyBulk(java.util.Collection<Container> roots) {
        if (roots == null || roots.isEmpty()) return;
        java.util.ArrayList<Container> restored = new java.util.ArrayList<>();
        writeLock.lock();
        try {
            for (Container root : roots) {
                if (root == null) continue;
                attachHierarchyInternal(root, null, restored);
            }
        } finally {
            writeLock.unlock();
        }
        // 锁外统一触发 Added 事件（子先父后影响较小，这里按 DFS 顺序触发）
        for (Container c : restored) {
            fireContainerAdded(c);
        }
    }

    // 递归将容器树挂入当前管理器（假定已持有写锁），并收集用于事件触发
    private void attachHierarchyInternal(Container node, Container parent, java.util.List<Container> restored) {
        containers.put(node.getId(), node);
        containersByName.put(node.getName(), node);
        if (parent == null) {
            rootContainers.add(node);
        } else {
            parent.addChildAt(node, parent.getChildCount());
        }
        restored.add(node);
        for (Container child : node.getChildrenInternal()) {
            attachHierarchyInternal(child, node, restored);
        }
    }

    /**
     * 批量把若干容器恢复到指定父与索引位置（一次加锁，不触发中间事件）。
     * 适用于 Delete 批量撤销等。
     */
    public void restoreContainersAtPositions(java.util.List<Container> items,
                                             java.util.List<Container> parents,
                                             java.util.List<Integer> indices) {
        if (items == null || items.isEmpty()) return;
        if (parents == null || indices == null) return;
        if (parents.size() != items.size() || indices.size() != items.size()) return;
        writeLock.lock();
        try {
            for (int i = 0; i < items.size(); i++) {
                Container c = items.get(i);
                if (c == null) continue;
                Container parent = parents.get(i);
                int index = indices.get(i) != null ? indices.get(i) : -1;
                containers.put(c.getId(), c);
                containersByName.put(c.getName(), c);
                if (parent != null) {
                    int applied = index < 0 ? parent.getChildCount() : Math.min(index, parent.getChildCount());
                    parent.addChildAt(c, applied);
                } else {
                    int applied = index < 0 ? rootContainers.size() : Math.min(index, rootContainers.size());
                    rootContainers.add(applied, c);
                }
            }
        } finally {
            writeLock.unlock();
        }
        // 统一触发 Added/Moved 事件
        for (int i = 0; i < items.size(); i++) {
            Container c = items.get(i);
            if (c == null) continue;
            fireContainerAdded(c);
            fireContainerMoved(c, parents.get(i));
        }
    }
    
    public void removeContainer(Container container) {
        if (container == null) return;
        if (container.isLocked()) {
            LOGGER.warn("[ContainerManager] 无法删除容器 {}：容器已锁定", container.getName());
            return;
        }

        // 1) 收集要删除的所有容器（子到父的后序顺序）
        List<Container> toRemove = new ArrayList<>();
        collectAllDescendants(container, toRemove);
        toRemove.add(container);

        // 2) 在锁外处理可见性恢复（避免长时间持有锁）
        try {
            var visibilityService = com.director.core.container.visibility.ContainerServices.visibility();
            if (visibilityService != null) {
                for (Container c : toRemove) {
                    if (c.isHidden() || visibilityService.hasSnapshot(c)) {
                        try {
                            visibilityService.showContainer(c);
                        } catch (Exception visEx) {
                            LOGGER.warn("[ContainerManager] 删除容器 {} 时恢复方块失败: {}", c.getName(), visEx.getMessage());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2.5) 在锁外删除时间线上的对应片段（避免长时间持有锁）
        try {
            var timelineService = com.director.ui.timeline.container.ContainerTimelineService.getInstance();
            for (Container c : toRemove) {
                int removedCount = timelineService.removeClipsForContainer(c.getId());
                if (removedCount > 0) {
                    LOGGER.info("[ContainerManager] 容器 {} 在时间线上的 {} 个片段已自动删除", c.getName(), removedCount);
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("[ContainerManager] 删除容器时删除片段失败: {}", ex.getMessage());
        }

        // 3) 一次性进入锁内进行结构与选择状态更新
        boolean selectionChanged = false;
        writeLock.lock();
        try {
            for (Container c : toRemove) {
                // 从父容器断开或根列表移除
                Container parent = c.getParent();
                if (parent != null) {
                    parent.removeChild(c);
                } else {
                    rootContainers.remove(c);
                }
                // 从总表移除
                containers.remove(c.getId());
                containersByName.remove(c.getName());
                // 更新选择状态
                if (deselectInternal(c)) {
                    selectionChanged = true;
                }
            }
        } finally {
            writeLock.unlock();
        }

        // 4) 在锁外批量触发事件（自底向上）
        for (Container c : toRemove) {
            fireContainerRemoved(c);
            LOGGER.info("[ContainerManager] 删除容器: {}", c.getName());
        }
        if (selectionChanged) {
            fireSelectionChanged();
        }
        ContainerPersistence.requestAutoSave();
    }

    // 后序遍历收集所有子孙
    private void collectAllDescendants(Container container, List<Container> list) {
        for (Container child : container.getChildren()) {
            collectAllDescendants(child, list);
            list.add(child);
        }
    }
    
    // ========== 容器查找 ==========
    
    public Container getContainer(UUID id) {
        if (id == null) return null;
        readLock.lock();
        try {
            return containers.get(id);
        } finally {
            readLock.unlock();
        }
    }
    
    public Container getContainer(String name) {
        if (name == null) return null;
        readLock.lock();
        try {
            return containersByName.get(name);
        } finally {
            readLock.unlock();
        }
    }
    
    public List<Container> getAllContainers() {
        readLock.lock();
        try {
            return new ArrayList<>(containers.values());
        } finally {
            readLock.unlock();
        }
    }

    public List<Container> getRootContainersView() {
        readLock.lock();
        try {
            return List.copyOf(rootContainers);
        } finally {
            readLock.unlock();
        }
    }

    public int getContainerCount() {
        readLock.lock();
        try {
            return containers.size();
        } finally {
            readLock.unlock();
        }
    }
    
    // ========== 容器层级操作 ==========
    /**
     * 批量删除多个容器（一次加锁、一次事件批量触发）。会自动展开并去重子孙集合。
     */
    public void removeContainers(Collection<Container> containersToDelete) {
        if (containersToDelete == null || containersToDelete.isEmpty()) return;
        // 收集所有需要删除的容器（包含其子孙），按出现顺序去重
        java.util.LinkedHashSet<Container> all = new java.util.LinkedHashSet<>();
        for (Container c : containersToDelete) {
            if (c == null) continue;
            java.util.ArrayList<Container> tmp = new java.util.ArrayList<>();
            collectAllDescendants(c, tmp);
            all.addAll(tmp);
            all.add(c);
        }
        // 先在锁外处理可见性恢复，避免隐藏残留
        var visibilityService = com.director.core.container.visibility.ContainerServices.visibility();
        if (visibilityService != null) {
            for (Container c : all) {
                try {
                    if (c.isHidden() || visibilityService.hasSnapshot(c)) {
                        visibilityService.showContainer(c);
                    }
                } catch (Exception ignored) {}
            }
        }
        // 在锁外删除时间线上的对应片段（避免长时间持有锁）
        try {
            var timelineService = com.director.ui.timeline.container.ContainerTimelineService.getInstance();
            for (Container c : all) {
                int removedCount = timelineService.removeClipsForContainer(c.getId());
                if (removedCount > 0) {
                    LOGGER.info("[ContainerManager] 容器 {} 在时间线上的 {} 个片段已自动删除", c.getName(), removedCount);
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("[ContainerManager] 批量删除容器时删除片段失败: {}", ex.getMessage());
        }
        // 锁内批量移除
        boolean selectionChanged = false;
        writeLock.lock();
        try {
            for (Container c : all) {
                Container parent = c.getParent();
                if (parent != null) {
                    parent.removeChild(c);
                } else {
                    rootContainers.remove(c);
                }
                containers.remove(c.getId());
                containersByName.remove(c.getName());
                if (deselectInternal(c)) {
                    selectionChanged = true;
                }
            }
        } finally {
            writeLock.unlock();
        }
        // 锁外批量事件
        for (Container c : all) {
            fireContainerRemoved(c);
        }
        if (selectionChanged) {
            fireSelectionChanged();
        }
        ContainerPersistence.requestAutoSave();
    }
    
    public void moveContainerToParent(Container container, Container newParent) {
        moveContainerToParent(container, newParent, -1);
    }

    /**
     * 批量移动多个容器到同一父级（一次加锁），按提供顺序插入。
     * @param containersToMove 需要移动的容器（将自动过滤循环、去重自身父子）
     * @param newParent 目标父级（null 表示根）
     * @param startIndex 起始插入索引（<0 追加到末尾）
     */
    public void moveContainersToParent(java.util.List<Container> containersToMove, Container newParent, int startIndex) {
        if (containersToMove == null || containersToMove.isEmpty()) return;
        // 过滤非法与循环引用
        java.util.ArrayList<Container> list = new java.util.ArrayList<>();
        for (Container c : containersToMove) {
            if (c == null) continue;
            if (newParent != null && (newParent == c || newParent.isDescendantOf(c))) continue;
            list.add(c);
        }
        if (list.isEmpty()) return;
        writeLock.lock();
        try {
            int insertIndex = startIndex < 0
                ? (newParent != null ? newParent.getChildCount() : rootContainers.size())
                : startIndex;
            for (Container c : list) {
                Container oldParent = c.getParent();
                if (oldParent != null) {
                    oldParent.removeChild(c);
                } else {
                    rootContainers.remove(c);
                }
                if (newParent != null) {
                    int applied = Math.min(insertIndex, newParent.getChildCount());
                    newParent.addChildAt(c, applied);
                } else {
                    int applied = Math.min(insertIndex, rootContainers.size());
                    rootContainers.add(applied, c);
                }
                insertIndex++;
            }
        } finally {
            writeLock.unlock();
        }
        // 继承组状态（锁外应用可见性/锁定），并逐个触发 moved 事件
        for (Container c : list) {
            if (newParent != null && newParent.isGroup()) {
                try {
                    if (newParent.isLocked() != c.isLocked()) {
                        c.setLocked(newParent.isLocked());
                    }
                    if (newParent.getState() != c.getState()) {
                        c.setState(newParent.getState());
                    }
                } catch (Exception e) {
                    LOGGER.warn("[ContainerManager] 批量移动时应用组状态出错: {}", e.getMessage());
                }
            }
            fireContainerMoved(c, newParent);
        }
    }

    public void moveContainerToParent(Container container, Container newParent, int index) {
        if (container == null) return;
        if (newParent != null && (newParent == container || newParent.isDescendantOf(container))) {
            LOGGER.warn("[ContainerManager] 无法将容器 {} 移动到其子孙节点 {}", container.getName(), newParent.getName());
            return;
        }

        Container oldParent;
        writeLock.lock();
        try {
            oldParent = container.getParent();
            if (oldParent != null) {
                oldParent.removeChild(container);
            } else {
                rootContainers.remove(container);
            }

            if (newParent != null) {
                int insertIndex = index < 0 ? newParent.getChildCount() : Math.min(index, newParent.getChildCount());
                newParent.addChildAt(container, insertIndex);
            } else {
                int insertIndex = index < 0 ? rootContainers.size() : Math.min(index, rootContainers.size());
                rootContainers.add(insertIndex, container);
            }
        } finally {
            writeLock.unlock();
        }
        // 继承组状态：如果新父级是组，则子容器跟随组的锁定与可见性
        if (newParent != null && newParent.isGroup()) {
            try {
                if (newParent.isLocked() != container.isLocked()) {
                    container.setLocked(newParent.isLocked());
                }
                if (newParent.getState() != container.getState()) {
                    container.setState(newParent.getState());
                }
            } catch (Exception e) {
                LOGGER.warn("[ContainerManager] 应用组状态到子容器时出错: {}", e.getMessage());
            }
        }
        fireContainerMoved(container, newParent);
    }

    public void swapContainers(Container first, Container second) {
        if (first == null || second == null || first == second) {
            return;
        }

        Container parentA;
        Container parentB;
        int indexA;
        int indexB;
        writeLock.lock();
        try {
            parentA = first.getParent();
            parentB = second.getParent();
            List<Container> listA = parentA != null ? parentA.getChildrenInternal() : rootContainers;
            List<Container> listB = parentB != null ? parentB.getChildrenInternal() : rootContainers;
            indexA = parentA != null ? parentA.indexOfChild(first) : listA.indexOf(first);
            indexB = parentB != null ? parentB.indexOfChild(second) : listB.indexOf(second);
            if (indexA < 0 || indexB < 0) {
                return;
            }

            if (listA == listB) {
                Collections.swap(listA, indexA, indexB);
            } else {
                if (parentA != null) {
                    parentA.removeChild(first);
                } else {
                    rootContainers.remove(first);
                }
                if (parentB != null) {
                    parentB.removeChild(second);
                } else {
                    rootContainers.remove(second);
                }
                if (parentB != null) {
                    parentB.addChildAt(first, indexB);
                } else {
                    rootContainers.add(Math.min(indexB, rootContainers.size()), first);
                }
                if (parentA != null) {
                    parentA.addChildAt(second, indexA);
                } else {
                    rootContainers.add(Math.min(indexA, rootContainers.size()), second);
                }
            }
        } finally {
            writeLock.unlock();
        }

        fireContainerMoved(first, parentB);
        fireContainerMoved(second, parentA);
    }
    
    // ========== 选中管理 ==========
    
    public void selectContainer(Container container) {
        selectContainer(container, false);
    }
    
    public void selectContainer(Container container, boolean multiSelect) {
        selectContainer(container, multiSelect, false);
    }
    
    /**
     * 选择容器
     * @param container 要选择的容器
     * @param multiSelect 是否为多选模式（Ctrl）
     * @param rangeSelect 是否为范围选择模式（Shift）
     */
    public void selectContainer(Container container, boolean multiSelect, boolean rangeSelect) {
        selectContainer(container, multiSelect, rangeSelect, null);
    }

    /**
     * 选择容器（支持按「可见顺序」做 Shift 范围选择，折叠子节点不参与）
     * @param visibleOrderForRange 当 rangeSelect 且非 null 时，用此顺序替代 getAllContainersInOrder
     */
    public void selectContainer(Container container, boolean multiSelect, boolean rangeSelect,
                                List<Container> visibleOrderForRange) {
        boolean changed = false;
        writeLock.lock();
        try {
            if (rangeSelect && lastSelectedContainer != null && container != null) {
                List<Container> order = (visibleOrderForRange != null && !visibleOrderForRange.isEmpty())
                    ? visibleOrderForRange
                    : getAllContainersInOrder();
                int lastIndex = order.indexOf(lastSelectedContainer);
                int currentIndex = order.indexOf(container);
                
                if (lastIndex >= 0 && currentIndex >= 0) {
                    int start = Math.min(lastIndex, currentIndex);
                    int end = Math.max(lastIndex, currentIndex);
                    if (!multiSelect) {
                        selectedContainers.clear();
                        selectedContainer = null;
                        changed = true;
                    }
                    for (int i = start; i <= end; i++) {
                        Container c = order.get(i);
                        if (selectedContainers.add(c)) {
                            changed = true;
                        }
                    }
                } else {
                    // 如果找不到索引，回退到普通选择
                    if (!multiSelect) {
                        if (!selectedContainers.isEmpty() || selectedContainer != null) {
                            selectedContainers.clear();
                            selectedContainer = null;
                            changed = true;
                        }
                    }
                    if (selectedContainers.add(container)) {
                        changed = true;
                    }
                }
                selectedContainer = container;
                changed = true;
            } else {
                // 普通选择或多选
                if (!multiSelect && !rangeSelect) {
                    if (!selectedContainers.isEmpty() || selectedContainer != null) {
                        selectedContainers.clear();
                        selectedContainer = null;
                        changed = true;
                    }
                }

                if (container != null) {
                    if (multiSelect && selectedContainers.contains(container)) {
                        // Ctrl+Click toggle：已选中则取消选中
                        selectedContainers.remove(container);
                        if (selectedContainer == container) {
                            selectedContainer = selectedContainers.isEmpty() ? null : selectedContainers.iterator().next();
                        }
                        changed = true;
                    } else {
                        if (selectedContainers.add(container)) {
                            changed = true;
                        }
                        if (selectedContainer != container) {
                            selectedContainer = container;
                            changed = true;
                        }
                    }
                }
            }
            
            // 更新最后选中的容器（用于 Shift 范围选择）
            if (container != null) {
                lastSelectedContainer = container;
            }
        } finally {
            writeLock.unlock();
        }
        if (changed) {
            fireSelectionChanged();
        }
    }
    
    /**
     * 获取所有容器按显示顺序排列的列表（用于范围选择）
     */
    private List<Container> getAllContainersInOrder() {
        List<Container> result = new ArrayList<>();
        for (Container root : rootContainers) {
            collectContainersInOrder(root, result);
        }
        return result;
    }
    
    private void collectContainersInOrder(Container container, List<Container> result) {
        result.add(container);
        for (Container child : container.getChildren()) {
            collectContainersInOrder(child, result);
        }
    }

    public void clearSelection() {
        boolean changed;
        writeLock.lock();
        try {
            changed = clearSelectionInternal();
        } finally {
            writeLock.unlock();
        }
        if (changed) {
            fireSelectionChanged();
        }
    }
    
    public Container getSelectedContainer() {
        readLock.lock();
        try {
            return selectedContainer;
        } finally {
            readLock.unlock();
        }
    }
    
    public Set<Container> getSelectedContainers() {
        readLock.lock();
        try {
            return new LinkedHashSet<>(selectedContainers);
        } finally {
            readLock.unlock();
        }
    }

    public Set<Container> getSelectedContainersView() {
        return Collections.unmodifiableSet(getSelectedContainers());
    }
    
    public boolean isSelected(Container container) {
        readLock.lock();
        try {
            return selectedContainers.contains(container);
        } finally {
            readLock.unlock();
        }
    }
    
    // ========== 激活容器管理（v5.0） ==========
    
    /**
     * 获取当前激活的容器（用于实时接收模式）
     */
    public Container getActive() {
        readLock.lock();
        try {
            return activeContainer;
        } finally {
            readLock.unlock();
        }
    }
    
    /**
     * 设置激活容器
     * 
     * @param id 容器ID，null则取消激活
     */
    public void setActive(UUID id) {
        Container newActive = null;
        if (id != null) {
            newActive = getContainer(id);
            if (newActive == null) {
                LOGGER.warn("[ContainerManager] 无法激活容器：容器 {} 不存在", id);
                return;
            }
        }
        
        Container oldActive;
        writeLock.lock();
        try {
            oldActive = activeContainer;
            activeContainer = newActive;
        } finally {
            writeLock.unlock();
        }
        
        if (oldActive != newActive) {
            fireActiveContainerChanged(oldActive, newActive);
            LOGGER.info("[ContainerManager] 激活容器: {}", 
                newActive != null ? newActive.getName() : "无");
        }
    }
    
    /**
     * 设置激活容器
     * 
     * @param container 容器对象，null则取消激活
     */
    public void setActive(Container container) {
        setActive(container != null ? container.getId() : null);
    }

    /**
     * 检查容器是否是激活的
     */
    public boolean isActive(Container container) {
        readLock.lock();
        try {
            return activeContainer == container;
        } finally {
            readLock.unlock();
        }
    }

    public void renameContainer(Container container, String newName) {
        if (container == null || newName == null) {
            return;
        }
        String trimmed = sanitizeContainerName(newName);
        if (trimmed.isEmpty()) {
            return;
        }

        String oldName;
        String appliedName = trimmed;
        writeLock.lock();
        try {
            oldName = container.getName();
            if (trimmed.equals(oldName)) {
                return;
            }
            Container conflict = containersByName.get(trimmed);
            if (conflict != null && conflict != container) {
                appliedName = generateUniqueName(trimmed);
            }
            if (oldName != null) {
                containersByName.remove(oldName);
            }
            container.setName(appliedName);
            containersByName.put(appliedName, container);
        } finally {
            writeLock.unlock();
        }
        fireContainerRenamed(container, oldName, appliedName);
    }

    private String sanitizeContainerName(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }

        // ImGui/ImString 可能带有 '\0' 终止符或控制字符，若不清理会导致渲染时被截断。
        int nullIndex = raw.indexOf('\0');
        String effective = (nullIndex >= 0) ? raw.substring(0, nullIndex) : raw;
        return effective.replaceAll("\\p{Cntrl}", "").trim();
    }

    private boolean deselectInternal(Container container) {
        boolean changed = false;
        if (container == null) {
            return false;
        }

        if (selectedContainers.remove(container)) {
            changed = true;
        }
        if (selectedContainer == container) {
            selectedContainer = selectedContainers.isEmpty() ? null : selectedContainers.iterator().next();
            changed = true;
        }
        if (lastSelectedContainer == container) {
            lastSelectedContainer = null;
        }
        return changed;
    }

    private boolean clearSelectionInternal() {
        if (!selectedContainers.isEmpty() || selectedContainer != null) {
            selectedContainers.clear();
            selectedContainer = null;
            lastSelectedContainer = null;
            return true;
        }
        return false;
    }
    
    // ========== 容器与选区交互（v5.0） ==========
    
    /**
     * 从选区创建容器
     * 
     * @param sel 选区状态
     * @param world 世界对象（用于捕获方块快照）
     * @param hiddenDefault 是否默认隐藏
     * @return 新创建的容器
     */
    public Container createFromSelection(com.director.core.selection.SelectionState sel, 
                                        net.minecraft.world.World world, 
                                        boolean hiddenDefault) {
        if (sel == null || sel.isEmpty()) {
            LOGGER.warn("[ContainerManager] 无法从空选区创建容器");
            return null;
        }
        
        String name = generateUniqueName("选区容器");
        Container container = createContainer(name, ContainerType.GENERAL);
        
        // 设置容器的世界归属（v5.2新增）
        if (world != null) {
            container.setLevelKey(world.getRegistryKey());
        }

        // 先添加方块，再设置状态：确保可见性服务能够正确处理所有方块
        Set<net.minecraft.util.math.BlockPos> blocks = sel.getSelectedBlocks();
        container.addBlocks(blocks);
        
        // 捕获方块快照（必须在添加方块后、设置状态前完成）
        if (world != null) {
            container.getBlockStore().addAllFromWorld(blocks, world);
        }
        
        // 设置状态（若需要隐藏）：此时方块已添加，可见性服务能正确处理
        if (hiddenDefault) {
            container.setState(ContainerState.HIDDEN);
        } else {
            container.setState(ContainerState.VISIBLE);
        }
        
        LOGGER.info("[ContainerManager] 从选区创建容器: {} ({} 个方块)", 
            name, blocks.size());
        
        return container;
    }
    
    /**
     * 添加方块到容器
     * 
     * @param id 容器ID
     * @param positions 方块位置集合
     * @param world 世界对象（用于捕获方块快照）
     */
    public void addBlocks(UUID id, Collection<net.minecraft.util.math.BlockPos> positions, 
                         net.minecraft.world.World world) {
        Container container = getContainer(id);
        if (container == null) {
            LOGGER.warn("[ContainerManager] 无法添加方块：容器 {} 不存在", id);
            return;
        }
        
        if (positions == null || positions.isEmpty()) {
            return;
        }
        
        // 设置容器的世界归属（如果还没有设置）
        if (world != null && container.getLevelKey() == null) {
            container.setLevelKey(world.getRegistryKey());
        }
        
        // 添加方块
        container.addBlocks(positions);
        
        // 捕获方块快照
        if (world != null) {
            container.getBlockStore().addAllFromWorld(positions, world);
        }
        
        // 若容器当前为隐藏状态，立刻应用可见性到新加入的方块，避免需手动切换一次才生效
        if (container.isHidden()) {
            try {
                var visibilityService = com.director.core.container.visibility.ContainerServices.visibility();
                if (visibilityService != null) {
                    visibilityService.hideContainer(container);
                }
            } catch (Exception e) {
                LOGGER.warn("[ContainerManager] 隐藏新加入方块失败 (容器 {}): {}", 
                    container.getName(), e.getMessage());
            }
        }
        
        // 从选区中移除已收纳的方块（如果它们在选区中的话）
        try {
            var selectionManager = com.director.core.selection.SelectionManager.get();
            if (selectionManager != null) {
                var selectionSet = selectionManager.getSelectionSet();
                if (selectionSet != null && !selectionSet.isEmpty()) {
                    int removedCount = 0;
                    for (net.minecraft.util.math.BlockPos pos : positions) {
                        if (selectionSet.contains(pos)) {
                            selectionManager.removeFromSelection(pos);
                            removedCount++;
                        }
                    }
                    if (removedCount > 0) {
                        LOGGER.debug("[ContainerManager] 从选区中移除了 {} 个已收纳的方块", removedCount);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("[ContainerManager] 从选区移除已收纳方块失败 (容器 {}): {}", 
                container.getName(), e.getMessage());
        }
        
        LOGGER.debug("[ContainerManager] 容器 {} 添加 {} 个方块", 
            container.getName(), positions.size());
    }

    /**
     * 当前选中容器数量是否满足合并条件
     */
    public boolean canMergeSelection() {
        return selectedContainers.size() >= 2;
    }

    /**
     * 过滤掉那些其祖先也包含在集合中的容器，只保留顶层容器。
     */
    public List<Container> getTopLevelSelection(Collection<Container> containers) {
        if (containers == null || containers.isEmpty()) {
            return Collections.emptyList();
        }

        List<Container> ordered;
        if (containers instanceof List<?> list) {
            ordered = new ArrayList<>(list.size());
            for (Object element : list) {
                if (element instanceof Container container) {
                    ordered.add(container);
                }
            }
        } else {
            ordered = new ArrayList<>(containers);
        }

        if (ordered.size() <= 1) {
            return new ArrayList<>(ordered);
        }

        Set<Container> candidateSet = new HashSet<>(ordered);
        ordered.removeIf(container -> container == null || hasAncestorInSet(container, candidateSet));
        return ordered;
    }

    private boolean hasAncestorInSet(Container container, Set<Container> candidates) {
        Container current = container.getParent();
        while (current != null) {
            if (candidates.contains(current)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
    
    // ========== 工具方法 ==========
    
    public void clear() {
        List<Container> removedContainers;
        boolean selectionChanged;
        writeLock.lock();
        try {
            removedContainers = new ArrayList<>(containers.values());
            containers.clear();
            containersByName.clear();
            rootContainers.clear();
            selectionChanged = !selectedContainers.isEmpty() || selectedContainer != null;
            selectedContainers.clear();
            selectedContainer = null;
        } finally {
            writeLock.unlock();
        }

        // 在锁外删除所有容器在时间线上的片段。
        // 加载存档时 clear() 仅用于切换世界，不应改写时间线存档。
        if (!ContainerPersistence.isLoading()) {
            try {
                var timelineService = com.director.ui.timeline.container.ContainerTimelineService.getInstance();
                int totalRemoved = 0;
                for (Container c : removedContainers) {
                    int removedCount = timelineService.removeClipsForContainer(c.getId());
                    totalRemoved += removedCount;
                }
                if (totalRemoved > 0) {
                    LOGGER.info("[ContainerManager] 清空容器时，时间线上的 {} 个片段已自动删除", totalRemoved);
                }
            } catch (Exception ex) {
                LOGGER.warn("[ContainerManager] 清空容器时删除片段失败: {}", ex.getMessage());
            }
        }
        
        LOGGER.info("[ContainerManager] 清空所有容器: {}", removedContainers.size());
        for (int i = removedContainers.size() - 1; i >= 0; i--) {
            fireContainerRemoved(removedContainers.get(i));
        }
        if (selectionChanged) {
            fireSelectionChanged();
        }
    }
    
    private void createDefaultContainers() {
        createContainer("Scene Root", ContainerType.GROUP);
        createContainer("City Core", ContainerType.GENERAL);
        createContainer("Residential Zone", ContainerType.GENERAL);
        createContainer("Tree Cluster", ContainerType.GENERAL);
        
        LOGGER.info("[ContainerManager] 创建默认容器");
    }
    
    // ========== 事件系统 ==========
    
    public void addListener(ContainerListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
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

    private void fireContainerRenamed(Container container, String oldName, String newName) {
        for (ContainerListener listener : listeners) {
            listener.onContainerRenamed(container, oldName, newName);
        }
    }
    
    private void fireSelectionChanged() {
        for (ContainerListener listener : listeners) {
            listener.onSelectionChanged(getSelectedContainers());
        }
    }
    
    private void fireActiveContainerChanged(Container oldActive, Container newActive) {
        for (ContainerListener listener : listeners) {
            listener.onActiveContainerChanged(oldActive, newActive);
        }
    }
    
    // ========== 监听器接口 ==========
    
    public interface ContainerListener {
        default void onContainerAdded(Container container) {}
        default void onContainerRemoved(Container container) {}
        default void onContainerMoved(Container container, Container newParent) {}
        default void onSelectionChanged(Set<Container> selected) {}
        default void onContainerRenamed(Container container, String oldName, String newName) {}
        default void onActiveContainerChanged(Container oldActive, Container newActive) {}
    }
}
