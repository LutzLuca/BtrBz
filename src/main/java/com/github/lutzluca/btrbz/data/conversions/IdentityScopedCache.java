package com.github.lutzluca.btrbz.data.conversions;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

final class IdentityScopedCache<K, V> {
    private final Map<K, Optional<V>> values = new HashMap<>();
    private Object primaryScope;
    private Object secondaryScope;

    synchronized Optional<V> getOrResolve(
        Object primaryScope,
        Object secondaryScope,
        K key,
        Supplier<Optional<V>> resolver
    ) {
        this.updateScope(primaryScope, secondaryScope);
        if (this.values.containsKey(key)) {
            return this.values.get(key);
        }

        var resolved = resolver.get();
        this.values.put(key, resolved);
        return resolved;
    }

    synchronized int clear() {
        var size = this.values.size();
        this.values.clear();
        this.primaryScope = null;
        this.secondaryScope = null;
        return size;
    }

    private void updateScope(Object primaryScope, Object secondaryScope) {
        if (this.primaryScope == primaryScope && this.secondaryScope == secondaryScope) {
            return;
        }

        this.values.clear();
        this.primaryScope = primaryScope;
        this.secondaryScope = secondaryScope;
    }
}
