package com.chronoblocks.client.settings;

/**
 * RenderSettings - 全局渲染设置中心（单例）
 * 统一控制选区渲染、工具预览、容器渲染与部分性能选项。
 */
public final class RenderSettings {

    private static volatile RenderSettings INSTANCE;

    public static RenderSettings get() {
        if (INSTANCE == null) {
            synchronized (RenderSettings.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RenderSettings();
                }
            }
        }
        return INSTANCE;
    }

    private RenderSettings() {}

    // ================= Selection (选区) =================
    public boolean selection_showOutline = true;      // 显示线框
    public boolean selection_showFill = true;          // 显示填充
    public boolean selection_enableGreedy = true;     // 贪婪合并（同时控制填充和线框）
    public boolean selection_showBoundingBox = true;  // 显示包围盒（默认全勾选）
    public boolean selection_bboxShowFill = true;      // 包围盒是否显示半透明填充（面）
    public int selection_greedyThreshold = 1;
    public int selection_maxRenderDistance = 64;
    public boolean selection_enableFrustumCull = true;
    public boolean selection_enableOcclusionCull = false;
    // 样式（填充透明度默认 100%；线宽默认 1，UI 不暴露）
    public float[] selection_outlineColor = new float[]{0.05f, 0.7f, 1.0f, 0.95f};
    public float[] selection_fillColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};  // alpha=100%
    public float selection_outlineWidth = 1.0f;
    public float selection_highlightIntensity = 1.0f;
    // 包围盒样式（独立于选区线框/填充）
    public float[] selection_bboxOutlineColor = new float[]{0.20f, 0.90f, 0.40f, 0.90f};
    public float[] selection_bboxFillColor = new float[]{0.20f, 0.90f, 0.40f, 0.18f};
    public float selection_bboxOutlineWidth = 1.0f;  // 固定 1，UI 不暴露

    // ================= Tool Preview (工具预览) =================
    public boolean tool_showOutline = true;      // 显示线框
    public boolean tool_showFill = true;          // 显示填充
    public boolean tool_enableGreedy = true;     // 贪婪合并（同时控制填充和线框）
    // 注意：工具预览不需要包围盒；线宽默认 1，UI 不暴露；面填充透明度默认 60%
    public float[] tool_outlineColor = new float[]{0.2f, 0.6f, 1.0f, 0.7f};
    public float[] tool_fillColor = new float[]{1.0f, 0.8f, 0.0f, 0.6f};  // alpha=60%
    public float tool_outlineWidth = 1.0f;

    // ================= Container (容器) =================
    public boolean container_showOutline = true;      // 显示线框
    public boolean container_showFill = false;        // 显示填充（默认不勾选）
    public boolean container_enableGreedy = true;     // 贪婪合并（同时控制填充和线框）
    public boolean container_showBoundingBox = false; // 显示包围盒（默认不勾选）
    public float container_outlineWidth = 1.0f;       // 线宽默认 1，UI 不暴露
    public float[] container_outlineColor = new float[]{0.4f, 1.0f, 0.5f, 0.9f};
    public float[] container_fillColor = new float[]{0.4f, 1.0f, 0.5f, 0.2f};

    // ================= Global Mesh / Performance =================
    public boolean useSharedMeshCache = true;
    public boolean fallbackHugeArea = true;
    public int hugeAreaThreshold = 15000;
}