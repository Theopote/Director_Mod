package com.chronoblocks.ui.adapters.impl;

import com.chronoblocks.ui.adapters.BasePropertyAdapter;

/**
 * 📁 ProjectAdapter - 项目属性适配器
 *
 * <p>为 ChronoBlocks 项目对象提供通用属性编辑器。
 *
 * <h3>✨ 支持的属性：</h3>
 * <ul>
 *     <li>📝 项目信息：名称、描述、作者</li>
 *     <li>⚙️ 项目设置：版本、创建/修改时间</li>
 *     <li>📊 项目统计：容器数、轨道数、关键帧数</li>
 * </ul>
 *
 * <h3>📖 使用说明：</h3>
 * <p>此适配器使用反射机制，支持任何包含 "Project" 名称的对象。
 * 当具体的 Project 类定义完成后，可以替换为类型安全的实现。
 *
 * @author ChronoBlocks Team
 * @version 4.5
 * @since 4.5
 */
public class ProjectAdapter extends BasePropertyAdapter<Object> {

    @Override
    protected boolean doRenderProperties(Object project) {
        boolean changed = false;

        // ========== 项目信息 ==========
        renderSectionHeader("📝 项目信息");
        
        // 项目名称
        String name = getStringProperty(project, "name", "getName");
        if (name != null) {
            changed |= renderTextProperty("项目名称",
                    () -> name,
                    value -> setStringProperty(project, "name", "setName", value),
                    256);
        } else {
            renderReadOnlyText("项目名称", "未设置");
        }

        // 项目描述
        String description = getStringProperty(project, "description", "getDescription");
        if (description != null) {
            changed |= renderTextProperty("描述",
                    () -> description,
                    value -> setStringProperty(project, "description", "setDescription", value),
                    512);
        }

        // 作者
        String author = getStringProperty(project, "author", "getAuthor");
        if (author != null) {
            renderReadOnlyText("作者", author);
        }

        // ========== 项目设置 ==========
        renderSectionHeader("⚙️ 项目设置");
        
        // 版本
        String version = getStringProperty(project, "version", "getVersion");
        if (version != null) {
            renderReadOnlyText("版本", version);
        } else {
            renderReadOnlyText("版本", "1.0.0");
        }

        // 创建时间
        Long createdTime = getLongProperty(project, "createdTime", "getCreatedTime");
        if (createdTime != null && createdTime > 0) {
            renderReadOnlyText("创建时间", formatTimestamp(createdTime));
        }

        // 修改时间
        Long modifiedTime = getLongProperty(project, "modifiedTime", "getModifiedTime");
        if (modifiedTime != null && modifiedTime > 0) {
            renderReadOnlyText("修改时间", formatTimestamp(modifiedTime));
        }

        // ========== 项目统计 ==========
        renderSectionHeader("📊 项目统计");
        
        // 容器数
        Integer containerCount = getIntProperty(project, "containerCount", "getContainerCount");
        if (containerCount != null) {
            renderReadOnlyText("容器数量", String.valueOf(containerCount));
        }

        // 轨道数
        Integer trackCount = getIntProperty(project, "trackCount", "getTrackCount");
        if (trackCount != null) {
            renderReadOnlyText("轨道数量", String.valueOf(trackCount));
        }

        // 关键帧数
        Integer keyframeCount = getIntProperty(project, "keyframeCount", "getKeyframeCount");
        if (keyframeCount != null) {
            renderReadOnlyText("关键帧数量", String.valueOf(keyframeCount));
        }

        // ========== 项目路径 ==========
        String projectPath = getStringProperty(project, "projectPath", "getProjectPath");
        if (projectPath != null && !projectPath.isEmpty()) {
            renderSectionHeader("📂 文件");
            renderReadOnlyText("项目路径", projectPath);
        }

        return changed;
    }

    // ==================== 反射辅助方法 ====================

    /**
     * 获取字符串属性
     */
    private String getStringProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result != null ? result.toString() : null;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * 设置字符串属性
     */
    private void setStringProperty(Object obj, String fieldName, String setterName, String value) {
        try {
            var method = obj.getClass().getMethod(setterName, String.class);
            method.invoke(obj, value);
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
            } catch (Exception ex) {
                logger.warn("[ProjectAdapter] 无法设置属性: {}", fieldName);
            }
        }
    }

    /**
     * 获取整数属性
     */
    private Integer getIntProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            if (result instanceof Integer) {
                return (Integer) result;
            } else if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                if (result instanceof Integer) {
                    return (Integer) result;
                } else if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            } catch (Exception ex) {
                // 忽略
            }
        }
        return null;
    }

    /**
     * 获取长整数属性
     */
    private Long getLongProperty(Object obj, String fieldName, String getterName) {
        try {
            var method = obj.getClass().getMethod(getterName);
            Object result = method.invoke(obj);
            if (result instanceof Long) {
                return (Long) result;
            } else if (result instanceof Number) {
                return ((Number) result).longValue();
            }
        } catch (Exception e) {
            try {
                var field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                if (result instanceof Long) {
                    return (Long) result;
                } else if (result instanceof Number) {
                    return ((Number) result).longValue();
                }
            } catch (Exception ex) {
                // 忽略
            }
        }
        return null;
    }

    /**
     * 格式化时间戳
     */
    private String formatTimestamp(long timestamp) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new java.util.Date(timestamp));
    }

    @Override
    public void onPropertyChanged(Object project) {
        super.onPropertyChanged(project);
        
        // 更新修改时间
        try {
            var method = project.getClass().getMethod("setModifiedTime", long.class);
            method.invoke(project, System.currentTimeMillis());
        } catch (Exception e) {
            // 忽略
        }
        
        logger.debug("[ProjectAdapter] 项目属性已更新");
    }

    @Override
    public boolean validate(Object project) {
        // 验证项目名称不为空
        String name = getStringProperty(project, "name", "getName");
        if (name == null || name.trim().isEmpty()) {
            logger.warn("[ProjectAdapter] 项目名称为空");
            return false;
        }

        return true;
    }

    @Override
    public Class<Object> getTargetType() {
        return Object.class; // 使用 Object 以支持各种项目类型
    }

    @Override
    public boolean supports(Object obj) {
        if (obj == null) {
            return false;
        }
        
        // 检查类名是否包含 "Project"
        String className = obj.getClass().getSimpleName().toLowerCase();
        return className.contains("project");
    }

    @Override
    public int getPriority() {
        return 50; // 中等优先级（通用适配器）
    }

    @Override
    public String getDisplayName() {
        return "📁 项目编辑器";
    }

    @Override
    public String getDescription() {
        return "用于编辑项目的基本信息和设置";
    }
}

