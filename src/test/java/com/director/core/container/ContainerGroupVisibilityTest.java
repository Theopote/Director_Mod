package com.director.core.container;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerGroupVisibilityTest {
    @Test
    void groupVisibilityMasksChildPreferencesWithoutOverwritingThem() {
        Container outer = new Container("outer", ContainerType.GROUP);
        Container inner = new Container("inner", ContainerType.GROUP);
        Container visibleChild = new Container("visible child", ContainerType.GENERAL);
        Container hiddenChild = new Container("hidden child", ContainerType.GENERAL);
        outer.addChild(inner);
        inner.addChild(visibleChild);
        inner.addChild(hiddenChild);
        hiddenChild.setState(ContainerState.HIDDEN, false);

        outer.setState(ContainerState.HIDDEN, false);

        assertTrue(visibleChild.isHidden());
        assertTrue(hiddenChild.isHidden());
        assertEquals(ContainerState.VISIBLE, visibleChild.getState());
        assertEquals(ContainerState.HIDDEN, hiddenChild.getState());

        visibleChild.setState(ContainerState.HIDDEN, false);
        outer.setState(ContainerState.VISIBLE, false);

        assertTrue(visibleChild.isHidden());
        assertTrue(hiddenChild.isHidden());
        assertEquals(ContainerState.HIDDEN, visibleChild.getState());
        assertEquals(ContainerState.HIDDEN, hiddenChild.getState());

        visibleChild.setState(ContainerState.VISIBLE, false);
        assertFalse(visibleChild.isHidden());
        assertTrue(hiddenChild.isHidden());
    }
}
