package com.github.lutzluca.btrbz.core.widgets.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsComponent.PresetState;
import com.github.lutzluca.btrbz.core.widgets.cache.ClipboardTracker;
import com.github.lutzluca.btrbz.core.widgets.cache.PurseTracker;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.screen.BazaarProductContext;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.screen.ScreenTracker.ScreenInfo;
import com.github.lutzluca.btrbz.utils.GameUtils;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrderPresetsComponentTest {
    @Test
    void pendingCancellationRetainsQuantityWorkflowButTransactionResetClearsIt() {
        var data = new BazaarData();
        var clipboard = new ClipboardTracker(() -> "");
        clipboard.initialize();
        var config = new OrderPresetsWidgetConfig();
        var presets = new OrderPresetsComponent(data, new BazaarProductContext(data), clipboard,
            new PurseTracker(Optional::empty), () -> config, () -> {});
        presets.onScreenSwitch(menu(BazaarMenuType.BuyOrderSetupVolume), menu(BazaarMenuType.Item));
        presets.onMaximumVolumeLoaded(List.of("Buy up to 128x"));

        presets.cancelPendingPreset();

        Assertions.assertTrue(presets.inTransaction());
        Assertions.assertEquals(128, presets.currentState().maximumVolume());

        presets.cancelTransaction();

        Assertions.assertFalse(presets.inTransaction());
        Assertions.assertEquals(GameUtils.GLOBAL_MAX_ORDER_VOLUME, presets.currentState().maximumVolume());
    }

    @Test
    void normalizesDurableConfiguredVolumesAscending() {
        assertEquals(
            List.of(2, 10),
            OrderPresetsComponent.normalizeConfiguredVolumes(Arrays.asList(10, null, -1, 2, 10)));
    }

    @Test
    void rendersMaximumClipboardThenAscendingConfiguredVolumes() {
        var states = OrderPresetsComponent.resolvePresets(
            List.of(10, 2, 2_000), 1_000, OptionalInt.of(3),
            Optional.of(10.0), Optional.of(55.0));
        assertEquals(List.of(
            new PresetState.Available(new OrderPreset.Maximum(), 5),
            new PresetState.Available(new OrderPreset.Clipboard(3), 3),
            new PresetState.Available(new OrderPreset.Fixed(2), 2),
            new PresetState.InsufficientCoins(new OrderPreset.Fixed(10))), states);
    }

    @Test
    void keepsCapturedFixedValuesAvailableWithoutMarketPrice() {
        assertEquals(List.of(
            new PresetState.PriceUnavailable(new OrderPreset.Maximum()),
            new PresetState.Available(new OrderPreset.Fixed(2), 2)),
            OrderPresetsComponent.resolvePresets(
                List.of(2), 1_000, OptionalInt.empty(), Optional.empty(), Optional.empty()));
    }

    @Test
    void distinguishesMissingPurseAndInsufficientCoins() {
        assertEquals(List.of(
            new PresetState.PurseUnavailable(new OrderPreset.Maximum()),
            new PresetState.PurseUnavailable(new OrderPreset.Fixed(2))),
            OrderPresetsComponent.resolvePresets(
                List.of(2), 1_000, OptionalInt.empty(), Optional.of(10.0), Optional.empty()));
        assertEquals(List.of(
            new PresetState.CannotAffordSingleItem(new OrderPreset.Maximum(), 5.0),
            new PresetState.InsufficientCoins(new OrderPreset.Fixed(2))),
            OrderPresetsComponent.resolvePresets(
                List.of(2), 1_000, OptionalInt.empty(), Optional.of(10.0), Optional.of(5.0)));
    }

    private static ScreenInfo menu(BazaarMenuType menu) {
        return new ScreenInfo(null) {
            @Override
            public boolean inMenu(BazaarMenuType candidate) {
                return candidate == menu;
            }
        };
    }
}
