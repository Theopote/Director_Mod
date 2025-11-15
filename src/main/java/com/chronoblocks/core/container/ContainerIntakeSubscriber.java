package com.chronoblocks.core.container;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 容器实时接收订阅器
 * 
 * <p>监听选择系统的变化,当有激活容器且实时接收开关打开时,
 * 自动将新选中的方块添加到容器中
 * 
 * <p>功能流程：
 * <ol>
 *   <li>监听SelectionManager的选择变化事件</li>
 *   <li>检查是否有激活容器且实时接收开关打开</li>
 *   <li>计算新增的方块（delta）</li>
 *   <li>将新增方块添加到容器</li>
 *   <li>如果容器是隐藏状态,方块立即从世界中消失</li>
 *   <li>清空选区（可选）</li>
 * </ol>
 * 
 * @author ChronoBlocks Team
 * @version 5.0
 */
public final class ContainerIntakeSubscriber {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ContainerIntakeSubscriber.class);
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    
    // ========== 实例 ==========
    private static ContainerIntakeSubscriber instance;
    
    // ========== 状态追踪 ==========
    private Set<BlockPos> lastSelection = new HashSet<>();
    private boolean enabled = true;
    
    // ========== 配置选项 ==========
    private boolean clearSelectionAfterIntake = true; // 收纳后是否清空选区
    
    private ContainerIntakeSubscriber() {
        // 注册监听器
        SelectionManager selectionManager = SelectionManager.get();
        if (selectionManager != null) {
            selectionManager.addSelectionChangeListener(this::onSelectionChanged);
            LOGGER.info("[ContainerIntakeSubscriber] 已注册选择变化监听器");
        } else {
            LOGGER.warn("[ContainerIntakeSubscriber] 无法获取SelectionManager,延迟注册");
        }
    }
    
    public static ContainerIntakeSubscriber getInstance() {
        if (instance == null) {
            instance = new ContainerIntakeSubscriber();
        }
        return instance;
    }
    
    /**
     * 初始化订阅器（在模组初始化时调用）
     */
    public static void initialize() {
        getInstance();
        LOGGER.info("[ContainerIntakeSubscriber] 初始化完成");
    }
    
    /**
     * 选择变化回调
     * 
     * @param newSelection 新的选择状态
     */
    private void onSelectionChanged(SelectionState newSelection) {
        if (!enabled) {
            return;
        }
        
        // 检查是否有激活容器
        ContainerManager manager = ContainerManager.getInstance();
        if (manager == null) {
            return;
        }
        
        Container activeContainer = manager.getActive();
        if (activeContainer == null) {
            // 没有激活容器,更新lastSelection后返回
            updateLastSelection(newSelection);
            return;
        }
        
        // 检查实时接收开关
        if (!activeContainer.isIntakeEnabled()) {
            // 实时接收未开启,更新lastSelection后返回
            updateLastSelection(newSelection);
            return;
        }
        
        // 计算新增的方块
        Set<BlockPos> currentSelection = newSelection != null && !newSelection.isEmpty() 
            ? new HashSet<>(newSelection.getSelectedBlocks()) 
            : new HashSet<>();
            
        Set<BlockPos> delta = calculateDelta(currentSelection);
        
        if (delta.isEmpty()) {
            // 没有新增方块
            updateLastSelection(newSelection);
            return;
        }
        
        // 将新增方块添加到容器
        World world = MC.world;
        if (world != null) {
            manager.addBlocks(activeContainer.getId(), delta, world);
            
            LOGGER.info("[ContainerIntakeSubscriber] 实时接收 {} 个方块到容器 {}", 
                delta.size(), activeContainer.getName());
        }
        
        // 清空选区（可选）
        if (clearSelectionAfterIntake) {
            SelectionManager selectionManager = SelectionManager.get();
            if (selectionManager != null) {
                // 延迟清空,避免在回调中修改选区导致问题
                MC.execute(() -> selectionManager.clearSelection());
            }
        }
        
        // 更新lastSelection
        updateLastSelection(newSelection);
    }
    
    /**
     * 计算新增的方块（delta）
     * 
     * @param currentSelection 当前选区
     * @return 新增的方块集合
     */
    private Set<BlockPos> calculateDelta(Set<BlockPos> currentSelection) {
        Set<BlockPos> delta = new HashSet<>(currentSelection);
        delta.removeAll(lastSelection);
        return delta;
    }
    
    /**
     * 更新lastSelection
     */
    private void updateLastSelection(SelectionState newSelection) {
        if (newSelection != null && !newSelection.isEmpty()) {
            lastSelection = new HashSet<>(newSelection.getSelectedBlocks());
        } else {
            lastSelection.clear();
        }
    }
    
    /**
     * 清空追踪的选区状态
     */
    public void clearState() {
        lastSelection.clear();
    }
    
    /**
     * 启用/禁用订阅器
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        LOGGER.info("[ContainerIntakeSubscriber] {}", enabled ? "已启用" : "已禁用");
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * 设置收纳后是否清空选区
     */
    public void setClearSelectionAfterIntake(boolean clear) {
        this.clearSelectionAfterIntake = clear;
    }
    
    public boolean isClearSelectionAfterIntake() {
        return clearSelectionAfterIntake;
    }
    
    /**
     * 手动触发收纳当前选区
     * <p>不检查实时接收开关,直接收纳当前选区到指定容器或激活容器
     * 
     * @param targetContainer 目标容器，如果为null则使用激活容器或选中容器
     */
    public void intakeCurrentSelection(Container targetContainer) {
        ContainerManager manager = ContainerManager.getInstance();
        if (manager == null) {
            LOGGER.warn("[ContainerIntakeSubscriber] ContainerManager未初始化");
            return;
        }
        
        // 确定目标容器：优先使用传入的容器，其次激活容器，最后选中的容器
        Container container = targetContainer;
        if (container == null) {
            container = manager.getActive();
        }
        if (container == null) {
            container = manager.getSelectedContainer();
        }
        if (container == null) {
            LOGGER.warn("[ContainerIntakeSubscriber] 没有可用的容器（激活或选中）");
            return;
        }
        
        // 组容器不能收纳方块
        if (container.getType() == ContainerType.GROUP) {
            LOGGER.warn("[ContainerIntakeSubscriber] 组容器无法收纳方块: {}", container.getName());
            return;
        }
        
        SelectionManager selectionManager = SelectionManager.get();
        if (selectionManager == null) {
            LOGGER.warn("[ContainerIntakeSubscriber] SelectionManager未初始化");
            return;
        }
        
        SelectionState currentSelection = selectionManager.getCurrentSelection();
        if (currentSelection == null || currentSelection.isEmpty()) {
            LOGGER.warn("[ContainerIntakeSubscriber] 当前没有选中方块");
            return;
        }
        
        Set<BlockPos> blocks = currentSelection.getSelectedBlocks();
        World world = MC.world;
        
        if (world != null && !blocks.isEmpty()) {
            manager.addBlocks(container.getId(), blocks, world);
            LOGGER.info("[ContainerIntakeSubscriber] 手动收纳 {} 个方块到容器 {}", 
                blocks.size(), container.getName());
                
            if (clearSelectionAfterIntake) {
                selectionManager.clearSelection();
            }
        }
    }
    
    /**
     * 手动触发收纳当前选区（使用激活容器或选中容器）
     */
    public void intakeCurrentSelection() {
        intakeCurrentSelection(null);
    }
}

