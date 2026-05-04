package com.director.core.selection;

import com.director.client.renderer.SelectionRenderer;
import com.director.client.renderer.ToolPreviewManager;
import com.director.core.selection.tools.FaceHandleManager;
import com.director.core.selection.tools.boxselecttool.BoxSelectTool;
import com.director.core.selection.events.*;
import com.director.core.selection.block_filters.BlockFilter;
import com.director.core.selection.block_filters.BlockFilterPipeline;
import com.director.core.selection.block_filters.BlockFilterPresetManager;
import com.director.core.selection.block_filters.BlockFilters;
import com.director.core.selection.block_filters.BlockFilterRegistry;
import com.director.core.selection.block_filters.FilterIds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.director.client.gizmo.Axis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

/**
 * 馃幆 SelectionManager - 閫夋嫨宸ュ叿绠＄悊鍣?
 * <p>
 * 璐熻矗绠＄悊褰撳墠閫変腑宸ュ叿銆侀€夊尯鐘舵€佷笌涓栫晫浜や簰浜嬩欢銆?
 * - 澶勭悊宸ュ叿鍒囨崲锛堟潵鑷?Toolbar锛?
 * - 杞彂榧犳爣浜嬩欢鑷冲綋鍓嶅伐鍏?
 * - 鍙戝嚭閫夊尯鍙樺寲浜嬩欢缁欏睘鎬ч潰鏉?/ 鏃堕棿绾?
 * - 绠＄悊鍏ㄥ眬杩囨护绠￠亾锛堢敱 SelectionFilterPanel 閰嶇疆锛?
 * 
 * @author Director Team
 * @version 2.0
 */
