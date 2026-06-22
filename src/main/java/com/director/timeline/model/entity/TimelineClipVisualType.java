package com.director.timeline.model.entity;

/**
 * TimelineClipVisualType v1.0
 *
 * <p>时间线 Clip 的视觉类型，用于区分不同的渲染风格。
 *
 * <p>设计目标：
 * <ul>
 *   <li>一眼看出哪些是「状态 / 意图区间」（Action）</li>
 *   <li>一眼看出哪些是「连续变化」（Transform / Path）</li>
 *   <li>一眼看出哪些是「瞬时事件」（ActionEvent / AnimationEvent）</li>
 * </ul>
 *
 * <p>视觉语法：
 * <table>
 *   <tr>
 *     <th>视觉类型</th>
 *     <th>视觉形态</th>
 *     <th>含义</th>
 *     <th>示例</th>
 *   </tr>
 *   <tr>
 *     <td>CURVE</td>
 *     <td>曲线 / 路径感</td>
 *     <td>连续变化</td>
 *     <td>TransformClip</td>
 *   </tr>
 *   <tr>
 *     <td>BLOCK</td>
 *     <td>实心区块</td>
 *     <td>状态区间</td>
 *     <td>ActorActionClip</td>
 *   </tr>
 *   <tr>
 *     <td>EVENT</td>
 *     <td>点 / 标记</td>
 *     <td>瞬时事件</td>
 *     <td>ActionEvent / AnimationEvent</td>
 *   </tr>
 * </table>
 *
 * <p>⚠️ 重要原则：
 * <ul>
 *   <li>这三种类型绝对不能共用同一种视觉风格</li>
 *   <li>颜色语义：ActionType 对应固定颜色，不是数值</li>
 *   <li>Block Clip 不显示关键帧</li>
 * </ul>
 *
 * @author Director Team
 * @version 1.0
 */
public enum TimelineClipVisualType {
    /**
     * 曲线类型（连续变化）
     * <p>用于 TransformClip 等需要显示关键帧和插值曲线的 Clip。
     * <p>视觉特征：
     * <ul>
     *   <li>线条（比 Block 更细）</li>
     *   <li>关键帧点（小圆点）</li>
     *   <li>颜色更"技术感"（青 / 绿）</li>
     * </ul>
     */
    CURVE,

    /**
     * 区块类型（状态区间）
     * <p>用于 ActorActionClip 等表示持续状态的 Clip。
     * <p>视觉特征：
     * <ul>
     *   <li>实心矩形（高度略大于 Curve）</li>
     *   <li>边角略微圆角（状态感）</li>
     * <li>中央显示 Action 名称</li>
     *   <li>不显示关键帧</li>
     *   <li>颜色 = 动作类别（稳定配色）</li>
     * </ul>
     */
    BLOCK,

    /**
     * 事件类型（瞬时）
     * <p>用于 ActionEvent / AnimationEvent 等瞬时事件。
     * <p>视觉特征：
     * <ul>
     *   <li>点 / 标记（◆ / ●）</li>
     *   <li>位置：Clip 内部 or 独立事件轨</li>
     *   <li>Hover 显示 tooltip</li>
     * </ul>
     */
    EVENT
}
