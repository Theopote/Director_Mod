package com.chronoblocks.plugin;

/**
 * PluginAPI - Extension points for ChronoBlocks plugins.
 */
public interface PluginAPI {
    String getName();

    default void onLoad() {}
}
