package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.dailylimit.DailyLimitWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarOrdersWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetData;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.ordervalue.OrderValueWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.pricedifference.PriceDifferenceWidgetConfig;
import com.github.lutzluca.btrbz.core.widgets.pricedifference.PriceDifferenceWidgetData;
import com.github.lutzluca.btrbz.core.widgets.pricedifference.PriceDifferenceWidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetProductContext;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.trackedorders.TrackedOrdersWidgetDefinition;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BtrBzWidgetDefinitionsTest {
    @Test
    void productionDefinitionIdsAreDistinct() {
        assertEquals(9, List.of(
            BazaarOrdersWidgetDefinition.ID,
            TrackedOrdersWidgetDefinition.ID,
            OrderValueWidgetDefinition.ID,
            OrderBookWidgetDefinition.ID,
            OrderBookPriceWidgetDefinition.ID,
            BookmarksWidgetDefinition.ID,
            OrderPresetsWidgetDefinition.ID,
            DailyLimitWidgetDefinition.ID,
            PriceDifferenceWidgetDefinition.ID
        ).stream().distinct().count());
    }

    @Test
    void definitionsAcceptOnlyTheirSemanticSessions() {
        assertTrue(BazaarOrdersWidgetDefinition.create(null).supports(
            session(true, false, false, null, null, false)
        ));
        assertTrue(TrackedOrdersWidgetDefinition.create(null, null).supports(
            session(false, false, false, BazaarMenuType.Item, null, false)
        ));
        assertTrue(OrderValueWidgetDefinition.create(null).supports(
            session(false, false, false, BazaarMenuType.Orders, null, false)
        ));
        assertTrue(OrderBookWidgetDefinition.create(new OrderBookWidgetData(null), null).supports(
            session(false, false, true, null, null, true)
        ));
        assertTrue(OrderBookPriceWidgetDefinition.create(new OrderBookWidgetData(null), null).supports(
            session(false, true, false, null, BazaarMenuType.BuyOrderSetupPrice, true)
        ));
        assertTrue(BookmarksWidgetDefinition.create(null).supports(
            session(false, false, false, BazaarMenuType.Main, null, false)
        ));
        assertTrue(OrderPresetsWidgetDefinition.create(null).supports(
            session(false, false, false, BazaarMenuType.BuyOrderSetupVolume, null, false)
        ));
        assertTrue(DailyLimitWidgetDefinition.create(null).supports(
            session(false, false, false, BazaarMenuType.ItemGroup, null, false)
        ));
        assertTrue(PriceDifferenceWidgetDefinition.create(null).supports(
            session(false, false, false, BazaarMenuType.Item, null, false)
        ));

        var invalid = session(false, false, false, BazaarMenuType.Settings, null, false);
        assertFalse(OrderValueWidgetDefinition.create(null).supports(invalid));
        assertFalse(DailyLimitWidgetDefinition.create(null).supports(invalid));
        assertFalse(PriceDifferenceWidgetDefinition.create(null).supports(invalid));

        var staleMenuOnCustomScreen = session(false, false, true, BazaarMenuType.Orders, null, true);
        assertFalse(OrderValueWidgetDefinition.create(null).supports(staleMenuOnCustomScreen));
        assertTrue(OrderBookWidgetDefinition.create(new OrderBookWidgetData(null), null).supports(staleMenuOnCustomScreen));
    }

    @Test
    void priceDifferenceVisibilityUsesItsOwnSnapshot() {
        var definition = PriceDifferenceWidgetDefinition.create(null);
        var unavailable = new PriceDifferenceWidgetData.Snapshot("Unavailable", ItemStack.EMPTY, 0, 0);
        var available = new PriceDifferenceWidgetData.Snapshot("Available", ItemStack.EMPTY, 1, 1);
        var session = session(false, false, false, BazaarMenuType.Item, null, false);
        assertFalse(definition.getVisibility().test(unavailable, new PriceDifferenceWidgetConfig(), session));
        assertTrue(definition.getVisibility().test(available, new PriceDifferenceWidgetConfig(), session));
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
                ProductIdentity.fromName("Product"), Component.literal("Product"), ItemStack.EMPTY
            ))
            : Optional.empty();
        return new WidgetSession(
            1, hud, sign, orderBook, false,
            Optional.ofNullable(menu), Optional.ofNullable(previous), true, context,
            sign ? Optional.of(OrderType.Buy) : Optional.empty(), 1, null
        );
    }
}
