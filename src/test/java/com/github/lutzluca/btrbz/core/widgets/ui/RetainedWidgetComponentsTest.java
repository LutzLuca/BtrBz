package com.github.lutzluca.btrbz.core.widgets.ui;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

            Assertions.assertSame(first.get(1), updated.get(0));
            Assertions.assertSame(first.get(0), updated.get(1));
            Assertions.assertEquals(20, updated.get(0).value);
            Assertions.assertEquals(10, updated.get(1).value);
        }

        @Test
        @DisplayName("drop removed identities and reject ambiguous keys")
        void dropsRemovedRowsAndRejectsDuplicates() {
            var rows = new RetainedRows<String, TestRow>();
            var original = reconcile(rows, List.of(new Model("a", 1))).getFirst();

            reconcile(rows, List.of());
            var recreated = reconcile(rows, List.of(new Model("a", 2))).getFirst();

            Assertions.assertNotSame(original, recreated);
            Assertions.assertThrows(IllegalArgumentException.class, () -> reconcile(
                rows,
                List.of(new Model("a", 1), new Model("a", 2))));
        }
    }

    private static List<TestRow> reconcile(RetainedRows<String, TestRow> rows, List<Model> models) {
        return rows.reconcile(
            models,
            Model::id,
            (model, _) -> new TestRow(model.id()),
            (row, model, _) -> row.value = model.value());
    }

    private record Model(String id, int value) {}

    private static final class TestRow {
        private final String id;
        private int value;

        private TestRow(String id) {
            this.id = id;
        }
    }

}
