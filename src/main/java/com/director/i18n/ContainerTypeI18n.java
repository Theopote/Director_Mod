package com.director.i18n;

import com.director.core.container.ContainerType;

/**
 * 容器类型名称的 lang 键解析（{@code gui.director.container.type.{type}.*}）。
 */
public final class ContainerTypeI18n {

    private static final String PREFIX = "gui.director.container.type.";

    private ContainerTypeI18n() {
    }

    public static String nameKey(ContainerType type) {
        return PREFIX + type.name().toLowerCase() + ".name";
    }

    public static String name(ContainerType type) {
        return DirectorI18n.translate(nameKey(type));
    }

    public static String iconAndName(ContainerType type) {
        return type.getIcon() + " " + name(type);
    }
}
