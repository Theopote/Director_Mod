package com.chronoblocks.ui.timeline.model.camera;

import java.util.Objects;

/**
 * CameraViewportProfile - 摄像机视图意图配置
 * 
 * <p>用于在动画制作阶段就锁定构图规则，让创作者在 Timeline / Preview 中
 * 看到与最终发布画幅一致的裁切和安全区提示。
 *
 * <h3>关键设计原则</h3>
 * <ul>
 *   <li>摄像机负责：画幅比例 / 构图安全区 / 参考线 / 预览分辨率（仅用于预览感知）</li>
 *   <li>导出负责：最终输出像素（1920x1080 / 1080x1920 / 4K 等）与编码参数</li>
 * </ul>
 *
 * <p>说明：
 * <ul>
 *   <li>previewResolution 仅用于 UI 显示与预览缩放策略参考，不应强制绑定最终导出尺寸</li>
 *   <li>aspectRatio 决定实际"裁切规则"，与导出像素可解耦（导出可同画幅多分辨率）</li>
 * </ul>
 *
 * @author ChronoBlocks Team
 * @version 1.0
 */
public class CameraViewportProfile {

    /** Profile 的显示名称（用于 UI 下拉框/属性面板） */
    private String displayName = "16:9 (Default)";

    /** 画幅比例模式（预设或自定义） */
    private AspectRatioMode aspectMode = AspectRatioMode.RATIO_16_9;

    /** 自定义画幅比例（仅当 aspectMode == CUSTOM 生效） */
    private int customAspectW = 16;
    private int customAspectH = 9;

    /**
     * 预览分辨率（仅用于 UI/预览感知，不等于导出分辨率）
     * 例如：1920x1080、1080x1920、3840x2160
     *
     * 为了兼容"不设置预览分辨率"的情况，默认使用 0 表示未指定。
     */
    private int previewWidth = 1920;
    private int previewHeight = 1080;

    /** 预览缩放策略：在编辑器窗口中如何呈现该画幅（FIT=留黑边，FILL=裁切填满） */
    private PreviewScalePolicy previewScalePolicy = PreviewScalePolicy.FIT;

    /** 是否显示 Letterbox/Mask（黑边遮罩），用于提示实际画幅裁切区域 */
    private boolean showLetterboxMask = true;

    /** Letterbox 遮罩透明度（0~1） */
    private float letterboxAlpha = 0.75f;

    /** 是否显示中心十字参考线 */
    private boolean showCenterCross = false;

    /** 是否显示三分法网格 */
    private boolean showRuleOfThirds = false;

    /** 是否显示安全区（Title/Action Safe） */
    private boolean showSafeAreas = true;

    /**
     * Action Safe：动作安全区比例（0~1），例如 0.90 表示保留 90% 中央区域
     * UI 表达可做成百分比滑条：90%
     */
    private float actionSafe = 0.90f;

    /**
     * Title Safe：字幕安全区比例（0~1），例如 0.80 表示保留 80% 中央区域
     * 一般 titleSafe <= actionSafe（更严格）
     */
    private float titleSafe = 0.80f;

    /** 是否启用校验并自动纠正不合法值（建议保持 true，避免 UI/持久化写坏） */
    private boolean autoClamp = true;

    // -------------------------------------------------------------------------
    // 枚举定义
    // -------------------------------------------------------------------------

    public enum AspectRatioMode {
        RATIO_16_9(16, 9, "16:9 (横屏 · YouTube / TV)"),
        RATIO_9_16(9, 16, "9:16 (竖屏 · Shorts / Reels / TikTok)"),
        RATIO_1_1(1, 1, "1:1 (正方形)"),
        RATIO_4_3(4, 3, "4:3 (传统电视)"),
        RATIO_3_2(3, 2, "3:2 (照片)"),
        RATIO_21_9(21, 9, "21:9 (超宽银幕)"),
        CUSTOM(0, 0, "自定义 (W:H)");

        private final int presetW;
        private final int presetH;
        private final String displayName;

        AspectRatioMode(int presetW, int presetH, String displayName) {
            this.presetW = presetW;
            this.presetH = presetH;
            this.displayName = displayName;
        }

        public boolean isCustom() {
            return this == CUSTOM;
        }

        public int getPresetW() {
            return presetW;
        }

