package com.github.lutzluca.btrbz.core.widgets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WidgetRegistry {
    private final List<WidgetDefinition<?, ?, ?>> definitions = new ArrayList<>();
    private final Map<WidgetId, WidgetDefinition<?, ?, ?>> byId = new LinkedHashMap<>();
    private boolean frozen;

    public void register(WidgetDefinition<?, ?, ?> definition) {
        if (this.frozen) throw new IllegalStateException("Widget registry is finalized");
        Objects.requireNonNull(definition, "definition");
        if (this.byId.putIfAbsent(definition.getId(), definition) != null) {
            throw new IllegalArgumentException("Widget already registered: " + definition.getId());
        }
        this.definitions.add(definition);
    }

    public List<WidgetDefinition<?, ?, ?>> all() { return List.copyOf(this.definitions); }
    public Optional<WidgetDefinition<?, ?, ?>> find(WidgetId id) {
        return Optional.ofNullable(this.byId.get(id));
    }

    void freeze() {
        this.frozen = true;
    }
}
