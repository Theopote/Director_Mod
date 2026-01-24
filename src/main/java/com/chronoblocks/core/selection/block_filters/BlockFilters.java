package com.chronoblocks.core.selection.block_filters;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

import java.util.Set;
import java.util.function.Predicate;

/**
 * 🔍 常用方块过滤器集合
 * <p>
 * 提供预定义的常用过滤器和工厂方法。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class BlockFilters {
    
    /**
     * 匹配特定方块类型
     */
    public static BlockFilter ofBlock(Block targetBlock) {
        return (world, pos, state) -> state.getBlock().equals(targetBlock);
    }
    
    /**
     * 匹配特定方块状态
     */
    public static BlockFilter ofBlockState(BlockState targetState) {
        return (world, pos, state) -> state.equals(targetState);
    }
    
    /**
     * 匹配非空气方块
     */
    public static BlockFilter nonAir() {
        return (world, pos, state) -> !state.isAir();
    }

    /**
     * 匹配水（含流水、静水）
     */
    public static BlockFilter water() {
        return (world, pos, state) -> state.getBlock() == Blocks.WATER;
    }

    /**
     * 匹配岩浆（含流岩浆、静岩浆）
     */
    public static BlockFilter lava() {
        return (world, pos, state) -> state.getBlock() == Blocks.LAVA;
    }

    /**
     * 匹配植物类方块：树叶、花、树苗、作物、地上的草/高草/蕨/枯灌木等。
     * 注：Short Grass、Tall Grass、Fern、Large Fern、Dead Bush 无单独 BlockTag，用方块判断。
     */
    public static BlockFilter plant() {
        return hasTag(BlockTags.LEAVES)
            .or(hasTag(BlockTags.SMALL_FLOWERS))
            .or(hasTag(BlockTags.SAPLINGS))
            .or(hasTag(BlockTags.CROPS))
            .or(BlockFilters::isGrassOrFernOrDeadBush);
    }

    private static boolean isGrassOrFernOrDeadBush(net.minecraft.world.World w, net.minecraft.util.math.BlockPos p, net.minecraft.block.BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.SHORT_GRASS || b == Blocks.TALL_GRASS
            || b == Blocks.FERN || b == Blocks.LARGE_FERN || b == Blocks.DEAD_BUSH;
    }
    
    /**
     * 匹配具有指定标签的方块
     */
    public static BlockFilter hasTag(TagKey<Block> tag) {
        return (world, pos, state) -> state.isIn(tag);
    }
    
    /**
     * 匹配名称包含特定字符串的方块
     */
    public static BlockFilter nameContains(String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        return (world, pos, state) -> 
            state.getBlock().toString().toLowerCase().contains(lowerKeyword);
    }
    
    /**
     * 匹配多个方块类型之一
     */
    public static BlockFilter ofBlocks(Set<Block> blocks) {
        return (world, pos, state) -> blocks.contains(state.getBlock());
    }
    
    /**
     * 匹配可被替换的方块（空气、草等）
     */
    public static BlockFilter replaceable() {
        return (world, pos, state) -> state.isReplaceable();
    }
    
    /**
     * 匹配实心方块
     */
    public static BlockFilter solid() {
        return (world, pos, state) -> state.isSolidBlock(world, pos);
    }
    
    /**
     * 匹配透明方块
     */
    public static BlockFilter transparent() {
        return (world, pos, state) -> !state.isOpaque();
    }
    
    /**
     * 匹配发光方块
     */
    public static BlockFilter luminous(int minLightLevel) {
        return (world, pos, state) -> state.getLuminance() >= minLightLevel;
    }
    
    /**
     * 匹配特定硬度范围的方块
     */
    public static BlockFilter hardnessInRange(float min, float max) {
        return (world, pos, state) -> {
            float hardness = state.getHardness(world, pos);
            return hardness >= min && hardness <= max;
        };
    }
    
    /**
     * 自定义谓词过滤器
     */
    public static BlockFilter custom(Predicate<BlockState> predicate) {
        return (world, pos, state) -> predicate.test(state);
    }
    
    /**
     * 匹配所有方块（用于测试）
     */
    public static BlockFilter all() {
        return (world, pos, state) -> true;
    }
    
    /**
     * 不匹配任何方块（用于测试）
     */
    public static BlockFilter none() {
        return (world, pos, state) -> false;
    }
}