        public int getPresetH() {
            return presetH;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PreviewScalePolicy {
        /** 保持画幅完整呈现（留黑边/留空） */
        FIT,
        /** 填满窗口（可能裁切） */
        FILL
    }

    // -------------------------------------------------------------------------
    // 构造与工厂方法（预设）
    // -------------------------------------------------------------------------

    public CameraViewportProfile() {
        clampIfNeeded();
    }

    public CameraViewportProfile(String displayName, AspectRatioMode aspectMode) {
        this.displayName = displayName;
        this.aspectMode = aspectMode == null ? AspectRatioMode.RATIO_16_9 : aspectMode;
        clampIfNeeded();
    }

    /** 16:9 默认（常用于电脑/电视/YouTube 横屏） */
    public static CameraViewportProfile preset16x9() {
        CameraViewportProfile p = new CameraViewportProfile("16:9 (Horizontal)", AspectRatioMode.RATIO_16_9);
        p.previewWidth = 1920;
        p.previewHeight = 1080;
        return p;
    }

    /** 9:16 默认（常用于 Shorts/Reels/TikTok 竖屏） */
    public static CameraViewportProfile preset9x16() {
        CameraViewportProfile p = new CameraViewportProfile("9:16 (Vertical)", AspectRatioMode.RATIO_9_16);
        p.previewWidth = 1080;
        p.previewHeight = 1920;
        return p;
    }

    /** 1:1 默认（方形） */
    public static CameraViewportProfile preset1x1() {
        CameraViewportProfile p = new CameraViewportProfile("1:1 (Square)", AspectRatioMode.RATIO_1_1);
        p.previewWidth = 1080;
        p.previewHeight = 1080;
        return p;
    }

    /** 21:9 默认（超宽银幕） */
    public static CameraViewportProfile preset21x9() {
        CameraViewportProfile p = new CameraViewportProfile("21:9 (Cinematic)", AspectRatioMode.RATIO_21_9);
        p.previewWidth = 2560;
        p.previewHeight = 1080;
        p.titleSafe = 0.85f;
        p.actionSafe = 0.92f;
        return p;
    }

    /** 自定义比例 */
    public static CameraViewportProfile custom(String displayName, int w, int h) {
        CameraViewportProfile p = new CameraViewportProfile(
                displayName == null ? "Custom" : displayName,
                AspectRatioMode.CUSTOM
        );
        p.customAspectW = w;
        p.customAspectH = h;
        p.clampIfNeeded();
        return p;
    }

    // -------------------------------------------------------------------------
    // 核心计算
    // -------------------------------------------------------------------------

    /**
     * 获取有效的画幅比例（width/height）。
     * - 预设模式：使用 presetW/presetH
     * - 自定义模式：使用 customAspectW/customAspectH
     */
    public float getAspectRatio() {
        int w = getEffectiveAspectW();
        int h = getEffectiveAspectH();
        if (h <= 0) return 16f / 9f;
        return (float) w / (float) h;
    }

    public int getEffectiveAspectW() {
        if (aspectMode != null && !aspectMode.isCustom()) {
            return aspectMode.getPresetW();
        }
        return Math.max(1, customAspectW);
    }

    public int getEffectiveAspectH() {
        if (aspectMode != null && !aspectMode.isCustom()) {
            return aspectMode.getPresetH();
        }
        return Math.max(1, customAspectH);
    }

    /**
     * 是否声明了预览分辨率（仅提示/预览用途）
     */
    public boolean hasPreviewResolution() {
        return previewWidth > 0 && previewHeight > 0;
    }

    /**
     * 预览分辨率的像素纵横比（若未设置则返回画幅比例）
     */
    public float getPreviewResolutionAspectRatio() {
        if (!hasPreviewResolution()) return getAspectRatio();
        return (float) previewWidth / (float) previewHeight;
    }

    // -------------------------------------------------------------------------
    // 校验/纠正
    // -------------------------------------------------------------------------

    public void clampIfNeeded() {
        if (!autoClamp) return;

        if (displayName == null || displayName.isBlank()) {
            displayName = "Viewport Profile";
        }

        if (aspectMode == null) {
            aspectMode = AspectRatioMode.RATIO_16_9;
        }

        // 自定义比例下：保证合法
        customAspectW = Math.max(1, customAspectW);
        customAspectH = Math.max(1, customAspectH);

        // 预览分辨率：允许为 0（未设置），但不能为负
        previewWidth = Math.max(0, previewWidth);
        previewHeight = Math.max(0, previewHeight);

        // 遮罩透明度 0~1
        letterboxAlpha = clamp01(letterboxAlpha);

        // 安全区：0~1，并确保 titleSafe <= actionSafe（title 更严格）
        actionSafe = clamp01(actionSafe);
        titleSafe = clamp01(titleSafe);

        // 常见约束：titleSafe 不应大于 actionSafe
        if (titleSafe > actionSafe) {
            float t = titleSafe;
            titleSafe = actionSafe;
            actionSafe = t;
        }

        // 安全区建议最小不要太离谱（避免 UI 一拉就看不见画面）
        actionSafe = Math.max(0.50f, actionSafe);
        titleSafe = Math.max(0.50f, titleSafe);

        if (previewScalePolicy == null) {
            previewScalePolicy = PreviewScalePolicy.FIT;
        }
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        return Math.min(v, 1f);
    }

    // -------------------------------------------------------------------------
    // 复制 / 对比（便于撤销/redo、脏标记、持久化差异比较）
    // -------------------------------------------------------------------------

    public CameraViewportProfile copy() {
        CameraViewportProfile c = new CameraViewportProfile();
        c.displayName = this.displayName;
        c.aspectMode = this.aspectMode;
        c.customAspectW = this.customAspectW;
        c.customAspectH = this.customAspectH;
        c.previewWidth = this.previewWidth;
        c.previewHeight = this.previewHeight;
        c.previewScalePolicy = this.previewScalePolicy;
        c.showLetterboxMask = this.showLetterboxMask;
        c.letterboxAlpha = this.letterboxAlpha;
        c.showCenterCross = this.showCenterCross;
        c.showRuleOfThirds = this.showRuleOfThirds;
        c.showSafeAreas = this.showSafeAreas;
        c.actionSafe = this.actionSafe;
        c.titleSafe = this.titleSafe;
        c.autoClamp = this.autoClamp;
        c.clampIfNeeded();
        return c;
    }

    @Override
    public String toString() {
        return "CameraViewportProfile{" +
                "displayName='" + displayName + '\'' +
                ", aspectMode=" + aspectMode +
                ", aspect=" + getEffectiveAspectW() + ":" + getEffectiveAspectH() +
                ", preview=" + (hasPreviewResolution() ? (previewWidth + "x" + previewHeight) : "unset") +
                ", safeAreas=" + (showSafeAreas ? ("A:" + actionSafe + ", T:" + titleSafe) : "off") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CameraViewportProfile)) return false;
        CameraViewportProfile that = (CameraViewportProfile) o;
        return customAspectW == that.customAspectW
                && customAspectH == that.customAspectH
                && previewWidth == that.previewWidth
                && previewHeight == that.previewHeight
                && showLetterboxMask == that.showLetterboxMask
                && Float.compare(that.letterboxAlpha, letterboxAlpha) == 0
                && showCenterCross == that.showCenterCross
                && showRuleOfThirds == that.showRuleOfThirds
                && showSafeAreas == that.showSafeAreas
                && Float.compare(that.actionSafe, actionSafe) == 0
                && Float.compare(that.titleSafe, titleSafe) == 0
                && autoClamp == that.autoClamp
                && Objects.equals(displayName, that.displayName)
                && aspectMode == that.aspectMode
                && previewScalePolicy == that.previewScalePolicy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(displayName, aspectMode, customAspectW, customAspectH,
                previewWidth, previewHeight, previewScalePolicy,
                showLetterboxMask, letterboxAlpha, showCenterCross, showRuleOfThirds,
                showSafeAreas, actionSafe, titleSafe, autoClamp);
    }

