package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderGrouping.SelfUndercutMatchKey;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class SelfUndercutDetector {

    private final Map<SelfUndercutMatchKey, SelfUndercutPricePair> state = new HashMap<>();

    void clear() {
        this.state.clear();
    }

    void remove(SelfUndercutMatchKey key) {
        this.state.remove(key);
    }

    void removeIfLastOrder(TrackedOrder removedOrder, List<TrackedOrder> remainingOrders) {
        var key = SelfUndercutMatchKey.from(removedOrder);
        boolean removedLastOrder = remainingOrders.stream()
            .noneMatch(curr -> SelfUndercutMatchKey.from(curr).equals(key));

        log.debug(
            "Removed last order for {}: {}",
            removedOrder.product.bazaarProductId().orElse(removedOrder.productName),
            removedLastOrder
        );
        if (removedLastOrder) {
            this.state.remove(key);
        }
    }

    List<SelfUndercutEvent> resolve(List<TrackedOrder> trackedOrders, MarketSnapshot snapshot) {
        var keys = trackedOrders
            .stream()
            .map(SelfUndercutMatchKey::from)
            .distinct()
            .toList();

        this.state.keySet().retainAll(keys);
        var events = new ArrayList<SelfUndercutEvent>();

        for (var key : keys) {
            var result = this.computeSelfUndercutState(key, trackedOrders, snapshot);
            var existing = this.state.get(key);

            if (result instanceof SelfUndercutResult.Undercut undercut) {
                boolean pricesChanged = existing == null
                    || Double.compare(existing.bestPrice(), undercut.bestPrice()) != 0
                    || Double.compare(existing.secondBestPrice(), undercut.secondBestPrice()) != 0;
                if (!pricesChanged) {
                    continue;
                }

                var displayKey = this.displayKey(key, trackedOrders);
                if (displayKey.isEmpty()) {
                    log.warn("Detected self-undercut for {}, but no tracked order could display it", key);
                    this.state.remove(key);
                    continue;
                }

                this.state.put(key, new SelfUndercutPricePair(
                    undercut.bestPrice(),
                    undercut.secondBestPrice()
                ));
                events.add(new SelfUndercutEvent(
                    displayKey.get(),
                    undercut.bestPrice(),
                    undercut.secondBestPrice()
                ));
                continue;
            }

            if (!result.isSelfUndercut()) {
                this.state.remove(key);
            }
        }

        return events;
    }

    private Optional<SelfUndercutKey> displayKey(SelfUndercutMatchKey key, List<TrackedOrder> trackedOrders) {
        return trackedOrders
            .stream()
            .filter(order -> SelfUndercutMatchKey.from(order).equals(key))
            .findFirst()
            .map(SelfUndercutKey::from);
    }

    private SelfUndercutResult computeSelfUndercutState(
        SelfUndercutMatchKey key,
        List<TrackedOrder> trackedOrders,
        MarketSnapshot snapshot
    ) {
        var matchingOrders = trackedOrders.stream()
            .filter(order -> SelfUndercutMatchKey.from(order).equals(key))
            .toList();

        Comparator<Double> bestFirst = key.type() == OrderType.Buy
            ? Comparator.reverseOrder()
            : Comparator.naturalOrder();

        var playerPrices = matchingOrders.stream()
            .map(order -> order.pricePerUnit)
            .distinct()
            .sorted(bestFirst)
            .toList();

        if (playerPrices.size() < 2) {
            return new SelfUndercutResult.NotUndercut();
        }

        var product = matchingOrders.getFirst().product;
        if (product.bazaarProductId().isEmpty()) {
            log.debug("Product '{}' has no market id", matchingOrders.getFirst().productName);
            return new SelfUndercutResult.NotUndercut();
        }

        if (!snapshot.contains(product)) {
            log.debug("Product {} not found in market snapshot", product);
            return new SelfUndercutResult.NotUndercut();
        }

        var summaries = snapshot.summariesForOrderType(product, key.type());
        if (summaries == null || summaries.size() < 2) {
            return new SelfUndercutResult.NotUndercut();
        }

        var topBucket = summaries.getFirst();
        var secondBucket = summaries.get(1);
        double bestPlayerPrice = playerPrices.getFirst();
        double secondBestPlayerPrice = playerPrices.get(1);

        if (Double.compare(topBucket.getPricePerUnit(), bestPlayerPrice) != 0) {
            return new SelfUndercutResult.NotUndercut();
        }

        long playerCountAtBest = matchingOrders.stream()
            .filter(order -> Double.compare(order.pricePerUnit, bestPlayerPrice) == 0)
            .count();

        if (topBucket.getOrders() != playerCountAtBest) {
            log.trace(
                "Top bucket count mismatch for {}: API orders={}, local tracked={}",
                product,
                topBucket.getOrders(),
                playerCountAtBest
            );
            return new SelfUndercutResult.NotUndercut();
        }

        if (Double.compare(secondBucket.getPricePerUnit(), secondBestPlayerPrice) != 0) {
            return new SelfUndercutResult.NotUndercut();
        }

        long playerCountAtSecondBest = matchingOrders.stream()
            .filter(order -> Double.compare(order.pricePerUnit, secondBestPlayerPrice) == 0)
            .count();

        if (secondBucket.getOrders() != playerCountAtSecondBest) {
            log.trace(
                "Second bucket count mismatch for {}: API orders={}, local tracked={}",
                product,
                secondBucket.getOrders(),
                playerCountAtSecondBest
            );
            return new SelfUndercutResult.NotUndercut();
        }

        return new SelfUndercutResult.Undercut(bestPlayerPrice, secondBestPlayerPrice);
    }

    record SelfUndercutEvent(SelfUndercutKey key, double bestPrice, double secondBestPrice) { }

    private record SelfUndercutPricePair(double bestPrice, double secondBestPrice) { }

    private sealed interface SelfUndercutResult {

        record Undercut(double bestPrice, double secondBestPrice) implements SelfUndercutResult { }

        record NotUndercut() implements SelfUndercutResult { }

        default boolean isSelfUndercut() {
            return this instanceof Undercut;
        }
    }
}
