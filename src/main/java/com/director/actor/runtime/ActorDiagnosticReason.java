package com.director.actor.runtime;

/** Stable diagnostic codes. Legacy terrain messages are adapted only at the reporting boundary. */
public enum ActorDiagnosticReason {
    ROUTE_EMPTY("路径没有锚点"),
    INVALID_POSITION("路径坐标无效"),
    NO_GROUND("路径起点下方没有可站立地面，或地形尚未加载"),
    NO_SPACE("路径起点空间不足"),
    ROUTE_LIMIT("路径过长，请拆成多个片段"),
    GROUND_GAP("前方有陡坎、断路或未加载地形，演员停在可达位置"),
    OBSTACLE("前方空间不足，演员停在障碍物前"),
    RELATION_TERRAIN("关系地形尚未就绪"),
    RELATION_POSITION("关系位置无效"),
    RELATION_LIMIT("关系检测距离超过 64 格，已限制到可检测范围"),
    ROOT_POSITION("最终动作位置无效"),
    ROOT_HEIGHT("地面演员的根骨骼高度位移已投影到地面，请使用短跳跃"),
    JUMP_BLOCKED("跳跃上方空间不足，已保持地面位置"),
    RELATION_CYCLE("关系链存在循环或超过 128 层，已使用自身轨道位置"),
    RELATION_TARGET("关系目标缺失、未激活或无法显示，已使用自身轨道位置"),
    BINDING_MISSING("轨道绑定的演员不存在，请重新绑定演员"),
    RELATION_DIMENSIONS("关系地形或演员碰撞尺寸尚未就绪"),
    ROUTE_DIMENSIONS("路径地形或演员碰撞尺寸尚未就绪"),
    TERRAIN_CHANGED("当前位置地形已改变或未加载，请重新检测路径"),
    PREVIEW_UNAVAILABLE("预览实体无法创建，请检查世界加载状态和演员类型"),
    ROOT_TERRAIN("动作地形尚未就绪"),
    PROP_PARAMETERS("道具事件参数无效，已跳过事件"),
    PROP_EMPTY_SLOT("投掷槽位没有道具，已跳过投掷"),
    PROP_LAUNCH("投掷起点或挂点不可用，道具无法预览"),
    PROP_SOCKET("演员骨架不支持此道具挂点，道具无法预览"),
    PROP_CUSTOM("自定义道具模型尚不支持预览"),
    PROP_ASSET("道具物品或方块资源不存在，无法预览");
    private final String legacyMessage;
    ActorDiagnosticReason(String message) { legacyMessage=message; }
    public String legacyMessage() { return legacyMessage; }
    public String translationKey() { return "gui.director.actor.diagnostics.reason."+name().toLowerCase(java.util.Locale.ROOT); }
    public static ActorDiagnosticReason fromLegacy(String message) {
        for(var reason:values()) if(reason.legacyMessage.equals(message)) return reason;
        return null;
    }
}
