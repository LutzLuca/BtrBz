package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager.TrackedOrderSnapshot;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/** Exact price-level matches from the active personal menu snapshots, independent of public depth. */
public final class OrderBookMatches {
    private static final Match NONE = new Match(0, OptionalLong.empty());
    private final Map<Key, Match> levels;

    private OrderBookMatches(Map<Key, Match> levels) {
        this.levels = Map.copyOf(levels);
    }

    public static OrderBookMatches from(ProductIdentity product, List<TrackedOrderSnapshot> orders) {
        var levels = new HashMap<Key, Match>();
        var productId = product.bazaarProductId();
        if (productId.isEmpty()) {
            return new OrderBookMatches(levels);
        }

        for (var order : orders) {
            if (!productId.equals(order.product().bazaarProductId()) || !supportedPrice(order.pricePerUnit())) {
                continue;
            }

            var key = new Key(order.type(), order.pricePerUnit());
            var previous = levels.get(key);

            boolean known = order.slot() >= 0 && order.volume() > 0
                && order.fillAmountSnapshot() >= 0
                && order.fillAmountSnapshot() <= order.volume();
            OptionalLong remaining = known
                ? OptionalLong.of((long) order.volume() - order.fillAmountSnapshot()) : OptionalLong.empty();
            if (previous != null) {
                remaining = previous.remaining().isPresent() && remaining.isPresent()
                    ? OptionalLong.of(previous.remaining().getAsLong() + remaining.getAsLong()) : OptionalLong.empty();
            }

            levels.put(key, new Match(previous == null ? 1 : previous.count() + 1, remaining));
        }

        return new OrderBookMatches(levels);
    }

    public Match at(OrderType side, double price) {
        return this.levels.getOrDefault(new Key(side, price), NONE);
    }

    static boolean supportedPrice(double price) {
        return Double.isFinite(price) && price > 0 && BigDecimal.valueOf(price).stripTrailingZeros().scale() <= 1;
    }

    private record Key(OrderType side, double price) {}

    public record Match(int count, OptionalLong remaining) {}
}
