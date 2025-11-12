package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.core.OrderHighlightManager;
import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import lombok.ToString;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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

        String productName();

        OrderType type();

        int volume();

        double pricePerUnit();

        int slotIdx();

        int filledAmount();

        int unclaimed();

        record UnfilledOrderInfo(
            String productName,
            OrderType type,
            int volume,
            double pricePerUnit,
            int filledAmount,
            int unclaimed,
            int slotIdx
        ) implements OrderInfo { }

        record FilledOrderInfo(
            String productName,
            OrderType type,
            int volume,
            double pricePerUnit,
            int filledAmount,
            int unclaimed,
            int slotIdx
        ) implements OrderInfo { }
    }

    public sealed abstract static class OrderStatus permits OrderStatus.Unknown,
        OrderStatus.Top,
        OrderStatus.Matched,
        OrderStatus.Undercut {

        @Override
        public final String toString() {
            return switch (this) {
                case Unknown ignored -> "Unknown";
                case Top ignored -> "Top";
                case Matched ignored -> "Matched";
                case Undercut ignored -> "Undercut";
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

        public final String productName;
        public final OrderType type;

        public final int volume;
        public final double pricePerUnit;
        public OrderStatus status = new OrderStatus.Unknown();
        public int slot;


        public TrackedOrder(OrderInfo.UnfilledOrderInfo info) {
            this.productName = info.productName;
            this.type = info.type;
            this.volume = info.volume;
            this.pricePerUnit = info.pricePerUnit;
            this.slot = info.slotIdx;
        }

        public TrackedOrder(OutstandingOrderInfo info) {
            this.productName = info.productName;
            this.type = info.type;
            this.volume = info.volume;
            this.pricePerUnit = info.pricePerUnit;
            this.slot = -1;
        }

        public boolean matches(OrderInfo info) {
            // @formatter:off
            return (
                this.productName.equals(info.productName())
                && this.type == info.type()
                && this.volume == info.volume()
                && Double.compare(this.pricePerUnit, info.pricePerUnit()) == 0
            );
            // @formatter:on
        }

        public MutableText format() {
            var typeStr = switch (type) {
                case Buy -> "Buy Order";
                case Sell -> "Sell Offer";
            };

            return Text
                .empty()
                .append(Text
                    .literal("[" + this.status.toString() + "] ")
                    .styled(style -> Style.EMPTY.withColor(OrderHighlightManager.colorForStatus(this.status))))
                .append(Text.literal(typeStr).formatted(Formatting.AQUA))
                .append(Text.literal(" for ").formatted(Formatting.GRAY))
                .append(Text.literal(this.volume + "x ").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal(this.productName).formatted(Formatting.GOLD))
                .append(Text.literal(" at ").formatted(Formatting.GRAY))
                .append(Text
                    .literal(Utils.formatDecimal(this.pricePerUnit, 1, true) + "coins")
                    .formatted(Formatting.YELLOW));
        }
    }

    public record OutstandingOrderInfo(
        String productName, OrderType type, int volume, double pricePerUnit, double total
    ) {

        public boolean matches(BazaarMessage.OrderSetup setupInfo) {
            // @formatter:off
            return this.productName.equals(setupInfo.productName()) 
                && this.type == setupInfo.type() 
                && this.volume == setupInfo.volume() 
                && Double.compare(this.total,setupInfo.total()) == 0;
            // @formatter:on
        }
    }
}
