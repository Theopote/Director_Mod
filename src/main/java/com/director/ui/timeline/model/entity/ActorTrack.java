package com.director.ui.timeline.model.entity;

import com.director.actor.ActorRef;

import java.util.*;

/**
 * ActorTrack v1.0
 *
 * <p>一个"演员"（Actor）轨道。
 *
 * <p>设计理念：
 * <ul>
 *   <li>轨道级：演员是谁、如何绑定、轨道显示信息</li>
 *   <li>片段级：表演段落 EntityClip 列表</li>
 * </ul>
 *
 * <p>核心原则：
 * <ul>
 *   <li>Timeline 永远不直接持有 Actor，只持有 ActorRef</li>
 *   <li>Actor 由 ActorRegistry 管理（Project 层）</li>
 *   <li>ActorTrack 通过 ActorRef 引用 Actor</li>
 * </ul>
 *
 * <p>与 {@link com.director.ui.timeline.model.TimelineTrack} 的关系：
 * <ul>
 *   <li>ActorTrack 是独立的演员轨道数据结构</li>
 *   <li>可以与 TimelineTrack 并存，用于不同的使用场景</li>
 *   <li>未来可以让 TimelineTrack (type=ACTOR) 的 payload 是 ActorTrack</li>
 * </ul>
 *
 * <p>使用方式：
 * <ul>
 *   <li>作为独立数据结构，先把 UI / 序列化 / 编辑跑通</li>
 *   <li>未来集成到 TimelineTrack 系统中</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public class ActorTrack {

    /** Actor 引用（Timeline 只持有 ActorRef，不直接持有 Actor） */
    private ActorRef actorRef;

    /** UI 显示名：主角 Steve / 守卫A（如果 actorRef 为 null 时使用） */
    private String displayName = "Actor";

    /** 绑定信息：生成/绑定已有 */
    private EntityBinding binding = new EntityBinding();

    /** 轨道开关 */
    private boolean enabled = true;

    /** 轨道颜色（可选，UI 用） */
    private int colorRgba = 0xFF66CCFF;

    /** 轨道片段（按时间排序） */
    private final NavigableSet<EntityClip> clips = new TreeSet<>();

    /**
     * 默认构造函数
     */
    public ActorTrack() {
    }

    /**
     * 构造函数（带显示名称）
     *
     * @param displayName 显示名称
     */
    public ActorTrack(String displayName) {
        this.displayName = (displayName == null || displayName.isBlank()) ? "Actor" : displayName;
    }

    // ========== 基础属性 ==========

    /**
     * 获取 Actor 引用
     *
     * @return ActorRef，如果未设置则返回 null
     */
    public ActorRef getActorRef() {
        return actorRef;
    }

    /**
     * 设置 Actor 引用
     *
     * @param actorRef ActorRef
     */
    public void setActorRef(ActorRef actorRef) {
        this.actorRef = actorRef;
    }

    /**
     * 获取演员ID（从 ActorRef 获取，如果 ActorRef 为 null 则返回 null）
     *
     * @return 演员ID
     */
    public UUID getActorId() {
        return actorRef != null ? actorRef.getActorId() : null;
    }

    /**
     * 获取显示名称
     *
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     */
    public void setDisplayName(String displayName) {
        this.displayName = (displayName == null || displayName.isBlank()) ? "Actor" : displayName;
    }

    /**
     * 获取绑定信息
     *
     * @return 绑定信息
     */
    public EntityBinding getBinding() {
        return binding;
    }

    /**
     * 设置绑定信息
     *
     * @param binding 绑定信息
     */
    public void setBinding(EntityBinding binding) {
        this.binding = (binding == null) ? new EntityBinding() : binding;
    }

    /**
     * 检查是否启用
     *
     * @return 如果启用则返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取轨道颜色
     *
     * @return 颜色（RGBA）
     */
    public int getColorRgba() {
        return colorRgba;
    }

    /**
     * 设置轨道颜色
     *
     * @param colorRgba 颜色（RGBA）
     */
    public void setColorRgba(int colorRgba) {
        this.colorRgba = colorRgba;
    }

    // ========== Clip 管理 ==========

    /**
     * 获取所有片段（按时间排序）
     *
     * @return 不可修改的片段列表
     */
    public List<EntityClip> getClips() {
        return Collections.unmodifiableList(new ArrayList<>(clips));
    }

    /**
     * 添加片段
     *
     * @param clip 片段
     */
    public void addClip(EntityClip clip) {
        if (clip != null) {
            clips.add(clip);
        }
    }

    /**
     * 移除片段
     *
     * @param clipId 片段ID
     * @return 如果成功移除则返回 true
     */
    public boolean removeClip(UUID clipId) {
        if (clipId == null) {
            return false;
        }
        EntityClip target = null;
        for (EntityClip c : clips) {
            if (clipId.equals(c.getId())) {
                target = c;
                break;
            }
        }
        return target != null && clips.remove(target);
    }

    /**
     * 清空所有片段
     */
    public void clearClips() {
        clips.clear();
    }

    /**
     * 查找片段
     *
     * @param clipId 片段ID
     * @return 片段，如果未找到则返回 empty
     */
    public Optional<EntityClip> findClip(UUID clipId) {
        if (clipId == null) {
            return Optional.empty();
        }
        for (EntityClip c : clips) {
            if (clipId.equals(c.getId())) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    /**
     * 找到 timeSeconds 覆盖到的 clip（可能有多个；比如 Action 与 Transform 重叠）
     *
     * @param timeSeconds 时间（秒）
     * @return 覆盖该时间的片段列表
     */
    public List<EntityClip> findClipsAt(float timeSeconds) {
        if (clips.isEmpty()) {
            return List.of();
        }
        List<EntityClip> out = new ArrayList<>();
        for (EntityClip c : clips) {
            if (timeSeconds >= c.getStartTime() && timeSeconds <= c.getEndTime()) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * 检查是否有片段
     *
     * @return 如果有片段则返回 true
     */
    public boolean hasClips() {
        return !clips.isEmpty();
    }

    /**
     * 获取片段数量
     *
     * @return 片段数量
     */
    public int getClipCount() {
        return clips.size();
    }

    /**
     * 添加默认的 SpawnClip（在当前时间点）
     * <p>这是一个便利方法，用于快速创建演员的入场片段
     *
     * @param time 时间（秒）
     */
    public void addDefaultSpawnClip(float time) {
        EntitySpawnClip clip = new EntitySpawnClip(
                "Spawn",
                time,
                time + 0.01f  // 极短的片段，表示"瞬间生成"
        );
        // 默认位置在原点（0, 0, 0），导演可以在属性面板中调整
        clip.setPosition(0f, 0f, 0f);
        clip.setYawDeg(0f);
        clip.setPitchDeg(0f);
        clip.setVisibleOnSpawn(true);
        addClip(clip);
    }

    /**
     * 获取所有 ActorActionClip（用于涂抹式编辑）
     *
     * @return ActorActionClip 列表（按时间排序）
     */
    public List<ActorActionClip> getActionClips() {
        List<ActorActionClip> result = new ArrayList<>();
        for (EntityClip clip : clips) {
            if (clip instanceof ActorActionClip actionClip) {
                result.add(actionClip);
            }
        }
        return result;
    }

    /**
     * 替换 ActionClips（用于涂抹式编辑）
     * <p>移除所有现有的 ActorActionClip，然后添加新的
     *
     * @param newClips 新的 ActionClips 列表
     */
    public void replaceActionClips(List<ActorActionClip> newClips) {
        // 移除所有现有的 ActorActionClip
        clips.removeIf(clip -> clip instanceof ActorActionClip);

        // 添加新的 ActionClips
        for (ActorActionClip clip : newClips) {
            if (clip != null) {
                clips.add(clip);
            }
        }
    }
}

