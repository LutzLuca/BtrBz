package com.github.lutzluca.btrbz.core.widgets.session;

import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookWidgetData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** Deterministic semantic contexts used only by widget-owned preview fixtures. */
public final class WidgetPreviewSessions {
    private WidgetPreviewSessions() {}

    public static WidgetSession hud() {
        return session(true, false, false, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static WidgetSession container(BazaarMenuType menu) {
        return session(false, false, false, Optional.of(menu), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static WidgetSession orderBook(OrderBookWidgetData.Snapshot data) {
        return session(false, false, true, Optional.empty(), Optional.empty(), product(data), Optional.empty());
    }

    public static WidgetSession sign(OrderBookWidgetData.Snapshot data) {
        return session(false, true, false, Optional.empty(), Optional.empty(), product(data),
            Optional.of(OrderType.Buy));
    }

    private static Optional<WidgetProductContext> product(OrderBookWidgetData.Snapshot data) {
        return Optional.of(new WidgetProductContext(
            ProductIdentity.fromName(data.itemName()), Component.literal(data.itemName()), data.itemStack()));
    }

    private static WidgetSession session(
        boolean hud,
        boolean sign,
        boolean orderBook,
        Optional<BazaarMenuType> menu,
        Optional<BazaarMenuType> previous,
        Optional<WidgetProductContext> product,
        Optional<OrderType> side
    ) {
        return new WidgetSession(1, hud, sign, orderBook, menu, previous, product, side, 1);
    }
}
