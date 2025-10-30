package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 🖱️ 点击选择工具
 *
 * 左键点击即可在世界中选中单个方块：
 * - 单击：仅保留被点击的方块
 * - Shift+单击：在现有选区中加选或减选
 */
public class ClickSelectTool implements SelectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClickSelectTool.class);

    @Override
    public String getId() {
        return "click";
    }

    @Override
    public String getDisplayName() {
        return "点击选择";
    }

    @Override
    public String getDescription() {
        return "单击方块以选中它，Shift+单击可执行加选或减选。";
    }

    @Override
    public String getIcon() {
        return "\uE97C"; // Icons.SELECT
    }

    @Override
    public void onClick(World world, BlockPos pos, boolean isShiftDown) {
        LOGGER.info("[ClickSelectTool] ======= onClick 开始 =======");
        
        if (world == null || pos == null) {
            LOGGER.warn("[ClickSelectTool] world 或 pos 为空，忽略点击事件 (world={}, pos={})", world, pos);
            return;
        }

        SelectionManager manager = SelectionManager.get();
        if (manager == null) {
            LOGGER.error("[ClickSelectTool] SelectionManager 为空！");
            return;
        }
        
        boolean alreadySelected = manager.getSelectionSet().contains(pos);

        LOGGER.info("[ClickSelectTool] 点击方块 {} (Shift: {}, 已在选区: {})", pos, isShiftDown, alreadySelected);
        LOGGER.info("[ClickSelectTool] 当前选区大小: {} 个方块", manager.getSelectionSet().size());

        if (isShiftDown) {
            if (alreadySelected) {
                manager.removeFromSelection(pos);
                LOGGER.info("[ClickSelectTool] ✅ 从选区移除方块 {}", pos);
            } else {
                manager.addToSelection(pos);
                LOGGER.info("[ClickSelectTool] ✅ 将方块加入选区 {}", pos);
            }
        } else {
            int prevSize = manager.getSelectionSet().size();
            manager.clearSelection();
            manager.addToSelection(pos);
            LOGGER.info("[ClickSelectTool] ✅ 清除选区 (之前 {} 个)，添加新方块 {}", prevSize, pos);
        }

        int finalSize = manager.getSelectionSet().size();
        LOGGER.info("[ClickSelectTool] 操作完成，选区内共有 {} 个方块", finalSize);
        LOGGER.info("[ClickSelectTool] ======= onClick 结束 =======");
    }

    @Override
    public void onDragStart(World world, BlockPos start) {
        // 点击工具不支持拖拽，保留钩子以兼容事件流
        LOGGER.debug("[ClickSelectTool] 拖拽开始: {}", start);
    }

    @Override
    public void onDragEnd(World world, BlockPos end) {
        LOGGER.debug("[ClickSelectTool] 拖拽结束: {}", end);
    }

    @Override
    public SelectionState getCurrentSelection() {
        SelectionManager manager = SelectionManager.get();
        return SelectionState.of(manager.getSelectionSet().asUnmodifiableSet());
    }

    @Override
    public void clearSelection() {
        SelectionManager manager = SelectionManager.get();
        manager.clearSelection();
        LOGGER.debug("[ClickSelectTool] 清除选区");
    }

    @Override
    public boolean shouldRenderOverlay() {
        SelectionManager manager = SelectionManager.get();
        return !manager.getSelectionSet().isEmpty();
    }

    @Override
    public void onActivate(World world) {
        LOGGER.debug("[ClickSelectTool] 工具激活");
    }

    @Override
    public void onDeactivate(World world) {
        LOGGER.debug("[ClickSelectTool] 工具停用");
    }
}
