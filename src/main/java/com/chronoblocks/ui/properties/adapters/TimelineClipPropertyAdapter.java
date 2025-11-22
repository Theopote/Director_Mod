package com.chronoblocks.ui.properties.adapters;

import com.chronoblocks.core.animation.ClipAnimationConfig;
import com.chronoblocks.core.container.AnimationMode;
import com.chronoblocks.core.container.Container;
import com.chronoblocks.core.container.ContainerManager;
import com.chronoblocks.core.animation.sequence.SequenceMode;
import com.chronoblocks.core.animation.sequence.SequenceParameters;
import com.chronoblocks.ui.properties.IPropertyAdapter;
import com.chronoblocks.ui.theme.UITheme;
import com.chronoblocks.ui.timeline.model.TimelineClip;
import com.chronoblocks.ui.theme.Icons;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import imgui.type.ImString;
import imgui.type.ImBoolean;

import java.util.UUID;

/**
 * {@link TimelineClip} 属性适配器（增强版）
 * 
 * <p>提供时间线片段的基础信息、动画配置编辑和特效预览。
 * 
 * <h3>功能特性</h3>
 * <ul>
 *   <li>基础信息：名称、时间范围</li>
 *   <li>动画配置：模式、时长、延迟、缓动曲线</li>
 *   <li>特效预览：动画模式效果说明和可视化</li>
 *   <li>容器信息：绑定的容器详情</li>
 * </ul>
 * 
 * @author ChronoBlocks Team
 * @version 2.0
 */
public class TimelineClipPropertyAdapter implements IPropertyAdapter<TimelineClip> {

    private final ImString nameBuffer = new ImString(128);
    private final ImString easingBuffer = new ImString(64);
    private final ImString orderingBuffer = new ImString(64);
    private final ImInt modeIndex = new ImInt();
    private final ImInt sequenceModeIndex = new ImInt();
    private final ImBoolean showPreview = new ImBoolean(false);
    private static final String[] MODE_LABELS = buildModeLabels();
    private static final String[] SEQUENCE_MODE_LABELS = buildSequenceModeLabels();

