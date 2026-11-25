package com.director.ui.feedback;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InlineNoticeTest {
    @Test
    void showsMessageOnlyForItsOwner() {
        InlineNotice notice = new InlineNotice();
        Object trackA = new Object();
        Object trackB = new Object();
        notice.show(trackA, InlineNotice.Level.WARNING, "locked");
        assertEquals("locked", notice.messageFor(trackA));
        assertEquals("", notice.messageFor(trackB));
    }

    @Test
    void switchingOwnerDiscardsTheOldMessage() {
        InlineNotice notice = new InlineNotice();
        Object trackA = new Object();
        notice.show(trackA, InlineNotice.Level.ERROR, "failed");
        notice.messageFor(new Object());
        assertEquals("", notice.messageFor(trackA));
    }

    @Test
    void clearRemovesTheMessage() {
        InlineNotice notice = new InlineNotice();
        Object owner = new Object();
        notice.show(owner, InlineNotice.Level.INFO, "done");
        notice.clear();
        assertEquals("", notice.messageFor(owner));
    }

    @Test
    void nullMessageIsTreatedAsEmptyAndLevelIsRequired() {
        InlineNotice notice = new InlineNotice();
        Object owner = new Object();
        notice.show(owner, InlineNotice.Level.INFO, null);
        assertEquals("", notice.messageFor(owner));
        assertThrows(NullPointerException.class, () -> notice.show(owner, null, "x"));
    }

    @Test
    void remembersTheLevelOfTheLastMessage() {
        InlineNotice notice = new InlineNotice();
        notice.show(new Object(), InlineNotice.Level.ERROR, "boom");
        assertEquals(InlineNotice.Level.ERROR, notice.level());
    }
}
