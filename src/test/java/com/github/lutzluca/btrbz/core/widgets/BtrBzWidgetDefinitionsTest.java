package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.alerts.AlertsWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarOrdersWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.productcontext.ProductContextWidgetData;
import com.github.lutzluca.btrbz.core.widgets.productcontext.ProductContextWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetProductContext;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetDefinition;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import com.github.lutzluca.btrbz.data.BazaarData;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtrBzWidgetDefinitionsTest {
    @Test
    void definitionsAcceptOnlyTheirSemanticSessions() {
        assertTrue(AlertsWidgetDefinition.supportsSession(
            session(false, false, false, BazaarMenuType.Item, null, false)));
        assertTrue(AlertsWidgetDefinition.supportsSession(
            session(false, true, false, null, BazaarMenuType.BuyOrderSetupPrice, true)));
        assertFalse(AlertsWidgetDefinition.supportsSession(session(true, false, false, null, null, false)));
        assertFalse(AlertsWidgetDefinition.supportsSession(session(false, true, false, null, null, false)));
        assertTrue(BazaarOrdersWidgetDefinition.supportsSession(
            session(true, false, false, null, null, false)));
        assertTrue(TrackedOrdersWidgetDefinition.supportsSession(
            session(false, false, false, BazaarMenuType.Item, null, false)));
        assertTrue(OrderValueWidgetDefinition.supportsSession(
            session(false, false, false, BazaarMenuType.Orders, null, false)));
        assertTrue(OrderBookWidgetDefinition.supportsSession(
            session(false, false, true, null, null, true)));
        assertTrue(OrderBookPriceWidgetDefinition.supportsSession(
            session(false, true, false, null, BazaarMenuType.BuyOrderSetupPrice, true)));
        assertTrue(BookmarksWidgetDefinition.supportsSession(
            session(false, false, false, BazaarMenuType.Main, null, false)));
        assertTrue(OrderPresetsWidgetDefinition.supportsSession(
            session(false, false, false, BazaarMenuType.BuyOrderSetupVolume, null, false)));
        assertTrue(OrderPresetsWidgetDefinition.supportsSession(
            session(false, true, false, null, BazaarMenuType.BuyOrderSetupVolume, false)));
        assertTrue(DailyLimitWidgetDefinition.supportsSession(
            session(false, false, false, BazaarMenuType.ItemGroup, null, false)));
        assertTrue(ProductContextWidgetDefinition.supportsSession(
            session(false, false, false, BazaarMenuType.Item, null, false)));

        var invalid = session(false, false, false, BazaarMenuType.Settings, null, false);
        assertFalse(OrderValueWidgetDefinition.supportsSession(invalid));
        assertFalse(DailyLimitWidgetDefinition.supportsSession(invalid));
        assertFalse(ProductContextWidgetDefinition.supportsSession(invalid));

        var staleMenuOnCustomScreen = session(false, false, true, BazaarMenuType.Orders, null, true);
        assertFalse(OrderValueWidgetDefinition.supportsSession(staleMenuOnCustomScreen));
        assertTrue(OrderBookWidgetDefinition.supportsSession(staleMenuOnCustomScreen));
    }

    @Test
    void productContextVisibilityUsesItsOwnSnapshot() {
        var prices = new BazaarData.MarketPrices(Optional.empty(), Optional.empty());
        var unavailable = new ProductContextWidgetData.Snapshot(Optional.empty(), prices,
            Optional.empty(), OptionalInt.empty());
        var product = new WidgetProductContext(ProductIdentity.fromName("Available"),
            Component.literal("Available"), Optional.empty());
        var zero = new ProductContextWidgetData.Snapshot(Optional.of(product), prices,
            Optional.empty(), OptionalInt.of(0));
        var unknown = new ProductContextWidgetData.Snapshot(Optional.of(product), prices,
            Optional.empty(), OptionalInt.empty());
        assertFalse(ProductContextWidgetDefinition.isVisible(unavailable));
        assertTrue(ProductContextWidgetDefinition.isVisible(zero));
        assertTrue(ProductContextWidgetDefinition.isVisible(unknown));
    }

    private static WidgetSession session(
        boolean hud,
        boolean sign,
        boolean orderBook,
        BazaarMenuType menu,
        BazaarMenuType previous,
        boolean product
    ) {
        Optional<WidgetProductContext> context = product
            ? Optional.of(new WidgetProductContext(
                ProductIdentity.fromName("Product"), Component.literal("Product"), Optional.empty()))
            : Optional.empty();
        return new WidgetSession(
            1, hud, sign, orderBook,
            Optional.ofNullable(menu), Optional.ofNullable(previous), context,
            sign ? Optional.of(OrderType.Buy) : Optional.empty(), 1);
    }
}
