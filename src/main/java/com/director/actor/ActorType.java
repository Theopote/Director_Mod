package com.director.actor;

/**
 * ActorType v1.0
 *
 * <p>演员类型枚举（导演语义的类型，不是 MC EntityType）。
 * <p>后面 Runtime 再映射到 Minecraft EntityType。
 *
 * @author Director Team
 * @version 1.0
 */
public enum ActorType {
    /** 玩家 */
    PLAYER("玩家"),
    
    /** 村民 */
    VILLAGER("村民"),
    
    /** 牛 */
    COW("牛"),
    
    /** 羊 */
    SHEEP("羊"),
    
    /** 僵尸 */
    ZOMBIE("僵尸"),
    
    /** 骷髅 */
    SKELETON("骷髅"),
    
    /** 自定义（将来用于模型 / Geckolib / BlockModelActor） */
    CUSTOM("自定义");

    private final String displayName;

    ActorType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

