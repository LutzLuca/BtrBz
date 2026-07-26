package com.github.lutzluca.btrbz.core.widgets;

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
        ), registry.all().stream().map(definition -> definition.id()).toList());
    }

    @Test
    void definitionsAcceptOnlyTheirProductionSessions() {
        var registry = registry();
        assertVisible(registry, BazaarWidgets.BAZAAR_ORDERS_ID, session(
            BtrBzWidgetSession.HostKind.HUD, null, null, false
        ));
        assertVisible(registry, BazaarWidgets.TRACKED_ORDERS_ID, session(
            BtrBzWidgetSession.HostKind.CONTAINER, BazaarMenuType.Item, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_VALUE_ID, session(
            BtrBzWidgetSession.HostKind.CONTAINER, BazaarMenuType.Orders, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_BOOK_SCREEN_ID, session(
            BtrBzWidgetSession.HostKind.ORDER_BOOK, null, null, true
        ));
        assertVisible(registry, BazaarWidgets.ORDER_BOOK_PRICE_ID, session(
            BtrBzWidgetSession.HostKind.SIGN, null, BazaarMenuType.BuyOrderSetupPrice, true
        ));
        assertVisible(registry, BazaarWidgets.BOOKMARKS_ID, session(
            BtrBzWidgetSession.HostKind.CONTAINER, BazaarMenuType.Main, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_PRESETS_ID, session(
            BtrBzWidgetSession.HostKind.CONTAINER, BazaarMenuType.BuyOrderSetupVolume, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_PRESETS_ID, session(
            BtrBzWidgetSession.HostKind.SIGN, null, BazaarMenuType.BuyOrderSetupVolume, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_LIMIT_ID, session(
            BtrBzWidgetSession.HostKind.CONTAINER, BazaarMenuType.Main, null, false
        ));
        assertVisible(registry, BazaarWidgets.ORDER_LIMIT_ID, session(
            BtrBzWidgetSession.HostKind.CONTAINER, BazaarMenuType.ItemGroup, null, false
        ));
        assertVisible(registry, BazaarWidgets.PRICE_DIFF_ID, session(
            BtrBzWidgetSession.HostKind.CONTAINER, BazaarMenuType.Item, null, false
        ));

        var invalid = new WidgetRenderContext(session(
            BtrBzWidgetSession.HostKind.CONTAINER, BazaarMenuType.Settings, null, false
        ));
        assertFalse(registry.find(BazaarWidgets.ORDER_VALUE_ID).orElseThrow().displayPredicate().test(invalid));
        assertFalse(registry.find(BazaarWidgets.ORDER_LIMIT_ID).orElseThrow().displayPredicate().test(invalid));
        assertFalse(registry.find(BazaarWidgets.PRICE_DIFF_ID).orElseThrow().displayPredicate().test(invalid));
    }

    @Test
    void dataPredicatesRetainValidZeroLimitButRejectUnavailablePriceDifference() {
        var registry = registry();
        var limit = registry.find(BazaarWidgets.ORDER_LIMIT_ID).orElseThrow();
        var diff = registry.find(BazaarWidgets.PRICE_DIFF_ID).orElseThrow();
        assertTrue(testData(limit, new BazaarData.DailyLimitData(0, 15_000_000_000L)));
        assertFalse(testData(diff, new BazaarData.PriceDifferenceData(
            "Unavailable", net.minecraft.world.item.ItemStack.EMPTY, 0, 0
        )));
        assertTrue(testData(diff, new BazaarData.PriceDifferenceData(
            "Zero spread", net.minecraft.world.item.ItemStack.EMPTY, 0, 1
        )));
    }

    private static WidgetRegistry registry() {
        var registry = new WidgetRegistry();
        var preview = new BazaarWidgetPreviewData();
        BazaarWidgets.register(
            registry, BazaarWidgetOptions::defaults, ignored -> null,
            preview, preview, (action, source, current) -> {}
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
            host == BtrBzWidgetSession.HostKind.SIGN ? Optional.of(OrderType.Buy) : Optional.empty(),
            1, null
        );
    }

    private static void assertVisible(
        WidgetRegistry registry,
        com.github.lutzluca.btrbz.widgets.framework.WidgetId id,
        BtrBzWidgetSession session
    ) {
        assertTrue(registry.find(id).orElseThrow().displayPredicate().test(new WidgetRenderContext(session)));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean testData(
        com.github.lutzluca.btrbz.widgets.framework.WidgetDefinition definition,
        Object data
    ) {
        return definition.dataDisplayPredicate().test(data);
    }
}
