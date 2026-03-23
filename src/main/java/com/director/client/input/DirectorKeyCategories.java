package com.director.client.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;

/**
 * 1.21.10+：KeyBinding 分类改为 {@link KeyBinding.Category}（由 Identifier 注册，翻译 key 为 key.category.<id>）。
 * 这里集中创建分类，避免多处重复 create() 导致重复注册异常。
 */
public final class DirectorKeyCategories {

    public static final KeyBinding.Category GENERAL =
            KeyBinding.Category.create(Identifier.of("director", "general"));

    public static final KeyBinding.Category SELECTION =
            KeyBinding.Category.create(Identifier.of("director", "selection"));

    private DirectorKeyCategories() {}
}


