package com.github.lutzluca.btrbz.core.widgets.ui;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Retained widget components")
class RetainedWidgetComponentsTest {
    @Nested
    @DisplayName("keyed rows")
    class KeyedRows {
        @Test
        @DisplayName("reuse row identity while order and data change")
        void reusesRowsByKey() {
            var rows = new RetainedRows<String, TestRow>();
            var first = reconcile(rows, List.of(new Model("a", 1), new Model("b", 2)));

            var updated = reconcile(rows, List.of(new Model("b", 20), new Model("a", 10)));

            assertSame(first.get(1), updated.get(0));
            assertSame(first.get(0), updated.get(1));
            assertEquals(20, updated.get(0).value);
            assertEquals(10, updated.get(1).value);
        }

        @Test
        @DisplayName("drop removed identities and reject ambiguous keys")
        void dropsRemovedRowsAndRejectsDuplicates() {
            var rows = new RetainedRows<String, TestRow>();
            var original = reconcile(rows, List.of(new Model("a", 1))).getFirst();

            reconcile(rows, List.of());
            var recreated = reconcile(rows, List.of(new Model("a", 2))).getFirst();

            assertNotSame(original, recreated);
            assertThrows(IllegalArgumentException.class, () -> reconcile(
                rows,
                List.of(new Model("a", 1), new Model("a", 2))
            ));
        }
    }

    @Test
    @DisplayName("detached flow mutations are mounted on reattachment")
    void detachedFlowMutationsRemainDirty() {
        var host = RetainedFlowLayout.vertical(Sizing.fixed(100), Sizing.content());
        var branch = RetainedFlowLayout.vertical(Sizing.fixed(100), Sizing.content());
        var first = new PassiveComponent();
        branch.child(first);
        host.child(branch);
        host.mount(null, 0, 0);
        host.inflate(Size.of(100, 100));
        host.clearChildren();

        var replacement = new PassiveComponent();
        branch.clearChildren();
        branch.child(replacement);
        host.child(branch);

        assertSame(branch, replacement.parent());
        assertNotNull(replacement.focusHandler());
    }

    private static List<TestRow> reconcile(RetainedRows<String, TestRow> rows, List<Model> models) {
        return rows.reconcile(
            models,
            Model::id,
            (model, _) -> new TestRow(model.id()),
            (row, model, _) -> row.value = model.value()
        );
    }

    private record Model(String id, int value) {}

    private static final class TestRow {
        private final String id;
        private int value;

        private TestRow(String id) {
            this.id = id;
        }
    }

    private static final class PassiveComponent extends BaseUIComponent {
        private PassiveComponent() {
            this.sizing(Sizing.fixed(10));
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {}
    }
}
