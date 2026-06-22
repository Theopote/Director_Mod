package com.director.core.selection.tools.magicwandtool;

import com.director.core.DirectorServices;
import com.director.client.renderer.ToolPreviewManager;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionOperation;
import com.director.core.selection.SelectionOperationSupport;
import com.director.core.selection.SelectionState;
import com.director.core.selection.SelectionTool;
import com.director.ui.theme.Icons;
import com.director.core.selection.block_filters.BlockFilterPipeline;
import com.director.core.selection.progressive.ProgressiveFloodController;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 🪄 MagicWandTool - 魔棒选择工具（Director Enhanced）
 * <p>
 * 泛洪式体素连通选择工具，比 Axiom 更强大：
 * - 更丰富的匹配模式（方块/状态/材质/标签/过滤器）
 * - 更灵活的连通性（6/18/26邻接）
 * - 实时预览 & 渐进式填充
 * - 与 SelectionManager 深度整合（NEW/ADD/SUBTRACT/INTERSECT）
 * - 友好的 ImGui 参数配置
 * <p>
 * 职责边界：
 * - 处理鼠标事件（点击/悬停）
 * - 管理异步任务（执行/预览）
 * - 与 SelectionManager 交互
 * - 不直接实现泛洪算法（委托给 MagicWandTask）
 * 
 * @author Director Team
 * @version 2.0 Enhanced
 */
