package com.github.lutzluca.btrbz.core.widgets.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Keyed reconciliation used by retained widget lists without leaking row state into widgets. */
public final class RetainedRows<K, C> {
    private final Map<K, C> byKey = new LinkedHashMap<>();

    public <M> List<C> reconcile(
        List<M> models,
        Function<M, K> keyExtractor,
        BiFunction<M, Integer, C> factory,
        RowUpdater<C, M> updater
    ) {
        var retainedKeys = new HashSet<K>();
        var ordered = new ArrayList<C>(models.size());

        for (int index = 0; index < models.size(); index++) {
            int rowIndex = index;
            var model = models.get(index);
            var key = keyExtractor.apply(model);

            if (!retainedKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate retained row key: " + key);
            }

            var row = this.byKey.computeIfAbsent(key, _ -> factory.apply(model, rowIndex));

            updater.update(row, model, rowIndex);
            ordered.add(row);
        }

        this.byKey.keySet().removeIf(key -> !retainedKeys.contains(key));

        return ordered;
    }

    boolean contains(K key) {
        return this.byKey.containsKey(key);
    }

    @FunctionalInterface
    public interface RowUpdater<C, M> {
        void update(C component, M model, int index);
    }
}
