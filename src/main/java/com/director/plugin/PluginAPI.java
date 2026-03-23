package com.director.plugin;

/**
 * PluginAPI - Extension points for Director plugins.
 */
public interface PluginAPI {
    String getName();

    default void onLoad() {}
}
