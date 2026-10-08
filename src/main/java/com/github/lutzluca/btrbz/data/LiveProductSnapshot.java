package com.github.lutzluca.btrbz.data;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;

/** Immutable market book using the player's order-side terminology. */
public record LiveProductSnapshot(
    ProductIdentity product,
    Optional<Instant> sourceUpdatedAt,
    MarketSide buyOrders,
    MarketSide sellOffers
) {
    public Optional<Double> buyPrice() {
        return this.sellOffers.levels().stream().findFirst().map(PriceLevel::price);
    }

    public Optional<Double> sellPrice() {
        return this.buyOrders.levels().stream().findFirst().map(PriceLevel::price);
    }

    static LiveProductSnapshot fromProduct(ProductIdentity identity, Optional<Instant> sourceUpdatedAt, Product raw) {
        var status = raw.getQuickStatus();
        var buyTotals = status == null
            ? Optional.<Totals>empty()
            : totals(status.getSellVolume(), status.getSellOrders());
        var sellTotals = status == null
            ? Optional.<Totals>empty()
            : totals(status.getBuyVolume(), status.getBuyOrders());
        return new LiveProductSnapshot(identity, sourceUpdatedAt,
            side(raw.getSellSummary(), Comparator.reverseOrder(), buyTotals),
            side(raw.getBuySummary(), Comparator.naturalOrder(), sellTotals));
    }

    private static Optional<Totals> totals(long items, long orders) {
        return items >= 0 && orders >= 0 ? Optional.of(new Totals(items, orders)) : Optional.empty();
    }

    private static MarketSide side(List<Summary> summaries, Comparator<Double> order, Optional<Totals> totals) {
        var levels = new TreeMap<Double, PriceLevel>(order);
        if (summaries != null) {
            for (var summary : summaries) {
                if (summary == null || !Double.isFinite(summary.getPricePerUnit())
                    || summary.getPricePerUnit() <= 0
                    || summary.getAmount() <= 0
                    || summary.getOrders() <= 0) {
                    continue;
                }
                var level = new PriceLevel(summary.getPricePerUnit(), summary.getAmount(), summary.getOrders());
                levels.merge(level.price(), level, (left, right) -> new PriceLevel(left.price(),
                    saturatedAdd(left.items(), right.items()), saturatedAdd(left.orders(), right.orders())));
            }
        }
        return new MarketSide(List.copyOf(levels.values()), totals);
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public record MarketSide(List<PriceLevel> levels, Optional<Totals> totals) {
        public MarketSide {
            levels = List.copyOf(levels);
        }
    }

    public record PriceLevel(double price, long items, long orders) {}

    public record Totals(long items, long orders) {}
}
