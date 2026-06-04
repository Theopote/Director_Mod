package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.BlockStore;
import com.director.core.container.visibility.ContainerServices;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 从容器移除方块命令 (v5.0)
 * <p>支持撤销/重做</p>
 * 
 * @author Director Team
 * @version 5.0
 */
public class RemoveBlocksFromContainerCommand implements ContainerCommand {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoveBlocksFromContainerCommand.class);
    
    private final Container container;
    private final Map<BlockPos, BlockStore.Entry> removedBlocks;
    
    public RemoveBlocksFromContainerCommand(Container container, 
                                           Collection<BlockPos> blocksToRemove) {
        this.container = container;
        this.removedBlocks = new HashMap<>();
        
        // 保存被移除方块的快照
        for (BlockPos pos : blocksToRemove) {
            BlockStore.Entry entry = container.getBlockStore().get(pos);
            if (entry != null) {
                removedBlocks.put(pos, entry);
            }
        }
    }
    
    @Override
    public void execute() {
        releaseFromHiddenContainer();

        // 移除方块
        for (BlockPos pos : removedBlocks.keySet()) {
            container.removeBlock(pos);
        }
        
        // v5.2: 如果容器是隐藏状态，可见性由ContainerVisibilityService管理
        LOGGER.debug("[RemoveBlocksFromContainerCommand] 从容器 {} 移除 {} 个方块", 
            container.getName(), removedBlocks.size());
    }
    
    @Override
    public void undo() {
        // 恢复方块
        container.addBlocks(removedBlocks.keySet());
        
        // 恢复方块快照（v5.3: 使用新的 Entry 格式）
        BlockStore store = container.getBlockStore();
        for (Map.Entry<BlockPos, BlockStore.Entry> entry : removedBlocks.entrySet()) {
            BlockStore.Entry snapshot = entry.getValue();
            // 直接使用 Entry 恢复
            store.raw().put(entry.getKey(), snapshot);
        }
        syncHiddenContainer();
        
        // v5.2: 如果容器是隐藏状态，可见性由ContainerVisibilityService管理
        LOGGER.debug("[RemoveBlocksFromContainerCommand] 撤销：恢复容器 {} 的 {} 个方块", 
            container.getName(), removedBlocks.size());
    }
    
    @Override
    public String getDescription() {
        return String.format("从容器 \"%s\" 移除 %d 个方块", 
            container.getName(), 
            removedBlocks.size());
    }

    private void releaseFromHiddenContainer() {
        if (!container.isHidden() || removedBlocks.isEmpty()) {
            return;
        }
        var visibilityService = ContainerServices.visibility();
        if (visibilityService != null) {
            visibilityService.restoreContainerBlocks(container, removedBlocks.keySet());
        }
    }

    private void syncHiddenContainer() {
        if (!container.isHidden() || removedBlocks.isEmpty()) {
            return;
        }
        var visibilityService = ContainerServices.visibility();
        if (visibilityService != null) {
            visibilityService.hideContainer(container);
        }
    }
}

