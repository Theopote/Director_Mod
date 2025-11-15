package com.chronoblocks.core.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
 * @author ChronoBlocks Team
 * @version 4.5
 */
public class ContainerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerManager.class);
    private static final AtomicReference<ContainerManager> GLOBAL = new AtomicReference<>();
    
    // ========== 容器存储 ==========
    private final Map<UUID, Container> containers = new LinkedHashMap<>();
    private final Map<String, Container> containersByName = new HashMap<>();
    private final List<Container> rootContainers = new ArrayList<>();
    private final Object containerLock = new Object();
    
    // ========== 选中状态 ==========
    private Container selectedContainer = null;
    private final Set<Container> selectedContainers = new LinkedHashSet<>();
    
    // ========== 激活容器（v5.0 - 用于实时接收模式） ==========
    private Container activeContainer = null;
    
    // ========== 事件监听器 ==========
    private final List<ContainerListener> listeners = new CopyOnWriteArrayList<>();
    
    public ContainerManager() {
        this(true);
    }

    private ContainerManager(boolean registerGlobal) {
        createDefaultContainers();
        if (registerGlobal) {
            registerGlobalInstance(this);
        }
        
        LOGGER.info("[ContainerManager] Initialized");
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
        ContainerManager created = new ContainerManager(false);
        if (GLOBAL.compareAndSet(null, created)) {
            LOGGER.info("[ContainerManager] Global fallback instance created");
            return created;
        }
        return GLOBAL.get();
    }
    
    // ========== 容器创建 ==========
    
    public Container createContainer(String name) {
        return createContainer(name, ContainerType.CUSTOM);
    }
    
    public Container createContainer(String name, ContainerType type) {
        Container container = new Container(name, type);
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
        synchronized (containerLock) {
            String candidate = prefix;
            int index = 1;
            while (containersByName.containsKey(candidate)) {
                candidate = prefix + "_" + index++;
            }
            return candidate;
        }
    }
    
    public void addContainer(Container container) {
        if (container == null) return;
        synchronized (containerLock) {
            containers.put(container.getId(), container);
            Container previous = containersByName.put(container.getName(), container);
            if (previous != null && previous != container) {
                LOGGER.warn("[ContainerManager] 覆盖重复名称容器: {}", container.getName());
            }
            if (container.getParent() == null) {
                rootContainers.add(container);
            }
        }
        
        // v5.2: 不再使用HiddenBlockRegistry，可见性由ContainerVisibilityService管理
        // 如果容器是隐藏状态，Container.setState()会自动调用ContainerVisibilityService.hideContainer()
        
        fireContainerAdded(container);
    }
    
    public void removeContainer(Container container) {
        if (container == null) return;
        
        // 递归删除所有子容器
        List<Container> children = new ArrayList<>(container.getChildren());
        for (Container child : children) {
            removeContainer(child);
        }
        
        // v5.2: 如果容器是隐藏状态，先恢复方块再删除
        if (container.isHidden()) {
            try {
                var visibilityService = com.chronoblocks.core.container.visibility.ContainerServices.visibility();
                if (visibilityService != null) {
                    visibilityService.showContainer(container);
                }
            } catch (Exception e) {
                LOGGER.warn("[ContainerManager] 删除容器 {} 时恢复方块失败: {}", 
                    container.getName(), e.getMessage());
            }
        }
        
        boolean selectionChanged;
        synchronized (containerLock) {
            // 从父容器中移除
            container.removeFromParent();
            
            // 从根列表中移除
            rootContainers.remove(container);
            
            // 从总容器列表中移除
            containers.remove(container.getId());
            containersByName.remove(container.getName());
            
            selectionChanged = deselectInternal(container);
        }
        fireContainerRemoved(container);
        LOGGER.info("[ContainerManager] 删除容器: {}", container.getName());

        if (selectionChanged) {
            fireSelectionChanged();
        }
    }
    
    // ========== 容器查找 ==========
    
    public Container getContainer(UUID id) {
        if (id == null) return null;
        synchronized (containerLock) {
            return containers.get(id);
        }
    }
    
    public Container getContainer(String name) {
        if (name == null) return null;
        synchronized (containerLock) {
            return containersByName.get(name);
        }
    }
    
    public List<Container> getAllContainers() {
        synchronized (containerLock) {
            return new ArrayList<>(containers.values());
        }
    }
    
    public List<Container> getRootContainers() {
        synchronized (containerLock) {
            return new ArrayList<>(rootContainers);
        }
    }

    public List<Container> getRootContainersView() {
        synchronized (containerLock) {
            return List.copyOf(rootContainers);
        }
    }
    
    public List<Container> getContainersByType(ContainerType type) {
        synchronized (containerLock) {
            return containers.values().stream()
                .filter(c -> c.getType() == type)
                .collect(Collectors.toList());
        }
    }
    
    public int getContainerCount() {
        synchronized (containerLock) {
            return containers.size();
        }
    }
    
    // ========== 容器层级操作 ==========
    
    public void moveContainerToParent(Container container, Container newParent) {
        moveContainerToParent(container, newParent, -1);
    }

    public void moveContainerToParent(Container container, Container newParent, int index) {
        if (container == null) return;
        if (newParent != null && (newParent == container || newParent.isDescendantOf(container))) {
            LOGGER.warn("[ContainerManager] 无法将容器 {} 移动到其子孙节点 {}", container.getName(), newParent.getName());
            return;
        }

        Container oldParent;
        synchronized (containerLock) {
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
        synchronized (containerLock) {
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
        }

        fireContainerMoved(first, parentB);
        fireContainerMoved(second, parentA);
    }
    
    public void moveContainerUp(Container container) {
        if (container == null) return;
        
        Container parent = container.getParent();
        boolean moved = false;
        synchronized (containerLock) {
            List<Container> siblings = parent != null ?
                parent.getChildrenInternal() : rootContainers;
            int index = siblings.indexOf(container);
            if (index > 0) {
                Collections.swap(siblings, index, index - 1);
                moved = true;
            }
        }
        if (moved) {
            fireContainerMoved(container, parent);
        }
    }
    
    public void moveContainerDown(Container container) {
        if (container == null) return;
        
        Container parent = container.getParent();
        boolean moved = false;
        synchronized (containerLock) {
            List<Container> siblings = parent != null ?
                parent.getChildrenInternal() : rootContainers;
            int index = siblings.indexOf(container);
            if (index < siblings.size() - 1 && index >= 0) {
                Collections.swap(siblings, index, index + 1);
                moved = true;
            }
        }
        if (moved) {
            fireContainerMoved(container, parent);
        }
    }
    
    // ========== 选中管理 ==========
    
    public void selectContainer(Container container) {
        selectContainer(container, false);
    }
    
    public void selectContainer(Container container, boolean multiSelect) {
        boolean changed = false;
        synchronized (containerLock) {
            if (!multiSelect) {
                if (!selectedContainers.isEmpty() || selectedContainer != null) {
                    selectedContainers.clear();
                    selectedContainer = null;
                    changed = true;
                }
            }

            if (container != null) {
                if (selectedContainers.add(container)) {
                    changed = true;
                }
                if (selectedContainer != container) {
                    selectedContainer = container;
                    changed = true;
                }
            }
        }
        if (changed) {
            fireSelectionChanged();
        }
    }
    
    public void deselectContainer(Container container) {
        boolean changed;
        synchronized (containerLock) {
            changed = deselectInternal(container);
        }
        if (changed) {
            fireSelectionChanged();
        }
    }
    
    public void clearSelection() {
        boolean changed = false;
        synchronized (containerLock) {
            if (!selectedContainers.isEmpty() || selectedContainer != null) {
                selectedContainers.clear();
                selectedContainer = null;
                changed = true;
            }
        }
        if (changed) {
            fireSelectionChanged();
        }
    }
    
    public Container getSelectedContainer() {
        synchronized (containerLock) {
            return selectedContainer;
        }
    }
    
    public Set<Container> getSelectedContainers() {
        synchronized (containerLock) {
            return new LinkedHashSet<>(selectedContainers);
        }
    }

    public Set<Container> getSelectedContainersView() {
        return Collections.unmodifiableSet(getSelectedContainers());
    }
    
    public boolean isSelected(Container container) {
        synchronized (containerLock) {
            return selectedContainers.contains(container);
        }
    }
    
    // ========== 激活容器管理（v5.0） ==========
    
    /**
     * 获取当前激活的容器（用于实时接收模式）
     */
    public Container getActive() {
        synchronized (containerLock) {
            return activeContainer;
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
        synchronized (containerLock) {
            oldActive = activeContainer;
            activeContainer = newActive;
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
     * 切换容器的激活状态
     * <p>如果容器当前是激活的，则取消激活；否则激活该容器
     */
    public void toggleActive(Container container) {
        if (container == null) {
            return;
        }
        
        if (activeContainer == container) {
            setActive((UUID)null);
        } else {
            setActive(container);
        }
    }
    
    /**
     * 检查容器是否是激活的
     */
    public boolean isActive(Container container) {
        synchronized (containerLock) {
            return activeContainer == container;
        }
    }

    public void renameContainer(Container container, String newName) {
        if (container == null || newName == null) {
            return;
        }
        String trimmed = newName.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String oldName;
        String appliedName = trimmed;
        synchronized (containerLock) {
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
        }
        fireContainerRenamed(container, oldName, appliedName);
    }

    private boolean deselectInternal(Container container) {
        boolean changed = false;
        if (container == null) {
            if (!selectedContainers.isEmpty() || selectedContainer != null) {
                selectedContainers.clear();
                selectedContainer = null;
                changed = true;
            }
            return changed;
        }

        if (selectedContainers.remove(container)) {
            changed = true;
        }
        if (selectedContainer == container) {
            selectedContainer = selectedContainers.isEmpty() ? null : selectedContainers.iterator().next();
            changed = true;
        }
        return changed;
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
    public Container createFromSelection(com.chronoblocks.core.selection.SelectionState sel, 
                                        net.minecraft.world.World world, 
                                        boolean hiddenDefault) {
        if (sel == null || sel.isEmpty()) {
            LOGGER.warn("[ContainerManager] 无法从空选区创建容器");
            return null;
        }
        
        String name = generateUniqueName("选区容器");
        Container container = createContainer(name, ContainerType.CUSTOM);
        
        // 设置容器的世界归属（v5.2新增）
        if (world != null) {
            container.setLevelKey(world.getRegistryKey());
        }
        
        // 添加方块
        Set<net.minecraft.util.math.BlockPos> blocks = sel.getSelectedBlocks();
        container.addBlocks(blocks);
        
        // 捕获方块快照
        if (world != null) {
            container.getBlockStore().addAllFromWorld(blocks, world);
        }
        
        // 设置状态（v5.2：不再使用HiddenBlockRegistry，由ContainerVisibilityService处理）
        container.setState(hiddenDefault ? ContainerState.HIDDEN : ContainerState.VISIBLE);
        
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
        
        // v5.2: 如果容器是隐藏状态，Container.addBlocks()会自动处理可见性
        
        LOGGER.debug("[ContainerManager] 容器 {} 添加 {} 个方块", 
            container.getName(), positions.size());
    }
    
    /**
     * 从容器移除方块
     * 
     * @param id 容器ID
     * @param positions 方块位置集合
     */
    public void removeBlocks(UUID id, Collection<net.minecraft.util.math.BlockPos> positions) {
        Container container = getContainer(id);
        if (container == null) {
            LOGGER.warn("[ContainerManager] 无法移除方块：容器 {} 不存在", id);
            return;
        }
        
        if (positions == null || positions.isEmpty()) {
            return;
        }
        
        // 从容器移除方块
        for (net.minecraft.util.math.BlockPos pos : positions) {
            container.removeBlock(pos);
        }
        
        // v5.2: 如果容器是隐藏状态，需要先恢复这些方块，然后重新隐藏容器
        // 由于方块已经是AIR，移除方块时不需要特别处理
        // 如果容器仍然是隐藏状态，Container.setState() 会处理可见性
        
        LOGGER.debug("[ContainerManager] 容器 {} 移除 {} 个方块", 
            container.getName(), positions.size());
    }
    
    /**
     * 合并两个容器
     * <p>将容器B的所有方块合并到容器A，然后删除容器B
     * 
     * @param idA 目标容器ID（保留）
     * @param idB 源容器ID（删除）
     */
    public void merge(UUID idA, UUID idB) {
        if (idA == null || idB == null || idA.equals(idB)) {
            LOGGER.warn("[ContainerManager] 合并失败：无效的容器ID");
            return;
        }
        
        Container containerA = getContainer(idA);
        Container containerB = getContainer(idB);
        
        if (containerA == null || containerB == null) {
            LOGGER.warn("[ContainerManager] 合并失败：容器不存在");
            return;
        }
        
        // 合并方块
        Set<net.minecraft.util.math.BlockPos> blocksB = containerB.getBlocks();
        containerA.addBlocks(blocksB);
        
        // 合并方块快照（v5.3: 使用新的 Entry 格式）
        for (net.minecraft.util.math.BlockPos pos : blocksB) {
            BlockStore.Entry entry = containerB.getBlockStore().get(pos);
            if (entry != null) {
                containerA.getBlockStore().add(pos, entry);
            }
        }
        
        // v5.2: 如果容器A是隐藏状态，新添加的方块会自动被隐藏（因为容器状态是HIDDEN）
        // 删除容器B时，removeContainer() 会自动处理可见性
        removeContainer(containerB);
        
        LOGGER.info("[ContainerManager] 合并容器: {} → {} ({} 个方块)", 
            containerB.getName(), containerA.getName(), blocksB.size());
    }
    
    /**
     * 释放容器
     * <p>删除容器，并恢复方块在世界中的显示
     * 
     * @param id 容器ID
     */
    public void release(UUID id) {
        Container container = getContainer(id);
        if (container == null) {
            LOGGER.warn("[ContainerManager] 无法释放容器：容器 {} 不存在", id);
            return;
        }
        
        // v5.2: 如果容器是隐藏状态，先恢复方块再释放
        if (container.isHidden()) {
            try {
                var visibilityService = com.chronoblocks.core.container.visibility.ContainerServices.visibility();
                if (visibilityService != null) {
                    visibilityService.showContainer(container);
                }
            } catch (Exception e) {
                LOGGER.warn("[ContainerManager] 释放容器 {} 时恢复方块失败: {}", 
                    container.getName(), e.getMessage());
            }
        }
        
        // 删除容器
        removeContainer(container);
        
        LOGGER.info("[ContainerManager] 释放容器: {} ({} 个方块)", 
            container.getName(), container.getBlockCount());
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
            duplicate.setName(generateUniqueName(duplicate.getName()));
            addContainer(duplicate);
            Container parent = container.getParent();
            if (parent != null) {
                int insertIndex = parent.indexOfChild(container) + 1;
                moveContainerToParent(duplicate, parent, insertIndex);
            }
            duplicates.add(duplicate);
        }
        return duplicates;
    }

    /**
     * 当前选中容器数量是否满足合并条件
     */
    public boolean canMergeSelection() {
        return selectedContainers.size() >= 2;
    }

    /**
     * 合并选中的容器，生成新的组合容器。
     *
     * @param preferredName 合并后的候选名称，允许为空
     * @return 新建的容器，若合并失败则返回 null
     */
    public Container mergeSelectedContainers(String preferredName) {
        if (!canMergeSelection()) {
            LOGGER.warn("[ContainerManager] 合并失败：需要至少选择两个容器");
            return null;
        }

        List<Container> toMerge = getTopLevelSelection(selectedContainers);
        if (toMerge.isEmpty()) {
            LOGGER.warn("[ContainerManager] 合并失败：无有效容器");
            return null;
        }
        Container first = toMerge.get(0);
        Container commonParent = first.getParent();
        boolean sameParent = toMerge.stream().allMatch(c -> c.getParent() == commonParent);

        String baseName = (preferredName == null || preferredName.isBlank())
            ? first.getName() + "_合并"
            : preferredName.trim();
        String uniqueName = generateUniqueName(baseName);

        Container merged = createContainer(uniqueName, ContainerType.GROUP);
        moveContainerToParent(merged, sameParent ? commonParent : null);

        for (Container container : toMerge) {
            // 跳过刚创建的容器（防御性）
            if (container == merged) {
                continue;
            }

            merged.addBlocks(container.getBlocks());

            for (Container child : container.getChildren()) {
                moveContainerToParent(child, merged);
            }
        }

        // 合并后移除原始容器
        for (Container container : toMerge) {
            if (container != merged) {
                removeContainer(container);
            }
        }

        selectContainer(merged);
        LOGGER.info("[ContainerManager] 合并 {} 个容器为 {}", toMerge.size(), uniqueName);
        return merged;
    }

    /**
     * 将选中的容器放入一个新的组容器中。
     *
     * @param preferredName 新组名称候选，允许为空
     * @return 新建的组容器，若没有选中容器则返回 null
     */
    public Container groupSelectedContainers(String preferredName) {
        if (selectedContainers.isEmpty()) {
            LOGGER.warn("[ContainerManager] 分组失败：没有选中的容器");
            return null;
        }

        List<Container> selection = getTopLevelSelection(selectedContainers);
        if (selection.isEmpty()) {
            LOGGER.warn("[ContainerManager] 分组失败：无有效容器");
            return null;
        }
        Container first = selection.get(0);
        Container commonParent = first.getParent();
        boolean sameParent = selection.stream().allMatch(c -> c.getParent() == commonParent);

        String baseName = (preferredName == null || preferredName.isBlank())
            ? "组"
            : preferredName.trim();
        String uniqueName = generateUniqueName(baseName);

        Container group = createContainer(uniqueName, ContainerType.GROUP);
        moveContainerToParent(group, sameParent ? commonParent : null);

        for (Container container : selection) {
            if (container == group) {
                continue;
            }
            moveContainerToParent(container, group);
        }

        selectContainer(group);
        LOGGER.info("[ContainerManager] 将 {} 个容器分组到 {}", selection.size(), uniqueName);
        return group;
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
        synchronized (containerLock) {
            removedContainers = new ArrayList<>(containers.values());
            containers.clear();
            containersByName.clear();
            rootContainers.clear();
            selectionChanged = !selectedContainers.isEmpty() || selectedContainer != null;
            selectedContainers.clear();
            selectedContainer = null;
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
        createContainer("City Core", ContainerType.STRUCTURE);
        createContainer("Residential Zone", ContainerType.ENVIRONMENT);
        createContainer("Tree Cluster", ContainerType.VEGETATION);
        
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