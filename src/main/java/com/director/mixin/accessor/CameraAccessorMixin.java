package com.director.mixin.accessor;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Camera 方法访问器 — 替代反射调用 setPos / setRotation。
 *
 * <p>使用 Mixin @Invoker 代替 Java 反射，优势：
 * <ul>
 *   <li>编译时检查：映射名变化时 Mixin 插件会报错（反射是运行时崩溃）</li>
 *   <li>零 JNI 开销：直接调用字节码</li>
 *   <li>跨版本更安全：Mixin 会自动处理中间映射</li>
 * </ul>
 */
@Mixin(Camera.class)
public interface CameraAccessorMixin {

    @Invoker("setPos")
    void director$invokeSetPos(double x, double y, double z);

    @Invoker("setRotation")
    void director$invokeSetRotation(float yaw, float pitch);
}
