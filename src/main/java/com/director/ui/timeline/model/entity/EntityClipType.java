package com.director.ui.timeline.model.entity;

/**
 * EntityClipType v1.0
 *
 * <p>实体片段类型枚举，定义不同类型的实体表演片段。
 * 
 * <p>设计理念：
 * <ul>
 *   <li>一个 Clip = 一段表演</li>
 *   <li>不同类型的 Clip 处理不同的功能</li>
 *   <li>支持复杂的"导演"场景</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public enum EntityClipType {
    /**
     * 出现/绑定片段
     * 
     * <p>解决"实体从哪来"的问题：
     * <ul>
     *   <li>SPAWN_NEW - 生成新实体</li>
     *   <li>BIND_EXISTING - 绑定已存在的实体（如玩家）</li>
     * </ul>
     * 
     * <p>用途：
     * <ul>
     *   <li>电影开头角色出现</li>
     *   <li>绑定真实玩家作为演员</li>
     *   <li>延迟入场</li>
     * </ul>
     */
    SPAWN("出现/绑定", "控制实体的出现和绑定"),

    /**
     * 位移/旋转/缩放片段
     * 
     * <p>最重要的片段类型，控制实体的位置、旋转和缩放。
     * 
     * <p>特点：
     * <ul>
     *   <li>不依赖 Minecraft AI（可控、可复现）</li>
     *   <li>支持路径插值（Linear, Bezier, CatmullRom）</li>
     *   <li>可复用 Camera Path 系统</li>
     * </ul>
     * 
     * <p>高级形态（未来扩展）：
     * <ul>
     *   <li>贝塞尔路径</li>
     *   <li>CameraPath 复用</li>
     *   <li>地形贴合</li>
     * </ul>
     */
    TRANSFORM("位移/旋转", "控制实体的位置、旋转和缩放"),

    /**
     * 动画片段
     * 
     * <p>控制"演什么动作"，而不是"怎么动"。
     * 
     * <p>支持的动画源：
     * <ul>
     *   <li>VANILLA - Minecraft 原版动画</li>
     *   <li>GECKOLIB - GeckoLib 动画</li>
     *   <li>CUSTOM - 自定义动画</li>
     * </ul>
     */
    ANIMATION("动画", "控制实体的动画播放"),

    /**
     * 行为片段
     * 
     * <p>导演最爱的片段类型，控制实体的行为。
     * 
     * <p>示例：
     * <ul>
     *   <li>挥剑</li>
     *   <li>放置方块（动画，不是真的 World SetBlock）</li>
     *   <li>对话（字幕轨道联动）</li>
     *   <li>发出声音（音频轨道联动）</li>
     * </ul>
     */
    ACTION("行为", "控制实体的行为（攻击、对话、交互等）"),

    /**
     * 状态片段
     * 
     * <p>控制实体的状态机。
     * 
     * <p>状态类型：
     * <ul>
     *   <li>潜行</li>
     *   <li>冲刺</li>
     *   <li>发光</li>
     *   <li>无敌</li>
     * </ul>
     * 
     * <p>特点：
     * <ul>
     *   <li>适合关键帧插值</li>
     *   <li>支持淡入淡出</li>
     * </ul>
     */
    STATE("状态", "控制实体的状态（潜行、冲刺、发光等）"),

    /**
     * 看向目标片段
     * 
     * <p>控制实体看向某个点/某个实体/摄像机。
     * 
     * <p>目标类型：
     * <ul>
     *   <li>WORLD_POINT - 世界坐标点</li>
     *   <li>ENTITY_UUID - 其他实体</li>
     *   <li>CAMERA - 摄像机（看向镜头）</li>
     * </ul>
     */
    LOOK_AT("看向", "控制实体看向某个点/某个实体/摄像机"),

    /**
     * 离场片段
     * 
     * <p>控制实体的离场。
     * 
     * <p>离场方式：
     * <ul>
     *   <li>淡出消失</li>
     *   <li>传送离开</li>
     *   <li>死亡动画</li>
     * </ul>
     */
    DESPAWN("离场", "控制实体的离场"),

    /**
     * 演员动作语义片段
     * 
     * <p>表示演员在一段时间内的"动作语义状态"。
     * 
     * <p>与 ACTION 的区别：
     * <ul>
     *   <li>ACTION：瞬时事件（Throw / Place）</li>
     *   <li>ACTOR_ACTION：持续动作语义（Run / Dance / Walk）</li>
     * </ul>
     * 
     * <p>示例：
     * <ul>
     *   <li>RUN（奔跑感觉）</li>
     *   <li>WALK（行走感觉）</li>
     *   <li>DANCE（跳舞）</li>
     *   <li>CARRY（搬运）</li>
     *   <li>IDLE（站立）</li>
     * </ul>
     * 
     * <p>特点：
     * <ul>
     *   <li>这是"导演意图"，不是"实现细节"</li>
     *   <li>不直接控制动画、不操作世界</li>
     *   <li>只影响 Runtime 表现策略</li>
     *   <li>必须是可插值 / 可过渡的</li>
     * </ul>
     */
    ACTOR_ACTION("动作语义", "控制演员的持续动作语义状态（Run / Dance / Walk 等）"),

    /**
     * 演员局部姿态覆盖片段。
     *
     * <p>用于在 RigProfile 生成基础动作后，对头部、身体、手臂、腿部做导演级微调。</p>
     */
    ACTOR_POSE("局部姿态", "覆盖演员头部/身体/四肢的局部姿态"),

    /**
     * 演员路线片段。
     *
     * <p>用确定性的路点序列驱动演员移动。未来可接入 Minecraft PathNavigation 作为局部避障辅助。</p>
     */
    ACTOR_ROUTE("路线", "沿路点序列移动演员");

    private final String displayName;
    private final String description;

    EntityClipType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}

