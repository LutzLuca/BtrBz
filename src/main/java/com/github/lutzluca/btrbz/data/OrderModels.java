package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.utils.Util;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;

public final class OrderModels {

    private OrderModels() { }

    public enum OrderType {
        Sell, Buy;

        public static Try<OrderType> tryFrom(String value) {
            return switch (value) {
                case "BUY" -> Try.success(OrderType.Buy);
                case "SELL" -> Try.success(OrderType.Sell);
                default ->
                    Try.failure(new IllegalArgumentException("Unknown order type: " + value));
            };
        }
    }

    public sealed abstract static class OrderStatus permits OrderStatus.Unknown, OrderStatus.Top,
        OrderStatus.Matched, OrderStatus.Undercut {

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

    public static class TrackedOrder {

        public final String productName;
        public final OrderType type;

        public final int volume;
        public final double pricePerUnit;
        public OrderStatus status = new OrderStatus.Unknown();
        public int slot;


        public TrackedOrder(OrderInfo info, int slot) {
            this.productName = info.productName;
            this.type = info.type;
            this.volume = info.volume;
            this.pricePerUnit = info.pricePerUnit;
            this.slot = slot;
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
                this.productName.equals(info.productName)
                && this.type == info.type
                && this.volume == info.volume
                && Double.compare(this.pricePerUnit, info.pricePerUnit) == 0
            );
            // @formatter:on
        }

        @Override
        public String toString() {
            var typeStr = switch (type) {
                case Buy -> "Buy Order";
                case Sell -> "Sell Offer";
            };

            return String.format("[%s] %s for %dx %s at %scoins per", status.toString(), typeStr,
                volume, productName, Util.formatDecimal(pricePerUnit, 1)
            );
        }
    }

    public record OrderInfo(
        String productName,
        OrderType type,
        int volume,
        double pricePerUnit,
        boolean filled,
        int slotIdx
    ) {

        public boolean notFilled() {
            return !this.filled;
        }
    }

    public record OutstandingOrderInfo(
        String productName, OrderType type, int volume, double pricePerUnit, double total
    ) {

        public boolean matches(ChatOrderConfirmationInfo chatOrder) {
            return this.type == chatOrder.type && this.productName.equalsIgnoreCase(
                chatOrder.productName) && this.volume == chatOrder.volume && Double.compare(
                this.total, chatOrder.total) == 0;
        }
    }

    public record ChatOrderConfirmationInfo(
        String productName, OrderType type, int volume, double total
    ) { }

    public record ChatFilledOrderInfo(String productName, OrderType type, int volume) { }
}
