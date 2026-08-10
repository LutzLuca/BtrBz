package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Bookmark row layout")
class BazaarBookmarkLayoutTest {
    @Nested
    @DisplayName("Scrollbar spacing")
    class ScrollbarSpacing {
        @Test
        @DisplayName("reserves trailing space only when the list overflows")
        void reservesTrailingSpaceOnlyForOverflowingLists() {
            int fiveRows = WidgetLayoutTokens.listViewportHeight(BazaarBookmarkRowComponent.HEIGHT, 5);

            assertFalse(BazaarBookmarkListComponent.reserveScrollbarSpace(5, fiveRows));
            assertFalse(BazaarBookmarkListComponent.reserveScrollbarSpace(2, fiveRows));
            assertTrue(BazaarBookmarkListComponent.reserveScrollbarSpace(6, fiveRows));
        }

        @Test
        @DisplayName("adds the shared scrollbar width and content gap")
        void addsScrollbarWidthAndContentGap() {
            assertEquals(
                WidgetLayoutTokens.ROW_HORIZONTAL_PADDING,
                WidgetLayoutTokens.rowTrailingInset(false)
            );
            assertEquals(
                WidgetLayoutTokens.ROW_HORIZONTAL_PADDING
                    + WidgetLayoutTokens.SCROLLBAR_THICKNESS
                    + WidgetLayoutTokens.SCROLLBAR_CONTENT_GAP,
                WidgetLayoutTokens.rowTrailingInset(true)
            );
        }
    }
}
