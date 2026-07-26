package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BookmarkDragControllerTest {
    @Test
    void keepsStableProductIdentityAndInsertionBoundary() {
        var drag = new BookmarkDragController();
        drag.start("BOOSTER_COOKIE", 1);
        drag.markMoved();
        drag.updateDropIndex(4);

        assertEquals(
            new BookmarkDragResult("BOOSTER_COOKIE", 1, 4, true),
            drag.finish()
        );
        assertFalse(drag.dragging());
    }
}
