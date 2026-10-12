package com.github.lutzluca.btrbz.core.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.FilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.core.appearance.AppearanceConfig;
import com.github.lutzluca.btrbz.core.config.Config;
import com.github.lutzluca.btrbz.core.appearance.AppearanceManager;
import com.github.lutzluca.btrbz.core.appearance.UiColor;
import com.github.lutzluca.btrbz.core.appearance.UiTheme;
import com.github.lutzluca.btrbz.core.orderdisplay.OrderHighlightManager;
import com.github.lutzluca.btrbz.core.orderdisplay.OrderTooltipProvider;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.cache.MemoizedWidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrdersWidgetDataTest {

    @Test
    void appearanceChangesRefreshSnapshotsWithTooltipsAndReuseUnthemedData() {
        var config = new Config();
        var appearance = new AppearanceManager(() -> config, () -> {});
        try {
            var market = new BazaarData();
            var manager = new TrackedOrderManager(market);
            var provider = new OrderTooltipProvider(market, new OrderHighlightManager());
            var screen = CacheToken.named("test.screen");
            var inventory = CacheToken.named("test.inventory");
            var themed = new MemoizedWidgetDataSource<>(new OrdersWidgetData(
                market, manager, provider, screen, inventory));
            var unthemed = new MemoizedWidgetDataSource<>(new OrdersWidgetData(
                market, manager, null, screen, inventory));
            var session = new WidgetSession(1, true, false, false,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0);
            var originalThemed = themed.snapshot(session);
            var originalUnthemed = unthemed.snapshot(session);

            appearance.apply(
                config.appearance.theme.withName("Changed").withPalette(
                    config.appearance.theme.palette().withColor(UiColor.Matched, 0xFF123456))
                    .withOrderHighlightOpacity(config.orderHighlight.opacity));

            Assertions.assertNotSame(originalThemed, themed.snapshot(session));
            Assertions.assertSame(originalUnthemed, unthemed.snapshot(session));
            Assertions.assertEquals(0x123456, UiStyles.color(UiColor.Matched).getColor().getValue());
        } finally {
            UiStyles.configure(new AppearanceConfig());
        }
    }

    @Test
    void exposesTheManagerOwnedFilledOrderCount() {
        var market = new BazaarData();
        var manager = new TrackedOrderManager(market);
        manager.syncOrders(List.of(
            new FilledOrderInfo("First", OrderType.Buy, 1, 10.0, 1, 1, 0),
            new FilledOrderInfo("Second", OrderType.Sell, 1, 20.0, 1, 20, 1)));
        var data = new OrdersWidgetData(
            market, manager, null,
            CacheToken.named("test.screen"), CacheToken.named("test.inventory"));

        assertEquals(2, data.computeSnapshot().filledOrderCount());
    }

    @Nested
    @DisplayName("observed product validation")
    class ObservedProductValidation {

        @Test
        void acceptsMatchingProductIds() {
            var expected = ProductIdentity.fromRuntime("Enchanted Diamond", "ENCHANTED_DIAMOND", null);
            var observed = ProductIdentity.fromRuntime("Different UI Name", "ENCHANTED_DIAMOND", null);

            assertTrue(OrdersWidgetData.sameProduct(expected, observed));
        }

        @Test
        void rejectsDifferentProductIdsEvenWhenNamesMatch() {
            var expected = ProductIdentity.fromRuntime("Enchanted Diamond", "ENCHANTED_DIAMOND", null);
            var observed = ProductIdentity.fromRuntime("Enchanted Diamond", "DIAMOND", null);

            assertFalse(OrdersWidgetData.sameProduct(expected, observed));
        }

        @Test
        void fallsBackToNormalizedNamesWhenAnIdIsUnavailable() {
            var expected = ProductIdentity.fromRuntime(" Enchanted   Diamond ", "ENCHANTED_DIAMOND", null);
            var observed = ProductIdentity.fromName("enchanted diamond");

            assertTrue(OrdersWidgetData.sameProduct(expected, observed));
        }

        @Test
        void rejectsDifferentProductsWithoutIds() {
            assertFalse(OrdersWidgetData.sameProduct(
                ProductIdentity.fromName("Enchanted Diamond"),
                ProductIdentity.fromName("Enchanted Emerald")));
        }
    }
}
