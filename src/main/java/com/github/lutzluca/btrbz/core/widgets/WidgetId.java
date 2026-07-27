package com.github.lutzluca.btrbz.core.widgets;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public final class WidgetId {
    private final Identifier value;

    private WidgetId(Identifier value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static WidgetId of(Identifier value) {
        return new WidgetId(value);
    }

    public static WidgetId parse(String value) {
        return new WidgetId(Identifier.parse(value));
    }

    public Identifier identifier() {
        return this.value;
    }

    @Override
    public String toString() {
        return this.value.toString();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof WidgetId widgetId && this.value.equals(widgetId.value);
    }

    @Override
    public int hashCode() {
        return this.value.hashCode();
    }
}
