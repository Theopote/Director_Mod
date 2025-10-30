package com.chronoblocks.core.selection.filter;

import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * SelectionFilter：判定某 Block 是否纳入选区。
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
@FunctionalInterface
public interface SelectionFilter {
    /**
     * @return true 该方块纳入选区
     */
    boolean test(World world, BlockPos pos, BlockState state);

    /** 工具：与/或/非 组合 */
    default SelectionFilter and(SelectionFilter other) {
        return (w, p, s) -> this.test(w, p, s) && other.test(w, p, s);
    }
    
    default SelectionFilter or(SelectionFilter other) {
        return (w, p, s) -> this.test(w, p, s) || other.test(w, p, s);
    }
    
    static SelectionFilter not(SelectionFilter base) {
        return (w, p, s) -> !base.test(w, p, s);
    }

    // 常用内置工厂
    static SelectionFilter any() { 
        return (w,p,s) -> true; 
    }
    
    static SelectionFilter none() { 
        return (w,p,s) -> false; 
    }

    /** 按 Block 类型 */
    static SelectionFilter ofBlock(net.minecraft.block.Block block) {
        return (w,p,s) -> s.isOf(block);
    }

    /** 按 Tag */
    static SelectionFilter ofTag(TagKey<net.minecraft.block.Block> tag) {
        return (w,p,s) -> s.isIn(tag);
    }

    /** 按 State 属性值（字符串对比，轻量实现） */
    static SelectionFilter hasPropertyEquals(String propertyName, String expected) {
        return (w,p,s) -> {
            var prop = s.getProperties().stream()
                    .filter(pr -> pr.getName().equals(propertyName))
                    .findFirst().orElse(null);
            if (prop == null) return false;
            var val = s.get(prop);
            return val != null && expected.equals(String.valueOf(val));
        };
    }
    
    /** 非空气方块 */
    static SelectionFilter nonAir() {
        return (w, p, s) -> !s.isAir();
    }
    
    /** 可替换方块 */
    static SelectionFilter replaceable() {
        return (w, p, s) -> s.isReplaceable();
    }
}
