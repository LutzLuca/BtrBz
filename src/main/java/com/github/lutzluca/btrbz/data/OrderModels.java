package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.core.OrderHighlightManager;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.ToString;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class OrderModels {

    private OrderModels() { }

    public enum OrderType {
        Sell,
        Buy;

        public static Try<OrderType> tryFrom(String value) {
            return switch (value) {
                case "BUY" -> Try.success(OrderType.Buy);
                case "SELL" -> Try.success(OrderType.Sell);
                default ->
                    Try.failure(new IllegalArgumentException("Unknown order type: " + value));
            };
        }
    }

    // Note: `unclaimed` when type == OrderType.Buy in items; when type == OrderType.Sell in coins
    public sealed interface OrderInfo permits OrderInfo.UnfilledOrderInfo,
        OrderInfo.FilledOrderInfo {

        ProductIdentity product();

        String uiProductName();

        default String productName() {
            return this.product().strippedName();
        }

        OrderType type();

        int volume();

        double pricePerUnit();

        int slotIdx();

        int filledAmountSnapshot();

        int unclaimed();

        record UnfilledOrderInfo(
            ProductIdentity product,
            String uiProductName,
            OrderType type,
            int volume,
            double pricePerUnit,
            int filledAmountSnapshot,
            int unclaimed,
            int slotIdx
        ) implements OrderInfo {

            public UnfilledOrderInfo(
                String productName,
                OrderType type,
                int volume,
                double pricePerUnit,
                int filledAmountSnapshot,
                int unclaimed,
                int slotIdx
            ) {
                this(
                    ProductIdentity.fromName(productName),
                    productName,
                    type,
                    volume,
                    pricePerUnit,
                    filledAmountSnapshot,
                    unclaimed,
                    slotIdx
                );
            }

            public UnfilledOrderInfo withProduct(ProductIdentity product) {
                return new UnfilledOrderInfo(
                    product,
                    this.uiProductName,
                    this.type,
                    this.volume,
                    this.pricePerUnit,
                    this.filledAmountSnapshot,
                    this.unclaimed,
                    this.slotIdx
                );
            }
        }

        record FilledOrderInfo(
            ProductIdentity product,
            String uiProductName,
            OrderType type,
            int volume,
            double pricePerUnit,
            int filledAmountSnapshot,
            int unclaimed,
            int slotIdx
        ) implements OrderInfo {

            public FilledOrderInfo(
                String productName,
                OrderType type,
                int volume,
                double pricePerUnit,
                int filledAmountSnapshot,
                int unclaimed,
                int slotIdx
            ) {
                this(
                    ProductIdentity.fromName(productName),
                    productName,
                    type,
                    volume,
                    pricePerUnit,
                    filledAmountSnapshot,
                    unclaimed,
                    slotIdx
                );
            }

            public FilledOrderInfo withProduct(ProductIdentity product) {
                return new FilledOrderInfo(
                    product,
                    this.uiProductName,
                    this.type,
                    this.volume,
                    this.pricePerUnit,
                    this.filledAmountSnapshot,
                    this.unclaimed,
                    this.slotIdx
                );
            }
        }
    }

    public sealed abstract static class OrderStatus permits OrderStatus.Unknown,
        OrderStatus.Top,
        OrderStatus.Matched,
        OrderStatus.Undercut {

        @Override
        public final String toString() {
            return switch (this) {
                case Unknown _ -> "Unknown";
                case Top _ -> "Top";
                case Matched _ -> "Matched";
                case Undercut _ -> "Undercut";
            };
        }

        public final boolean sameVariant(OrderStatus other) {
            return other != null && this.getClass() == other.getClass();
        }

        public static final class Unknown extends OrderStatus { }

        public static final class Top extends OrderStatus { }

        public static final class Matched extends OrderStatus { }

        @AllArgsConstructor
        public static final class Undercut extends OrderStatus {

            public final double amount;
        }
    }

    @ToString
    public static class TrackedOrder {

        public ProductIdentity product;
        public String productName;
        public final String uiProductName;
        public final OrderType type;

        public final int volume;
        public final double pricePerUnit;
        public OrderStatus status = new OrderStatus.Unknown();
        public int slot;
        /**
         * The amount of items that were filled at the time this order was last viewed in the Bazaar UI.
         * This value is a snapshot from the UI and may differ from the actual Bazaar state.
         * It should ONLY be used for UI-side heuristics like the estimated fill time feature.
         */
        public int fillAmountSnapshot;

        public TrackedOrder(OrderInfo.UnfilledOrderInfo info) {
            this(info, info.product());
        }

        public TrackedOrder(OrderInfo.UnfilledOrderInfo info, ProductIdentity product) {
            this.product = product;
            this.productName = product.strippedName();
            this.uiProductName = info.uiProductName();
            this.type = info.type;
            this.volume = info.volume;
            this.pricePerUnit = info.pricePerUnit;
            this.slot = info.slotIdx;
            this.fillAmountSnapshot = info.filledAmountSnapshot;
        }

        public TrackedOrder(OutstandingOrderInfo info) {
            this.product = info.product();
            this.productName = this.product.strippedName();
            this.uiProductName = info.uiProductName();
            this.type = info.type;
            this.volume = info.volume;
            this.pricePerUnit = info.pricePerUnit;
            this.slot = -1;
            this.fillAmountSnapshot = 0;
        }

        public boolean matches(OrderInfo info) {
            // @formatter:off
            return (
                this.productsMatch(info)
                && this.type == info.type()
                && this.volume == info.volume()
                && Double.compare(this.pricePerUnit, info.pricePerUnit()) == 0
            );
            // @formatter:on
        }

        public void applyProduct(ProductIdentity product) {
            this.product = product;
            this.productName = product.strippedName();
        }

        private boolean productsMatch(OrderInfo info) {
            var currentId = this.product.bazaarProductId();
            var incomingId = info.product().bazaarProductId();
            if (currentId.isPresent() && incomingId.isPresent()) {
                return currentId.get().equals(incomingId.get());
            }

            return Utils
                .normalizeDisplayName(this.uiProductName)
                .equals(Utils.normalizeDisplayName(info.uiProductName()));
        }

        public MutableComponent format() {
            var typeStr = switch (type) {
                case Buy -> "Buy Order";
                case Sell -> "Sell Offer";
            };
            var visualName = this.product.visualName();
            var productNameComponent = Component.literal(visualName);

            return Component
                .empty()
                .append(Component
                    .literal("[" + this.status.toString() + "] ")
                    .withStyle(style -> Style.EMPTY.withColor(OrderHighlightManager.colorForStatus(this.status))))
                .append(Component.literal(typeStr).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(this.volume + "x ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(productNameComponent)
                .append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
                .append(Component
                    .literal(Utils.formatDecimal(this.pricePerUnit, 1, true) + "coins")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    public record OutstandingOrderInfo(
        ProductIdentity product,
        String uiProductName,
        OrderType type,
        int volume,
        double pricePerUnit,
        double total
    ) {

        public OutstandingOrderInfo(String productName, OrderType type, int volume, double pricePerUnit, double total) {
            this(ProductIdentity.fromName(productName), productName, type, volume, pricePerUnit, total);
        }

        public OutstandingOrderInfo withProduct(ProductIdentity product) {
            return new OutstandingOrderInfo(
                product,
                this.uiProductName,
                this.type,
                this.volume,
                this.pricePerUnit,
                this.total
            );
        }

        public String productName() {
            return this.product.strippedName();
        }

        public boolean matches(BazaarMessage.OrderSetup setupInfo) {
            // @formatter:off
            return Utils.normalizeDisplayName(this.uiProductName).equals(Utils.normalizeDisplayName(setupInfo.productName()))
                && this.type == setupInfo.type() 
                && this.volume == setupInfo.volume() 
                && Double.compare(this.total,setupInfo.total()) == 0;
            // @formatter:on
        }
    }
}
