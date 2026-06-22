package com.director.core.selection;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * 📦 SelectionSet
 * 存储并管理当前选中的方块集合，提供：
 * - O(1) 包含/删除
 * - 分层（layerKey）支持（可用于区分不同容器/阶段）
 * - 过滤（Predicate）
 * - 包围盒/中心点/计数
 * - 事件回调（监听器）
 * 
 * @author Director Team
 * @version 1.0
 */
public class SelectionSet {

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionSet.class);

    /** 主集合：去重存储所有 BlockPos */
    private final Set<BlockPos> all = new HashSet<>();

    /** 分层索引：layerKey -> positions */
    private final Map<String, Set<BlockPos>> layers = new HashMap<>();

    /** 监听器（线程安全） */
    private final List<SelectionSetListener> listeners = new CopyOnWriteArrayList<>();

    /** 最近一次变化的时间戳（用于渲染端节流） */
    private volatile long lastChangeNanos = System.nanoTime();

    // ---------- 基础操作 ----------

    public boolean add(BlockPos pos) {
        if (pos == null) return false;
        boolean changed = all.add(pos);
        if (changed) notifyChanged();
        return changed;
    }

    public boolean add(BlockPos pos, String layerKey) {
        if (pos == null) return false;
        boolean changed = all.add(pos);
        if (layerKey != null) {
            layers.computeIfAbsent(layerKey, k -> new HashSet<>()).add(pos);
        }
        if (changed) notifyChanged();
        return changed;
    }

    public int addAll(Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return 0;
        int before = all.size();
        all.addAll(positions);
        int added = all.size() - before;
        if (added > 0) notifyChanged();
        return added;
    }

    public int addAll(Collection<BlockPos> positions, String layerKey) {
        if (positions == null || positions.isEmpty()) return 0;
        int added = 0;
        Set<BlockPos> layer = null;
        if (layerKey != null) {
            layer = layers.computeIfAbsent(layerKey, k -> new HashSet<>());
        }
        for (BlockPos p : positions) {
            if (all.add(p)) {
                added++;
                if (layer != null) layer.add(p);
            }
        }
        if (added > 0) notifyChanged();
        return added;
    }

    public boolean remove(BlockPos pos) {
        if (pos == null) return false;
        boolean changed = all.remove(pos);
        if (changed) {
            // 同步移除分层引用
            for (Set<BlockPos> layer : layers.values()) layer.remove(pos);
            notifyChanged();
        }
        return changed;
    }

    public int removeIf(Predicate<BlockPos> filter) {
        if (filter == null) return 0;
        int removed = 0;
        Iterator<BlockPos> it = all.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (filter.test(p)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            for (Set<BlockPos> layer : layers.values()) layer.removeIf(filter);
            notifyChanged();
        }
        return removed;
    }

    public void clear() {
        if (all.isEmpty()) return;
        all.clear();
        layers.clear();
        notifyChanged();
    }

    // ---------- 查询 ----------

    public boolean contains(BlockPos pos) { 
        return all.contains(pos); 
    }

    public Set<BlockPos> asUnmodifiableSet() { 
        return Collections.unmodifiableSet(all); 
    }

    public int size() { 
        return all.size(); 
    }

    public boolean isEmpty() { 
        return all.isEmpty(); 
    }

    // ---------- 空间信息 ----------

    public Box getBoundingBox() {
        if (all.isEmpty()) return new Box(0, 0, 0, 1, 1, 1);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : all) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }
        // Box 的闭区间上界需要 +1（方块为单位体）
        return new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    public BlockPos getCenterBlock() {
        if (all.isEmpty()) return new BlockPos(0, 0, 0);
        Box box = getBoundingBox();
        return new BlockPos(
            (int)Math.floor(box.getCenter().x),
            (int)Math.floor(box.getCenter().y),
            (int)Math.floor(box.getCenter().z)
        );
    }

    private void notifyChanged() {
        lastChangeNanos = System.nanoTime();
        for (SelectionSetListener l : listeners) {
            try { 
                l.onSelectionChanged(this); 
            } catch (RuntimeException e) {
                LOGGER.warn("[SelectionSet] 选区监听器抛出异常，已忽略", e);
            }
        }
    }

}
