package com.github.lutzluca.btrbz.core.widgets.presets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("Order presets widget data")
class OrderPresetsWidgetDataTest {
    @Nested
    @DisplayName("preview")
    class Preview {
        @Test
        @DisplayName("shows the clipboard amount and identifies its source in the tooltip")
        void showsClipboardAmount() {
            var presets = OrderPresetsWidgetData.preview().presets();
            var clipboard = presets.get(1);

            assertEquals("Max", presets.getFirst().label());
            assertInstanceOf(OrderPreset.Clipboard.class, clipboard.preset());
            assertEquals("320", clipboard.label());
            assertEquals("Clipboard", clipboard.tooltip());
        }
    }
}
