package com.director.client.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;

/**
 * Director 在 Minecraft 控制设置中的按键分类。
 * <p>
 * 关闭 Director 界面后，与 Minecraft 集成的 Director 快捷键仅有「打开 Director」
 * （见 {@link com.director.client.DirectorClient} 注册的 {@code key.director.open_ui}）。
 * Director 界面内的编辑快捷键由 {@link com.director.ui.input.ShortcutDispatcher} 处理，
 * 不会注册为 Minecraft {@link KeyBinding}。
 */
public final class DirectorKeyCategories {

    public static final KeyBinding.Category GENERAL =
            KeyBinding.Category.create(Identifier.of("director", "general"));

    private DirectorKeyCategories() {}
}
