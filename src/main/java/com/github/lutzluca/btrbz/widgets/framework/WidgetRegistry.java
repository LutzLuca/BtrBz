package com.github.lutzluca.btrbz.widgets.framework;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WidgetRegistry {
    private final Map<WidgetId, WidgetDefinition<?, ?>> definitions = new LinkedHashMap<>();
    private final List<WidgetDefinition<?, ?>> hudDefinitions = new ArrayList<>();
    private final List<WidgetDefinition<?, ?>> bazaarDefinitions = new ArrayList<>();
    private final List<WidgetDefinition<?, ?>> containerDefinitions = new ArrayList<>();

    public <T, A> void registerHud(WidgetDefinition<T, A> definition) {
        this.register(definition, this.hudDefinitions);
    }

    public <T, A> void registerBazaar(WidgetDefinition<T, A> definition) {
        this.register(definition, this.bazaarDefinitions);
    }

    public <T, A> void registerContainer(WidgetDefinition<T, A> definition) {
        this.register(definition, this.containerDefinitions);
    }

    public Optional<WidgetDefinition<?, ?>> find(WidgetId id) {
        return Optional.ofNullable(this.definitions.get(id));
    }

    public List<WidgetDefinition<?, ?>> all() {
        return List.copyOf(this.definitions.values());
    }

    public List<WidgetDefinition<?, ?>> hud() {
        return List.copyOf(this.hudDefinitions);
    }

    public List<WidgetDefinition<?, ?>> bazaar() {
        return List.copyOf(this.bazaarDefinitions);
    }

    public List<WidgetDefinition<?, ?>> container() {
        return List.copyOf(this.containerDefinitions);
    }

    private void register(
        WidgetDefinition<?, ?> definition,
        List<WidgetDefinition<?, ?>> destination
    ) {
        if (this.definitions.putIfAbsent(definition.getId(), definition) != null) {
            throw new IllegalArgumentException("Widget already registered: " + definition.getId());
        }

        destination.add(definition);
    }
}
