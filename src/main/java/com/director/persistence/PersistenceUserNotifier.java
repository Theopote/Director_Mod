package com.director.persistence;

import com.director.i18n.DirectorI18n;
import com.director.i18n.DirectorLang;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

/**
 * 将持久化保存结果即时通知给玩家（聊天栏）。
 */
public final class PersistenceUserNotifier {

    private PersistenceUserNotifier() {
    }

    public static void notifySaveFailureIfNeeded(@Nullable MinecraftClient client) {
        if (client == null || client.player == null || !PersistenceSaveStatus.hasSaveFailure()) {
            return;
        }
        String reason = PersistenceSaveStatus.getLastFailureReason();
        if (reason == null || reason.isBlank()) {
            reason = DirectorI18n.translate(DirectorLang.Status.SAVE_FAILED_ADVISORY, "unknown");
        } else {
            reason = DirectorI18n.translate(DirectorLang.Status.SAVE_FAILED_ADVISORY, reason);
        }
        client.player.sendMessage(net.minecraft.text.Text.literal(reason), false);
    }
}
