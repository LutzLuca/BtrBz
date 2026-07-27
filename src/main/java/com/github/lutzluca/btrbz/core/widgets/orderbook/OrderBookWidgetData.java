package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared order-book snapshots for the custom-screen and sign widgets. */
public final class OrderBookWidgetData {
    private final BazaarData market;

    public OrderBookWidgetData(BazaarData market) {
        this.market = market;
    }

    public Snapshot snapshot(WidgetSession session) {
        ProductIdentity product = session.product().map(context -> context.identity()).orElse(null);
        String name = "Order Book";
        ItemStack icon = ItemStack.EMPTY;
        Optional<BazaarWidgetViewData.OrderSide> appropriateSide = Optional.empty();
        if (session.product().isPresent()) {
            var context = session.product().orElseThrow();
            name = context.displayName().getString();
            icon = context.icon();
        }
        if (session.inSign()) {
            appropriateSide = session.side().map(side -> side == OrderType.Buy
                ? BazaarWidgetViewData.OrderSide.Buy
                : BazaarWidgetViewData.OrderSide.Sell);
        }
        if (product == null) {
            return new Snapshot(name, icon, List.of(), List.of(), appropriateSide);
        }
        var lists = this.market.getOrderLists(product);
        return new Snapshot(
            name,
            icon,
            lists.buyOrders().stream().map(summary -> new Entry(
                BazaarWidgetViewData.OrderSide.Buy, summary.getPricePerUnit(),
                (int) summary.getAmount(), (int) summary.getOrders()
            )).toList(),
            lists.sellOffers().stream().map(summary -> new Entry(
                BazaarWidgetViewData.OrderSide.Sell, summary.getPricePerUnit(),
                (int) summary.getAmount(), (int) summary.getOrders()
            )).toList(),
            appropriateSide
        );
    }

    public static Snapshot preview() {
        return new Snapshot(
            "Booster Cookie", new ItemStack(Items.COOKIE),
            previewLevels(BazaarWidgetViewData.OrderSide.Buy, 9_811_000.1),
            previewLevels(BazaarWidgetViewData.OrderSide.Sell, 9_835_000.0),
            Optional.of(BazaarWidgetViewData.OrderSide.Sell)
        );
    }

    private static List<Entry> previewLevels(
        BazaarWidgetViewData.OrderSide side,
        double start
    ) {
        var values = new ArrayList<Entry>();
        for (int index = 0; index < 6; index++) {
            values.add(new Entry(
                side,
                start + (side == BazaarWidgetViewData.OrderSide.Buy ? -index : index) * 12_500,
                18 + index * 23,
                5 + index * 4
            ));
        }
        return values;
    }

    public record Entry(BazaarWidgetViewData.OrderSide side, double price, int quantity, int orders) {
        public String priceText() {
            return BazaarWidgetViewData.formatPrice(this.price);
        }

        public String quantityText() {
            return BazaarWidgetViewData.formatInt(this.quantity);
        }
    }

    public record Snapshot(
        String itemName,
        ItemStack icon,
        List<Entry> buyOffers,
        List<Entry> sellOffers,
        Optional<BazaarWidgetViewData.OrderSide> appropriateSide
    ) {
        public Snapshot {
            icon = icon.copy();
            buyOffers = List.copyOf(buyOffers);
            sellOffers = List.copyOf(sellOffers);
            appropriateSide = java.util.Objects.requireNonNull(appropriateSide, "appropriateSide");
        }

        @Override
        public ItemStack icon() {
            return this.icon.copy();
        }

        public ItemStack iconCopy() {
            return this.icon.copy();
        }
    }
}
