package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.MessageQueue;
import com.github.lutzluca.btrbz.utils.MessageQueue.Level;
import com.github.lutzluca.btrbz.utils.Utils;
import com.google.common.collect.BiMap;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class BazaarData {

    private final List<Consumer<Map<String, Product>>> listeners = new ArrayList<>();
    private Map<String, Product> lastProducts = Collections.emptyMap();
    private volatile BiMap<String, String> idToName;

    public BazaarData(BiMap<String, String> conversions) {
        this.idToName = conversions;
    }

    public static Optional<Double> firstSummaryPrice(List<Summary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return Optional.empty();
        }

        return Try.of(summaries::getFirst).map(Summary::getPricePerUnit).toJavaOptional();
    }

    public void loadConversions() {
        log.info("Loading bazaar conversions");

        ConversionLoader.load().thenAccept(result ->
            result
                .onSuccess(loadResult -> {
                    this.idToName = loadResult.conversions();
                    log.debug("Conversions applied ({} entries)", loadResult.conversions().size());

                    ConversionLoader.checkForConversionUpdates(loadResult.contentHash())
                        .thenAccept(updateResult ->
                            updateResult
                                .onSuccess(maybeNew ->
                                    maybeNew.ifPresent(newResult -> {
                                        this.idToName = newResult.conversions();
                                        log.debug("Updated conversions applied ({} entries)", newResult.conversions().size());
                                    })
                                )
                                .onFailure(err -> {
                                    log.error("Failed to refresh conversions, stale data may be used", err);
                                    log.debug("Conversions remain stale until next successful refresh");
                                })
                        );
                })
                .onFailure(err -> {
                    log.error("Failed to load bazaar conversions", err);
                    MessageQueue.sendOrQueue(
                        "Failed to load bazaar conversions; some features may not work as expected",
                        Level.Error
                    );
                })
        );
    }

    public void onUpdate(Map<String, Product> products) {
        this.lastProducts = products;
        for (var listener : this.listeners) {
            listener.accept(this.lastProducts);
        }
    }

    public void addListener(Consumer<Map<String, Product>> listener) {
        this.listeners.add(listener);
        log.trace(
            "Inserting listener for onBazaarUpdate currently, listeners registered: {}",
            this.listeners.size()
        );
    }

    public void removeListener(Consumer<Map<String, Product>> listener) {
        if (this.listeners.remove(listener)) {
            log.trace(
                "Removing listener for onBazaarUpdate currently, listeners registered: {}",
                this.listeners.size()
            );
        }
    }

    public Map<String, Product> getProducts() {
        return this.lastProducts;
    }

    public Optional<Double> lowestSellPrice(String productId) {
        var product = Optional.ofNullable(this.getProducts().get(productId));
        return product.flatMap(prod -> firstSummaryPrice(prod.getBuySummary()));
    }

    public Optional<Double> highestBuyPrice(String productId) {
        var product = Optional.ofNullable(this.getProducts().get(productId));
        return product.flatMap(prod -> firstSummaryPrice(prod.getSellSummary()));
    }

    public Optional<String> nameToId(String name) {
        if (this.idToName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.idToName.inverse().get(name));
    }

    public OrderPriceInfo getOrderPrices(String productId) {
        var buyOrderPrice = this.highestBuyPrice(productId);
        var sellOfferPrice = this.lowestSellPrice(productId);

        return new OrderPriceInfo(buyOrderPrice, sellOfferPrice);
    }

    public OrderLists getOrderLists(String productId) {
        return Optional.ofNullable(this.getProducts().get(productId))
                       .map(prod -> new OrderLists(prod.getSellSummary(), prod.getBuySummary()))
                       .orElse(new OrderLists(List.of(), List.of()));
    }

    public Optional<OrderQueueInfo> calculateQueuePosition(
        String productName, OrderType orderType,
        double pricePerUnit
    ) {
        return calculateQueuePosition(productName, orderType, pricePerUnit, false);
    }

    public Optional<OrderQueueInfo> calculateQueuePosition(
        String productName, OrderType orderType,
        double pricePerUnit, boolean includeAtPrice
    ) {
        var productId = this.nameToId(productName);

        if (productId.isEmpty()) {
            return Optional.empty();
        }

        var product = this.lastProducts.get(productId.get());

        if (product == null) {
            return Optional.empty();
        }

        var summaries = switch (orderType) {
            case Sell -> product.getBuySummary();
            case Buy -> product.getSellSummary();
        };

        if (summaries == null || summaries.isEmpty()) {
            return Optional.empty();
        }

        var queueInfo = new OrderQueueInfo(0, 0);
        for (var summary : summaries) {
            boolean isSamePrice = summary.getPricePerUnit() == pricePerUnit;
            boolean isBetter = switch (orderType) {
                case Sell -> summary.getPricePerUnit() < pricePerUnit;
                case Buy -> summary.getPricePerUnit() > pricePerUnit;
            };

            if (!isBetter && !(isSamePrice && includeAtPrice)) {
                break;
            }
            queueInfo.ordersAhead += (int) summary.getOrders();
            queueInfo.itemsAhead += (int) summary.getAmount();
        }

        return queueInfo.ordersAhead > 0 ? Optional.of(queueInfo) : Optional.empty();
    }

    public Optional<Double> getEstimatedFillTimeMinutes(String productName, OrderType orderType, int remainingVolume) {
        if (remainingVolume <= 0) {
            return Optional.of(0.0);
        }

        var productId = this.nameToId(productName);
        if (productId.isEmpty()) {
            return Optional.empty();
        }

        var product = this.lastProducts.get(productId.get());
        if (product == null) {
            return Optional.empty();
        }

        var qs = product.getQuickStatus();
        long movingWeek = switch (orderType) {
            case Sell -> qs.getBuyMovingWeek();
            case Buy -> qs.getSellMovingWeek();
        };

        if (movingWeek <= 0) {
            return Optional.empty();
        }

        double hourlyRate = movingWeek / 168.0;
        double minutesRate = hourlyRate / 60.0;

        return Optional.of(remainingVolume / minutesRate);
    }

    @ToString
    @AllArgsConstructor
    public static final class OrderQueueInfo {
        public int ordersAhead;
        public int itemsAhead;
    }

    public record OrderPriceInfo(
        Optional<@Nullable Double> buyOrderPrice,
        Optional<@Nullable Double> sellOfferPrice
    ) { }

    public record OrderLists(List<Summary> buyOrders, List<Summary> sellOffers) { }

    public static final class TrackedProduct {

        @Getter
        private final String productName;
        private final BazaarData data;
        private final Consumer<Map<String, Product>> updater;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        @Getter
        private Optional<Product> product;
        private boolean listenerRegistered = false;

        public TrackedProduct(BazaarData data, String productName) {
            this.data = data;
            this.productName = productName;
            this.product = Optional.empty();

            this.updater = products ->
                this.data.nameToId(productName)
                        .flatMap(id -> Optional.ofNullable(products.get(id)))
                        .ifPresent(updated -> this.product = Optional.of(updated));
        }

        private void ensureInitialized() {
            if (this.listenerRegistered) {
                return;
            }

            this.data
                .nameToId(productName)
                .flatMap(id -> Optional.ofNullable(data.getProducts().get(id)))
                .ifPresent(prod -> {
                    this.product = Optional.of(prod);

                    this.data.addListener(this.updater);
                    this.listenerRegistered = true;
                });
        }

        public Optional<Double> getSellOfferPrice() {
            this.ensureInitialized();

            return this.product.flatMap(
                prod -> Utils.getFirst(prod.getBuySummary()).map(Summary::getPricePerUnit)
            );
        }

        public Optional<Double> getBuyOrderPrice() {
            this.ensureInitialized();

            return this.product.flatMap(
                prod -> Utils.getFirst(prod.getSellSummary()).map(Summary::getPricePerUnit)
            );
        }

        public void destroy() {
            this.product = Optional.empty();
            this.data.removeListener(this.updater);
        }
    }
}