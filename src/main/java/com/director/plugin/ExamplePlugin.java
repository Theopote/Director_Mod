package com.director.plugin;

/**
 * ExamplePlugin - Demonstrates how a plugin could implement the API.
 */
public class ExamplePlugin implements PluginAPI {
    @Override
    public String getName() {
        return "ExamplePlugin";
    }
}
