package com.github.lutzluca.btrbz.core.widgets.presets;

public sealed interface OrderPresetsAction permits OrderPresetsAction.Apply {
    record Apply(OrderPreset preset) implements OrderPresetsAction {}
}
