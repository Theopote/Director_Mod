package com.director.i18n;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

/**
 * Director 文案翻译：委托 Minecraft 标准 lang 资源（{@code assets/director/lang/*.json}）。
 * <p>
 * ImGui 面板在渲染时调用 {@link #translate(String)} 获取当前语言字符串；
 * 进入聊天/物品栏等原生 {@link Text} 管线时使用 {@link #translatable(String)}。
 * <p>
 * 语言切换由 Minecraft 资源重载自动处理，无需自定义 loader。
 */
public final class DirectorI18n {

    private DirectorI18n() {
    }

    public static String translate(String key) {
        return I18n.translate(key);
    }

    public static String translate(String key, Object... args) {
        return String.format(I18n.translate(key), args);
    }

    public static Text translatable(String key) {
        return Text.translatable(key);
    }

    public static Text translatable(String key, Object... args) {
        return Text.translatable(key, args);
    }
}
