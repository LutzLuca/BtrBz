package com.github.lutzluca.btrbz.core.modules.orderpreset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.modules.orderpreset.OrderPresetsModule.PresetUnavailableReason;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderPresetsModuleTest {

    @Nested
    @DisplayName("preset availability")
    class PresetAvailability {

        @Test
        void describesMaxClipboardAndConfiguredVolumes() {
            var descriptions = OrderPresetsModule.describePresets(
                List.of(2, 10, 2_000),
                1_000,
                OptionalInt.of(3),
                Optional.of(10.0),
                Optional.of(55.0),
                false
            );

            assertEquals(List.of("Max", "3", "2", "10"), descriptions
                .stream()
                .map(OrderPresetsModule.PresetDescription::displayText)
                .toList());
            assertEquals(5, descriptions.getFirst().resolvedVolume().orElseThrow());
            assertTrue(descriptions.get(1).canApply());
            assertFalse(descriptions.getLast().canApply());
            assertEquals(
                PresetUnavailableReason.INSUFFICIENT_COINS,
                descriptions.getLast().unavailableReason()
            );
        }

        @Test
        void hidesUnaffordableEntriesButKeepsExplicitAmountsWithoutPriceData() {
            var hidden = OrderPresetsModule.describePresets(
                List.of(2, 10),
                1_000,
                OptionalInt.empty(),
                Optional.of(10.0),
                Optional.of(55.0),
                true
            );
            var withoutPrice = OrderPresetsModule.describePresets(
                List.of(2),
                1_000,
                OptionalInt.empty(),
                Optional.empty(),
                Optional.empty(),
                false
            );

            assertEquals(List.of("Max", "2"), hidden
                .stream()
                .map(OrderPresetsModule.PresetDescription::displayText)
                .toList());
            assertFalse(withoutPrice.getFirst().canApply());
            assertTrue(withoutPrice.getLast().canApply());
        }
    }
}
