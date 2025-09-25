package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.TrackedOrder.OrderInfo;
import com.github.lutzluca.btrbz.TrackedOrder.OrderStatus;
import com.google.common.collect.BiMap;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;

public class BzOrderManager {

    private final BiMap<String, String> idToName;

    private final List<TrackedOrder> trackedOrders = new ArrayList<>();

    public BzOrderManager(BiMap<String, String> conversions) {
        this.idToName = conversions;
    }


    public void syncFromUi(Collection<OrderInfo> parsedOrders) {
        var toRemove = new ArrayList<TrackedOrder>();
        var remaining = new ArrayList<>(parsedOrders);

        for (var tracked : this.trackedOrders) {
            var match = remaining.stream().filter(tracked::match).findFirst();

            match.ifPresentOrElse(info -> {
                remaining.remove(info);
                if (info.filled()) {
                    toRemove.add(tracked);
                    return;
                }

                tracked.slot = info.slotIdx();
            }, () -> {
                toRemove.add(tracked);
            });
        }

        Notifier.logDebug("Tracked orders: {}, toRemove: {}, toAdd: {}",
                this.trackedOrders.toString(), toRemove.toString(),
                remaining.stream().filter(OrderInfo::notFilled).toList().toString());

        this.trackedOrders.removeAll(toRemove);
        this.trackedOrders.addAll(remaining.stream().filter(OrderInfo::notFilled).map(info -> {
            var slot = info.slotIdx();
            return new TrackedOrder(info, slot);
        }).toList());
    }

    public void onBazaarUpdate(Map<String, Product> products) {
        this.trackedOrders.stream().map(tracked -> {
            var id = nameToId(tracked.productName);
            if (id.isEmpty()) {
                Notifier.logWarn("No name -> id mapping found for product with name: '{}'",
                        tracked.productName);
                return Optional.<StatusUpdate>empty();
            }

            var product = Optional.ofNullable(products.get(id.get()));
            if (product.isEmpty()) {
                Notifier.logWarn("No product found for item with name '{}' and mapped id '{}'",
                        tracked.productName, id.get());
                return Optional.<StatusUpdate>empty();
            }

            var status = getStatus(tracked, product.get());
            if (status.isEmpty()) {
                Notifier.logInfo("Unable to determine status for product '{}' with id '{}'",
                        tracked.productName, id.get());
                return Optional.<StatusUpdate>empty();
            }

            return Optional.of(new StatusUpdate(tracked, status.get()));
        }).flatMap(Optional::stream).filter(
                statusUpdate -> !statusUpdate.trackedOrder.status.sameVariant(statusUpdate.status))
                .forEach(statusUpdate -> {
                    statusUpdate.trackedOrder.status = statusUpdate.status;
                    HighlightManager.updateStatus(statusUpdate.trackedOrder.slot,
                            statusUpdate.status);
                    Notifier.notifyOrderStatus(statusUpdate);
                });
    }

    public Optional<OrderStatus> getStatus(TrackedOrder order, Product product) {
        Function<List<Summary>, Optional<Summary>> getFirst =
                (list) -> Try.of(list::getFirst).toJavaOptional();

        // floating point inaccuracy in for player exposure is handled see `Utils.formatDecimal`
        return switch (order.type) {
            case Buy -> getFirst.apply(product.getSellSummary()).map(summary -> {
                double bestPrice = summary.getPricePerUnit();
                if (order.pricePerUnit == bestPrice) {
                    return summary.getOrders() > 1 ? new OrderStatus.Matched()
                            : new OrderStatus.Top();
                }
                if (order.pricePerUnit > bestPrice) {
                    return new OrderStatus.Top();
                }
                return new OrderStatus.Undercut(bestPrice - order.pricePerUnit);
            });
            case Sell -> getFirst.apply(product.getBuySummary()).map(summary -> {
                double bestPrice = summary.getPricePerUnit();
                if (order.pricePerUnit == bestPrice) {
                    return summary.getOrders() > 1 ? new OrderStatus.Matched()
                            : new OrderStatus.Top();
                }
                if (order.pricePerUnit < bestPrice) {
                    return new OrderStatus.Top();
                }
                return new OrderStatus.Undercut(order.pricePerUnit - bestPrice);
            });
        };
    }

    private Optional<String> nameToId(String name) {
        return Optional.ofNullable(this.idToName.inverse().get(name));
    }

    public void resetTrackedOrders() {
        var removed = this.trackedOrders.size();
        this.trackedOrders.clear();
        Notifier.logInfo("Reset tracked orders (removed {})", removed);
    }

    public List<TrackedOrder> getTrackedOrders() {
        return List.copyOf(this.trackedOrders);
    }

    public void addTrackedOrder(TrackedOrder order) {
        this.trackedOrders.add(order);
    }

    public record StatusUpdate(TrackedOrder trackedOrder, OrderStatus status) {
    }
}