public class SelectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionManager.class);
    private static SelectionManager INSTANCE;

    private SelectionTool currentTool;
    private final SelectionRegistry registry = new SelectionRegistry();
    private final List<Consumer<SelectionState>> selectionChangeListeners = new ArrayList<>();
    
    /** 閫夊尯闆嗗悎 */
    private final SelectionSet selectionSet = new SelectionSet();

    /** 鍏变韩闈㈡嫋鎷挎墜鏌勭鐞嗗櫒锛堟墍鏈夐€夋嫨宸ュ叿鍏卞悓浣跨敤锛� */
    private final FaceHandleManager faceHandleManager = FaceHandleManager.SHARED;

    // ===== 闈㈡嫋鎷藉疄鏃堕瑙堢姸鎬?(v4.9) =====
    private Box activeResizeBaseBounds = null;
    private Box activeResizePreviewBounds = null;
    private Integer activeResizeFaceIndex = null;
    private double activeResizeOffset = 0.0;

    // ===== BOUNDS Gizmo锛堢Щ鍔ㄥ寘鍥寸洅锛夊疄鏃堕瑙堢姸鎬?=====
    private Box activeBoundsGizmoMovePreviewBounds = null;

    // 当选区中没有非空气方块时，仍保留最近一次可编辑的包围盒
    private Box emptySelectionBounds = null;
    
    /** 浜嬩欢澶勭悊鍣?*/
    private SelectionToolEventProcessor eventProcessor;
    
    // ================================
    // 馃З 瀹炴椂棰勮鍚屾閰嶇疆 (v3.1)
    // ================================
    
    /** 鏄惁鑷姩鏄剧ず棰勮妗?*/
    private boolean autoPreviewEnabled = true;
    
    /** 鍏ㄥ眬杩囨护绠￠亾锛堝彲鐢?SelectionFilterPanel 閰嶇疆锛夛紱榛樿鏄惧紡鍔犲叆 exclude_air锛屼笉鍐嶇敤銆岀┖绠￠亾=蹇界暐绌烘皵銆嶉殣寮忚鍒欍€?*/
    private final BlockFilterPipeline globalPipeline = createInitialPipeline();

    private static BlockFilterPipeline createInitialPipeline() {
        var p = BlockFilterPresetManager.getPreset(FilterIds.PRESET_DEFAULT);
        if (p != null && !p.isEmpty()) return p;
        BlockFilter f = BlockFilterRegistry.getFilter(FilterIds.EXCLUDE_AIR);
        return new BlockFilterPipeline().add(f != null ? f : BlockFilters.nonAir());
    }

    private SelectionManager() {
        registerDefaultTools();
        // 寤惰繜鍒濆鍖栦簨浠跺鐞嗗櫒锛岄伩鍏嶅惊鐜緷璧?
        this.eventProcessor = null;
    }

    public static SelectionManager get() {
        if (INSTANCE == null) {
            INSTANCE = new SelectionManager();
        }
        return INSTANCE;
    }
    
    /**
     * 寤惰繜鍒濆鍖栦簨浠跺鐞嗗櫒
     */
    private SelectionToolEventProcessor getEventProcessorInternal() {
        if (eventProcessor == null) {
            eventProcessor = new SelectionToolEventProcessor();
        }
        return eventProcessor;
    }

    /**
     * 娉ㄥ唽榛樿宸ュ叿
     */
    private void registerDefaultTools() {
        // 鍩虹宸ュ叿
        registry.register(new com.director.core.selection.tools.ClickSelectTool());
        registry.register(new com.director.core.selection.tools.magicwandtool.MagicWandTool());
        
        // 楂樼骇宸ュ叿
        registry.register(new com.director.core.selection.tools.faceselecttool.FaceSelectTool());
        // 鍑犱綍閫夋嫨宸ュ叿宸插悎骞跺埌鍒烽€夊伐鍏蜂腑锛屼笉鍐嶅崟鐙敞鍐?
        // registry.register(new com.director.core.selection.tools.geometry.GeometrySelectTool());
        registry.register(new com.director.core.selection.tools.brushselecttool.BrushSelectTool());
        registry.register(new com.director.core.selection.tools.lassoselecttool.LassoSelectTool());
        
        // 澶氭ā寮忛€夋嫨宸ュ叿
        registry.register(new com.director.core.selection.tools.boxselecttool.BoxSelectTool());
        
        LOGGER.info("[SelectionManager] 宸叉敞鍐?{} 涓€夋嫨宸ュ叿", registry.size());
    }

    /**
     * 璁剧疆褰撳墠宸ュ叿
     */
    public void setCurrentTool(String toolId, World world) {
        SelectionTool tool = registry.getTool(toolId);
        if (tool == null) {
            LOGGER.warn("[SelectionManager] 鏃犳硶鍒囨崲鍒版湭鐭ュ伐鍏? {}", toolId);
            return;
        }

        // String previousToolId = currentTool != null ? currentTool.getId() : null; // 鏆傛椂涓嶄娇鐢?
        
        // 鍋滅敤褰撳墠宸ュ叿
        if (currentTool != null) {
            currentTool.onDeactivate(world);
            // 鍙戝竷宸ュ叿鍋滅敤浜嬩欢
            getEventProcessorInternal().processToolDeactivation(currentTool.getId(), world);
        }

        // 工具切换时清理共享面拖拽状态，避免跨工具残留
        faceHandleManager.cancelDrag();
        
        // 婵€娲绘柊宸ュ叿
        currentTool = tool;
        currentTool.onActivate(world);
        
        // 鍙戝竷宸ュ叿婵€娲讳簨浠?
        getEventProcessorInternal().processToolActivation(toolId, world);

        LOGGER.info("[SelectionManager] 褰撳墠宸ュ叿鍒囨崲涓? {}", tool.getDisplayName());
    }

    /**
     * 鍒囨崲鍒颁笅涓€涓伐鍏?
     */
    public void switchToNextTool(World world) {
        SelectionTool nextTool = registry.getNextTool(currentTool);
        if (nextTool != null) {
            setCurrentTool(nextTool.getId(), world);
        }
    }

    /**
     * 鍒囨崲鍒颁笂涓€涓伐鍏?
     */
    public void switchToPreviousTool(World world) {
        SelectionTool prevTool = registry.getPreviousTool(currentTool);
        if (prevTool != null) {
            setCurrentTool(prevTool.getId(), world);
        }
    }

    /**
     * 鑾峰彇閫夊尯闆嗗悎
     */
    public SelectionSet getSelectionSet() { 
        return selectionSet; 
    }

    public FaceHandleManager getFaceHandleManager() {
        return faceHandleManager;
    }

    /**
     * 娓呴櫎閫夊尯
     */
    public void clearSelection() { 
        selectionSet.clear();
        emptySelectionBounds = null;
        
        // 馃殌 v2.0: 浣跨綉鏍肩紦瀛樺け鏁堬紝寮哄埗涓嬫娓叉煋鏃堕噸鏂拌绠?
        invalidateRendererCache();
        
        // 鑷姩娓呴櫎棰勮妗?
        if (autoPreviewEnabled) {
            try {
                ToolPreviewManager.getInstance().clearPreview();
            } catch (Exception e) {
                LOGGER.debug("[SelectionManager] 娓呴櫎棰勮妗嗗け璐ワ紙鍙兘涓嶅湪瀹㈡埛绔級: {}", e.getMessage());
            }
        }
    }

    /**
     * 娣诲姞鍒伴€夊尯
     */
    public void addToSelection(BlockPos pos) { 
        if (pos != null) {
            emptySelectionBounds = null;
            selectionSet.add(pos);
            // 馃殌 v2.0: 浣跨綉鏍肩紦瀛樺け鏁?
            invalidateRendererCache();
        }
    }

    /**
     * 鎵归噺娣诲姞鍒伴€夊尯
     */
    public void addAllToSelection(Collection<BlockPos> positions) {
        emptySelectionBounds = null;
        selectionSet.addAll(positions);
        // 馃殌 v2.0: 浣跨綉鏍肩紦瀛樺け鏁?
        invalidateRendererCache();
    }

    /**
     * 浠庨€夊尯绉婚櫎
     */
    public void removeFromSelection(BlockPos pos) { 
        if (pos != null) {
            emptySelectionBounds = null;
            selectionSet.remove(pos);
            // 馃殌 v2.0: 浣跨綉鏍肩紦瀛樺け鏁?
            invalidateRendererCache();
        }
    }
    
    /**
     * 馃殌 v2.0: 浣挎覆鏌撳櫒缂撳瓨澶辨晥鐨勮緟鍔╂柟娉?
     */
    public void setSelectionBoundsOverride(Box bounds) {
        emptySelectionBounds = copyBox(bounds);
        invalidateRendererCache();
    }

    public void clearSelectionBoundsOverride() {
        emptySelectionBounds = null;
        invalidateRendererCache();
    }

    private void invalidateRendererCache() {
        try {
            SelectionRenderer.getInstance().invalidateCache();
        } catch (Exception e) {
            // 濡傛灉 SelectionRenderer 涓嶅彲鐢紙鏈嶅姟鍣ㄧ锛夛紝蹇界暐閿欒
            LOGGER.debug("[SelectionManager] 浣跨綉鏍肩紦瀛樺け鏁堝け璐ワ紙鍙兘涓嶅湪瀹㈡埛绔級: {}", e.getMessage());
        }
    }

    /**
     * 澶勭悊鐐瑰嚮浜嬩欢
     */
    public void handleClick(World world, BlockPos pos, boolean shift) {
        LOGGER.info("[SelectionManager] 澶勭悊鐐瑰嚮浜嬩欢: pos={}, shift={}, currentTool={}", 
            pos, shift, currentTool != null ? currentTool.getId() : "null");
        
        if (currentTool != null) {
            // 鍙戝竷宸ュ叿鐐瑰嚮浜嬩欢
            LOGGER.info("[SelectionManager] 鍙戝竷宸ュ叿鐐瑰嚮浜嬩欢");
            getEventProcessorInternal().processToolClick(world, pos, shift, false);
            
            // 鎵ц宸ュ叿鐐瑰嚮閫昏緫
            LOGGER.info("[SelectionManager] 鎵ц宸ュ叿鐐瑰嚮閫昏緫");
            currentTool.onClick(world, pos, shift);
            
            // 閫氱煡閫夋嫨鍙樺寲
            LOGGER.info("[SelectionManager] 閫氱煡閫夋嫨鍙樺寲");
            notifySelectionChanged();
        } else {
            LOGGER.warn("[SelectionManager] 没有当前工具，无法处理点击事件");
        }
    }

    /**
     * 澶勭悊鎷栨嫿寮€濮?
     * @param hitOrNull 灏勭嚎鍛戒腑缁撴灉锛屾湭鍛戒腑鎴栨潵鑷钩闈㈡姇褰辨椂涓?null
     */
    public void handleDragStart(World world, BlockPos start, BlockHitResult hitOrNull) {
        if (currentTool != null) {
            // 鍙戝竷宸ュ叿鎷栨嫿寮€濮嬩簨浠?
            getEventProcessorInternal().processToolDragStart(world, start);
            
            // 鎵ц宸ュ叿鎷栨嫿寮€濮嬮€昏緫锛?-鍙傛暟锛屼緵濂楃储绛夊彇琛ㄩ潰鐐癸級
            currentTool.onDragStart(world, start, hitOrNull);

            // 寮€濮嬪伐鍏烽瑙堬紙妗嗛€夊伐鍏疯嚜宸辫礋璐?setPreviewBox 瀹炴椂棰勮锛岄伩鍏嶅弻閲嶉瑙堜笌 O(n^3) 棰勮璁＄畻锛?
            String toolId = currentTool.getId();
            if (!"box".equalsIgnoreCase(toolId) && !"box_v3".equalsIgnoreCase(toolId)) {
                com.director.client.renderer.ToolPreviewManager.getInstance().startDragPreview(start);
            }
        }
    }

    /**
     * 澶勭悊鎷栨嫿鏇存柊
     * @param hitOrNull 灏勭嚎鍛戒腑缁撴灉锛屾湭鍛戒腑鎴栨潵鑷钩闈㈡姇褰辨椂涓?null
     */
    public void handleDragUpdate(World world, BlockPos current, BlockHitResult hitOrNull) {
        if (currentTool != null) {
            // 鍙戝竷宸ュ叿鎷栨嫿鏇存柊浜嬩欢
            getEventProcessorInternal().processToolDragUpdate(world, current, current);
            
            // 鎵ц宸ュ叿鎷栨嫿鏇存柊閫昏緫锛?-鍙傛暟锛屼緵濂楃储绛夊彇琛ㄩ潰鐐癸級
            currentTool.onDragUpdate(world, current, hitOrNull);

            // 鏇存柊宸ュ叿棰勮锛堟閫夊伐鍏疯嚜宸辫礋璐?setPreviewBox 瀹炴椂棰勮锛?
            String toolId = currentTool.getId();
            if (!"box".equalsIgnoreCase(toolId) && !"box_v3".equalsIgnoreCase(toolId)) {
                com.director.client.renderer.ToolPreviewManager.getInstance().updateDragPreview(current);
            }
        }
    }

    /**
     * 澶勭悊鎷栨嫿缁撴潫
     * @param hitOrNull 灏勭嚎鍛戒腑缁撴灉锛屾湭鍛戒腑鎴栨潵鑷钩闈㈡姇褰辨椂涓?null
     */
    public void handleDragEnd(World world, BlockPos end, BlockHitResult hitOrNull) {
        if (currentTool != null) {
            // 鍙戝竷宸ュ叿鎷栨嫿缁撴潫浜嬩欢
            getEventProcessorInternal().processToolDragEnd(world, end, end);
            
            // 鎵ц宸ュ叿鎷栨嫿缁撴潫閫昏緫锛?-鍙傛暟锛屼緵濂楃储绛夊彇琛ㄩ潰鐐癸級
            currentTool.onDragEnd(world, end, hitOrNull);

            // 缁撴潫宸ュ叿棰勮锛堟閫夊伐鍏疯嚜宸辫礋璐?clearPreview锛?
            String toolId = currentTool.getId();
            if (!"box".equalsIgnoreCase(toolId) && !"box_v3".equalsIgnoreCase(toolId)) {
                com.director.client.renderer.ToolPreviewManager.getInstance().endDragPreview();
            }
            
            // 馃殌 v2.0: 浣跨綉鏍肩紦瀛樺け鏁堬紙鎷栨嫿缁撴潫閫氬父鎰忓懗鐫€閫夊尯鏀瑰彉锛?
            invalidateRendererCache();
            
            // 閫氱煡閫夋嫨鍙樺寲
            notifySelectionChanged();
        }
    }

    /**
     * 姣忓抚鏇存柊
     */
    public void tick(World world) {
        if (currentTool != null) {
            currentTool.tick(world);
        }
    }

    /**
     * 娣诲姞閫夋嫨鍙樺寲鐩戝惉鍣?
     */
    public void addSelectionChangeListener(Consumer<SelectionState> listener) {
        selectionChangeListeners.add(listener);
    }

    /**
     * 绉婚櫎閫夋嫨鍙樺寲鐩戝惉鍣?
     * 
     * @deprecated 褰撳墠鏈娇鐢紝淇濈暀鐢ㄤ簬鏈潵鎵╁睍
     */
    @Deprecated
    @SuppressWarnings("unused")
    public void removeSelectionChangeListener(Consumer<SelectionState> listener) {
        selectionChangeListeners.remove(listener);
    }

    /**
     * 閫氱煡閫夋嫨鐘舵€佸彉鍖?
     */
    private void notifySelectionChanged() {
        if (currentTool != null) {
            SelectionState state = currentTool.getCurrentSelection();
            
            // 鍒涘缓閫夋嫨鍙樺寲浜嬩欢
            Set<BlockPos> addedBlocks = new HashSet<>(); // 绠€鍖栧疄鐜帮紝瀹為檯搴旇璺熻釜鍙樺寲
            Set<BlockPos> removedBlocks = new HashSet<>(); // 绠€鍖栧疄鐜帮紝瀹為檯搴旇璺熻釜鍙樺寲
            SelectionChangedEvent event = new SelectionChangedEvent(
                null, // 绠€鍖栧疄鐜帮紝鏆傛椂浣跨敤null
                state, addedBlocks, removedBlocks);
            
            // 鍙戝竷閫夋嫨鍙樺寲浜嬩欢
            getEventProcessorInternal().processSelectionChanged(null, event); // 绠€鍖栧疄鐜帮紝鏆傛椂浣跨敤null
            
            // 閫氱煡浼犵粺鐩戝惉鍣?
            for (Consumer<SelectionState> listener : selectionChangeListeners) {
                try {
                    listener.accept(state);
                } catch (Exception e) {
                    LOGGER.warn("[SelectionManager] 选择变化监听器执行失败", e);
                }
            }
        }
    }

    // ========== Getters ==========

    public SelectionTool getCurrentTool() {
        return currentTool;
    }

    public SelectionRegistry getRegistry() {
        return registry;
    }

    public SelectionState getCurrentSelection() {
        return SelectionState.of(selectionSet.asUnmodifiableSet());
    }
    

    public boolean hasCurrentTool() {
        return currentTool != null;
    }

    public String getCurrentToolId() {
        return currentTool != null ? currentTool.getId() : null;
    }

    public String getCurrentToolDisplayName() {
        return currentTool != null ? currentTool.getDisplayName() : "无工具";
    }

    /**
     * 鑾峰彇鍏ㄥ眬杩囨护绠￠亾
     * 
     * @return 鍏ㄥ眬杩囨护绠￠亾瀹炰緥锛屽彲鐢ㄤ簬鍖哄煙閫夋嫨宸ュ叿
     */
    public BlockFilterPipeline getPipeline() {
        return globalPipeline;
    }

    /**
     * 鑾峰彇鐢ㄤ簬鏂瑰潡閫夋嫨鐨勩€屾湁鏁堣繃婊ゅ櫒銆嶃€傜┖绠￠亾 = 鍏ㄩ儴閫氳繃锛岀敱 pipeline.test 璇箟淇濊瘉銆?
     */
    public BlockFilter getEffectiveBlockFilter() {
        return globalPipeline::test;
    }

    /**
     * 鑾峰彇鐢ㄤ簬闇€瑕?BlockFilterPipeline 鐨勩€屾湁鏁堢閬撱€嶇殑鍓湰銆傜┖绠￠亾 = 鍏ㄩ儴閫氳繃銆?
     */
    public BlockFilterPipeline getEffectiveFilterPipeline() {
        return globalPipeline.clone();
    }

    // =====================================================
    // 馃攧 闈㈡嫋鎷藉疄鏃堕瑙?(v4.9)
    // =====================================================

    public void beginFaceResize(Box baseBounds, int faceIndex) {
        if (baseBounds == null) {
            cancelFaceResize();
            return;
        }

        activeResizeBaseBounds = copyBox(baseBounds);
        activeResizePreviewBounds = activeResizeBaseBounds;
        activeResizeFaceIndex = faceIndex;
        activeResizeOffset = 0.0;
    }

    public void previewFaceResize(int faceIndex, double offset) {
        if (activeResizeBaseBounds == null || activeResizeFaceIndex == null) {
            Box currentBounds = getCurrentBounds();
            if (currentBounds == null) {
                return;
            }
            beginFaceResize(currentBounds, faceIndex);
        }

        if (activeResizeFaceIndex == null) {
            return;
        }

        if (!activeResizeFaceIndex.equals(faceIndex)) {
            beginFaceResize(activeResizeBaseBounds != null ? activeResizeBaseBounds : getCurrentBounds(), faceIndex);
        }

        activeResizeOffset = offset;
        activeResizePreviewBounds = adjustBounds(activeResizeBaseBounds, activeResizeFaceIndex, activeResizeOffset);
    }

    public void applyFaceResize(int faceIndex, double offset) {
        if (activeResizeBaseBounds == null || activeResizeFaceIndex == null) {
            Box currentBounds = getCurrentBounds();
            if (currentBounds == null) {
                return;
            }
            beginFaceResize(currentBounds, faceIndex);
        }

        previewFaceResize(faceIndex, offset);

        if (activeResizePreviewBounds == null) {
            cancelFaceResize();
            return;
        }

        // 无论是否命中非空气方块，都保留用户调整后的包围盒尺寸
        emptySelectionBounds = copyBox(activeResizePreviewBounds);

        rebuildSelectionFromBounds(activeResizePreviewBounds);
        notifySelectionChanged();
        
        // 鏇存柊 Gizmo 浣嶇疆浠ヨ窡闅忔柊鐨勯€夊尯涓績
        updateGizmoPosition();
        
        cancelFaceResize();
    }

    public void cancelFaceResize() {
        activeResizeBaseBounds = null;
        activeResizePreviewBounds = null;
        activeResizeFaceIndex = null;
        activeResizeOffset = 0.0;
    }

    public boolean isFaceResizeActive() {
        return activeResizeBaseBounds != null;
    }

    public Box getResizePreviewBounds() {
        return activeResizePreviewBounds;
    }

    public boolean isBoundsGizmoMoveActive() {
        return activeBoundsGizmoMovePreviewBounds != null;
    }

    public Box getBoundsGizmoMovePreviewBounds() {
        return activeBoundsGizmoMovePreviewBounds;
    }

    public void setBoundsGizmoMovePreviewBounds(Box box) {
        activeBoundsGizmoMovePreviewBounds = box;
    }

    public void clearBoundsGizmoMovePreviewBounds() {
        activeBoundsGizmoMovePreviewBounds = null;
    }

    public Box getResizeBaseBounds() {
        return activeResizeBaseBounds;
    }

    private void rebuildSelectionFromBounds(Box bounds) {
        if (bounds == null) {
            return;
        }

        emptySelectionBounds = copyBox(bounds);

        selectionSet.clear();
        World world = MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().world : null;
        if (world == null) {
            return;
        }

        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX) - 1;
        int maxY = (int) Math.ceil(bounds.maxY) - 1;
        int maxZ = (int) Math.ceil(bounds.maxZ) - 1;

        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return;
        }

        // Bounds Gizmo resize：只保留非空气方块
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isAir(pos)) {
                        selectionSet.add(pos);
                    }
                }
            }
        }
    }

    /**
     * ✓ 用于 BOUNDS Gizmo 使用：把一个体积包围盒变为选区（包含所有位置，包括空气）。
     *
     * <p>注意：这里不调用 {@link #updateGizmoPosition()}，否则拖拽过程中会反复重建Gizmo，
     * 导致每次最多移动一格、拖拽中断。</p>
     */
    public void rebuildSelectionFromBoundsForBoundsGizmo(Box bounds) {
        if (bounds == null) {
            return;
        }

        emptySelectionBounds = copyBox(bounds);


        selectionSet.clear();
        World world = MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().world : null;
        if (world == null) {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
            return;
        }

        int minX = (int) Math.floor(bounds.minX);
        int minY = (int) Math.floor(bounds.minY);
        int minZ = (int) Math.floor(bounds.minZ);
        int maxX = (int) Math.ceil(bounds.maxX) - 1;
        int maxY = (int) Math.ceil(bounds.maxY) - 1;
        int maxZ = (int) Math.ceil(bounds.maxZ) - 1;

        if (maxX < minX || maxY < minY || maxZ < minZ) {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
            return;
        }

        // Bounds Gizmo resize：只保留非空气方块
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isAir(pos)) {
                        selectionSet.add(pos);
                    }
                }
            }
        }

        // 选区重建后需要让渲染缓存失效，否则可能出现"选区没更新、不稳定"的视觉表现
        invalidateRendererCache();

        if (selectionSet.isEmpty()) {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_CLEARED);
        } else {
            SelectionEvents.fireChanged(SelectionChangeType.SELECTION_UPDATED);
        }

        // 让面拖拽监听器也能收到变化（不触发updateGizmoPosition，避免拖拽中断）
        notifySelectionChanged();
    }

    private Box adjustBounds(Box base, int faceIndex, double offset) {
        if (base == null) {
            return null;
        }

        double minX = base.minX;
        double minY = base.minY;
        double minZ = base.minZ;
        double maxX = base.maxX;
        double maxY = base.maxY;
        double maxZ = base.maxZ;

        double minThickness = 0.01;

        switch (faceIndex) {
            case 0 -> maxX = Math.max(minX + minThickness, maxX + offset);
            case 1 -> minX = Math.min(maxX - minThickness, minX - offset);
            case 2 -> maxY = Math.max(minY + minThickness, maxY + offset);
            case 3 -> minY = Math.min(maxY - minThickness, minY - offset);
            case 4 -> maxZ = Math.max(minZ + minThickness, maxZ + offset);
            case 5 -> minZ = Math.min(maxZ - minThickness, minZ - offset);
            default -> {
            }
        }

        if (maxX - minX < minThickness) {
            double mid = (minX + maxX) * 0.5;
            minX = mid - minThickness * 0.5;
            maxX = mid + minThickness * 0.5;
        }
        if (maxY - minY < minThickness) {
            double mid = (minY + maxY) * 0.5;
            minY = mid - minThickness * 0.5;
            maxY = mid + minThickness * 0.5;
        }
        if (maxZ - minZ < minThickness) {
            double mid = (minZ + maxZ) * 0.5;
            minZ = mid - minThickness * 0.5;
            maxZ = mid + minThickness * 0.5;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Box copyBox(Box source) {
        return source == null ? null : new Box(source.minX, source.minY, source.minZ,
                source.maxX, source.maxY, source.maxZ);
    }
    
    /**
     * 鏇存柊 Gizmo 浣嶇疆浠ヨ窡闅忛€夊尯涓績
     */
    private void updateGizmoPosition() {
        // Gizmo 鐢ㄤ簬鈥滄暣浣撶Щ鍔ㄥ寘鍥寸洅/閫夊尯鈥濓紝鍙湁鍦ㄦ樉绀哄寘鍥寸洅鏃舵墠鍚敤锛岄伩鍏嶄笌鎷栨嫿寤虹洅浜х敓姒傚康/浜や簰鍐茬獊
        if (!com.director.client.settings.RenderSettings.get().selection_showBoundingBox) {
            try {
                com.director.client.gizmo.GizmoManager.get().deactivate();
            } catch (Exception ignored) {
            }
            return;
        }

        Box bounds = getCurrentBounds();
        if (bounds == null) {
            return;
        }
        
        // 璁＄畻鏂扮殑鍑犱綍涓績
        Vec3d newPivot = new Vec3d(
            (bounds.minX + bounds.maxX) / 2.0,
            (bounds.minY + bounds.maxY) / 2.0,
            (bounds.minZ + bounds.maxZ) / 2.0
        );
        
        try {
            com.director.client.gizmo.GizmoManager gizmoManager = com.director.client.gizmo.GizmoManager.get();
            if (gizmoManager != null && gizmoManager.isActive()) {
                // 鉁?涓嶈鍦ㄩ€夊尯鍙樺寲鏃跺弽澶?activate()锛堜細閲嶅缓 controller/handles锛屽鑷粹€滄嫋鎷藉彧鑳藉姩涓€鏍?涓嶇ǔ瀹氣€濓級
                gizmoManager.setPivot(newPivot);
                gizmoManager.setTargetBox(bounds);
                LOGGER.debug("[SelectionManager] 鏇存柊 Gizmo 浣嶇疆(鏃犻噸寤?: pivot={}", newPivot);
            }
        } catch (Exception e) {
            LOGGER.warn("[SelectionManager] 鏇存柊 Gizmo 浣嶇疆澶辫触", e);
        }
    }

    // =====================================================
    // 馃З expandSelection 鈥?閫夊尯缂╂斁鏍稿績閫昏緫 (v3.0)
    // =====================================================
    
    /**
     * 娌挎寚瀹氳酱鎵╁睍鎴栨敹缂╁綋鍓嶉€夊尯銆?
     * <p>
     * 鏀寔鍔熻兘锛?
     * 鉁?姝?璐熸柟鍚戞墿灞曢€夊尯
     * 鉁?鑷姩閲嶆柊璁＄畻 Bounding Box
     * 鉁?鍏煎 BoxSelectTool_v3 涓?GizmoRenderer
     * 鉁?瀹炴椂棰勮鍒锋柊锛堥鐣欙級
     * 
     * @param axis  鏂瑰悜杞达紙X/Y/Z锛?
     * @param delta 澧為噺锛?0 鎵╁睍锛?0 鏀剁缉锛?
     */
    public void expandSelection(Axis axis, int delta) {
        if (selectionSet.isEmpty()) {
            LOGGER.warn("[SelectionManager] 当前无选区，无法扩展");
            return;
        }
        
        // 璁＄畻褰撳墠閫夊尯杈圭晫锛堜娇鐢?getCurrentBounds()锛?
        Box bounds = getCurrentBounds();
        if (bounds == null) {
            LOGGER.warn("[SelectionManager] 鏃犳硶璁＄畻閫夊尯杈圭晫");
            return;
        }
        
        int minX = (int) bounds.minX;
        int minY = (int) bounds.minY;
        int minZ = (int) bounds.minZ;
        int maxX = (int) bounds.maxX - 1; // Box 鐨?max 鏄?exclusive锛岄渶瑕佸噺 1
        int maxY = (int) bounds.maxY - 1;
        int maxZ = (int) bounds.maxZ - 1;
        
        // 鍩轰簬鏂瑰悜鎵╁睍杈圭晫
        switch (axis) {
            case X -> {
                if (delta > 0) {
                    // 姝ｅ悜鎵╁睍锛氬湪 maxX 澶栦晶娣诲姞鏂瑰潡
                    for (int x = maxX + 1; x <= maxX + delta; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                selectionSet.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                } else {
                    // 璐熷悜鏀剁缉锛氱Щ闄?maxX 渚х殑澶栧眰鏂瑰潡
                    for (int x = maxX; x > maxX + delta; x--) {
                        final int removeX = x;
                        selectionSet.removeIf(p -> p.getX() == removeX);
                    }
                }
            }
            case Y -> {
                if (delta > 0) {
                    // 姝ｅ悜鎵╁睍锛氬湪 maxY 澶栦晶娣诲姞鏂瑰潡
                    for (int y = maxY + 1; y <= maxY + delta; y++) {
                        for (int x = minX; x <= maxX; x++) {
                            for (int z = minZ; z <= maxZ; z++) {
                                selectionSet.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                } else {
                    // 璐熷悜鏀剁缉锛氱Щ闄?maxY 渚х殑澶栧眰鏂瑰潡
                    for (int y = maxY; y > maxY + delta; y--) {
                        final int removeY = y;
                        selectionSet.removeIf(p -> p.getY() == removeY);
                    }
                }
            }
            case Z -> {
                if (delta > 0) {
                    // 姝ｅ悜鎵╁睍锛氬湪 maxZ 澶栦晶娣诲姞鏂瑰潡
                    for (int z = maxZ + 1; z <= maxZ + delta; z++) {
                        for (int x = minX; x <= maxX; x++) {
                            for (int y = minY; y <= maxY; y++) {
                                selectionSet.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                } else {
                    // 璐熷悜鏀剁缉锛氱Щ闄?maxZ 渚х殑澶栧眰鏂瑰潡
                    for (int z = maxZ; z > maxZ + delta; z--) {
                        final int removeZ = z;
                        selectionSet.removeIf(p -> p.getZ() == removeZ);
                    }
                }
            }
        }
        
        LOGGER.info("[SelectionManager] 娌?{} 杞磋皟鏁撮€夊尯 螖={}锛屽綋鍓嶆柟鍧楁暟={}",
                axis.name(), delta, selectionSet.size());
        
        // 閫氱煡閫夋嫨鍙樺寲锛堣Е鍙戞覆鏌撴洿鏂帮級
        notifySelectionChanged();
        
        // 馃З 瀹炴椂鍚屾棰勮妗嗭紙濡傛灉鍚敤锛?
        if (autoPreviewEnabled) {
            Box newBounds = getCurrentBounds();
            if (newBounds != null) {
                try {
                    ToolPreviewManager.getInstance().setPreviewBox(newBounds);
                } catch (Exception e) {
                    LOGGER.debug("[SelectionManager] 璁剧疆棰勮妗嗗け璐ワ紙鍙兘涓嶅湪瀹㈡埛绔級: {}", e.getMessage());
                }
            }
        }
    }
    
    // =====================================================
    // 馃摝 offsetSelection 鈥?骞崇Щ閫夊尯 (v3.0)
    // =====================================================
    
    /**
     * 骞崇Щ閫夊尯
     * 
     * @param offset 鍋忕Щ鍚戦噺
     */
    public void offsetSelection(BlockPos offset) {
        if (selectionSet.isEmpty()) {
            LOGGER.debug("[SelectionManager] 选区为空，跳过平移");
            return;
        }
        
        Set<BlockPos> moved = new HashSet<>();
        
        // 璁＄畻鏂扮殑浣嶇疆
        for (BlockPos pos : selectionSet.asUnmodifiableSet()) {
            moved.add(pos.add(offset));
        }
        
        // 娓呯┖鏃ч€夊尯锛屾坊鍔犳柊浣嶇疆
        selectionSet.clear();
        selectionSet.addAll(moved);
        
        LOGGER.debug("[SelectionManager] 骞崇Щ閫夊尯: offset={}, 鏂瑰潡鏁?{}", offset, selectionSet.size());
        
        // 閫氱煡閫夋嫨鍙樺寲锛堣Е鍙戞覆鏌撴洿鏂帮級
        notifySelectionChanged();
        
        // 鉁?鏇存柊 Gizmo 浣嶇疆锛岃窡闅忛€夊尯绉诲姩
        updateGizmoPosition();
        
        // 馃З 瀹炴椂鍚屾棰勮妗嗭紙濡傛灉鍚敤锛?
        if (autoPreviewEnabled) {
            Box newBounds = getCurrentBounds();
            if (newBounds != null) {
                try {
                    ToolPreviewManager.getInstance().setPreviewBox(newBounds);
                } catch (Exception e) {
                    LOGGER.debug("[SelectionManager] 璁剧疆棰勮妗嗗け璐ワ紙鍙兘涓嶅湪瀹㈡埛绔級: {}", e.getMessage());
                }
            }
        }
    }
    
    // =====================================================
    // 馃摝 getCurrentBounds 鈥?褰撳墠閫夊尯鍖呭洿鐩?(v3.1)
    // =====================================================
    
    /**
     * 鑾峰彇褰撳墠閫夊尯鐨勫寘鍥寸洅锛圔ox锛?
     * <p>
     * 鐢ㄤ簬锛?
     * - Gizmo 绯荤粺鑾峰彇閫夊尯杈圭晫
     * - SelectionRenderer 棰勮妗嗗悓姝?
     * - 鍏朵粬闇€瑕侀€夊尯杈圭晫鐨勫姛鑳?
     * 
     * @return 褰撳墠閫夊尯鐨勫寘鍥寸洅锛屽鏋滈€夊尯涓虹┖鍒欒繑鍥?null
     */
    public Box getCurrentBounds() {
        if (currentTool instanceof BoxSelectTool && emptySelectionBounds != null) {
            return copyBox(emptySelectionBounds);
        }
        if (selectionSet.isEmpty()) {
            return emptySelectionBounds != null ? copyBox(emptySelectionBounds) : null;
        }
        
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        
        for (BlockPos pos : selectionSet.asUnmodifiableSet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        
        // 杩斿洖 Box锛堝寘鍚?min 鍒?max+1 鐨勮寖鍥达紝鍥犱负 BlockPos 鏄暣鏁板潗鏍囷級
        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }
}
