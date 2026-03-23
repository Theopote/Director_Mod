package com.director.ui.timeline.render;

import com.director.actor.action.ActorActionType;

/**
 * ActionTypeColors v1.0
 *
 * <p>ActionType 到颜色的映射（稳定配色）。
 *
 * <p>设计原则：
 * <ul>
 *   <li>颜色 = 动作类别，不是数值</li>
 *   <li>同一个 ActionType 在任何 Track 都是同色</li>
 *   <li>颜色必须稳定（不随时间/轨道变化）</li>
 * </ul>
 *
 * <p>推荐配色规则：
 * <table>
 *   <tr>
 *     <th>ActionType</th>
 *     <th>颜色（RGBA）</th>
 *     <th>说明</th>
 *   </tr>
 *   <tr>
 *     <td>IDLE</td>
 *     <td>灰色</td>
 *     <td>静止状态</td>
 *   </tr>
 *   <tr>
 *     <td>WALK</td>
 *     <td>蓝色</td>
 *     <td>行走</td>
 *   </tr>
 *   <tr>
 *     <td>RUN</td>
 *     <td>橙色</td>
 *     <td>奔跑</td>
 *   </tr>
 *   <tr>
 *     <td>DANCE</td>
 *     <td>紫色</td>
 *     <td>舞蹈</td>
 *   </tr>
 *   <tr>
 *     <td>CARRY</td>
 *     <td>绿色</td>
 *     <td>搬运</td>
 *   </tr>
 *   <tr>
 *     <td>THROW_PREPARE</td>
 *     <td>红色</td>
 *     <td>投掷准备</td>
 *   </tr>
 *   <tr>
 *     <td>ATTACK</td>
 *     <td>深红</td>
 *     <td>攻击</td>
 *   </tr>
 *   <tr>
 *     <td>CELEBRATE</td>
 *     <td>黄色</td>
 *     <td>庆祝</td>
 *   </tr>
 *   <tr>
 *     <td>SIT</td>
 *     <td>棕色</td>
 *     <td>坐下</td>
 *   </tr>
 *   <tr>
 *     <td>MEDITATE</td>
 *     <td>青色</td>
 *     <td>冥想</td>
 *   </tr>
 *   <tr>
 *     <td>GRAZE</td>
 *     <td>草绿</td>
 *     <td>吃草</td>
 *   </tr>
 *   <tr>
 *     <td>ROAR</td>
 *     <td>深橙</td>
 *     <td>咆哮</td>
 *   </tr>
 *   <tr>
 *     <td>CUSTOM</td>
 *     <td>粉色</td>
 *     <td>自定义</td>
 *   </tr>
 * </table>
 *
 * @author Director Team
 * @version 1.0
 */
public final class ActionTypeColors {

    private ActionTypeColors() {
        // 工具类，不允许实例化
    }

    /**
     * 获取 ActionType 对应的颜色（RGBA，0xAARRGGBB 格式）
     *
     * @param actionType 动作类型
     * @return 颜色值（RGBA）
     */
    public static int getColorForActionType(ActorActionType actionType) {
        if (actionType == null) {
            return rgba(128, 128, 128, 255); // 默认灰色
        }

        return switch (actionType) {
            case IDLE -> rgba(128, 128, 128, 255);        // 灰色
            case WALK -> rgba(100, 150, 255, 255);        // 蓝色
            case RUN -> rgba(255, 165, 0, 255);           // 橙色
            case DANCE -> rgba(200, 100, 255, 255);       // 紫色
            case CARRY -> rgba(100, 255, 100, 255);       // 绿色
            case THROW_PREPARE -> rgba(255, 100, 100, 255); // 红色
            case ATTACK -> rgba(200, 0, 0, 255);          // 深红
            case CELEBRATE -> rgba(255, 255, 100, 255);   // 黄色
            case SIT -> rgba(139, 90, 43, 255);           // 棕色
            case MEDITATE -> rgba(100, 200, 255, 255);    // 青色
            case GRAZE -> rgba(144, 238, 144, 255);       // 草绿
            case ROAR -> rgba(255, 140, 0, 255);          // 深橙
            case CUSTOM -> rgba(255, 182, 193, 255);      // 粉色
        };
    }

    /**
     * 将 RGBA 分量转换为整数颜色值
     *
     * @param r 红色分量 [0-255]
     * @param g 绿色分量 [0-255]
     * @param b 蓝色分量 [0-255]
     * @param a 透明度分量 [0-255]
     * @return 颜色值（0xAARRGGBB 格式）
     */
    private static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }
}