    // -------------------------------------------------------------------------
    // Getter / Setter（保持简单，方便现有属性面板适配器接入）
    // -------------------------------------------------------------------------

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        clampIfNeeded();
    }

    public AspectRatioMode getAspectMode() {
        return aspectMode;
    }

    public void setAspectMode(AspectRatioMode aspectMode) {
        this.aspectMode = aspectMode;
        clampIfNeeded();
    }

    public int getCustomAspectW() {
        return customAspectW;
    }

    public void setCustomAspectW(int customAspectW) {
        this.customAspectW = customAspectW;
        clampIfNeeded();
    }

    public int getCustomAspectH() {
        return customAspectH;
    }

    public void setCustomAspectH(int customAspectH) {
        this.customAspectH = customAspectH;
        clampIfNeeded();
    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public void setPreviewWidth(int previewWidth) {
        this.previewWidth = previewWidth;
        clampIfNeeded();
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    public void setPreviewHeight(int previewHeight) {
        this.previewHeight = previewHeight;
        clampIfNeeded();
    }

    public void setPreviewResolution(int w, int h) {
        this.previewWidth = w;
        this.previewHeight = h;
        clampIfNeeded();
    }

    public PreviewScalePolicy getPreviewScalePolicy() {
        return previewScalePolicy;
    }

    public void setPreviewScalePolicy(PreviewScalePolicy previewScalePolicy) {
        this.previewScalePolicy = previewScalePolicy;
        clampIfNeeded();
    }

    public boolean isShowLetterboxMask() {
        return showLetterboxMask;
    }

    public void setShowLetterboxMask(boolean showLetterboxMask) {
        this.showLetterboxMask = showLetterboxMask;
        clampIfNeeded();
    }

    public float getLetterboxAlpha() {
        return letterboxAlpha;
    }

    public void setLetterboxAlpha(float letterboxAlpha) {
        this.letterboxAlpha = letterboxAlpha;
        clampIfNeeded();
    }

    public boolean isShowCenterCross() {
        return showCenterCross;
    }

    public void setShowCenterCross(boolean showCenterCross) {
        this.showCenterCross = showCenterCross;
    }

    public boolean isShowRuleOfThirds() {
        return showRuleOfThirds;
    }

    public void setShowRuleOfThirds(boolean showRuleOfThirds) {
        this.showRuleOfThirds = showRuleOfThirds;
    }

    public boolean isShowSafeAreas() {
        return showSafeAreas;
    }

    public void setShowSafeAreas(boolean showSafeAreas) {
        this.showSafeAreas = showSafeAreas;
    }

    public float getActionSafe() {
        return actionSafe;
    }

    public void setActionSafe(float actionSafe) {
        this.actionSafe = actionSafe;
        clampIfNeeded();
    }

    public float getTitleSafe() {
        return titleSafe;
    }

    public void setTitleSafe(float titleSafe) {
        this.titleSafe = titleSafe;
        clampIfNeeded();
    }

    public boolean isAutoClamp() {
        return autoClamp;
    }

    public void setAutoClamp(boolean autoClamp) {
        this.autoClamp = autoClamp;
        clampIfNeeded();
    }
    
    // -------------------------------------------------------------------------
    // 向后兼容方法（适配现有代码）
    // -------------------------------------------------------------------------
    
    /**
     * 向后兼容：获取画幅比例类型（旧 API）
     * @deprecated 使用 getAspectMode() 代替
     */
    @Deprecated
    public AspectRatioType getAspectRatioType() {
        // 将新的 AspectRatioMode 映射到旧的 AspectRatioType
        return switch (aspectMode) {
            case RATIO_16_9 -> AspectRatioType.RATIO_16_9;
            case RATIO_9_16 -> AspectRatioType.RATIO_9_16;
            case RATIO_1_1 -> AspectRatioType.RATIO_1_1;
            default -> AspectRatioType.CUSTOM;
        };
    }
    
    /**
     * 向后兼容：设置画幅比例类型（旧 API）
     * @deprecated 使用 setAspectMode() 代替
     */
    @Deprecated
    public void setAspectRatioType(AspectRatioType type) {
        // 将旧的 AspectRatioType 映射到新的 AspectRatioMode
        this.aspectMode = switch (type) {
            case RATIO_16_9 -> AspectRatioMode.RATIO_16_9;
            case RATIO_9_16 -> AspectRatioMode.RATIO_9_16;
            case RATIO_1_1 -> AspectRatioMode.RATIO_1_1;
            case CUSTOM -> AspectRatioMode.CUSTOM;
        };
        clampIfNeeded();
    }
    
    /**
     * 向后兼容：获取自定义画幅比例宽度（旧 API 使用 float）
     * @deprecated 使用 getCustomAspectW() 代替
     */
    @Deprecated
    public float getCustomAspectRatioWidth() {
        return (float) customAspectW;
    }
    
    /**
     * 向后兼容：设置自定义画幅比例宽度（旧 API 使用 float）
     * @deprecated 使用 setCustomAspectW() 代替
     */
    @Deprecated
    public void setCustomAspectRatioWidth(float width) {
        this.customAspectW = Math.round(width);
        clampIfNeeded();
    }
    
    /**
     * 向后兼容：获取自定义画幅比例高度（旧 API 使用 float）
     * @deprecated 使用 getCustomAspectH() 代替
     */
    @Deprecated
    public float getCustomAspectRatioHeight() {
        return (float) customAspectH;
    }
    
    /**
     * 向后兼容：设置自定义画幅比例高度（旧 API 使用 float）
     * @deprecated 使用 setCustomAspectH() 代替
     */
    @Deprecated
    public void setCustomAspectRatioHeight(float height) {
        this.customAspectH = Math.round(height);
        clampIfNeeded();
    }
    
    /**
     * 向后兼容：获取预览分辨率宽度
     * @deprecated 使用 getPreviewWidth() 代替
     */
    @Deprecated
    public int getPreviewResolutionWidth() {
        return previewWidth;
    }
    
    /**
     * 向后兼容：设置预览分辨率宽度
     * @deprecated 使用 setPreviewWidth() 代替
     */
    @Deprecated
    public void setPreviewResolutionWidth(int width) {
        setPreviewWidth(width);
    }
    
    /**
     * 向后兼容：获取预览分辨率高度
     * @deprecated 使用 getPreviewHeight() 代替
     */
    @Deprecated
    public int getPreviewResolutionHeight() {
        return previewHeight;
    }
    
    /**
     * 向后兼容：设置预览分辨率高度
     * @deprecated 使用 setPreviewHeight() 代替
     */
    @Deprecated
    public void setPreviewResolutionHeight(int height) {
        setPreviewHeight(height);
    }
    
    /**
     * 向后兼容：是否显示标题安全区
     * @deprecated 使用 isShowSafeAreas() 和 getTitleSafe() 代替
     */
    @Deprecated
    public boolean isShowTitleSafeArea() {
        return showSafeAreas;
    }
    
    /**
     * 向后兼容：设置是否显示标题安全区
     * @deprecated 使用 setShowSafeAreas() 代替
     */
    @Deprecated
    public void setShowTitleSafeArea(boolean show) {
        this.showSafeAreas = show;
    }
    
    /**
     * 向后兼容：获取标题安全区边距（百分比）
     * @deprecated 使用 getTitleSafe() 代替
     */
    @Deprecated
    public float getTitleSafeAreaMargin() {
        return 1.0f - titleSafe; // 旧 API 是边距，新 API 是保留比例
    }
    
    /**
     * 向后兼容：设置标题安全区边距（百分比）
     * @deprecated 使用 setTitleSafe() 代替
     */
    @Deprecated
    public void setTitleSafeAreaMargin(float margin) {
        this.titleSafe = Math.max(0.0f, Math.min(1.0f, 1.0f - margin));
        clampIfNeeded();
    }
    
    /**
     * 向后兼容：是否显示动作安全区
     * @deprecated 使用 isShowSafeAreas() 和 getActionSafe() 代替
     */
    @Deprecated
    public boolean isShowActionSafeArea() {
        return showSafeAreas;
    }
    
    /**
     * 向后兼容：设置是否显示动作安全区
     * @deprecated 使用 setShowSafeAreas() 代替
     */
    @Deprecated
    public void setShowActionSafeArea(boolean show) {
        this.showSafeAreas = show;
    }
    
    /**
     * 向后兼容：获取动作安全区边距（百分比）
     * @deprecated 使用 getActionSafe() 代替
     */
    @Deprecated
    public float getActionSafeAreaMargin() {
        return 1.0f - actionSafe; // 旧 API 是边距，新 API 是保留比例
    }
    
    /**
     * 向后兼容：设置动作安全区边距（百分比）
     * @deprecated 使用 setActionSafe() 代替
     */
    @Deprecated
    public void setActionSafeAreaMargin(float margin) {
        this.actionSafe = Math.max(0.0f, Math.min(1.0f, 1.0f - margin));
        clampIfNeeded();
    }
    
    /**
     * 向后兼容：是否显示 Letterbox 预览
     * @deprecated 使用 isShowLetterboxMask() 代替
     */
    @Deprecated
    public boolean isShowLetterboxPreview() {
        return showLetterboxMask;
    }
    
    /**
     * 向后兼容：设置是否显示 Letterbox 预览
     * @deprecated 使用 setShowLetterboxMask() 代替
     */
    @Deprecated
    public void setShowLetterboxPreview(boolean show) {
        this.showLetterboxMask = show;
    }
    
    /**
     * 向后兼容的枚举（用于旧代码）
     * @deprecated 使用 AspectRatioMode 代替
     */
    @Deprecated
    public enum AspectRatioType {
        RATIO_16_9,
        RATIO_9_16,
        RATIO_1_1,
        CUSTOM
    }
}
