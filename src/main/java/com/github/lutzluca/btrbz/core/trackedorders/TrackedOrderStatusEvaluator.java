package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Matched;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Top;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Undercut;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
final class TrackedOrderStatusEvaluator {

    Stream<StatusUpdate> computeStatusUpdates(List<TrackedOrder> trackedOrders, MarketSnapshot snapshot) {
        return trackedOrders
            .stream()
            .map(order -> this.getTrackedStatus(order, snapshot))
            .flatMap(Optional::stream)
            .filter(trackedStatus -> !trackedStatus.order().status.sameVariant(trackedStatus.status()))
            .map(trackedStatus -> new StatusUpdate(
                trackedStatus.order(),
                trackedStatus.status(),
                trackedStatus.order().status
            ));
    }

    @Nullable GroupStatus getCurrentGroupStatus(GroupKey key, List<TrackedOrder> orders, MarketSnapshot snapshot) {
        boolean hasMatched = orders.stream().anyMatch(order -> order.status instanceof OrderStatus.Matched);
        boolean hasUndercut = orders.stream().anyMatch(order -> order.status instanceof OrderStatus.Undercut);
        boolean hasUnknown = orders.stream().anyMatch(order -> order.status instanceof OrderStatus.Unknown);

        if (hasUnknown) {
            log.warn(
                "Group ({}) has Unknown-status order after poll. Likely unresolved product name, skipping group notification",
                key
            );
            return null;
        }

        if (hasMatched && hasUndercut) {
            log.warn(
                "Group ({}) has both Matched and Undercut orders. This must be a logic error, skipping group notification",
                key
            );
            return null;
        }

        if (hasUndercut) {
            var representativeOrder = orders.getFirst();
            var undercutAmount = ((OrderStatus.Undercut) representativeOrder.status).amount;
            return new GroupStatus.Undercut(undercutAmount);
        }

        int bucketOrderCount = this.countOrdersAtBestPrice(key, snapshot);
        if (bucketOrderCount == -1) {
            log.warn("Group ({}) has no orders at the given price per unit, skipping group notification", key);
            return null;
        }

        return orders.size() == bucketOrderCount
            ? new GroupStatus.SelfMatched(orders.size())
            : new GroupStatus.Matched();
    }

    @Nullable GroupStatus getPreviousGroupStatus(
        GroupKey key,
        List<TrackedOrder> orders,
        List<StatusUpdate> updates
    ) {
        Map<TrackedOrder, OrderStatus> prevStatuses = orders.stream()
            .collect(Collectors.toMap(order -> order, order -> order.status));

        updates.forEach(update -> prevStatuses.put(update.order(), update.prev()));

        var values = prevStatuses.values();

        if (values.stream().anyMatch(status -> status instanceof OrderStatus.Undercut)) {
            double amount = values.stream()
                .filter(status -> status instanceof OrderStatus.Undercut)
                .map(status -> ((OrderStatus.Undercut) status).amount)
                .findFirst()
                .orElseThrow();
            return new GroupStatus.Undercut(amount);
        }

        if (values.stream().anyMatch(status -> status instanceof OrderStatus.Matched)) {
            return new GroupStatus.Matched();
        }

        log.debug(
            "Group ({}) had no prior matched group state. currently tracked orders: {} | updates: {}",
            key,
            orders,
            updates
        );
        return null;
    }

    private Optional<TrackedStatus> getTrackedStatus(TrackedOrder order, MarketSnapshot snapshot) {
        if (order.product.bazaarProductId().isEmpty()) {
            log.warn("Tracked order product has no market id: '{}'", order.productName);
            return Optional.empty();
        }

        if (!snapshot.contains(order.product)) {
            log.warn("No product found for tracked order product {}", order.product);
            return Optional.empty();
        }

        var status = this.getStatus(order, snapshot.summariesForOrderType(order.product, order.type));
        if (status.isEmpty()) {
            log.debug("Unable to determine curr for product {}", order.product);
            return Optional.empty();
        }

        return Optional.of(new TrackedStatus(order, status.get()));
    }

    private int countOrdersAtBestPrice(GroupKey key, MarketSnapshot snapshot) {
        if (key.product().bazaarProductId().isEmpty()) {
            log.warn("Group ({}) has no market product id, skipping group notification", key);
            return -1;
        }

        var lists = snapshot.getOrderLists(key.product());
        var relevantSummaries = switch (key.type()) {
            case Buy -> lists.buyOrders();
            case Sell -> lists.sellOffers();
        };

        return relevantSummaries
            .stream()
            .filter(summary -> Double.compare(summary.getPricePerUnit(), key.pricePerUnit()) == 0)
            .findFirst()
            .map(summary -> (int) summary.getOrders())
            .orElse(-1);
    }

    private Optional<OrderStatus> getStatus(TrackedOrder order, List<Summary> summaries) {
        return Utils.getFirst(summaries).map(summary -> {
            double bestPrice = summary.getPricePerUnit();
            if (Double.compare(order.pricePerUnit, bestPrice) == 0) {
                return summary.getOrders() > 1
                    ? this.matchedOrGhostTop(order, summary)
                    : new Top();
            }

            return switch (order.type) {
                case Buy -> order.pricePerUnit > bestPrice
                    ? new Top()
                    : new Undercut(bestPrice - order.pricePerUnit);
                case Sell -> order.pricePerUnit < bestPrice
                    ? new Top()
                    : new Undercut(order.pricePerUnit - bestPrice);
            };
        });
    }

    /**
     * The Hypixel API can contain "ghost orders", entries that report
     * {@code orders > 1} at a price bucket but carry 0 actual items. When only ghost
     * orders sit alongside the player's order, the bucket's total item amount equals
     * or is less than the player's own volume, meaning there is no real competition.
     */
    private @NotNull OrderStatus matchedOrGhostTop(TrackedOrder order, Summary summary) {
        int itemsAhead = Math.max(0, (int) summary.getAmount() - order.volume);
        if (itemsAhead == 0) {
            log.debug(
                "Ghost order detected for {}: bucket has {} orders but 0 items ahead of player volume ({}), treating as Top",
                order.product.bazaarProductId().orElse(order.productName),
                (int) summary.getOrders(),
                order.volume
            );
            return new Top();
        }
        return new Matched();
    }

    private record TrackedStatus(TrackedOrder order, OrderStatus status) { }
}
