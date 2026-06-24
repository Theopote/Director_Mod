package com.director.client;

import com.director.config.DirectorConfig;
import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Director 客户端 JVM 堆内存建议（非强制）。
 *
 * <p>模组无法单独向操作系统申请内存；{@code -Xmx} 由启动器/Gradle 决定整进程堆上限。
 * 条件允许时推荐 8G+；堆较小时仍可正常使用，大容器隐藏/显示可能更卡。
 */
public final class DirectorMemoryRequirements {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorMemoryRequirements.class);

    private static volatile boolean joinAdvisoryShown;
    private static volatile boolean openUiAdvisoryShown;

    private DirectorMemoryRequirements() {
    }

    public static int getRecommendedHeapMb() {
        try {
            return Math.max(512, DirectorConfig.getConfig().performance.recommendedClientHeapMb);
        } catch (Exception ignored) {
            return 8192;
        }
    }

    public static long getMaxHeapMb() {
        return Runtime.getRuntime().maxMemory() / (1024L * 1024L);
    }

    public static boolean isBelowRecommended() {
        return getMaxHeapMb() < getRecommendedHeapMb();
    }

    public static void onClientStarted(MinecraftClient client) {
        long maxMb = getMaxHeapMb();
        int recommendedMb = getRecommendedHeapMb();
        if (!isBelowRecommended()) {
            LOGGER.info("[Director] JVM 堆内存: {} MB（推荐 ≥ {} MB）", maxMb, recommendedMb);
            return;
        }
        LOGGER.warn(
                "[Director] JVM 堆内存低于推荐值: {} MB < 推荐 {} MB。可继续使用；处理大容器时可能更卡。可在启动器添加 -Xmx8G。",
                maxMb, recommendedMb);
    }

    /** 进世界时一次性软提示（不阻止游戏）。 */
    public static void onPlayerJoin(MinecraftClient client) {
        if (joinAdvisoryShown || client == null || !isBelowRecommended()) {
            return;
        }
        joinAdvisoryShown = true;
        sendAdvisory(client);
    }

    /** 打开 Director 界面前一次性软提示（始终允许打开）。 */
    public static void adviseIfLowHeap(MinecraftClient client) {
        if (openUiAdvisoryShown || client == null || !isBelowRecommended()) {
            return;
        }
        openUiAdvisoryShown = true;
        sendAdvisory(client);
    }

    private static void sendAdvisory(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        long maxMb = getMaxHeapMb();
        int recommendedMb = getRecommendedHeapMb();
        Text message = DirectorI18n.translatable(
                DirectorLang.Memory.LOW_HEAP_ADVISORY,
                maxMb,
                recommendedMb);
        client.player.sendMessage(message, false);
        Text hint = DirectorI18n.translatable(DirectorLang.Memory.LOW_HEAP_ADVISORY_HINT);
        client.player.sendMessage(hint, false);
    }
}
