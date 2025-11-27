package com.chronoblocks.core.animation.sequence;

import java.io.Serial;
import java.io.Serializable;
import net.minecraft.util.math.BlockPos;

/**
 * 序列行为内部计算用的方块条目。
 *
 * <p>包含：
 * <ul>
 *   <li>原始位置</li>
 *   <li>在容器中的原始索引</li>
 *   <li>排序所需的 group / score 临时值</li>
 * </ul>
 */
public final class BlockSequenceItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final BlockPos pos;
    private final int index;

    private int groupId;
    private double score;

    public BlockSequenceItem(BlockPos pos, int index) {
        this.pos = pos;
        this.index = index;
        this.groupId = 0;
        this.score = 0.0;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getIndex() {
        return index;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}

