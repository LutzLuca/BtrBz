package com.github.lutzluca.btrbz.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A strong-key resolution cache invalidated when either owning context changes identity.
 * This is intended for values whose validity belongs to the lifetime of two context objects.
 */
public final class IdentityScopedCache<K, V> {
    private final Map<K, Optional<V>> values = new HashMap<>();
    private Object primaryScope;
    private Object secondaryScope;

    public synchronized Optional<V> getOrResolve(
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

    public synchronized int clear() {
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