public class MagicWandTool implements SelectionTool, SelectionOperationSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(MagicWandTool.class);
    
    // ==================== 线程池 ====================
    
    private static final ExecutorService THREAD_POOL = Executors.newFixedThreadPool(2,
        r -> {
            Thread t = new Thread(r, "MagicWand-Worker");
            t.setDaemon(true);
            return t;
        });
    
    // ==================== 配置与状态 ====================
    
    /** 魔棒配置 */
    private final MagicWandConfig config = MagicWandConfig.createDefault();
    
    /** 操作模式（NEW/ADD/SUBTRACT/INTERSECT） */
    private SelectionOperation operation = SelectionOperation.NEW;
    
    /** 当前选择状态（本地缓存） */
    private final SelectionState currentSelection = new SelectionState("wand");
    
    // ==================== 预览系统 ====================
    
    /** 当前悬停的方块 */
    private BlockPos hoverSeed = null;
    
    /** 预览任务的 Future */
    private CompletableFuture<MagicWandTask.MagicWandTaskResult> previewFuture = null;
    
    /** 最后的预览结果 */
    private MagicWandTask.MagicWandTaskResult lastPreviewResult = null;
    
    /** 预览是否脏（需要重新计算） */
    private boolean previewDirty = false;
    
    /** 预览限制（避免预览计算过长） */
    private static final int PREVIEW_LIMIT = 10_000;
    
    /** 上次更新预览的时间戳（用于节流） */
    private long lastPreviewUpdateTime = 0L;
    
    /** 预览更新的最小间隔（毫秒） */
    private static final long PREVIEW_UPDATE_INTERVAL_MS = 80L;
    
    /** 渐进式预览控制器（分帧执行） */
    private ProgressiveFloodController progressiveController = null;
    
    /** 是否使用渐进式预览（大范围flood时启用） */
    private boolean useProgressivePreview = false;
    
    /** 每帧处理的步数 */
    private static final int STEPS_PER_FRAME = 2048;
    
    // ==================== 执行任务 ====================
    
    /** 当前执行的任务 Future */
    private CompletableFuture<MagicWandTask.MagicWandTaskResult> executionFuture = null;

    // ==================== SelectionTool 接口实现 ====================
    
    @Override 
    public String getId() { 
        return "wand"; 
    }
    
    @Override 
    public String getDisplayName() { 
        return "魔棒工具"; 
    }
    
    @Override
    public String getDescription() {
        return "泛洪式体素连通选择。支持多种匹配模式、连通性配置和实时预览。";
    }
    
    @Override
    public String getIcon() {
        return Icons.MAGIC;
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        if (world == null || pos == null) {
            return;
        }
        
        LOGGER.info("[MagicWandTool] 执行选择: pos={}, shift={}, operation={}",
            pos, isShiftDown, operation);
        
        // Shift 临时切换为加选模式
        SelectionOperation actualOperation = isShiftDown ? SelectionOperation.ADD : operation;
        
        // 启动异步任务
        executeSelection(world, pos, actualOperation);
    }
    
    @Override
    public void onHover(World world, BlockPos pos) {
        onHoverBlock(pos);
    }
    
    @Override
    public void onRightClick(World world, BlockPos pos) {
        // 右键可以用来快速减选
        if (world == null || pos == null) {
            return;
        }
        
        LOGGER.info("[MagicWandTool] 右键减选: pos={}", pos);
        executeSelection(world, pos, SelectionOperation.SUBTRACT);
    }
    
    @Override
    public void tick(World world) {
        // 1. 检查渐进式预览状态（它在后台异步执行）
        if (progressiveController != null && !progressiveController.isRunning()) {
            // 渐进式预览完成
            MagicWandTask.MagicWandTaskResult result = progressiveController.getCurrentResult();
            if (result != null) {
                lastPreviewResult = result;
                LOGGER.debug("[MagicWandTool] 渐进式预览完成: {} 个方块", result.size());
            }
            progressiveController = null;
        }
        
        // 2. 处理普通预览任务
        updatePreview(world);
        
        // 3. 处理执行任务
        updateExecution();
    }
    
    // ==================== 核心方法：执行选择 ====================
    
    /**
     * 执行魔棒选择
     */
    private void executeSelection(World world, BlockPos seed, SelectionOperation op) {
        // 取消之前的任务
        if (executionFuture != null && !executionFuture.isDone()) {
            executionFuture.cancel(true);
        }
        
        // 获取限定区域（如果配置了）
        SelectionState limitRegion = config.isLimitToCurrentSelection() ?
            DirectorServices.get().selection().getCurrentSelection() : null;
        
        // 创建任务
        MagicWandTask task = new MagicWandTask(world, seed, config, limitRegion, getActiveFilterPipeline());
        
        // 异步执行
        executionFuture = CompletableFuture.supplyAsync(() -> {
            LOGGER.debug("[MagicWandTool] 开始执行泛洪填充...");
            return task.run(-1); // -1 表示一次执行完
        }, THREAD_POOL);
        
        // 完成后应用结果（在主线程中执行）
        executionFuture.thenAcceptAsync(result -> {
            if (result != null && !result.isEmpty()) {
                applyResult(result, op);
            } else {
                LOGGER.debug("[MagicWandTool] 未找到匹配的方块");
            }
        }, runnable -> {
            // 确保在主线程（客户端线程）中执行
            net.minecraft.client.MinecraftClient.getInstance().execute(runnable);
        }).exceptionally(ex -> {
            if (!(ex instanceof java.util.concurrent.CancellationException)) {
                LOGGER.error("[MagicWandTool] 执行失败", ex);
            }
            return null;
        });
    }
    
    /**
     * 应用选择结果到 SelectionManager
     */
    private void applyResult(MagicWandTask.MagicWandTaskResult result, SelectionOperation op) {
        SelectionManager manager = DirectorServices.get().selection();
        
        LOGGER.info("[MagicWandTool] 应用结果: {} 个方块, 操作={}, 达到上限={}",
            result.size(), op, result.reachedLimit());
        
        // 根据操作模式应用
        switch (op) {
            case NEW -> {
                manager.clearSelection();
                manager.addAllToSelection(result.blocks());
            }
            case ADD -> manager.addAllToSelection(result.blocks());
            case SUBTRACT -> {
                for (BlockPos pos : result.blocks()) {
                    manager.removeFromSelection(pos);
                }
            }
            case INTERSECT -> {
                var current = manager.getSelectionSet().asUnmodifiableSet();
                manager.clearSelection();
                for (BlockPos pos : result.blocks()) {
                    if (current.contains(pos)) {
                        manager.addToSelection(pos);
                    }
                }
            }
        }
        
        // 更新本地缓存
        syncCurrentSelection(manager);
        
        if (result.reachedLimit()) {
            LOGGER.warn("[MagicWandTool] 达到最大方块数限制: {}", config.getMaxBlocks());
        }
    }

    // ==================== 预览系统 ====================
    
    /**
     * 更新悬停预览（外部调用）
     */
    public void onHoverBlock(BlockPos pos) {
        // 节流检查：如果距离上次更新时间太短，且方块位置没有显著变化，则跳过
        long now = System.currentTimeMillis();
        if (hoverSeed != null && Objects.equals(this.hoverSeed, pos)) {
            return; // 同一个方块，无需更新
        }
        
        // 节流：最小间隔检查
        if (now - lastPreviewUpdateTime < PREVIEW_UPDATE_INTERVAL_MS) {
            // 仍然标记为脏，但推迟实际执行
            if (!Objects.equals(this.hoverSeed, pos)) {
                this.hoverSeed = pos;
                this.previewDirty = true;
            }
            return;
        }
        
        // 更新时间戳
        lastPreviewUpdateTime = now;
        
        // 方块变化，更新状态
        if (!Objects.equals(this.hoverSeed, pos)) {
            this.hoverSeed = pos;
            this.previewDirty = true;
            
            // 取消之前的预览任务
            if (previewFuture != null && !previewFuture.isDone()) {
                previewFuture.cancel(true);
                LOGGER.debug("[MagicWandTool] 取消旧预览任务");
            }
            
            // 取消渐进式预览控制器
            if (progressiveController != null && progressiveController.isRunning()) {
                progressiveController.cancel();
                progressiveController = null;
                LOGGER.debug("[MagicWandTool] 取消渐进式预览");
            }
            
            LOGGER.debug("[MagicWandTool] 悬停方块变化: {}", pos);
        }
    }
    
    /**
     * 更新预览（每帧调用）
     */
    private void updatePreview(World world) {
        if (world == null) {
            return;
        }
        
        // 如果预览脏且没有正在运行的预览任务，启动新预览
        if (previewDirty && hoverSeed != null &&
            (previewFuture == null || previewFuture.isDone()) &&
            (progressiveController == null || !progressiveController.isRunning())) {
            
            previewDirty = false;
            
            // 创建预览配置（限制最大数量）
            MagicWandConfig previewConfig = config.clone();
            previewConfig.setMaxBlocks(Math.min(config.getMaxBlocks(), PREVIEW_LIMIT));
            
            // 获取限定区域
            SelectionState limitRegion = config.isLimitToCurrentSelection() ?
                DirectorServices.get().selection().getCurrentSelection() : null;
            
            // 创建预览任务
            BlockPos seedCopy = hoverSeed; // 避免并发修改
            MagicWandTask task = new MagicWandTask(world, seedCopy, previewConfig, limitRegion, getActiveFilterPipeline());
            
            // 决定使用渐进式还是一次性预览
            // 如果预计方块数较大，使用渐进式预览以获得平滑体验
            boolean shouldUseProgressive = config.getMaxBlocks() > 5000 || useProgressivePreview;
            
            if (shouldUseProgressive) {
                // 使用渐进式预览
                try {
                    progressiveController = new ProgressiveFloodController(
                        task,
                        STEPS_PER_FRAME,
                        config.getMaxBlocks(),
                        update -> {
                            // 进度回调：实时更新预览
                            if (update.partialResult() != null && !update.partialResult().isEmpty()) {
                                net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
                                    ToolPreviewManager.get().updateMagicWandPreview(
                                        update.partialResult().blocks(),
                                        update.partialResult().depthMap()
                                    );
                                    lastPreviewResult = update.partialResult();
                                });
                            }
                            LOGGER.trace("[MagicWandTool] 预览进度: {}", update.getProgressString());
                        }
                    );
                    progressiveController.start();
                    LOGGER.debug("[MagicWandTool] 启动渐进式预览: seed={}", seedCopy);
                } catch (Exception e) {
                    LOGGER.error("[MagicWandTool] 启动渐进式预览失败", e);
                    progressiveController = null;
                }
            } else {
                // 使用一次性异步预览
                previewFuture = CompletableFuture.supplyAsync(() -> task.run(PREVIEW_LIMIT), THREAD_POOL);
                
                // 完成后更新结果（在主线程中执行）
                previewFuture.thenAcceptAsync(result -> {
                    lastPreviewResult = result;
                    
                    // 更新 ToolPreviewManager 的预览（带深度信息用于渐变效果）
                    if (result != null && !result.isEmpty()) {
                        ToolPreviewManager.get().updateMagicWandPreview(result.blocks(), result.depthMap());
                        LOGGER.debug("[MagicWandTool] 预览完成: {} 个方块，最大深度={}", 
                            result.size(), result.getMaxDepth());
                    } else {
                        ToolPreviewManager.get().clearMagicWandPreview();
                    }
                }, runnable -> {
                    // 确保在主线程中执行
                    net.minecraft.client.MinecraftClient.getInstance().execute(runnable);
                }).exceptionally(ex -> {
                    if (!(ex instanceof java.util.concurrent.CancellationException)) {
                        LOGGER.error("[MagicWandTool] 预览失败", ex);
                    }
                    // 清除预览也要在主线程执行
                    net.minecraft.client.MinecraftClient.getInstance().execute(() -> ToolPreviewManager.get().clearMagicWandPreview());
                    return null;
                });
            }
        }
        
        // 检查预览任务是否完成
        if (previewFuture != null && previewFuture.isDone()) {
            try {
                lastPreviewResult = previewFuture.get();
            } catch (Exception e) {
                // 忽略取消异常
            }
        }
    }
    
    /**
     * 更新执行任务
     */
    private void updateExecution() {
        if (executionFuture != null && executionFuture.isDone()) {
            // 任务已完成，清理 Future
            executionFuture = null;
        }
    }
    
    /**
     * 获取预览结果（供渲染器使用）
     */
    public MagicWandTask.MagicWandTaskResult getPreviewResult() {
        return lastPreviewResult;
    }
    
    /**
     * 是否有活跃预览
     */
    public boolean hasPreview() {
        return lastPreviewResult != null && !lastPreviewResult.isEmpty();
    }
    
    /**
     * 获取最后一次预览的方块数量（供 UI 显示）
     */
    public int getLastPreviewCount() {
        return lastPreviewResult != null ? lastPreviewResult.size() : 0;
    }

    /**
     * 请求刷新当前预览（例如参数发生变化时）
     */
    public void requestPreviewRefresh() {
        previewDirty = true;
        lastPreviewResult = null;
        lastPreviewUpdateTime = 0L;

        if (previewFuture != null && !previewFuture.isDone()) {
            previewFuture.cancel(true);
            previewFuture = null;
        }

        if (progressiveController != null) {
            progressiveController.cancel();
            progressiveController = null;
        }

        ToolPreviewManager.get().clearMagicWandPreview();
    }

    /**
     * 获取当前激活的过滤器管线；委托 SelectionManager.getEffectiveFilterPipeline()，
     * 与框选、刷选等行为一致。
     */
    private BlockFilterPipeline getActiveFilterPipeline() {
        SelectionManager manager = DirectorServices.get().selection();
        if (manager == null) {
            return new BlockFilterPipeline().add(com.director.core.selection.block_filters.BlockFilters.nonAir());
        }
        return manager.getEffectiveFilterPipeline();
    }
    
    // ==================== 配置访问 ====================
    
    public MagicWandConfig getConfig() {
        return config;
    }
    
    @Override
    public SelectionOperation getOperation() {
        return operation;
    }
    
    @Override
    public void setOperation(SelectionOperation operation) {
        this.operation = operation;
        LOGGER.debug("[MagicWandTool] 操作模式切换为: {}", operation);
    }
    
    // ==================== SelectionTool 基础方法 ====================
    
    @Override
    public SelectionState getCurrentSelection() {
        return currentSelection;
    }

    @Override
    public void clearSelection() {
        currentSelection.clear();
        DirectorServices.get().selection().clearSelection();
        LOGGER.debug("[MagicWandTool] 清除选择");
    }

    @Override
    public boolean shouldRenderOverlay() {
        return !currentSelection.isEmpty() || hasPreview();
    }

    private void syncCurrentSelection(SelectionManager manager) {
        currentSelection.clear();
        currentSelection.addBlocks(manager.getSelectionSet().asUnmodifiableSet());
    }

    @Override
    public void onActivate(World world) {
        syncCurrentSelection(DirectorServices.get().selection());
        LOGGER.info("[MagicWandTool] 魔棒工具激活 - config={}", config);
    }

    @Override
    public void onDeactivate(World world) {
        // 取消所有任务
        if (previewFuture != null) {
            previewFuture.cancel(true);
            previewFuture = null;
        }
        if (executionFuture != null) {
            executionFuture.cancel(true);
            executionFuture = null;
        }
        
        // 取消渐进式控制器
        if (progressiveController != null) {
            progressiveController.cancel();
            progressiveController = null;
        }
        
        // 清除预览
        lastPreviewResult = null;
        hoverSeed = null;
        previewDirty = false;
        useProgressivePreview = false;
        
        // 清除 ToolPreviewManager 的预览
        ToolPreviewManager.get().clearMagicWandPreview();
        
        LOGGER.info("[MagicWandTool] 魔棒工具停用");
    }
    
    /**
     * 便捷方法：获取最大选择数量
     */
    public int getMaxSelectionSize() {
        return config.getMaxBlocks();
    }
    
    /**
     * 便捷方法：设置最大选择数量
     */
    public void setMaxSelectionSize(int maxSize) {
        config.setMaxBlocks(maxSize);
        LOGGER.debug("[MagicWandTool] 最大选择数量设置为: {}", maxSize);
    }
    
    /**
     * 是否包含对角线连接
     */
    public boolean isIncludeDiagonal() {
        return config.getConnectivity() != MagicWandConfig.Connectivity.NEIGHBOR_6;
    }
    
    /**
     * 设置是否包含对角线连接
     */
    public void setIncludeDiagonal(boolean includeDiagonal) {
        config.setConnectivity(includeDiagonal ?
            MagicWandConfig.Connectivity.NEIGHBOR_26 :
            MagicWandConfig.Connectivity.NEIGHBOR_6);
    }
}
