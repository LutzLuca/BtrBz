package com.github.lutzluca.btrbz.widgets.framework;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Ephemeral state isolated to one host and widget definition. */
public final class WidgetInstanceState {
    private final Map<String, Object> values = new HashMap<>();

    public <T> T getOrCreate(String key, Class<T> type, Supplier<? extends T> factory) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        Object value = this.values.computeIfAbsent(key, ignored -> factory.get());
        if (!type.isInstance(value)) {
            throw new IllegalStateException("State key '" + key + "' contains " + value.getClass().getName()
                + ", expected " + type.getName());
        }
        return type.cast(value);
    }

    public void clear() {
        this.values.clear();
    }
}
