package com.director.export.video;

/** Overrides physical framebuffer dimensions only on the render thread inside an export pass. */
public final class ExportRenderDimensions {
    public record Size(int width, int height) {}
    private static final ThreadLocal<Size> CURRENT = new ThreadLocal<>();
    private ExportRenderDimensions() {}

    public static Size current() { return CURRENT.get(); }

    static Scope enter(int width, int height) {
        if (width < 16 || height < 16 || width > 8192 || height > 8192)
            throw new IllegalArgumentException("离屏渲染尺寸无效");
        Size previous = CURRENT.get();
        CURRENT.set(new Size(width, height));
        return new Scope(previous);
    }

    static final class Scope implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final Size previous;
        private boolean closed;
        Scope(Size previous) { this.previous = previous; }
        @Override public void close() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("必须在原渲染线程恢复尺寸");
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
