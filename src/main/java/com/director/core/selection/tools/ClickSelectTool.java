package com.director.core.selection.tools;

import com.director.core.DirectorServices;
import com.director.core.selection.SelectionManager;
import com.director.core.selection.SelectionOperation;
import com.director.core.selection.SelectionOperationSupport;
import com.director.core.selection.SelectionState;
import com.director.core.selection.SelectionTool;
import com.director.ui.theme.Icons;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * 🖱️ 点击选择工具
 * <p>
 * 左键点击即可在世界中选中单个方块：
 * - 单击：按当前操作模式（新建/加选/减选/交集）处理
 * - Shift+单击：强制加选（与其它选择工具一致）
 */
public class ClickSelectTool implements SelectionTool, SelectionOperationSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClickSelectTool.class);
    private static final Direction[] CARDINAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST,
            Direction.WEST, Direction.UP, Direction.DOWN
    };

    private SelectionOperation operation = SelectionOperation.NEW;
    private boolean doubleClickExpandEnabled = true;
    private DoubleClickMode doubleClickMode = DoubleClickMode.CONNECTED_ALL;
    private int expansionRange = 24;
    private int maxSelectionCount = 8192;
    private boolean visibleOnlySelection = false;
    private DoubleClickPreset doubleClickPreset = DoubleClickPreset.PRECISE;

    public enum DoubleClickMode {
        CONNECTED_ALL("相邻全部"),
        CONNECTED_SAME_BLOCK("相邻同类型"),
        GLOBAL_SAME_BLOCK("范围内同类型");

        private final String displayName;

        DoubleClickMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum DoubleClickPreset {
        QUICK("快速"),
        PRECISE("精确"),
        WIDE("大范围"),
        CUSTOM("自定义");

        private final String displayName;

        DoubleClickPreset(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

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
        return "单击选择方块，双击可按配置扩展选择。";
    }

    @Override
    public String getIcon() {
        return Icons.SELECT;
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

        SelectionManager manager = DirectorServices.get().selection();
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

    public void onDoubleClick(World world, BlockPos pos, boolean isShiftDown) {
        LOGGER.info("[ClickSelectTool] ======= onDoubleClick 开始 =======");

        if (!doubleClickExpandEnabled) {
            LOGGER.debug("[ClickSelectTool] 双击扩选已禁用，忽略");
            return;
        }
        if (world == null || pos == null) {
            LOGGER.warn("[ClickSelectTool] world 或 pos 为空，忽略双击事件 (world={}, pos={})", world, pos);
            return;
        }

        BlockState seedState = world.getBlockState(pos);
        if (seedState.isAir()) {
            LOGGER.debug("[ClickSelectTool] 双击命中空气，忽略: {}", pos);
            return;
        }

        SelectionManager manager = DirectorServices.get().selection();
        if (manager == null) {
            LOGGER.error("[ClickSelectTool] SelectionManager 为空！");
            return;
        }

        Set<BlockPos> expanded = switch (doubleClickMode) {
            case CONNECTED_ALL -> collectConnected(world, pos, false);
            case CONNECTED_SAME_BLOCK -> collectConnected(world, pos, true);
            case GLOBAL_SAME_BLOCK -> collectGlobalSame(world, pos, seedState.getBlock());
        };

        if (expanded.isEmpty()) {
            LOGGER.info("[ClickSelectTool] 双击扩选为空，跳过应用");
            return;
        }

        SelectionOperation op = isShiftDown ? SelectionOperation.ADD : operation;
        applySelectionOperation(manager, op, expanded);

        LOGGER.info("[ClickSelectTool] 双击扩选完成，模式={}, 数量={}, 当前选区={}",
                doubleClickMode, expanded.size(), manager.getSelectionSet().size());
        LOGGER.info("[ClickSelectTool] ======= onDoubleClick 结束 =======");
    }

    private Set<BlockPos> collectConnected(World world, BlockPos seed, boolean sameBlockOnly) {
        Set<BlockPos> result = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<Node> queue = new ArrayDeque<>();

        Block seedBlock = world.getBlockState(seed).getBlock();
        queue.offer(new Node(seed, 0));
        visited.add(seed);

        while (!queue.isEmpty() && result.size() < maxSelectionCount) {
            Node node = queue.poll();
            BlockPos current = node.pos;
            int depth = node.depth;

            BlockState currentState = world.getBlockState(current);
            if (currentState.isAir()) {
                continue;
            }
            if (sameBlockOnly && currentState.getBlock() != seedBlock) {
                continue;
            }

            if (passesVisibilityFilter(world, current)) {
                result.add(current.toImmutable());
            }
            if (depth >= expansionRange) {
                continue;
            }

            for (Direction direction : CARDINAL_DIRECTIONS) {
                BlockPos next = current.offset(direction);
                if (visited.add(next)) {
                    queue.offer(new Node(next, depth + 1));
                }
            }
        }
        return result;
    }

    private Set<BlockPos> collectGlobalSame(World world, BlockPos seed, Block targetBlock) {
        Set<BlockPos> result = new HashSet<>();
        int range = expansionRange;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int dx = -range; dx <= range && result.size() < maxSelectionCount; dx++) {
            for (int dy = -range; dy <= range && result.size() < maxSelectionCount; dy++) {
                for (int dz = -range; dz <= range && result.size() < maxSelectionCount; dz++) {
                    mutable.set(seed.getX() + dx, seed.getY() + dy, seed.getZ() + dz);
                    BlockState state = world.getBlockState(mutable);
                    if (!state.isAir() && state.getBlock() == targetBlock && passesVisibilityFilter(world, mutable)) {
                        result.add(mutable.toImmutable());
                    }
                }
            }
        }
        return result;
    }

    private boolean passesVisibilityFilter(World world, BlockPos pos) {
        return !visibleOnlySelection || isVisibleBlock(world, pos);
    }

    private boolean isVisibleBlock(World world, BlockPos pos) {
        for (Direction direction : CARDINAL_DIRECTIONS) {
            BlockPos neighbor = pos.offset(direction);
            if (world.getBlockState(neighbor).isAir()) {
                return true;
            }
        }
        return false;
    }

    private void applySelectionOperation(SelectionManager manager, SelectionOperation op, Set<BlockPos> targets) {
        switch (op) {
            case NEW -> {
                manager.clearSelection();
                manager.addAllToSelection(targets);
            }
            case ADD -> manager.addAllToSelection(targets);
            case SUBTRACT -> targets.forEach(manager::removeFromSelection);
            case INTERSECT -> {
                Set<BlockPos> keep = new HashSet<>(manager.getSelectionSet().asUnmodifiableSet());
                keep.retainAll(targets);
                manager.clearSelection();
                manager.addAllToSelection(keep);
            }
        }
    }

    private record Node(BlockPos pos, int depth) {
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
        SelectionManager manager = DirectorServices.get().selection();
        return SelectionState.of(manager.getSelectionSet().asUnmodifiableSet());
    }

    @Override
    public void clearSelection() {
        SelectionManager manager = DirectorServices.get().selection();
        manager.clearSelection();
        LOGGER.debug("[ClickSelectTool] 清除选区");
    }

    @Override
    public boolean shouldRenderOverlay() {
        SelectionManager manager = DirectorServices.get().selection();
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

    public boolean isDoubleClickExpandEnabled() {
        return doubleClickExpandEnabled;
    }

    public void setDoubleClickExpandEnabled(boolean doubleClickExpandEnabled) {
        this.doubleClickExpandEnabled = doubleClickExpandEnabled;
    }

    public DoubleClickMode getDoubleClickMode() {
        return doubleClickMode;
    }

    public void setDoubleClickMode(DoubleClickMode doubleClickMode) {
        this.doubleClickPreset = DoubleClickPreset.CUSTOM;
        this.doubleClickMode = Objects.requireNonNull(doubleClickMode, "doubleClickMode");
    }

    public int getExpansionRange() {
        return expansionRange;
    }

    public void setExpansionRange(int expansionRange) {
        this.doubleClickPreset = DoubleClickPreset.CUSTOM;
        this.expansionRange = Math.max(1, Math.min(64, expansionRange));
    }

    public int getMaxSelectionCount() {
        return maxSelectionCount;
    }

    public void setMaxSelectionCount(int maxSelectionCount) {
        this.doubleClickPreset = DoubleClickPreset.CUSTOM;
        this.maxSelectionCount = Math.max(128, Math.min(200_000, maxSelectionCount));
    }

    public boolean isVisibleOnlySelection() {
        return visibleOnlySelection;
    }

    public void setVisibleOnlySelection(boolean visibleOnlySelection) {
        this.doubleClickPreset = DoubleClickPreset.CUSTOM;
        this.visibleOnlySelection = visibleOnlySelection;
    }

    public DoubleClickPreset getDoubleClickPreset() {
        return doubleClickPreset;
    }

    public void setDoubleClickPreset(DoubleClickPreset preset) {
        this.doubleClickPreset = Objects.requireNonNull(preset, "preset");
        switch (preset) {
            case QUICK -> {
                this.doubleClickMode = DoubleClickMode.CONNECTED_ALL;
                this.expansionRange = 12;
                this.maxSelectionCount = 4_096;
                this.visibleOnlySelection = true;
            }
            case PRECISE -> {
                this.doubleClickMode = DoubleClickMode.CONNECTED_SAME_BLOCK;
                this.expansionRange = 24;
                this.maxSelectionCount = 8_192;
                this.visibleOnlySelection = true;
            }
            case WIDE -> {
                this.doubleClickMode = DoubleClickMode.GLOBAL_SAME_BLOCK;
                this.expansionRange = 32;
                this.maxSelectionCount = 50_000;
                this.visibleOnlySelection = false;
            }
            case CUSTOM -> {
                // 保持用户当前参数
            }
        }
    }
}
