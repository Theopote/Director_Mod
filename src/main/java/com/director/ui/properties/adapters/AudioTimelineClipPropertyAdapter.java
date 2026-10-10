package com.director.ui.properties.adapters;

import com.director.core.DirectorServices;
import com.director.i18n.ClipPropertyUiI18n;
import com.director.core.container.Container;
import com.director.core.container.ContainerType;
import com.director.timeline.model.TimelineClip;
import com.director.timeline.model.TrackType;
import com.director.timeline.model.audio.AudioClipKeyframe;
import com.director.timeline.model.audio.AudioTimelineClip;
import com.director.ui.properties.IPropertyAdapter;
import com.director.ui.theme.UITheme;
import com.director.ui.timeline.audio.TimelineAudioService;
import com.director.util.ParseUtils;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;

import java.util.Map;
import java.util.UUID;

/**
 * 音频片段属性适配器
 *
 * <p>用于 AUDIO 轨道上的 {@link AudioTimelineClip}。</p>
 */
public class AudioTimelineClipPropertyAdapter implements IPropertyAdapter<AudioTimelineClip> {

    @Override
    public boolean renderProperties(AudioTimelineClip clip) {
        if (clip == null) {
            UITheme.label("未选中音频片段", UITheme.DarkTheme.TEXT_DISABLED);
            return false;
        }

        boolean modified = false;

        UITheme.title(TrackType.AUDIO.getIcon() + " 音频片段");

        if (ImGui.collapsingHeader("基础", ImGuiTreeNodeFlags.DefaultOpen)) {
            boolean isBound = clip.getBoundContainerId() != null;
            if (isBound) {
                ImGui.textDisabled("提示：该音频片段已绑定容器。时间范围将自动与容器片段保持一致；若要整体移动请先取消绑定。");
                ImGui.beginDisabled(true);
            }

            float[] start = { clip.getStartTime() };
            if (ImGui.dragFloat("开始时间(秒)", start, 0.05f, 0.0f, 100000f, "%.2fs")) {
                clip.setStartTime(Math.max(0.0f, start[0]));
                modified = true;
            }

            float[] len = { clip.getLength() };
            if (ImGui.dragFloat("时长(秒)", len, 0.05f, 0.05f, 100000f, "%.2fs")) {
                clip.setLength(len[0]);
                modified = true;
            }

            if (isBound) {
                ImGui.endDisabled();
            }
        }

        if (ImGui.collapsingHeader("链接", ImGuiTreeNodeFlags.DefaultOpen)) {
            String boundName = clip.getBoundContainerName();
            if (boundName == null || boundName.isBlank()) {
                boundName = "（未绑定）";
            }
            ImGui.textDisabled("绑定容器: " + boundName);
            if (clip.getBoundContainerId() != null) {
                ImGui.textDisabled("容器ID: " + clip.getBoundContainerId());
            }

            var timelineModel = DirectorServices.get().containerTimeline().getTimelineModel();
            if (timelineModel != null) {
                float now = timelineModel.getCurrentTime();
                float volume = TimelineAudioService.getInstance().getVolumeAtTimeForClip(clip, now);
                ImGui.textDisabled(String.format("当前时间音量倍率: %.2f", volume));
            }
        }

        if (ImGui.collapsingHeader("音效同步", ImGuiTreeNodeFlags.DefaultOpen)) {
            if (clip.getBoundContainerId() == null) {
                ImGui.textDisabled("未绑定容器：此处无自动放置/破坏音效可同步。");
            } else {
                TimelineAudioService audioService = TimelineAudioService.getInstance();
                TimelineClip containerClip = audioService.getContainerClipForAudioClip(clip).orElse(null);
                if (containerClip == null) {
                    ImGui.textColored(1.0f, 0.5f, 0.0f, 1.0f, "⚠ 未找到绑定的容器片段（可能需要先生成一次自动音效）。");
                } else {
                    var cfg = containerClip.getAnimationConfig();
                    if (cfg == null) {
                        ImGui.textDisabled("容器片段动画配置为空，无法同步音效。");
                    } else if (cfg.isWholeContainer()) {
                        ImGui.textWrapped(ClipPropertyUiI18n.tr("gui.director.properties.clip_ui.whole_animation.sound_hint"));
                    } else {
                        boolean isErase = isDemolitionContainer(containerClip.getBoundContainerId());

                        Map<String, Object> params = cfg.getParams();
                        boolean changed = false;
                        boolean requestRefresh = false;

                        if (!isErase) {
                            // ===== 放置音效（ENTER）=====
                            String[] timingLabels = new String[] {
                                    "落地同步（推荐）",   // END
                                    "第一次到位（IMPACT）",
                                    "开始（START）",
                                    "归位前一帧（PRE_LAND）"
                            };
                            String cur = paramString(params, "enterSoundTiming", "END");
                            int idx = switch (cur.toUpperCase()) {
                                case "IMPACT" -> 1;
                                case "START" -> 2;
                                case "PRE_LAND" -> 3;
                                default -> 0;
                            };
                            ImInt buf = new ImInt(idx);
                            ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
                            if (ImGui.combo("放置音效时机", buf, timingLabels)) {
                                String newTiming = switch (buf.get()) {
                                    case 1 -> "IMPACT";
                                    case 2 -> "START";
                                    case 3 -> "PRE_LAND";
                                    default -> "END";
                                };
                                params.put("enterSoundTiming", newTiming);
                                changed = true;
                                requestRefresh = true; // combo 选择完成即可刷新
                            }
                            ImGui.popItemWidth();
                            if (ImGui.isItemHovered()) {
                                ImGui.setTooltip(
                                        """
                                                落地同步：与真实方块在目标格出现的瞬间对齐（默认推荐）
                                                第一次到位：适合部分有弹性/回弹的动画，但可能偏早
                                                开始：动画一开始就播放
                                                归位前一帧：比落地同步略早（约 1/60s）"""
                                );
                            }

                            float offset = ParseUtils.asFloat(params.get("enterSoundOffsetSeconds"), 0.0f, "enterSoundOffsetSeconds");
                            float[] offArr = { offset };
                            if (ImGui.dragFloat("放置音效偏移(秒)", offArr, 0.01f, -2.0f, 2.0f, "%+.2fs")) {
                                params.put("enterSoundOffsetSeconds", offArr[0]);
                                changed = true;
                            }
                            // ✅ 关键：松开滑条（结束编辑）时刷新，避免“拖动过程中一直 active 导致不刷新”
                            if (ImGui.isItemDeactivatedAfterEdit()) {
                                requestRefresh = true;
                            }
                            if (ImGui.isItemHovered()) {
                                ImGui.setTooltip("对所有自动“放置音效”整体做时间偏移（正值=更晚，负值=更早）。");
                            }
                        } else {
                            // ===== 破坏音效（EXIT）=====
                            String[] timingLabels = new String[] {
                                    "消失同步（推荐）",   // END
                                    "第一次到位（IMPACT）",
                                    "开始（START）",
                                    "消失前一帧（PRE_LAND）"
                            };
                            String cur = paramString(params, "exitSoundTiming", "END");
                            int idx = switch (cur.toUpperCase()) {
                                case "IMPACT" -> 1;
                                case "START" -> 2;
                                case "PRE_LAND" -> 3;
                                default -> 0;
                            };
                            ImInt buf = new ImInt(idx);
                            ImGui.pushItemWidth(ImGui.getContentRegionAvailX());
                            if (ImGui.combo("破坏音效时机", buf, timingLabels)) {
                                String newTiming = switch (buf.get()) {
                                    case 1 -> "IMPACT";
                                    case 2 -> "START";
                                    case 3 -> "PRE_LAND";
                                    default -> "END";
                                };
                                params.put("exitSoundTiming", newTiming);
                                changed = true;
                                requestRefresh = true;
                            }
                            ImGui.popItemWidth();
                            if (ImGui.isItemHovered()) {
                                ImGui.setTooltip(
                                        """
                                                消失同步：与真实方块从世界中移除的瞬间对齐（默认推荐）
                                                第一次到位：对某些曲线可能偏早（通常等价于同步）
                                                开始：动画一开始就播放
                                                消失前一帧：比同步略早（约 1/60s）"""
                                );
                            }

                            float offset = ParseUtils.asFloat(params.get("exitSoundOffsetSeconds"), 0.0f, "exitSoundOffsetSeconds");
                            float[] offArr = { offset };
                            if (ImGui.dragFloat("破坏音效偏移(秒)", offArr, 0.01f, -2.0f, 2.0f, "%+.2fs")) {
                                params.put("exitSoundOffsetSeconds", offArr[0]);
                                changed = true;
                            }
                            if (ImGui.isItemDeactivatedAfterEdit()) {
                                requestRefresh = true;
                            }
                            if (ImGui.isItemHovered()) {
                                ImGui.setTooltip("对所有自动“破坏音效”整体做时间偏移（正值=更晚，负值=更早）。");
                            }
                        }

                        // 只有在用户结束编辑时才重建（避免拖动时每帧重建）
                        if ((requestRefresh || (changed && !ImGui.isAnyItemActive()))) {
                            audioService.refreshAutoSoundEventsIfNeeded(containerClip);
                            modified = true;
                        }
                    }
                }
            }
        }

        if (ImGui.collapsingHeader("音量自动化", ImGuiTreeNodeFlags.DefaultOpen)) {
            int count = 0;
            for (var kf : clip.getClipKeyframes()) {
                if (kf instanceof AudioClipKeyframe) {
                    count++;
                }
            }
            ImGui.textDisabled("音量关键帧数量: " + count);
            ImGui.textDisabled("提示：在时间线上双击该音频片段可添加音量关键帧。");
        }

        return modified;
    }

    @Override
    public Class<AudioTimelineClip> getTargetType() {
        return AudioTimelineClip.class;
    }

    @Override
    public String getAdapterName() {
        return TrackType.AUDIO.getIcon() + " 音频片段";
    }

    private static boolean isDemolitionContainer(UUID containerId) {
        if (containerId == null) {
            return false;
        }
        Container container = DirectorServices.get().containers().getContainer(containerId);
        return container != null && container.getType() == ContainerType.DEMOLITION;
    }

    private static String paramString(Map<String, Object> params, String key, String defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString();
        return text.isBlank() ? defaultValue : text;
    }
}


