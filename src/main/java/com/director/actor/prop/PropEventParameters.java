package com.director.actor.prop;

import com.director.actor.Actor;
import com.director.actor.rig.ActorRigCompatibility;
import com.director.actor.rig.skeleton.HumanoidRig;
import com.director.timeline.model.entity.EntityActionClip.ActionType;
import java.util.*;

/** Shared typed contract for authoring and dispatch; unknown JSON metadata is preserved. */
public final class PropEventParameters {
    private PropEventParameters() {}
    public static boolean supports(ActionType type) {
        return type == ActionType.ATTACH_PROP || type == ActionType.DETACH_PROP
                || type == ActionType.SWITCH_PROP || type == ActionType.THROW_PROP;
    }
    public static boolean supportsSlot(Actor actor, ActorPropSlot slot, ActionType type) {
        return actor != null && slot != null && ActorRigCompatibility.accepts(actor, HumanoidRig.DEFINITION.id())
                && (type != ActionType.THROW_PROP || slot == ActorPropSlot.MAIN_HAND
                    || slot == ActorPropSlot.OFF_HAND || slot == ActorPropSlot.BOTH_HANDS);
    }
    public static void validate(ActionType type, Map<String,Object> params) {
        if (!supports(type)) throw new IllegalArgumentException("此事件尚未接入演员运行时");
        requireFiniteMetadata(params,0);
        if (type == ActionType.SWITCH_PROP) {
            slot(params, "newSlot"); prop(params, "newPropId");
            if (params.containsKey("oldSlot")) slot(params, "oldSlot");
        } else {
            slot(params, "slot");
            if (type == ActionType.ATTACH_PROP) prop(params, "propId");
        }
        if (type == ActionType.ATTACH_PROP || type == ActionType.SWITCH_PROP)
            for (String key : List.of("offsetX","offsetY","offsetZ","rotationX","rotationY","rotationZ"))
                if (params.containsKey(key)) number(params,key);
        if (type == ActionType.THROW_PROP) {
            number(params,"targetX"); number(params,"targetY"); number(params,"targetZ");
            if (params.containsKey("arcHeight") && number(params,"arcHeight") < 0)
                throw new IllegalArgumentException("投掷弧高必须非负");
            if (params.containsKey("flightTime") && number(params,"flightTime") <= 0)
                throw new IllegalArgumentException("飞行时长必须大于零");
        }
    }
    public static void validateActor(Actor actor, ActionType type, Map<String,Object> params) {
        validate(type,params);
        for (String key : type == ActionType.SWITCH_PROP ? List.of("newSlot","oldSlot") : List.of("slot"))
            if (params.containsKey(key) && !supportsSlot(actor,slot(params,key),type))
                throw new IllegalArgumentException("当前演员或事件不支持此道具槽位");
    }
    public static ActorPropSlot slot(Map<String,Object> params, String key) {
        if (!(params.get(key) instanceof String value)) throw new IllegalArgumentException("缺少有效道具槽位："+key);
        try { return ActorPropSlot.valueOf(value); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("无效道具槽位："+key); }
    }
    private static void prop(Map<String,Object> params, String key) {
        if (!(params.get(key) instanceof String value) || PropRegistry.getInstance().get(value).isEmpty())
            throw new IllegalArgumentException("道具不存在："+key);
    }
    public static double number(Map<String,Object> params, String key) {
        if (!(params.get(key) instanceof Number value) || !Double.isFinite(value.doubleValue())
                || !Float.isFinite(value.floatValue())) throw new IllegalArgumentException("参数必须为有限数值："+key);
        return value.doubleValue();
    }
    private static void requireFiniteMetadata(Object value,int depth) {
        if (depth > 64) throw new IllegalArgumentException("事件参数嵌套过深");
        if (value instanceof Double d && !Double.isFinite(d) || value instanceof Float f && !Float.isFinite(f))
            throw new IllegalArgumentException("事件参数包含非有限数值");
        if (value instanceof Map<?,?> map) for (var item : map.values()) requireFiniteMetadata(item,depth+1);
        if (value instanceof List<?> list) for (var item : list) requireFiniteMetadata(item,depth+1);
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> copy(Map<String,Object> source, boolean immutable) {
        return (Map<String,Object>) copyValue(source,immutable,0);
    }
    private static Object copyValue(Object value, boolean immutable, int depth) {
        if (depth > 64) throw new IllegalArgumentException("事件参数嵌套过深");
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float
                || value instanceof Double || value instanceof java.math.BigDecimal || value instanceof java.math.BigInteger) return value;
        if (value instanceof Map<?,?> map) {
            var result = new LinkedHashMap<String,Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("事件参数键必须为字符串");
                result.put(key,copyValue(entry.getValue(),immutable,depth+1));
            }
            return immutable ? Collections.unmodifiableMap(result) : result;
        }
        if (value instanceof List<?> list) {
            var result = new ArrayList<>(); for (var item : list) result.add(copyValue(item,immutable,depth+1));
            return immutable ? Collections.unmodifiableList(result) : result;
        }
        throw new IllegalArgumentException("事件参数只支持 JSON 值");
    }
}
