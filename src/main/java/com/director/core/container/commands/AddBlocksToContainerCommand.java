package com.director.core.container.commands;

import com.director.core.container.Container;
import com.director.core.container.ContainerManager;
import com.director.core.container.visibility.ContainerServices;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 添加方块到容器命令 (v5.0)
 * <p>支持撤销/重做</p>
 * 
 * @author Director Team
 * @version 5.0
 */
public class AddBlocksToContainerCommand implements ContainerCommand {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AddBlocksToContainerCommand.class);
    
    private final Container container;
    private final Set<BlockPos> blocksToAdd;
    private final World world;
    private final Set<BlockPos> actuallyAdded = new HashSet<>();
    
    public AddBlocksToContainerCommand(Container container, 
                                       Collection<BlockPos> blocks, 
                                       World world) {
        this.container = container;
        this.blocksToAdd = new HashSet<>(blocks);
        this.world = world;
    }
    
    @Override
    public void execute() {
        actuallyAdded.clear();
        Collection<BlockPos> acceptedBlocks = filterUnownedBlocks();
        for (BlockPos pos : acceptedBlocks) {
            if (pos != null && !container.containsBlock(pos)) {
                actuallyAdded.add(pos);
            }
        }

        // 添加方块
        container.addBlocks(acceptedBlocks);
        actuallyAdded.removeIf(pos -> pos == null || !container.containsBlock(pos));
        
        // 捕获方块快照
        if (world != null) {
            container.getBlockStore().addAllFromWorld(acceptedBlocks, world);
        }

        syncHiddenContainer();
        
        // v5.2: 如果容器是隐藏状态，可见性由ContainerVisibilityService管理
        LOGGER.debug("[AddBlocksToContainerCommand] 添加 {} 个方块到容器 {}", 
            blocksToAdd.size(), container.getName());
    }
    
    @Override
    public void undo() {
        // 移除方块
        releaseFromHiddenContainer(actuallyAdded);
        for (BlockPos pos : actuallyAdded) {
            container.removeBlock(pos);
        }
        
        // v5.2: 如果容器是隐藏状态，可见性由ContainerVisibilityService管理
        LOGGER.debug("[AddBlocksToContainerCommand] 撤销：从容器 {} 移除 {} 个方块", 
            container.getName(), blocksToAdd.size());
    }
    
    @Override
    public String getDescription() {
        return String.format("添加 %d 个方块到容器 \"%s\"", 
            blocksToAdd.size(), 
            container.getName());
    }

    private void syncHiddenContainer() {
        if (!container.isHidden()) {
            return;
        }
        var visibilityService = ContainerServices.visibility();
        if (visibilityService != null) {
            visibilityService.hideContainer(container);
        }
    }

    private void releaseFromHiddenContainer(Collection<BlockPos> blocks) {
        if (!container.isHidden() || blocks == null || blocks.isEmpty()) {
            return;
        }
        var visibilityService = ContainerServices.visibility();
        if (visibilityService != null) {
            visibilityService.restoreContainerBlocks(container, blocks);
        }
    }

    private Collection<BlockPos> filterUnownedBlocks() {
        ContainerManager manager = ContainerManager.getInstance();
        if (manager == null) {
            return blocksToAdd;
        }
        var worldKey = world != null ? world.getRegistryKey() : container.getLevelKey();
        return manager.filterUnownedBlocks(blocksToAdd, worldKey, container.getId());
    }
}

