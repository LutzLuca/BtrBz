package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import com.github.lutzluca.btrbz.core.orderbook.OwnOrderLevels;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;

/** Shared order-book snapshots for the custom-screen and sign widgets. */
public final class OrderBookWidgetData implements WidgetDataSource<OrderBookWidgetData.Snapshot> {
    private final BazaarData market;
    private final TrackedOrderManager orders;
    private final CacheDependencies dependencies;

    public OrderBookWidgetData(
        BazaarData market,
        TrackedOrderManager orders
    ) {
        this.market = market;
        this.orders = orders;
        this.dependencies = CacheDependencies.of(market.marketChanges(), market.indexChanges(), orders.dataChanges());
    }

    @Override
    public CacheDependencies cacheDependencies() {
        return this.dependencies;
    }

    @Override
    public Snapshot snapshot(WidgetSession session) {
        ProductIdentity product = session.product().map(context -> context.identity()).orElse(null);
        Component name = Component.literal("Order Book");
        Optional<ItemStack> itemStack = Optional.empty();
        Optional<BazaarWidgetViewData.OrderSide> appropriateSide = Optional.empty();
        if (session.product().isPresent()) {
            var context = session.product().orElseThrow();
            name = product.formattedName() == null
                ? context.displayName()
                : GameUtils.legacyFormattedComponent(product.visualName());
            itemStack = context.itemStack();
        }
        if (session.inSign()) {
            appropriateSide = session.side().map(side -> side == OrderType.Buy
                ? BazaarWidgetViewData.OrderSide.Buy
                : BazaarWidgetViewData.OrderSide.Sell);
        }
        if (product == null) {
            return new Snapshot(name, itemStack, List.of(), List.of(), appropriateSide);
        }
        var lists = this.market.getOrderLists(product);
        var own = OwnOrderLevels.from(product, this.orders.currentOrders());
        return new Snapshot(
            name,
            itemStack,
            lists.buyOrders().stream().map(summary -> new Entry(
                BazaarWidgetViewData.OrderSide.Buy, summary.getPricePerUnit(),
                summary.getAmount(), summary.getOrders(), own.at(OrderType.Buy, summary.getPricePerUnit()).count()))
                .toList(),
            lists.sellOffers().stream().map(summary -> new Entry(
                BazaarWidgetViewData.OrderSide.Sell, summary.getPricePerUnit(),
                summary.getAmount(), summary.getOrders(), own.at(OrderType.Sell, summary.getPricePerUnit()).count()))
                .toList(),
            appropriateSide);
    }

    public static Snapshot preview() {
        return new Snapshot(
            Component.literal("Booster Cookie").withStyle(ChatFormatting.GOLD),
            Optional.of(new ItemStack(Items.COOKIE)),
            previewLevels(BazaarWidgetViewData.OrderSide.Buy, 9_811_000.1),
            previewLevels(BazaarWidgetViewData.OrderSide.Sell, 9_835_000.0),
            Optional.of(BazaarWidgetViewData.OrderSide.Sell));
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
                5 + index * 4, index == 1 ? 2 : index == 4 ? 1 : 0));
        }
        return values;
    }

    public record Entry(BazaarWidgetViewData.OrderSide side, double price, long quantity, long orders, int ownOrders) {
        public String ordersText(boolean detailed) {
            return BazaarWidgetViewData.formatInt(this.orders)
                + (detailed && this.ownOrders > 0 ? " (" + this.ownOrders + ")" : "");
        }

        public String priceText() {
            return BazaarWidgetViewData.formatPrice(this.price);
        }

        public String quantityText() {
            return BazaarWidgetViewData.formatInt(this.quantity);
        }
    }

    public record Snapshot(
        Component nativeName,
        Optional<ItemStack> itemStack,
        List<Entry> buyOffers,
        List<Entry> sellOffers,
        Optional<BazaarWidgetViewData.OrderSide> appropriateSide
    ) {
        public Snapshot {
            nativeName = nativeName.copy();
            itemStack = itemStack.map(ItemStack::copy);
            buyOffers = List.copyOf(buyOffers);
            sellOffers = List.copyOf(sellOffers);
        }

        public String itemName() {
            return this.nativeName.getString();
        }

        @Override
        public Component nativeName() {
            return this.nativeName.copy();
        }

        @Override
        public Optional<ItemStack> itemStack() {
            return this.itemStack.map(ItemStack::copy);
        }
    }
}
