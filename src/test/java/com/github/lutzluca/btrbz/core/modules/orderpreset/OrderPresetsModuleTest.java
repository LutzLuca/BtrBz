package com.github.lutzluca.btrbz.core.modules.orderpreset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.core.modules.orderpreset.OrderPresetsModule.PresetState;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderPresetsModuleTest {

    @Nested
    @DisplayName("preset resolution")
    class PresetResolution {

        @Test
        void ordersEntriesAndResolvesAffordableVolumes() {
            var states = OrderPresetsModule.resolvePresets(
                List.of(2, 10, 2_000),
                1_000,
                OptionalInt.of(3),
                Optional.of(10.0),
                Optional.of(55.0),
                false
            );

            assertEquals(List.of(
                new PresetState.Available(new OrderPreset.Max(), 5),
                new PresetState.Available(new OrderPreset.Clipboard(3), 3),
                new PresetState.Available(new OrderPreset.Volume(2), 2),
                new PresetState.InsufficientCoins(new OrderPreset.Volume(10))
            ), states);
        }

        @Test
        void keepsExplicitVolumesAvailableWithoutPriceData() {
            var states = OrderPresetsModule.resolvePresets(
                List.of(2),
                1_000,
                OptionalInt.empty(),
                Optional.empty(),
                Optional.empty(),
                false
            );

            assertEquals(List.of(
                new PresetState.PriceUnavailable(new OrderPreset.Max()),
                new PresetState.Available(new OrderPreset.Volume(2), 2)
            ), states);
        }

        @Test
        void distinguishesMissingPurseFromInsufficientCoins() {
            var missingPurse = OrderPresetsModule.resolvePresets(
                List.of(2),
                1_000,
                OptionalInt.empty(),
                Optional.of(10.0),
                Optional.empty(),
                false
            );
            var insufficientCoins = OrderPresetsModule.resolvePresets(
                List.of(2),
                1_000,
                OptionalInt.empty(),
                Optional.of(10.0),
                Optional.of(5.0),
                false
            );

            assertEquals(List.of(
                new PresetState.PurseUnavailable(new OrderPreset.Max()),
                new PresetState.PurseUnavailable(new OrderPreset.Volume(2))
            ), missingPurse);
            assertEquals(List.of(
                new PresetState.CannotAffordSingleItem(new OrderPreset.Max(), 5.0),
                new PresetState.InsufficientCoins(new OrderPreset.Volume(2))
            ), insufficientCoins);
        }

        @Test
        void hidesOnlyUnaffordableStates() {
            var states = OrderPresetsModule.resolvePresets(
                List.of(2, 10),
                1_000,
                OptionalInt.empty(),
                Optional.of(10.0),
                Optional.of(55.0),
                true
            );

            assertEquals(List.of(
                new PresetState.Available(new OrderPreset.Max(), 5),
                new PresetState.Available(new OrderPreset.Volume(2), 2)
            ), states);
        }
    }
}
