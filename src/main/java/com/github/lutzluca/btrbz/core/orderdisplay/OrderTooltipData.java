package com.github.lutzluca.btrbz.core.orderdisplay;

import com.github.lutzluca.btrbz.core.trackedorders.OrderManagerConfig.QueueDisplayMode;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import org.jetbrains.annotations.Nullable;

/** Computed tooltip sections, independent of the appearance used to display them. */
public record OrderTooltipData(
    @Nullable String unknownProduct,
    @Nullable OrderStatus status,
    @Nullable Double estimatedMinutes,
    @Nullable Queue queue,
    @Nullable OwnOrder ownOrder,
    @Nullable MarketPrices prices
) {
    public static final OrderTooltipData EMPTY = new OrderTooltipData(null, null, null, null, null, null);

    public record Queue(int ordersAhead, int itemsAhead, QueueDisplayMode displayMode) {}

    public record OwnOrder(double unitPrice, int volume) {}
}
