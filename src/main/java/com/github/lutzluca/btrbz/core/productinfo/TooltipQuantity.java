package com.github.lutzluca.btrbz.core.productinfo;

sealed interface TooltipQuantity {

    record Stack(int amount) implements TooltipQuantity {
        public Stack {
            if (amount < 1) {
                throw new IllegalArgumentException("Stack quantity must be positive");
            }
        }
    }

    record OriginalOrder(int amount) implements TooltipQuantity {
        public OriginalOrder {
            if (amount < 1) {
                throw new IllegalArgumentException("Original order quantity must be positive");
            }
        }
    }

    record UnavailableOrder() implements TooltipQuantity {}

    default int priceMultiplier(boolean shiftHeld) {
        if (!shiftHeld) {
            return 1;
        }
        return switch (this) {
            case Stack stack -> stack.amount();
            case OriginalOrder order -> order.amount();
            case UnavailableOrder _ -> 1;
        };
    }
}
