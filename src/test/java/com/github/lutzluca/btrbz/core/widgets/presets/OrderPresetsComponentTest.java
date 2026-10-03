package com.github.lutzluca.btrbz.core.widgets.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsComponent.PresetState;
import com.github.lutzluca.btrbz.core.Activation;
import com.github.lutzluca.btrbz.core.FeatureRuntime;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.cache.ClipboardTracker;
import com.github.lutzluca.btrbz.core.widgets.cache.PurseTracker;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.screen.BazaarProductContext;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.screen.ScreenTracker.ScreenInfo;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OrderPresetsComponentTest {
    @Test
    void suspensionPreservesQuantityWorkflowAndPermitsANewClickAfterRecovery() throws Exception {
        var data = new BazaarData();
        var presets = presets(data);
        try (var orders = new TrackedOrderManager(data)) {
            var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}),
                data, orders, () -> {}, presets::cancelPendingPreset, presets::cancelTransaction);
            runtime.activate();
            presets.onScreenSwitch(new MenuInfo(BazaarMenuType.BuyOrderSetupVolume), new MenuInfo(BazaarMenuType.Item));
            presets.onMaximumVolumeLoaded(List.of("Buy up to 128x"));
            var pending = OrderPresetsComponent.class.getDeclaredField("pendingPreset");
            pending.setAccessible(true);
            var pendingVolume = OrderPresetsComponent.class.getDeclaredField("pendingVolume");
            pendingVolume.setAccessible(true);
            pending.setBoolean(presets, true);
            pendingVolume.setInt(presets, 10);
            var quantitySign = quantitySign();

            runtime.hibernate();

            Assertions.assertTrue(presets.inTransaction());
            Assertions.assertEquals(128, presets.currentState().maximumVolume());
            Assertions.assertFalse(pending.getBoolean(presets));
            Assertions.assertEquals(-1, pendingVolume.getInt(presets));

            runtime.recover(BazaarData.prepareSnapshot(new Gson().fromJson("""
                {"products":{"TEST":{"sell_summary":[{"pricePerUnit":100,"amount":100,"orders":2}]}}}
                """, SkyBlockBazaarReply.class).getProducts()));

            Assertions
                .assertTrue(OrderPresetsActionHandler.canApply(quantitySign, quantitySign, presets.inTransaction()));
            Assertions.assertFalse(pending.getBoolean(presets));
            Assertions.assertEquals(-1, pendingVolume.getInt(presets));
            runtime.deactivate();
            Assertions.assertFalse(presets.inTransaction());
            Assertions.assertEquals(GameUtils.GLOBAL_MAX_ORDER_VOLUME, presets.currentState().maximumVolume());
            Assertions
                .assertFalse(OrderPresetsActionHandler.canApply(quantitySign, quantitySign, presets.inTransaction()));
        }
    }

    @Test
    void quantityWorkflowAndMaximumVolumeAreObservedDuringHibernate() {
        var data = new BazaarData();
        var presets = presets(data);
        try (var orders = new TrackedOrderManager(data)) {
            var runtime = new FeatureRuntime(new Activation(() -> true, () -> true, _ -> {}),
                data, orders, () -> {}, presets::cancelPendingPreset, presets::cancelTransaction);
            runtime.activate();
            runtime.hibernate();

            presets.onScreenSwitch(new MenuInfo(BazaarMenuType.BuyOrderSetupVolume), new MenuInfo(BazaarMenuType.Item));
            presets.onMaximumVolumeLoaded(List.of("Buy up to 64x"));

            Assertions.assertTrue(runtime.isHibernating());
            Assertions.assertTrue(presets.inTransaction());
            Assertions.assertEquals(64, presets.currentState().maximumVolume());
            var sign = quantitySign();
            Assertions.assertTrue(OrderPresetsActionHandler.canApply(sign, sign, presets.inTransaction()));
            presets.onScreenSwitch(new MenuInfo(BazaarMenuType.Orders),
                new MenuInfo(BazaarMenuType.BuyOrderSetupVolume));
            Assertions.assertFalse(presets.inTransaction());
            Assertions.assertEquals(GameUtils.GLOBAL_MAX_ORDER_VOLUME, presets.currentState().maximumVolume());
        }
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

    private static OrderPresetsComponent presets(BazaarData data) {
        var clipboard = new ClipboardTracker(() -> "");
        clipboard.initialize();
        var purse = new PurseTracker(() -> Optional.of(1000.0));
        var config = new OrderPresetsWidgetConfig();
        return new OrderPresetsComponent(data, new BazaarProductContext(data), clipboard, purse, () -> config,
            () -> {});
    }

    private static WidgetSession quantitySign() {
        return new WidgetSession(1, false, true, false, Optional.empty(),
            Optional.of(BazaarMenuType.BuyOrderSetupVolume), Optional.empty(), Optional.empty(), 0);
    }

    private static final class MenuInfo extends ScreenInfo {
        private final BazaarMenuType menu;

        private MenuInfo(BazaarMenuType menu) {
            super(null);
            this.menu = menu;
        }

        @Override
        public boolean inMenu(BazaarMenuType menu) {
            return this.menu == menu;
        }

        @Override
        public boolean inMenu(BazaarMenuType... menus) {
            return Arrays.asList(menus).contains(this.menu);
        }
    }
}
