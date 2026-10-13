package com.github.lutzluca.btrbz.core.widgets.data;

import com.github.lutzluca.btrbz.core.appearance.UiColor;
import com.github.lutzluca.btrbz.core.orderdisplay.OrderTooltipData;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.utils.Utils;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Immutable, presentation-ready snapshots consumed by the Bazaar widgets. */
public final class BazaarWidgetViewData {
    private static final NumberFormat INTEGER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private BazaarWidgetViewData() {}

    public static String formatInt(long value) {
        return INTEGER_FORMAT.format(value);
    }

    public static String formatCompact(double value) {
        return Utils.formatCompact(value);
    }

    public static String formatPrice(double value) {
        return Utils.formatDecimal(value, 1, true);
    }

    public enum OrderSide {
        Buy("Buy"),
        Sell("Sell");

        private final String label;

        OrderSide(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public UiColor role() {
            return switch (this) {
                case Buy -> UiColor.Buy;
                case Sell -> UiColor.Sell;
            };
        }
    }

    public enum OrderStatus {
        Top("Best"),
        Matched("Matched"),
        Undercut("Undercut"),
        Unknown("Unknown");

        private final String label;

        OrderStatus(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }

        public UiColor role() {
            return switch (this) {
                case Top -> UiColor.Best;
                case Matched -> UiColor.Matched;
                case Undercut -> UiColor.Undercut;
                case Unknown -> UiColor.Unknown;
            };
        }
    }

    public record OrdersData(List<Order> orders, StatusCounts counts, int filledOrderCount) {
        public OrdersData {
            orders = List.copyOf(orders);
            counts = Objects.requireNonNull(counts, "counts");

            if (filledOrderCount < 0) {
                throw new IllegalArgumentException("filledOrderCount must be non-negative");
            }
        }

        public OrdersData(List<Order> orders) {
            this(orders, StatusCounts.from(orders), 0);
        }

        public OrdersData(List<Order> orders, int filledOrderCount) {
            this(orders, StatusCounts.from(orders), filledOrderCount);
        }
    }

    public record StatusCounts(int top, int matched, int undercut, int unknown) {
        public static StatusCounts from(List<Order> orders) {
            int top = 0;
            int matched = 0;
            int undercut = 0;
            int unknown = 0;

            for (var order : orders) {
                switch (order.status()) {
                    case Top -> top++;
                    case Matched -> matched++;
                    case Undercut -> undercut++;
                    case Unknown -> unknown++;
                }
            }

            return new StatusCounts(top, matched, undercut, unknown);
        }

        public int total() {
            return this.top + this.matched + this.undercut + this.unknown;
        }
    }

    public record Order(
        TrackedOrderId id,
        OrderSide side,
        String itemName,
        Component nativeName,
        Optional<ItemStack> itemStack,
        double unitPrice,
        int totalAmount,
        Optional<FillProgress> liveProgress,
        OrderStatus status,
        Optional<MarketInfo> marketInfo,
        OrderTooltipData tooltip,
        long creationSequence
    ) {
        public Order {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(itemName, "itemName");
            nativeName = Objects.requireNonNull(nativeName, "nativeName").copy();
            itemStack = itemStack.map(ItemStack::copy);

            if (totalAmount < 0) {
                throw new IllegalArgumentException("totalAmount must be non-negative");
            }

            liveProgress = Objects.requireNonNull(liveProgress, "liveProgress");
            liveProgress.ifPresent(progress -> {
                if (progress.total() != totalAmount) {
                    throw new IllegalArgumentException("live progress total must match totalAmount");
                }
            });
            Objects.requireNonNull(status, "status");
            marketInfo = Objects.requireNonNull(marketInfo, "marketInfo");
            Objects.requireNonNull(tooltip, "tooltip");
        }

        public Order(
            TrackedOrderId id,
            OrderSide side,
            String itemName,
            Component nativeName,
            Optional<ItemStack> itemStack,
            double unitPrice,
            int totalAmount,
            Optional<FillProgress> liveProgress,
            OrderStatus status,
            Optional<MarketInfo> marketInfo,
            OrderTooltipData tooltip
        ) {
            this(id, side, itemName, nativeName, itemStack, unitPrice, totalAmount,
                liveProgress, status, marketInfo, tooltip, 0);
        }

        public Order(
            TrackedOrderId id,
            OrderSide side,
            String itemName,
            Component nativeName,
            Optional<ItemStack> itemStack,
            double unitPrice,
            int totalAmount,
            int liveFilledAmount,
            OrderStatus status,
            OrderTooltipData tooltip
        ) {
            this(
                id, side, itemName, nativeName, itemStack, unitPrice, totalAmount,
                Optional.of(new FillProgress(liveFilledAmount, totalAmount)),
                status, Optional.empty(), tooltip, 0);
        }

        @Override
        public Optional<ItemStack> itemStack() {
            return this.itemStack.map(ItemStack::copy);
        }

        @Override
        public Component nativeName() {
            return this.nativeName.copy();
        }

        /** The stable volume originally placed. Live remaining volume belongs to {@link FillProgress}. */
        public int amount() {
            return this.totalAmount;
        }

        public String unitPriceText() {
            return formatCompact(this.unitPrice);
        }

        public String amountText() {
            return formatInt(this.totalAmount);
        }

    }

    public record FillProgress(int filled, int total) {
        public FillProgress {
            if (total < 0) {
                throw new IllegalArgumentException("total must be non-negative");
            }

            if (filled < 0 || filled > total) {
                throw new IllegalArgumentException("filled must be between zero and total");
            }
        }

        public int remaining() {
            return this.total - this.filled;
        }

        public double fraction() {
            return this.total == 0 ? 0 : (double) this.filled / this.total;
        }

        public String text() {
            return formatInt(this.filled) + " / " + formatInt(this.total);
        }
    }

    public record MarketInfo(
        OptionalDouble bestPrice,
        OptionalDouble priceDifference,
        OptionalInt ordersAhead,
        OptionalLong itemsAhead
    ) {
        public MarketInfo {
            bestPrice = Objects.requireNonNull(bestPrice, "bestPrice");
            priceDifference = Objects.requireNonNull(priceDifference, "priceDifference");
            ordersAhead = Objects.requireNonNull(ordersAhead, "ordersAhead");
            itemsAhead = Objects.requireNonNull(itemsAhead, "itemsAhead");
        }

        public static MarketInfo bestPrice(double bestPrice, double priceDifference) {
            return new MarketInfo(
                OptionalDouble.of(bestPrice), OptionalDouble.of(Math.abs(priceDifference)),
                OptionalInt.empty(), OptionalLong.empty());
        }

        public static MarketInfo queue(int ordersAhead, long itemsAhead) {
            return new MarketInfo(
                OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalInt.of(Math.max(0, ordersAhead)), OptionalLong.of(Math.max(0, itemsAhead)));
        }

        public static MarketInfo bestPriceAndQueue(
            double bestPrice,
            double priceDifference,
            int ordersAhead,
            long itemsAhead
        ) {
            return new MarketInfo(
                OptionalDouble.of(bestPrice), OptionalDouble.of(Math.abs(priceDifference)),
                OptionalInt.of(Math.max(0, ordersAhead)), OptionalLong.of(Math.max(0, itemsAhead)));
        }
    }

}
