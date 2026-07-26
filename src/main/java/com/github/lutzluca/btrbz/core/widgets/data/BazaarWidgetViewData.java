package com.github.lutzluca.btrbz.core.widgets.data;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.core.widgets.hud.BazaarHudOptions;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPreset;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarNumberFormat;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;

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
        return BazaarNumberFormat.compact(value);
    }

    public static String formatPrice(double value) {
        var format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(1);
        format.setMaximumFractionDigits(1);
        return format.format(value);
    }

    public enum OrderSide {
        Buy("Buy", BazaarStyles.BUY_ACCENT),
        Sell("Sell", BazaarStyles.SELL_ACCENT);

        private final String label;
        private final int accentColor;

        OrderSide(String label, int accentColor) {
            this.label = label;
            this.accentColor = accentColor;
        }

        public String label() {
            return this.label;
        }

        public int accentColor() {
            return this.accentColor;
        }
    }

    public enum OrderStatus {
        Top("Best", BazaarStyles.STATUS_TOP),
        Matched("Matched", BazaarStyles.STATUS_MATCHED),
        Undercut("Undercut", BazaarStyles.STATUS_UNDERCUT),
        Unknown("Unknown", BazaarStyles.STATUS_UNKNOWN);

        private final String label;
        private final int color;

        OrderStatus(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public String label() {
            return this.label;
        }

        public int color() {
            return this.color;
        }
    }

    public record OrdersData(List<Order> orders, StatusCounts counts, int filledOrderCount) {
        public OrdersData {
            orders = List.copyOf(orders);
            counts = Objects.requireNonNull(counts, "counts");
            if (filledOrderCount < 0) throw new IllegalArgumentException("filledOrderCount must be non-negative");
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
        Component formattedItemName,
        ItemStack icon,
        long unitPrice,
        int totalAmount,
        Optional<FillProgress> liveProgress,
        OrderStatus status,
        Optional<MarketInfo> marketInfo,
        List<Component> tooltipLines,
        long creationSequence
    ) {
        public Order {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(itemName, "itemName");
            Objects.requireNonNull(formattedItemName, "formattedItemName");
            icon = icon.copy();
            if (totalAmount < 0) throw new IllegalArgumentException("totalAmount must be non-negative");
            liveProgress = Objects.requireNonNull(liveProgress, "liveProgress");
            liveProgress.ifPresent(progress -> {
                if (progress.total() != totalAmount) {
                    throw new IllegalArgumentException("live progress total must match totalAmount");
                }
            });
            Objects.requireNonNull(status, "status");
            marketInfo = Objects.requireNonNull(marketInfo, "marketInfo");
            tooltipLines = List.copyOf(tooltipLines);
        }

        public Order(
            TrackedOrderId id,
            OrderSide side,
            String itemName,
            Component formattedItemName,
            ItemStack icon,
            long unitPrice,
            int totalAmount,
            Optional<FillProgress> liveProgress,
            OrderStatus status,
            Optional<MarketInfo> marketInfo,
            List<Component> tooltipLines
        ) {
            this(id, side, itemName, formattedItemName, icon, unitPrice, totalAmount,
                liveProgress, status, marketInfo, tooltipLines, 0);
        }

        public Order(
            TrackedOrderId id,
            OrderSide side,
            String itemName,
            Component formattedItemName,
            ItemStack icon,
            long unitPrice,
            int totalAmount,
            int liveFilledAmount,
            OrderStatus status,
            List<Component> tooltipLines
        ) {
            this(
                id, side, itemName, formattedItemName, icon, unitPrice, totalAmount,
                Optional.of(new FillProgress(liveFilledAmount, totalAmount)),
                status, Optional.empty(), tooltipLines, 0
            );
        }

        @Override
        public ItemStack icon() {
            return this.icon.copy();
        }

        public ItemStack iconCopy() {
            return this.icon.copy();
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

        public String totalPriceText() {
            return formatCompact((double) this.unitPrice * this.totalAmount);
        }

        public Component formattedItemName(boolean abbreviateEnchanted) {
            if (!abbreviateEnchanted) return this.formattedItemName.copy();
            return Component.literal(BazaarHudOptions.productName(this.itemName, abbreviateEnchanted))
                .setStyle(this.formattedItemName.getStyle());
        }
    }

    public record FillProgress(int filled, int total) {
        public FillProgress {
            if (total < 0) throw new IllegalArgumentException("total must be non-negative");
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
                OptionalInt.empty(), OptionalLong.empty()
            );
        }

        public static MarketInfo queue(int ordersAhead, long itemsAhead) {
            return new MarketInfo(
                OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalInt.of(Math.max(0, ordersAhead)), OptionalLong.of(Math.max(0, itemsAhead))
            );
        }

        public static MarketInfo bestPriceAndQueue(
            double bestPrice,
            double priceDifference,
            int ordersAhead,
            long itemsAhead
        ) {
            return new MarketInfo(
                OptionalDouble.of(bestPrice), OptionalDouble.of(Math.abs(priceDifference)),
                OptionalInt.of(Math.max(0, ordersAhead)), OptionalLong.of(Math.max(0, itemsAhead))
            );
        }
    }

    public record OrderBookEntry(OrderSide side, double price, int quantity, int orders) {
        public String priceText() {
            return formatPrice(this.price);
        }

        public String quantityText() {
            return formatInt(this.quantity);
        }
    }

    public record OrderValueData(long buyLocked, long buyItems, long sellClaimable, long sellPending, long total) {}

    public record Bookmark(
        String productId,
        String productName,
        Component formattedProductName,
        ItemStack icon,
        boolean buyOrder,
        boolean sellOrder
    ) {
        public Bookmark {
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(productName, "productName");
            Objects.requireNonNull(formattedProductName, "formattedProductName");
            icon = icon.copy();
        }

        public Bookmark(String productId, String productName, ItemStack icon, boolean buyOrder, boolean sellOrder) {
            this(productId, productName, Component.literal(productName), icon, buyOrder, sellOrder);
        }

        @Override
        public ItemStack icon() {
            return this.icon.copy();
        }

        public ItemStack iconCopy() {
            return this.icon.copy();
        }

        public Component formattedProductName(boolean abbreviateEnchanted) {
            if (!abbreviateEnchanted) return this.formattedProductName.copy();
            return Component.literal(BazaarHudOptions.productName(this.productName, abbreviateEnchanted))
                .setStyle(this.formattedProductName.getStyle());
        }
    }

    public record BookmarksData(List<Bookmark> bookmarks) {
        public BookmarksData {
            bookmarks = List.copyOf(bookmarks);
        }
    }

    public record Preset(OrderPreset preset, String label, String tooltip, boolean available) {
        public Preset {
            Objects.requireNonNull(preset, "preset");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(tooltip, "tooltip");
        }
    }

    public record PresetsData(List<Preset> presets) {
        public PresetsData {
            presets = List.copyOf(presets);
        }
    }

    public record PriceDifferenceData(String productName, ItemStack icon, long perItem, int quantity) {
        public PriceDifferenceData {
            icon = icon.copy();
        }

        @Override
        public ItemStack icon() {
            return this.icon.copy();
        }

        public ItemStack iconCopy() {
            return this.icon.copy();
        }

        public long total() {
            return this.perItem * this.quantity;
        }
    }

    public record OrderBookData(
        String itemName,
        ItemStack icon,
        List<OrderBookEntry> buyOffers,
        List<OrderBookEntry> sellOffers,
        Optional<OrderSide> appropriateSide
    ) {
        public OrderBookData {
            icon = icon.copy();
            buyOffers = List.copyOf(buyOffers);
            sellOffers = List.copyOf(sellOffers);
            appropriateSide = Objects.requireNonNull(appropriateSide, "appropriateSide");
        }

        @Override
        public ItemStack icon() {
            return this.icon.copy();
        }

        public ItemStack iconCopy() {
            return this.icon.copy();
        }
    }

    public record DailyLimitData(long used, long limit) {
        public DailyLimitData {
            if (used < 0 || limit <= 0) throw new IllegalArgumentException("limit values must be positive");
        }
    }

}
