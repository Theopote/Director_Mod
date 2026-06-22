#!/usr/bin/env python3
"""Refactor TimelineModelPersistence inline enum/UUID try-catch to ParseUtils."""
from __future__ import annotations

import re
from pathlib import Path

FILE = Path(__file__).resolve().parents[1] / "src/main/java/com/director/timeline/persistence/TimelineModelPersistence.java"


def enum_field(enum_fqn: str, suffix: str = "") -> str:
    parts = enum_fqn.split(".")
    name = parts[-1] if len(parts) >= 1 else enum_fqn
    if suffix:
        return f"{name}.{suffix}"
    return name


def main() -> None:
    text = FILE.read_text(encoding="utf-8")

    # obj.method(EnumType.valueOf(arg))
    pat_setter = re.compile(
        r"try\s*\{\s*(\w+)\.(set\w+|apply\w+)\(([\w.]+)\.valueOf\(([^)]+)\)\);\s*\}"
        r"\s*catch\s*\(Exception\s+ignored\)\s*\{\s*\}",
    )

    def repl_setter(m: re.Match[str]) -> str:
        obj, method, enum_fqn, arg = m.group(1), m.group(2), m.group(3), m.group(4)
        field = enum_field(enum_fqn)
        return (
            f"ParseUtils.setEnumIfValid({arg}, {enum_fqn}.class, "
            f'"{field}", {obj}::{method});'
        )

    text, n1 = pat_setter.subn(repl_setter, text)

    # var = EnumType.valueOf(arg)
    pat_assign = re.compile(
        r"try\s*\{\s*(\w+)\s*=\s*([\w.]+)\.valueOf\(([^)]+)\);\s*\}"
        r"\s*catch\s*\(Exception\s+ignored\)\s*\{\s*\}",
    )

    def repl_assign(m: re.Match[str]) -> str:
        var, enum_fqn, arg = m.group(1), m.group(2), m.group(3)
        field = enum_field(enum_fqn)
        return (
            f"{var} = ParseUtils.enumOr({arg}, {enum_fqn}.class, {var}, \"{field}\");"
        )

    text, n2 = pat_assign.subn(repl_assign, text)

    # obj.method(UUID.fromString(arg)) with optional java.util. prefix
    pat_uuid = re.compile(
        r"try\s*\{\s*(\w+)\.(set\w+)\((?:java\.util\.)?UUID\.fromString\(([^)]+)\)\);\s*\}"
        r"\s*catch\s*\(Exception\s+ignored\)\s*\{\s*\}",
    )

    def repl_uuid(m: re.Match[str]) -> str:
        obj, method, arg = m.group(1), m.group(2), m.group(3)
        field = f"{obj}.{method}"
        return (
            f"ParseUtils.setUuidIfValid({arg}, \"{field}\", {obj}::{method});"
        )

    text, n3 = pat_uuid.subn(repl_uuid, text)

    # ActorRef UUID constructor
    pat_actor_ref = re.compile(
        r"try\s*\{\s*(\w+)\.setActorRef\(new com\.director\.actor\.ActorRef\("
        r"(?:java\.util\.)?UUID\.fromString\(([^)]+)\)\)\);\s*\}"
        r"\s*catch\s*\(Exception\s+ignored\)\s*\{\s*\}",
    )

    def repl_actor_ref(m: re.Match[str]) -> str:
        obj, arg = m.group(1), m.group(2)
        return (
            f"{{ UUID actorRefId = ParseUtils.uuidOrNull({arg}, \"ActorTrack.actorRefId\"); "
            f"if (actorRefId != null) {obj}.setActorRef(new com.director.actor.ActorRef(actorRefId)); }}"
        )

    text, n4 = pat_actor_ref.subn(repl_actor_ref, text)

    # EntityClipType.valueOf without try - inside try block already
    # AnimationMode - already has warn log, convert to ParseUtils
    text = text.replace(
        """                if (clipDTO.animationMode != null) {
                    try {
                        config.setMode(AnimationMode.valueOf(clipDTO.animationMode));
                    } catch (IllegalArgumentException ignored) {
                        LOGGER.warn("[TimelineModelPersistence] 未知的动画模式: {}", clipDTO.animationMode);
                    }
                }""",
        """                if (clipDTO.animationMode != null) {
                    AnimationMode mode = ParseUtils.enumOrNull(clipDTO.animationMode, AnimationMode.class, "Clip.animationMode");
                    if (mode != null) {
                        config.setMode(mode);
                    } else {
                        LOGGER.warn("[TimelineModelPersistence] 未知的动画模式: {}", clipDTO.animationMode);
                    }
                }""",
    )

    # Marker UUID
    text = text.replace(
        """                UUID id = null;
                if (markerDTO.id != null && !markerDTO.id.isBlank()) {
                    try {
                        id = UUID.fromString(markerDTO.id);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[TimelineModelPersistence] 无法解析标记 ID: {}", markerDTO.id);
                    }
                }""",
        """                UUID id = ParseUtils.uuidOrNull(markerDTO.id, "TimelineMarker.id");
                if (markerDTO.id != null && !markerDTO.id.isBlank() && id == null) {
                    LOGGER.warn("[TimelineModelPersistence] 无法解析标记 ID: {}", markerDTO.id);
                }""",
    )

    # bound container id
    text = text.replace(
        """                if (clipDTO.boundContainerId != null && !clipDTO.boundContainerId.isBlank()) {
                    try {
                        clip.setBoundContainerId(UUID.fromString(clipDTO.boundContainerId));
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[TimelineModelPersistence] 无法解析容器 ID: {}", clipDTO.boundContainerId);
                    }
                }""",
        """                if (clipDTO.boundContainerId != null && !clipDTO.boundContainerId.isBlank()) {
                    UUID containerId = ParseUtils.uuidOrNull(clipDTO.boundContainerId, "Clip.boundContainerId");
                    if (containerId != null) {
                        clip.setBoundContainerId(containerId);
                    } else {
                        LOGGER.warn("[TimelineModelPersistence] 无法解析容器 ID: {}", clipDTO.boundContainerId);
                    }
                }""",
    )

    # getWorldDirectorDir
    text = text.replace(
        """        } catch (Exception ignored) {
        }
        return null;
    }
    
    /**
     * 时间线数据传输对象
     */""",
        """        } catch (RuntimeException e) {
            LOGGER.debug("[TimelineModelPersistence] Cannot resolve world save path: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 时间线数据传输对象
     */""",
    )

    # visibility keyframe empty catch
    text = text.replace(
        """                        try {
                            clip.addVisibilityKeyframe(new VisibilityKeyframe(vdto.time, vdto.alpha, mode));
                        } catch (Exception ignored) {
                            // 保持加载流程不中断
                        }""",
        """                        try {
                            clip.addVisibilityKeyframe(new VisibilityKeyframe(vdto.time, vdto.alpha, mode));
                        } catch (IllegalArgumentException e) {
                            LOGGER.debug("[TimelineModelPersistence] Skip invalid visibility keyframe at {}: {}",
                                    vdto.time, e.getMessage());
                        }""",
    )

    # entity keyframe restore empty catch
    text = text.replace(
        """                                        } catch (Exception ignored) {}
                                    }
                                }

                                // 类型特定字段""",
        """                                        } catch (RuntimeException e) {
                                            LOGGER.debug("[TimelineModelPersistence] Skip invalid entity clip keyframe at {}: {}",
                                                    kfdto.time, e.getMessage());
                                        }
                                    }
                                }

                                // 类型特定字段""",
    )

    # parseEnum / asFloat / asInt private methods -> ParseUtils
    text = text.replace("parseEnum(", "ParseUtils.enumFromObject(")
    text = re.sub(
        r'ParseUtils\.enumFromObject\(([^,]+),\s*([^,]+),\s*([^)]+)\)',
        r'ParseUtils.enumFromObject(\1, \2, \3, "sequenceParams")',
        text,
    )
    # Fix the above - enumFromObject needs 4 args, sequence params need distinct field names
    text = text.replace(
        'seqParams.setFlowType(ParseUtils.enumFromObject(raw.get("flowType"), FlowType.class, seqParams.getFlowType(), "sequenceParams"));',
        'seqParams.setFlowType(ParseUtils.enumFromObject(raw.get("flowType"), FlowType.class, seqParams.getFlowType(), "sequenceParams.flowType"));',
    )
    text = text.replace(
        'seqParams.setFillType(ParseUtils.enumFromObject(raw.get("fillType"), FillType.class, seqParams.getFillType(), "sequenceParams"));',
        'seqParams.setFillType(ParseUtils.enumFromObject(raw.get("fillType"), FillType.class, seqParams.getFillType(), "sequenceParams.fillType"));',
    )
    text = text.replace(
        'seqParams.setOriginType(ParseUtils.enumFromObject(raw.get("originType"), OriginType.class, seqParams.getOriginType(), "sequenceParams"));',
        'seqParams.setOriginType(ParseUtils.enumFromObject(raw.get("originType"), OriginType.class, seqParams.getOriginType(), "sequenceParams.originType"));',
    )
    text = text.replace('asFloat(raw.get(', 'ParseUtils.asFloat(raw.get(')
    text = text.replace('asInt(raw.get(', 'ParseUtils.asInt(raw.get(')
    text = text.replace('asBoolean(raw.get(', 'ParseUtils.asBoolean(raw.get(')

    # Add field names to asFloat/asInt calls - manual fix for known keys
    for key in ("planeYaw", "planePitch", "priorityWeight"):
        text = text.replace(
            f'ParseUtils.asFloat(raw.get("{key}"), seqParams.get',
            f'ParseUtils.asFloat(raw.get("{key}"), seqParams.get',
        )
    text = text.replace(
        'ParseUtils.asFloat(raw.get("planeYaw"), seqParams.getPlaneYaw())',
        'ParseUtils.asFloat(raw.get("planeYaw"), seqParams.getPlaneYaw(), "sequenceParams.planeYaw")',
    )
    text = text.replace(
        'ParseUtils.asFloat(raw.get("planePitch"), seqParams.getPlanePitch())',
        'ParseUtils.asFloat(raw.get("planePitch"), seqParams.getPlanePitch(), "sequenceParams.planePitch")',
    )
    text = text.replace(
        'ParseUtils.asFloat(raw.get("priorityWeight"), seqParams.getPriorityWeight())',
        'ParseUtils.asFloat(raw.get("priorityWeight"), seqParams.getPriorityWeight(), "sequenceParams.priorityWeight")',
    )
    text = text.replace(
        'ParseUtils.asInt(raw.get("customX"), seqParams.getCustomX())',
        'ParseUtils.asInt(raw.get("customX"), seqParams.getCustomX(), "sequenceParams.customX")',
    )
    text = text.replace(
        'ParseUtils.asInt(raw.get("customY"), seqParams.getCustomY())',
        'ParseUtils.asInt(raw.get("customY"), seqParams.getCustomY(), "sequenceParams.customY")',
    )
    text = text.replace(
        'ParseUtils.asInt(raw.get("customZ"), seqParams.getCustomZ())',
        'ParseUtils.asInt(raw.get("customZ"), seqParams.getCustomZ(), "sequenceParams.customZ")',
    )

    # Add import
    if "import com.director.util.ParseUtils;" not in text:
        text = text.replace(
            "import com.director.util.WorldIdentifier;",
            "import com.director.util.ParseUtils;\nimport com.director.util.WorldIdentifier;",
        )

    # Remove private parse helpers at end
    text = re.sub(
        r"\n    private static <E extends Enum<E>> E parseEnum\(.*?\n    \}\n\n    private static float asFloat\(.*?\n    \}\n\n    private static int asInt\(.*?\n    \}\n\n    private static boolean asBoolean\(.*?\n    \}\n",
        "\n",
        text,
        flags=re.DOTALL,
    )

    FILE.write_text(text, encoding="utf-8")
    print(f"Replacements: setter={n1}, assign={n2}, uuid={n3}, actorRef={n4}")


if __name__ == "__main__":
    main()
