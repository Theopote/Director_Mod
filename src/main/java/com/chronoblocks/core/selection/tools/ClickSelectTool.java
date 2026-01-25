package com.chronoblocks.core.selection.tools;

import com.chronoblocks.core.selection.SelectionManager;
import com.chronoblocks.core.selection.SelectionOperation;
import com.chronoblocks.core.selection.SelectionOperationSupport;
import com.chronoblocks.core.selection.SelectionState;
import com.chronoblocks.core.selection.SelectionTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 🖱️ 点击选择工具
 * <p>
 * 左键点击即可在世界中选中单个方块：
 * - 单击：按当前操作模式（新建/加选/减选/交集）处理
 * - Shift+单击：强制加选（与其它选择工具一致）
 */
public class ClickSelectTool implements SelectionTool, SelectionOperationSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClickSelectTool.class);

    private SelectionOperation operation = SelectionOperation.NEW;

    @Override
    public SelectionOperation getOperation() {
        return operation;
    }

    @Override
    public void setOperation(SelectionOperation operation) {
        this.operation = Objects.requireNonNull(operation, "operation");
    }

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
        return "单击选择方块。";
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

        // 检查是否为空气方块：所有选择工具都只能选中非空气方块
        if (world.getBlockState(pos).isAir()) {
            LOGGER.debug("[ClickSelectTool] 点击的是空气方块，忽略选择: {}", pos);
            return;
        }

        SelectionManager manager = SelectionManager.get();
        if (manager == null) {
            LOGGER.error("[ClickSelectTool] SelectionManager 为空！");
            return;
        }

        // 与套索、框选等工具一致：Shift+单击强制加选，否则使用当前操作模式
        SelectionOperation op = isShiftDown ? SelectionOperation.ADD : operation;

        LOGGER.info("[ClickSelectTool] 点击方块 {} (Shift: {}, 操作: {})", pos, isShiftDown, op);

        switch (op) {
            case NEW -> {
                manager.clearSelection();
                manager.addToSelection(pos);
                LOGGER.info("[ClickSelectTool] ✅ 新建选区，选中方块 {}", pos);
            }
            case ADD -> {
                manager.addToSelection(pos);
                LOGGER.info("[ClickSelectTool] ✅ 加选方块 {}", pos);
            }
            case SUBTRACT -> {
                manager.removeFromSelection(pos);
                LOGGER.info("[ClickSelectTool] ✅ 从选区移除方块 {}", pos);
            }
            case INTERSECT -> {
                if (manager.getSelectionSet().contains(pos)) {
                    manager.clearSelection();
                    manager.addToSelection(pos);
                    LOGGER.info("[ClickSelectTool] ✅ 交集：仅保留方块 {}", pos);
                } else {
                    manager.clearSelection();
                    LOGGER.info("[ClickSelectTool] ✅ 交集：方块不在选区内，清空选区");
                }
            }
        }

        LOGGER.info("[ClickSelectTool] 操作完成，选区内共有 {} 个方块", manager.getSelectionSet().size());
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
