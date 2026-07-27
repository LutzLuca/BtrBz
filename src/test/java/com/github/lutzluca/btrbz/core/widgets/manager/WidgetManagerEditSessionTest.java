package com.github.lutzluca.btrbz.core.widgets.manager;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Widget manager edit session")
class WidgetManagerEditSessionTest {
    @Nested
    @DisplayName("close")
    class Close {
        @Test
        @DisplayName("does not save an unchanged manager")
        void skipsCleanSession() {
            var saves = new AtomicInteger();
            var session = new WidgetManagerEditSession(saves::incrementAndGet);

            session.close();

            assertEquals(0, saves.get());
        }

        @Test
        @DisplayName("saves dirty state exactly once")
        void savesDirtySessionOnce() {
            var saves = new AtomicInteger();
            var session = new WidgetManagerEditSession(saves::incrementAndGet);
            session.markDirty();
            session.markDirty();

            session.close();
            session.close();

            assertEquals(1, saves.get());
        }
    }
}
