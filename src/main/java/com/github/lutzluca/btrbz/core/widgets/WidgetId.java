package com.github.lutzluca.btrbz.core.widgets;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public record WidgetId(Identifier identifier) {
    public WidgetId {
        Objects.requireNonNull(identifier, "identifier");
    }

    public static WidgetId of(Identifier identifier) {
        return new WidgetId(identifier);
    }

    public static WidgetId parse(String value) {
        return new WidgetId(Identifier.parse(value));
    }

    @Override
    public String toString() {
        return this.identifier.toString();
    }
}
