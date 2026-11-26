package com.director.core.container.visibility;

import net.minecraft.block.Block;

/**
 * 容器批量世界方块写入时使用的 {@code setBlockState} 标志。
 *
 * <p>批量隐藏/显示时跳过逐方块 {@code NOTIFY_NEIGHBORS}，光照检查仍由 WorldChunk 自动排队，服务端光照线程异步处理。
 *
 * <p>必须带上 {@link Block#SKIP_BLOCK_ADDED_CALLBACK}：否则恢复雕刻南瓜等方块时会走
 * {@code CarvedPumpkinBlock.trySpawnEntity} → {@code BlockPattern}，在
 * {@code CachedBlockPosition.getBlockState()} 为 null 时直接 NPE 崩服。
 * 这与命令方块/结构方块的“严格落位”语义一致。
 */
public final class ContainerBlockUpdateFlags {

  /**
   * 批量写入：同步客户端 + 强制落位 + 跳过 onBlockAdded/BE replaced/掉落，不逐格通知邻居。
   */
  public static final int BATCH =
      Block.NOTIFY_LISTENERS | Block.FORCE_STATE_AND_SKIP_CALLBACKS_AND_DROPS;

  private ContainerBlockUpdateFlags() {}
}
