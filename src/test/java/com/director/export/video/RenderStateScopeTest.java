package com.director.export.video;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class RenderStateScopeTest {
    @Test void restoresFramebufferOutlineAndDimensionsInReverseOrder() {
        var main = new AtomicReference<>("window");
        var outline = new AtomicReference<>("original-outline");
        List<String> restored = new ArrayList<>();
        try (var state = new RenderStateScope()) {
            state.restoreWith(() -> restored.add(main.get() + ":" + outline.get() + ":" + ExportRenderDimensions.current()));
            state.replace(main::get, main::set, "export");
            state.replace(outline::get, outline::set, "export-outline");
            var dimensions = ExportRenderDimensions.enter(3840, 2160);
            state.restoreWith(dimensions::close);
            assertEquals(3840, ExportRenderDimensions.current().width());
        }
        assertEquals(List.of("window:original-outline:null"), restored);
    }

    @Test void partialSetupFailureStillRestoresAlreadyChangedTargets() {
        var main = new AtomicReference<>("window");
        assertThrows(IllegalArgumentException.class, () -> {
            try (var state = new RenderStateScope()) {
                state.replace(main::get, main::set, "export");
                state.replace(() -> "old", value -> { if (value.equals("bad")) throw new IllegalArgumentException(); }, "bad");
            }
        });
        assertEquals("window", main.get());
    }

    @Test void oneFailedRestoreDoesNotPreventRestoringOtherResources() {
        List<String> restored = new ArrayList<>();
        var scope = new RenderStateScope();
        scope.restoreWith(() -> restored.add("main"));
        scope.restoreWith(() -> { throw new IllegalStateException("outline"); });
        scope.restoreWith(() -> restored.add("dimensions"));
        assertThrows(IllegalStateException.class, scope::close);
        assertEquals(List.of("dimensions", "main"), restored);
        scope.close();
    }

    @Test void dimensionsAreThreadLocalAndSupportNestedRestoration() throws Exception {
        assertNull(ExportRenderDimensions.current());
        try (var outer = ExportRenderDimensions.enter(3840, 2160)) {
            var other = new AtomicReference<ExportRenderDimensions.Size>();
            Thread thread = new Thread(() -> other.set(ExportRenderDimensions.current()));
            thread.start(); thread.join();
            assertNull(other.get());
            try (var inner = ExportRenderDimensions.enter(1080, 1920)) {
                assertEquals(1920, ExportRenderDimensions.current().height());
            }
            assertEquals(3840, ExportRenderDimensions.current().width());
        }
        assertNull(ExportRenderDimensions.current());
        assertThrows(IllegalArgumentException.class, () -> ExportRenderDimensions.enter(100000, 2160));
        assertNull(ExportRenderDimensions.current());
    }
    @Test void repeatedDriverExceptionStillRestoresRemainingState() {
        var main = new AtomicReference<>("export");
        var failure = new IllegalStateException("device failure");
        var scope = new RenderStateScope();
        scope.restoreWith(() -> main.set("window"));
        scope.restoreWith(() -> { throw failure; });
        scope.restoreWith(() -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, scope::close));
        assertEquals("window", main.get());
    }

}
