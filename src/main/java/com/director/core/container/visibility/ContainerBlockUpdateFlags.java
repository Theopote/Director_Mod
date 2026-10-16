package com.director.core.container.visibility;

import net.minecraft.block.Block;

/**
 * 容器批量世界方块写入时使用的 {@code setBlockState} 标志。
 *
 * <p>批量隐藏/显示时跳过逐方块 {@code NOTIFY_NEIGHBORS}，光照检查仍由 WorldChunk 自动排队，服务端光照线程异步处理。
 */
public final class ContainerBlockUpdateFlags {

  /** 批量写入：同步客户端 + 强制落位，不逐格通知邻居。 */
  public static final int BATCH = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

  private ContainerBlockUpdateFlags() {}
}
