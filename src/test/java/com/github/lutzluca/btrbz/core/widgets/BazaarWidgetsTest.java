package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetData;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetPreviewData;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.BtrBzWidgetSession;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.widgets.framework.WidgetRegistry;
import com.github.lutzluca.btrbz.widgets.framework.WidgetRenderContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BazaarWidgetsTest {
    @Test
    void registersExactlyTheNineProductionWidgetsInContractOrder() {
        var registry = registry();
        assertEquals(java.util.List.of(
            BazaarWidgets.BAZAAR_ORDERS_ID,
            BazaarWidgets.TRACKED_ORDERS_ID,
            BazaarWidgets.ORDER_VALUE_ID,
            BazaarWidgets.ORDER_BOOK_SCREEN_ID,
            BazaarWidgets.ORDER_BOOK_PRICE_ID,
            BazaarWidgets.BOOKMARKS_ID,
            BazaarWidgets.ORDER_PRESETS_ID,
            BazaarWidgets.ORDER_LIMIT_ID,
            BazaarWidgets.PRICE_DIFF_ID
        ), registry.all().stream().map(definition -> definition.getId()).toList());
    }

    @Test
    void definitionsAcceptOnlyTheirProductionSessions() {
        var registry = registry();
        assertVisible(registry, BazaarWidgets.BAZAAR_ORDERS_ID, session(
            BtrBzWidgetSession.HostKind.Hud, null, null, false
        ));
        assertVisible(registry, BazaarWidgets.TRACKED_ORDERS_ID, session(
            BtrBzWidgetSession.HostKind.Container, BazaarMenuType.Item, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_VALUE_ID, session(
            BtrBzWidgetSession.HostKind.Container, BazaarMenuType.Orders, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_BOOK_SCREEN_ID, session(
            BtrBzWidgetSession.HostKind.OrderBook, null, null, true
        ));
        assertVisible(registry, BazaarWidgets.ORDER_BOOK_PRICE_ID, session(
            BtrBzWidgetSession.HostKind.Sign, null, BazaarMenuType.BuyOrderSetupPrice, true
        ));
        assertVisible(registry, BazaarWidgets.BOOKMARKS_ID, session(
            BtrBzWidgetSession.HostKind.Container, BazaarMenuType.Main, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_PRESETS_ID, session(
            BtrBzWidgetSession.HostKind.Container, BazaarMenuType.BuyOrderSetupVolume, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_PRESETS_ID, session(
            BtrBzWidgetSession.HostKind.Sign, null, BazaarMenuType.BuyOrderSetupVolume, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_LIMIT_ID, session(
            BtrBzWidgetSession.HostKind.Container, BazaarMenuType.Main, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_LIMIT_ID, session(
            BtrBzWidgetSession.HostKind.Container, BazaarMenuType.ItemGroup, null, false
        ));
        assertVisible(registry, BazaarWidgets.PRICE_DIFF_ID, session(
            BtrBzWidgetSession.HostKind.Container, BazaarMenuType.Item, null, false
        ));

        var invalid = new WidgetRenderContext(session(
            BtrBzWidgetSession.HostKind.Container, BazaarMenuType.Settings, null, false
        ));
        assertFalse(registry.find(BazaarWidgets.ORDER_VALUE_ID).orElseThrow().getDisplayPredicate().test(invalid));
        assertFalse(registry.find(BazaarWidgets.ORDER_LIMIT_ID).orElseThrow().getDisplayPredicate().test(invalid));
        assertFalse(registry.find(BazaarWidgets.PRICE_DIFF_ID).orElseThrow().getDisplayPredicate().test(invalid));
    }

    @Test
    void dataPredicatesRetainValidZeroLimitButRejectUnavailablePriceDifference() {
        var registry = registry();
        var limit = registry.find(BazaarWidgets.ORDER_LIMIT_ID).orElseThrow();
        var diff = registry.find(BazaarWidgets.PRICE_DIFF_ID).orElseThrow();
        assertTrue(testData(limit, new BazaarWidgetViewData.DailyLimitData(0, 15_000_000_000L)));
        assertFalse(testData(diff, new BazaarWidgetViewData.PriceDifferenceData(
            "Unavailable", net.minecraft.world.item.ItemStack.EMPTY, 0, 0
        )));
        assertTrue(testData(diff, new BazaarWidgetViewData.PriceDifferenceData(
            "Zero spread", net.minecraft.world.item.ItemStack.EMPTY, 0, 1
        )));
    }

    private static WidgetRegistry registry() {
        var registry = new WidgetRegistry();
        var preview = new BazaarWidgetPreviewData();
        var runtime = new BazaarWidgetData(null, null, null, null, null, null, null, null);
        BazaarWidgets.register(
            registry, BazaarWidgetOptions::defaults, _ -> null,
            runtime, preview, (action, source, current) -> {}
        );
        return registry;
    }

    private static BtrBzWidgetSession session(
        BtrBzWidgetSession.HostKind host,
        BazaarMenuType menu,
        BazaarMenuType previous,
        boolean product
    ) {
        return new BtrBzWidgetSession(
            1, host, Optional.ofNullable(menu), Optional.ofNullable(previous), true,
            product ? Optional.of("PRODUCT") : Optional.empty(),
            host == BtrBzWidgetSession.HostKind.Sign ? Optional.of(OrderType.Buy) : Optional.empty(),
            1, null
        );
    }

    private static void assertVisible(
        WidgetRegistry registry,
        com.github.lutzluca.btrbz.widgets.framework.WidgetId id,
        BtrBzWidgetSession session
    ) {
        assertTrue(registry.find(id).orElseThrow().getDisplayPredicate().test(new WidgetRenderContext(session)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean testData(
        com.github.lutzluca.btrbz.widgets.framework.WidgetDefinition definition,
        Object data
    ) {
        return definition.getDataDisplayPredicate().test(data);
    }
}
