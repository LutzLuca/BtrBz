package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.utils.Util;
import com.google.common.collect.BiMap;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;

// TODO: think about whether to use this as a global instead of passing it to the components
@Slf4j
public class BazaarData {

    private final List<Consumer<Map<String, Product>>> listeners = new ArrayList<>();
    private Map<String, Product> lastProducts = Collections.emptyMap();
    private BiMap<String, String> idToName = null;

    public BazaarData(BiMap<String, String> conversions) {
        this.idToName = conversions;
    }

    private static Optional<Double> firstSummaryPrice(List<Summary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return Optional.empty();
        }

        return Try.of(summaries::getFirst).map(Summary::getPricePerUnit).toJavaOptional();
    }

    public void setConversions(BiMap<String, String> conversions) {
        this.idToName = conversions;
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

            this.updater = products -> {
                this.data
                    .nameToId(productName)
                    .flatMap(id -> Optional.ofNullable(products.get(id)))
                    .ifPresent(updated -> this.product = Optional.of(updated));
            };
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

            return this.product.flatMap(prod -> Util
                .getFirst(prod.getBuySummary())
                .map(Summary::getPricePerUnit));
        }

        public Optional<Double> getBuyOrderPrice() {
            this.ensureInitialized();

            return this.product.flatMap(prod -> Util
                .getFirst(prod.getSellSummary())
                .map(Summary::getPricePerUnit));
        }

        public void destroy() {
            this.product = Optional.empty();
            this.data.removeListener(this.updater);
        }
    }
}
