package com.director.timeline.playback;

import net.minecraft.client.MinecraftClient;

/**
 * 时间线播放控制器的并发契约。
 *
 * <h3>设计决策（全项目统一）</h3>
 * <ul>
 *   <li><strong>写入</strong>：所有变更控制器状态的 public API 使用 {@code synchronized}（或等价锁），
 *       在锁内更新字段并刷新 {@code volatile cachedHasOverride}，再 publish 到 {@code *RenderOverride}。</li>
 *   <li><strong>读取（热路径）</strong>：{@code hasOverride()} 可无锁读 {@code cachedHasOverride}；
 *       需要 sample 组合字段时由渲染快照提供一致性。</li>
 *   <li><strong>读取（UI / 调试）</strong>：{@code getCurrentSample()} 等走 synchronized。</li>
 *   <li><strong>主线程</strong>：常规 UI + {@code PlaybackOrchestrator} tick 在客户端渲染帧内顺序执行；
 *       导出等跨线程路径必须通过 {@link MinecraftClient#execute(Runnable)} 回到客户端线程。</li>
 * </ul>
 */
public final class TimelinePlaybackThreading {

    private TimelinePlaybackThreading() {
    }

    /**
     * 开发期断言：当前在 Minecraft 客户端线程。非 client 线程调用控制器写入 API 时应尽快 schedule 到 client。
     */
    public static void assertClientThread() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            assert client.isOnThread() : "Timeline playback controller mutation off client thread";
        }
    }
}
