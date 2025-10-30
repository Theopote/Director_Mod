package com.chronoblocks.core.selection.filter;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.*;

/**
 * 选择过滤器预设管理器
 * 保存过滤器预设至 JSON（config/chronoblocks/selection_presets.json）
 * 
 * @author ChronoBlocks Team
 * @version 1.0
 */
public final class SelectionPresetManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SelectionPresetManager.class);
    private static final Path FILE = Paths.get("config/chronoblocks/selection_presets.json");

    /**
     * 保存过滤器预设
     */
    public static void savePreset(String name, List<SelectionFilter> filters) {
        try {
            JsonObject root = Files.exists(FILE) 
                ? JsonParser.parseString(Files.readString(FILE)).getAsJsonObject()
                : new JsonObject();
            
            JsonArray arr = new JsonArray();
            for (SelectionFilter f : filters) {
                // 简化的序列化 - 实际应用中可能需要更复杂的序列化
                arr.add(f.getClass().getSimpleName());
            }
            
            root.add(name, arr);
            
            // 确保目录存在
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
            
            LOGGER.info("[SelectionPresetManager] 保存预设: {} ({} 个过滤器)", name, filters.size());
        } catch (Exception e) {
            LOGGER.error("[SelectionPresetManager] 保存预设失败: {}", name, e);
        }
    }

    /**
     * 加载所有预设
     */
    public static Map<String, List<String>> loadPresets() {
        if (!Files.exists(FILE)) {
            return new LinkedHashMap<>();
        }
        
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            Map<String, List<String>> map = new LinkedHashMap<>();
            
            for (var e : root.entrySet()) {
                List<String> arr = new ArrayList<>();
                e.getValue().getAsJsonArray().forEach(j -> arr.add(j.getAsString()));
                map.put(e.getKey(), arr);
            }
            
            LOGGER.info("[SelectionPresetManager] 加载了 {} 个预设", map.size());
            return map;
        } catch (Exception e) {
            LOGGER.error("[SelectionPresetManager] 加载预设失败", e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 删除预设
     */
    public static void deletePreset(String name) {
        try {
            if (!Files.exists(FILE)) return;
            
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            if (root.has(name)) {
                root.remove(name);
                Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
                LOGGER.info("[SelectionPresetManager] 删除预设: {}", name);
            }
        } catch (Exception e) {
            LOGGER.error("[SelectionPresetManager] 删除预设失败: {}", name, e);
        }
    }

    /**
     * 检查预设是否存在
     */
    public static boolean hasPreset(String name) {
        try {
            if (!Files.exists(FILE)) return false;
            
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            return root.has(name);
        } catch (Exception e) {
            LOGGER.error("[SelectionPresetManager] 检查预设失败: {}", name, e);
            return false;
        }
    }

    /**
     * 获取所有预设名称
     */
    public static Set<String> getPresetNames() {
        return loadPresets().keySet();
    }
}
