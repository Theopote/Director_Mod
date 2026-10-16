package com.director.core.container.visibility;

import com.director.core.container.BlockStore;
import com.director.core.container.Container;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class VisibilityTaskBudgetTest {
    @Test void panelTaskRetainsItsCursorWhenDeadlineExpiresAndResumesWithinCountBudget() throws Exception {
        var service = new ContainerVisibilityService(null);
        var taskClass = Class.forName(ContainerVisibilityService.class.getName() + "$VisibilityTask");
        var kind = Class.forName(ContainerVisibilityService.class.getName() + "$TaskKind").getEnumConstants()[0];
        var constructor = taskClass.getDeclaredConstructors()[0]; constructor.setAccessible(true);
        // Null entries exercise the real task cursor without requiring a running Minecraft world.
        var task = constructor.newInstance(service, new Container("large"), null, new BlockStore(),
                Collections.nCopies(10000, null), kind, "hide");
        var process = taskClass.getDeclaredMethod("process", int.class, long.class); process.setAccessible(true);
        var remaining = taskClass.getDeclaredMethod("remaining"); remaining.setAccessible(true);
        assertEquals(0, process.invoke(task, 10000, 0L));
        assertEquals(10000, remaining.invoke(task));
        assertEquals(7, process.invoke(task, 7, Long.MAX_VALUE));
        assertEquals(9993, remaining.invoke(task));
        assertEquals(0, process.invoke(task, 10000, 0L));
        assertEquals(9993, remaining.invoke(task));
        assertEquals(9993, process.invoke(task, 10000, Long.MAX_VALUE));
        assertEquals(0, remaining.invoke(task));
    }
}
