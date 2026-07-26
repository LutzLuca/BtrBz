package com.github.lutzluca.btrbz.core.widgets.presets;

/** Immutable semantic preset captured at click time. */
public sealed interface OrderPreset permits OrderPreset.Fixed, OrderPreset.Clipboard, OrderPreset.Maximum {
    record Fixed(int amount) implements OrderPreset {
        public Fixed {
            if (amount <= 0) throw new IllegalArgumentException("Preset amount must be positive");
        }
    }

    record Clipboard(int amount) implements OrderPreset {
        public Clipboard {
            if (amount <= 0) throw new IllegalArgumentException("Clipboard amount must be positive");
        }
    }

    record Maximum() implements OrderPreset {}
}
