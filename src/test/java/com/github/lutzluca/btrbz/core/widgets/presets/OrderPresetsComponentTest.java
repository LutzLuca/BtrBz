package com.github.lutzluca.btrbz.core.widgets.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsComponent.PresetState;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class OrderPresetsComponentTest {
    @Test
    void normalizesDurableConfiguredVolumesAscending() {
        assertEquals(
            List.of(2, 10),
            OrderPresetsComponent.normalizeConfiguredVolumes(java.util.Arrays.asList(10, null, -1, 2, 10))
        );
    }

    @Test
    void rendersMaximumClipboardThenAscendingConfiguredVolumes() {
        var states = OrderPresetsComponent.resolvePresets(
            List.of(10, 2, 2_000), 1_000, OptionalInt.of(3),
            Optional.of(10.0), Optional.of(55.0)
        );
        assertEquals(List.of(
            new PresetState.Available(new OrderPreset.Maximum(), 5),
            new PresetState.Available(new OrderPreset.Clipboard(3), 3),
            new PresetState.Available(new OrderPreset.Fixed(2), 2),
            new PresetState.InsufficientCoins(new OrderPreset.Fixed(10))
        ), states);
    }

    @Test
    void keepsCapturedFixedValuesAvailableWithoutMarketPrice() {
        assertEquals(List.of(
            new PresetState.PriceUnavailable(new OrderPreset.Maximum()),
            new PresetState.Available(new OrderPreset.Fixed(2), 2)
        ), OrderPresetsComponent.resolvePresets(
            List.of(2), 1_000, OptionalInt.empty(), Optional.empty(), Optional.empty()
        ));
    }

    @Test
    void distinguishesMissingPurseAndInsufficientCoins() {
        assertEquals(List.of(
            new PresetState.PurseUnavailable(new OrderPreset.Maximum()),
            new PresetState.PurseUnavailable(new OrderPreset.Fixed(2))
        ), OrderPresetsComponent.resolvePresets(
            List.of(2), 1_000, OptionalInt.empty(), Optional.of(10.0), Optional.empty()
        ));
        assertEquals(List.of(
            new PresetState.CannotAffordSingleItem(new OrderPreset.Maximum(), 5.0),
            new PresetState.InsufficientCoins(new OrderPreset.Fixed(2))
        ), OrderPresetsComponent.resolvePresets(
            List.of(2), 1_000, OptionalInt.empty(), Optional.of(10.0), Optional.of(5.0)
        ));
    }
}