    private static String[] buildModeLabels() {
        AnimationMode[] modes = AnimationMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = modes[i].getDisplayName();
        }
        return labels;
    }
    
    private static String[] buildSequenceModeLabels() {
        SequenceMode[] modes = SequenceMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            labels[i] = modes[i].getDisplayName();
        }
        return labels;
    }

    @Override
    public boolean renderProperties(TimelineClip clip) {
        boolean modified = false;

        // ========== 标题 ==========
        UITheme.title(Icons.CLIPBOARD + " 时间线片段");

        // ========== [1] 基础设置 ==========
        if (ImGui.collapsingHeader("⚙️ 基础设置", ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderBasicSettings(clip);
        }

        // ========== [2] 容器信息 ==========
        UUID containerId = clip.getBoundContainerId();
        if (containerId != null) {
            if (ImGui.collapsingHeader("📦 容器信息")) {
                modified |= renderContainerInfo(clip, containerId);
            }
        }

        // ========== [3] 动画类型 ==========
        if (ImGui.collapsingHeader("🎬 动画类型", ImGuiTreeNodeFlags.DefaultOpen)) {
            modified |= renderAnimationType(clip);
        }

        // ========== [4] 顺序逻辑 ==========
        if (ImGui.collapsingHeader("📋 顺序逻辑")) {
            modified |= renderSequenceLogic(clip);
        }

        // ========== [5] 粒子与音效 ==========
        if (ImGui.collapsingHeader("✨ 粒子与音效")) {
            modified |= renderEffectsAndAudio(clip);
        }

        // ========== [6] 高级参数 ==========
        if (ImGui.collapsingHeader("🔧 高级参数")) {
            modified |= renderAdvancedSettings(clip);
        }

        // ========== [7] 其他属性 ==========
        if (ImGui.collapsingHeader("⚙️ 其他属性")) {
            modified |= renderOtherProperties(clip);
        }

        if (modified) {
            clip.getParentTrack(); // 访问以确保引用存在，未来可触发刷新
        }

        return modified;
    }
    
    /**
     * 渲染基础设置
     */
    private boolean renderBasicSettings(TimelineClip clip) {
        boolean modified = false;
        
        // 启用动画开关（预留，未来可以禁用动画）
        ImBoolean enabled = new ImBoolean(true); // 暂时固定为true
        ImGui.beginDisabled(); // 暂时禁用
        ImGui.checkbox("启用动画", enabled);
        ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("控制是否启用动画效果（功能开发中）");
        }
        
        UITheme.spacingSmall();
        
        // 名称
        nameBuffer.set(clip.getLabel());
        if (ImGui.inputText("名称", nameBuffer, ImGuiInputTextFlags.EnterReturnsTrue)) {
            String newName = nameBuffer.get().trim();
            if (!newName.isEmpty() && !newName.equals(clip.getLabel())) {
                clip.setLabel(newName);
                modified = true;
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("片段的显示名称");
        }

        UITheme.spacingSmall();

        // 时间控制
        float[] start = {clip.getStartTime()};
        if (ImGui.dragFloat("起始时间", start, 0.05f, 0.0f, 10_000f, "%.2fs")) {
            clip.setStartTime(Math.max(0.0f, start[0]));
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("片段在时间轴上的开始时间");
        }

        float[] length = {clip.getLength()};
        if (ImGui.dragFloat("持续时长", length, 0.05f, 0.05f, 10_000f, "%.2fs")) {
            clip.setLength(length[0]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("片段持续时间（方块从隐藏到完全出现的动画过程）");
        }

        float endTime = clip.getEndTime();
        ImGui.textDisabled(String.format("结束时间: %.2fs", endTime));
        
        UITheme.spacingSmall();
        
        // 整体延迟
        ClipAnimationConfig config = clip.getAnimationConfig();
        float[] delay = {config.getDelay()};
        if (ImGui.dragFloat("整体延迟", delay, 0.0f, 0.0f, 600f, "%.2fs")) {
            config.setDelay(delay[0]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("动画开始前的延迟时间");
        }
        
        return modified;
    }
    
    /**
     * 渲染容器信息
     */
    private boolean renderContainerInfo(TimelineClip clip, UUID containerId) {
        Container container = ContainerManager.getInstance().getContainer(containerId);
        if (container != null) {
            UITheme.label("容器名称", UITheme.DarkTheme.TEXT);
            ImGui.text(container.getName());
            
            UITheme.spacingSmall();
            
            UITheme.label("方块数量", UITheme.DarkTheme.TEXT);
            ImGui.text(String.format("%d 个方块", container.getBlockCount()));
            
            UITheme.spacingSmall();
            
            UITheme.label("容器类型", UITheme.DarkTheme.TEXT);
            ImGui.text(container.getType().getDisplayName());
            
            UITheme.spacingSmall();
            
            UITheme.label("容器ID", UITheme.DarkTheme.TEXT);
            ImGui.textDisabled(containerId.toString());
        } else {
            UITheme.label("容器不存在", UITheme.DarkTheme.TEXT_DISABLED);
            ImGui.textDisabled("绑定的容器已被删除");
        }
        UITheme.spacingSmall();
        return false; // 容器信息只读，不修改
    }
    
    /**
     * 渲染动画类型
     */
    private boolean renderAnimationType(TimelineClip clip) {
        boolean modified = false;
        ClipAnimationConfig config = clip.getAnimationConfig();
        AnimationMode[] modes = AnimationMode.values();
        
        // 动画模式选择
        modeIndex.set(config.getMode().ordinal());
        if (ImGui.combo("动画模式", modeIndex, MODE_LABELS)) {
            AnimationMode newMode = modes[modeIndex.get()];
            config.setMode(newMode);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("控制片段出现时的基础动画方式\n\n" +
                "• 瞬间：所有方块立即显示\n" +
                "• 向上生长：方块从下到上逐层出现\n" +
                "• 扩散：方块从中心到周边逐渐扩散\n" +
                "• 更多效果正在开发中...");
        }

        UITheme.spacingSmall();
        
        // 动画模式描述
        AnimationMode currentMode = config.getMode();
        UITheme.label("效果说明", UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.label(currentMode.getDescription(), UITheme.DarkTheme.TEXT_DISABLED);
        
        UITheme.spacingSmall();
        
        // 特效预览开关
        if (ImGui.checkbox("显示特效预览", showPreview)) {
            // 预览开关切换，showPreview 会自动更新
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("在属性面板中显示动画效果的详细说明和可视化预览");
        }
        
        if (showPreview.get()) {
            renderAnimationPreview(currentMode, config, clip);
        }

        UITheme.spacingSmall();
        UITheme.separator();

        // 动画时长
        float[] animDuration = {config.getDuration()};
        if (ImGui.dragFloat("动画时长", animDuration, 0.05f, 0.05f, 10_000f, "%.2fs")) {
            config.setDuration(animDuration[0]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("动画效果的持续时间（在片段时长内）\n" +
                "如果动画时长超过片段时长，会被限制为片段时长");
        }
        
        return modified;
    }
    
    /**
     * 渲染顺序逻辑
     */
    private boolean renderSequenceLogic(TimelineClip clip) {
        boolean modified = false;
        ClipAnimationConfig config = clip.getAnimationConfig();
        
        // 序列模式选择
        SequenceMode currentSequenceMode = config.getSequenceMode();
        SequenceMode[] sequenceModes = SequenceMode.values();
        sequenceModeIndex.set(currentSequenceMode.ordinal());
        if (ImGui.combo("序列模式", sequenceModeIndex, SEQUENCE_MODE_LABELS)) {
            config.setSequenceMode(sequenceModes[sequenceModeIndex.get()]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("决定方块动画的执行顺序\n" +
                "• 顺序执行：按列表顺序一个接一个\n" +
                "• 随机执行：随机打乱顺序\n" +
                "• 批量执行：每批N个方块同时执行\n" +
                "• 波浪式：按行列或距离波浪式执行\n" +
                "• 螺旋式：从中心向外螺旋式执行\n" +
                "• 并行执行：所有方块同时执行");
        }
        
        // 序列模式描述
        UITheme.spacingSmall();
        UITheme.label("模式说明", UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.label(currentSequenceMode.getDescription(), UITheme.DarkTheme.TEXT_DISABLED);
        
        UITheme.spacingSmall();
        UITheme.separator();
        
        // 序列参数配置（根据模式显示不同的参数）
        SequenceParameters seqParams = config.getSequenceParameters();
        if (seqParams != null) {
            // 批量模式：批量大小
            if (currentSequenceMode == SequenceMode.BATCH) {
                int[] batchSize = {seqParams.getBatchSize()};
                if (ImGui.dragInt("批量大小", batchSize, 1, 1, 1000, "%d 个方块/批")) {
                    seqParams.setBatchSize(batchSize[0]);
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("每批同时执行的方块数量");
                }
            }
            
            // 随机模式：随机种子
            if (currentSequenceMode == SequenceMode.RANDOM) {
                Long seed = seqParams.getRandomSeed();
                String seedStr = seed != null ? String.valueOf(seed) : "随机";
                ImString seedBuffer = new ImString(seedStr, 64);
                if (ImGui.inputText("随机种子", seedBuffer)) {
                    String input = seedBuffer.get().trim();
                    if (input.isEmpty() || input.equals("随机")) {
                        seqParams.setRandomSeed(null);
                    } else {
                        try {
                            seqParams.setRandomSeed(Long.parseLong(input));
                        } catch (NumberFormatException e) {
                            // 忽略无效输入
                        }
                    }
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("随机种子（留空表示使用系统时间）\n" +
                        "相同的种子会产生相同的随机顺序，可用于重现效果");
                }
            }
            
            // 波浪模式：波浪方向、间隔、起始点
            if (currentSequenceMode == SequenceMode.WAVE) {
                ImString waveDirBuffer = new ImString(seqParams.getWaveDirection(), 16);
                if (ImGui.inputText("波浪方向", waveDirBuffer)) {
                    seqParams.setWaveDirection(waveDirBuffer.get().trim());
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("波浪方向：X、Y、Z 或组合（如 XY）\n" +
                        "• X：沿X轴方向形成波浪\n" +
                        "• Y：沿Y轴方向形成波浪（默认）\n" +
                        "• Z：沿Z轴方向形成波浪");
                }
                
                float[] waveInterval = {seqParams.getWaveInterval()};
                if (ImGui.dragFloat("波幅间隔", waveInterval, 0.1f, 0.1f, 100.0f, "%.1f 方块")) {
                    seqParams.setWaveInterval(waveInterval[0]);
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("波幅间隔（单位：方块）\n" +
                        "间隔越大，波浪效果越明显");
                }
                
                ImString waveStartBuffer = new ImString(seqParams.getWaveStart(), 16);
                if (ImGui.inputText("起始位置", waveStartBuffer)) {
                    seqParams.setWaveStart(waveStartBuffer.get().trim());
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("波浪起始位置：min（最小值）、max（最大值）、center（中心）");
                }
            }
            
            // 螺旋模式：起始方向、螺旋方向
            if (currentSequenceMode == SequenceMode.SPIRAL) {
                ImString spiralStartBuffer = new ImString(seqParams.getSpiralStart(), 16);
                if (ImGui.inputText("起始方向", spiralStartBuffer)) {
                    seqParams.setSpiralStart(spiralStartBuffer.get().trim());
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("螺旋起始方向：X、Y、Z\n" +
                        "决定在哪个平面上进行螺旋（垂直于该方向）\n" +
                        "• X：在YZ平面上螺旋\n" +
                        "• Y：在XZ平面上螺旋（默认）\n" +
                        "• Z：在XY平面上螺旋");
                }
                
                ImString spiralDirBuffer = new ImString(seqParams.getSpiralDirection(), 32);
                if (ImGui.inputText("螺旋方向", spiralDirBuffer)) {
                    seqParams.setSpiralDirection(spiralDirBuffer.get().trim());
                    modified = true;
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip("螺旋方向：clockwise（顺时针）或 counterclockwise（逆时针）");
                }
            }
        }
        
        return modified;
    }
    
    /**
     * 渲染粒子与音效（预留功能）
     */
    private boolean renderEffectsAndAudio(TimelineClip clip) {
        UITheme.label("粒子效果", UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.spacingSmall();
        
        ImGui.beginDisabled();
        ImString particleBuffer = new ImString("未配置", 128);
        ImGui.inputText("粒子效果", particleBuffer);
        ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("粒子效果资源标识（功能开发中）");
        }
        
        UITheme.spacingSmall();
        
        UITheme.label("音效", UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.spacingSmall();
        
        ImGui.beginDisabled();
        ImString soundBuffer = new ImString("未配置", 128);
        ImGui.inputText("声音效果", soundBuffer);
        ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("声音效果资源标识（功能开发中）");
        }
        
        UITheme.spacingSmall();
        
        float[] volume = {1.0f};
        ImGui.beginDisabled();
        ImGui.dragFloat("音量", volume, 0.01f, 0.0f, 1.0f, "%.2f");
        ImGui.endDisabled();
        
        float[] pitch = {1.0f};
        ImGui.beginDisabled();
        ImGui.dragFloat("音调", pitch, 0.01f, 0.5f, 2.0f, "%.2f");
        ImGui.endDisabled();
        
        UITheme.spacingSmall();
        UITheme.label("提示：粒子与音效功能正在开发中...", UITheme.DarkTheme.TEXT_DISABLED);
        
        return false;
    }
    
    /**
     * 渲染高级参数
     */
    private boolean renderAdvancedSettings(TimelineClip clip) {
        boolean modified = false;
        ClipAnimationConfig config = clip.getAnimationConfig();
        
        // Easing 曲线
        easingBuffer.set(config.getEasing());
        if (ImGui.inputText("Easing 曲线", easingBuffer)) {
            config.setEasing(easingBuffer.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("缓动曲线类型（控制动画的加速/减速）\n" +
                "常用值：Linear（线性）、EaseIn（加速）、EaseOut（减速）、EaseInOut（先加速后减速）");
        }
        
        UITheme.spacingSmall();
        
        // 旋转角度（预留）
        UITheme.label("旋转角度", UITheme.DarkTheme.TEXT_SELECTED);
        UITheme.spacingSmall();
        
        float[] rotateYaw = {0.0f};
        float[] rotatePitch = {0.0f};
        float[] rotateRoll = {0.0f};
        
        ImGui.beginDisabled();
        ImGui.dragFloat3("旋转 (Yaw/Pitch/Roll)", new float[]{rotateYaw[0], rotatePitch[0], rotateRoll[0]}, 1.0f, -360.0f, 360.0f, "%.1f°");
        ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("旋转角度（功能开发中）");
        }
        
        UITheme.spacingSmall();
        
        // 缩放比例（预留）
        float[] scaleFactor = {1.0f};
        ImGui.beginDisabled();
        ImGui.dragFloat("缩放比例", scaleFactor, 0.01f, 0.1f, 10.0f, "%.2f");
        ImGui.endDisabled();
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("缩放比例（功能开发中）");
        }
        
        UITheme.spacingSmall();
        
        // 播放顺序（旧字段，保留向后兼容）
        orderingBuffer.set(config.getOrdering());
        if (ImGui.inputText("播放顺序（旧）", orderingBuffer)) {
            config.setOrdering(orderingBuffer.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("方块的播放顺序（旧字段，建议使用序列模式）\n" +
                "常用值：Sequential（顺序）、Random（随机）、Parallel（并行）");
        }
        
        return modified;
    }
    
    /**
     * 渲染其他属性
     */
    private boolean renderOtherProperties(TimelineClip clip) {
        boolean modified = false;
        
        // 静音
        ImBoolean muted = new ImBoolean(clip.isMuted());
        if (ImGui.checkbox("静音", muted)) {
            clip.setMuted(muted.get());
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("静音片段，使其在播放时被忽略");
        }

        UITheme.spacingSmall();

        // 透明度
        float[] opacity = {clip.getOpacity()};
        if (ImGui.dragFloat("透明度", opacity, 0.01f, 0.0f, 1.0f, "%.2f")) {
            clip.setOpacity(opacity[0]);
            modified = true;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("片段的透明度（0.0 = 完全透明，1.0 = 完全不透明）");
        }
        
        return modified;
    }
            ClipAnimationConfig config = clip.getAnimationConfig();
            AnimationMode[] modes = AnimationMode.values();
            
            // 动画模式选择
            modeIndex.set(config.getMode().ordinal());
            if (ImGui.combo("动画模式", modeIndex, MODE_LABELS)) {
                AnimationMode newMode = modes[modeIndex.get()];
                config.setMode(newMode);
                modified = true;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("控制片段出现时的基础动画方式\n\n" +
                    "• 瞬间：所有方块立即显示\n" +
                    "• 向上生长：方块从下到上逐层出现\n" +
                    "• 扩散：方块从中心到周边逐渐扩散\n" +
                    "• 更多效果正在开发中...");
            }

            UITheme.spacingSmall();
            
            // 动画模式描述和预览
            AnimationMode currentMode = config.getMode();
            UITheme.label("效果说明", UITheme.DarkTheme.TEXT_SELECTED);
            UITheme.label(currentMode.getDescription(), UITheme.DarkTheme.TEXT_DISABLED);
            
            UITheme.spacingSmall();
            
            // 特效预览开关
            if (ImGui.checkbox("显示特效预览", showPreview)) {
                // 预览开关切换，showPreview 会自动更新
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip("在属性面板中显示动画效果的详细说明和可视化预览");
            }
            
            if (showPreview.get()) {
                renderAnimationPreview(currentMode, config, clip);
            }

    
    /**
     * 渲染动画效果预览
     */
    private void renderAnimationPreview(AnimationMode mode, ClipAnimationConfig config, TimelineClip clip) {
        UITheme.spacingSmall();
        UITheme.separator();
        
        UITheme.label("特效预览", UITheme.DarkTheme.TEXT_SELECTED);
        
        // 根据不同的动画模式显示不同的预览信息
        switch (mode) {
            case INSTANT:
                UITheme.label("效果：所有方块在片段开始时间立即显示", UITheme.DarkTheme.TEXT_DISABLED);
                break;
                
            case GROW_UP:
                UITheme.label("效果：方块按照Y坐标从低到高逐层出现", UITheme.DarkTheme.TEXT_DISABLED);
                UITheme.label(String.format("底层方块：%.2fs", clip.getStartTime()), UITheme.DarkTheme.TEXT_DISABLED);
                if (clip.getBoundContainerId() != null) {
                    Container container = ContainerManager.getInstance().getContainer(clip.getBoundContainerId());
                    if (container != null) {
                        Container.BoundingBox bbox = container.getBoundingBox();
                        if (bbox != null) {
                            float layerCount = bbox.getHeight();
                            if (layerCount > 0) {
                                float timePerLayer = config.getDuration() / layerCount;
                                UITheme.label(String.format("每层间隔：约 %.2fs", timePerLayer), UITheme.DarkTheme.TEXT_DISABLED);
                            }
                        }
                    }
                }
                break;
                
            case EXPAND:
                UITheme.label("效果：方块按照距离中心从近到远依次出现", UITheme.DarkTheme.TEXT_DISABLED);
                UITheme.label("中心方块：片段开始时间", UITheme.DarkTheme.TEXT_DISABLED);
                UITheme.label("边缘方块：片段开始时间 + 动画时长", UITheme.DarkTheme.TEXT_DISABLED);
                break;
                
            default:
                UITheme.label("预览信息：暂未实现", UITheme.DarkTheme.TEXT_DISABLED);
                break;
        }
        
        // 显示动画进度条预览
        float clipDuration = clip.getLength();
        float animationDuration = config.getDuration();
        float animationRatio = Math.min(1.0f, animationDuration / clipDuration);
        
        UITheme.spacingSmall();
        UITheme.label("动画进度（相对于片段时长）", UITheme.DarkTheme.TEXT);
        float[] progress = {animationRatio};
        ImGui.progressBar(progress[0], 0.0f, 1.0f, 
            String.format("%.1f%% (%.2fs / %.2fs)", 
                animationRatio * 100f, animationDuration, clipDuration));
    }

    @Override
    public Class<TimelineClip> getTargetType() {
        return TimelineClip.class;
    }
}

