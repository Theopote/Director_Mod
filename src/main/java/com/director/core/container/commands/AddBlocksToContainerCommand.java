package com.director.core.container.commands;

import com.director.core.container.Container;
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
    
    public AddBlocksToContainerCommand(Container container, 
                                       Collection<BlockPos> blocks, 
                                       World world) {
        this.container = container;
        this.blocksToAdd = new HashSet<>(blocks);
        this.world = world;
    }
    
    @Override
    public void execute() {
        // 添加方块
        container.addBlocks(blocksToAdd);
        
        // 捕获方块快照
        if (world != null) {
            container.getBlockStore().addAllFromWorld(blocksToAdd, world);
        }
        
        // v5.2: 如果容器是隐藏状态，可见性由ContainerVisibilityService管理
        LOGGER.debug("[AddBlocksToContainerCommand] 添加 {} 个方块到容器 {}", 
            blocksToAdd.size(), container.getName());
    }
    
    @Override
    public void undo() {
        // 移除方块
        for (BlockPos pos : blocksToAdd) {
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
}

