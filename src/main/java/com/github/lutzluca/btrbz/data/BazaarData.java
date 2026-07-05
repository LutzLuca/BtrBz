package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndexService;
import com.github.lutzluca.btrbz.data.conversions.ConversionStatus;
import com.github.lutzluca.btrbz.utils.Utils;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class BazaarData {

    private final List<Consumer<MarketSnapshot>> listeners = new ArrayList<>();
    private final ConversionIndexService conversionIndexService;
    private Map<String, Product> lastProducts = Collections.emptyMap();

    public BazaarData() {
        this(new ConversionIndexService());
    }

    public BazaarData(ConversionIndexService conversionIndexService) {
        this.conversionIndexService = conversionIndexService;
    }

    public static Optional<Double> firstSummaryPrice(List<Summary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return Optional.empty();
        }

        return Try.of(summaries::getFirst).map(Summary::getPricePerUnit).toJavaOptional();
    }

    public void loadConversions() {
        log.info("Loading bazaar conversion index");
        this.conversionIndexService.loadConversionIndex();
        this.conversionIndexService.refreshConversionIndex(false);
    }

    public boolean refreshConversions(boolean manual) {
        return this.conversionIndexService.refreshConversionIndex(manual);
    }

    public ConversionStatus getConversionStatus() {
        return this.conversionIndexService.status();
    }

    public Optional<ProductRef> resolveProductId(String productId) {
        return this.conversionIndexService.productById(productId);
    }

    public List<ProductRef> allProducts() {
        return this.conversionIndexService.allProducts();
    }

    public ProductRef refreshProductRef(ProductRef product) {
        // Keep stale display metadata if the active conversion index no longer contains this id.
        return this.resolveProductId(product.productId()).orElse(product);
    }

    public ProductIdentity resolveProduct(ItemStack stack) {
        return this.conversionIndexService.resolveProduct(stack);
    }

    public ProductIdentity resolveProduct(ItemStack stack, String displayNameEvidence) {
        return this.conversionIndexService.resolveProduct(stack, displayNameEvidence);
    }

    public ProductIdentity resolveProduct(@Nullable String rawProductId, String displayName) {
        return this.conversionIndexService.resolveProduct(rawProductId, displayName);
    }

    public ProductIdentity resolveProductName(String displayName) {
        return this.conversionIndexService.resolveProductName(displayName);
    }

    public void addIndexChangeListener(Runnable listener) {
        this.conversionIndexService.addIndexChangeListener(listener);
    }

    public void removeIndexChangeListener(Runnable listener) {
        this.conversionIndexService.removeIndexChangeListener(listener);
    }

    public void addConversionEventListener(Consumer<ConversionEvent> listener) {
        this.conversionIndexService.addConversionEventListener(listener);
    }

    public void onUpdate(Map<String, Product> products) {
        this.lastProducts = Collections.unmodifiableMap(new LinkedHashMap<>(
            products == null ? Map.of() : products
        ));
        var snapshot = this.currentSnapshot();

        for (var listener : this.listeners) {
            Try.run(() -> listener.accept(snapshot)).onFailure(err -> log.error(
                "Bazaar update listener '{}' failed while processing {} products",
                listener.getClass().getName(),
                snapshot.size(),
                err
            ));
        }
    }

    public void addListener(Consumer<MarketSnapshot> listener) {
        this.listeners.add(listener);
        log.trace(
            "Inserting listener for onBazaarUpdate currently, listeners registered: {}",
            this.listeners.size()
        );
    }

    public void removeListener(Consumer<MarketSnapshot> listener) {
        if (this.listeners.remove(listener)) {
            log.trace(
                "Removing listener for onBazaarUpdate currently, listeners registered: {}",
                this.listeners.size()
            );
        }
    }

    private MarketSnapshot currentSnapshot() {
        return new MarketSnapshot(this.lastProducts);
    }

    public Optional<Double> lowestSellOfferPrice(ProductRef product) {
        return this.currentSnapshot().lowestSellOfferPrice(product);
    }

    public Optional<Double> highestBuyOrderPrice(ProductRef product) {
        return this.currentSnapshot().highestBuyOrderPrice(product);
    }

    public MarketPrices getMarketPrices(ProductRef product) {
        return this.currentSnapshot().getMarketPrices(product);
    }

    public OrderLists getOrderLists(ProductRef product) {
        return this.currentSnapshot().getOrderLists(product);
    }

    public Optional<OrderQueueInfo> calculateQueuePosition(
        ProductRef product, OrderType orderType,
        double pricePerUnit
    ) {
        return this.calculateQueuePosition(product, orderType, pricePerUnit, false);
    }

    public Optional<OrderQueueInfo> calculateQueuePosition(
        ProductRef product, OrderType orderType,
        double pricePerUnit, boolean includeAtPrice
    ) {
        var summaries = this.currentSnapshot().summariesForOrderType(product, orderType);
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

    public Optional<Double> getEstimatedFillTimeMinutes(ProductRef product, OrderType orderType, int remainingVolume) {
        if (remainingVolume <= 0) {
            return Optional.of(0.0);
        }

        var qs = this.currentSnapshot()
            .rawProduct(product)
            .map(Product::getQuickStatus)
            .orElse(null);
        if (qs == null) {
            return Optional.empty();
        }

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

    public record MarketPrices(
        Optional<@Nullable Double> highestBuyOrderPrice,
        Optional<@Nullable Double> lowestSellOfferPrice
    ) { }

    public record OrderLists(List<Summary> buyOrders, List<Summary> sellOffers) {
        public static OrderLists empty() {
            return new OrderLists(List.of(), List.of());
        }
    }

    public static final class MarketSnapshot {

        private final Map<String, Product> products;

        private MarketSnapshot(Map<String, Product> products) {
            this.products = products;
        }

        public int size() {
            return this.products.size();
        }

        public boolean contains(ProductRef product) {
            return this.products.containsKey(product.productId());
        }

        public Optional<Double> lowestSellOfferPrice(ProductRef product) {
            return this.rawProduct(product)
                .flatMap(prod -> firstSummaryPrice(prod.getBuySummary()));
        }

        public Optional<Double> highestBuyOrderPrice(ProductRef product) {
            return this.rawProduct(product)
                .flatMap(prod -> firstSummaryPrice(prod.getSellSummary()));
        }

        public MarketPrices getMarketPrices(ProductRef product) {
            return new MarketPrices(
                this.highestBuyOrderPrice(product),
                this.lowestSellOfferPrice(product)
            );
        }

        public OrderLists getOrderLists(ProductRef product) {
            // Hypixel summary names are action-based: sell_summary is actual buy orders, buy_summary is actual sell offers.
            return this.rawProduct(product)
                .map(prod -> new OrderLists(
                    Optional.ofNullable(prod.getSellSummary()).orElse(List.of()),
                    Optional.ofNullable(prod.getBuySummary()).orElse(List.of())
                ))
                .orElse(OrderLists.empty());
        }

        public List<Summary> summariesForOrderType(ProductRef product, OrderType orderType) {
            var lists = this.getOrderLists(product);
            return switch (orderType) {
                case Buy -> lists.buyOrders();
                case Sell -> lists.sellOffers();
            };
        }

        private Optional<Product> rawProduct(ProductRef product) {
            return Optional.ofNullable(this.products.get(product.productId()));
        }
    }

    public static final class TrackedProduct {

        @Getter
        private ProductRef product;
        private final BazaarData data;
        private final Consumer<MarketSnapshot> updater;
        private final Runnable indexUpdater;
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        @Getter
        private Optional<Product> bazaarProduct;
        private boolean listenerRegistered = false;

        public TrackedProduct(BazaarData data, ProductRef product) {
            this.data = data;
            this.product = product;
            this.bazaarProduct = Optional.empty();

            this.updater = snapshot -> this.bazaarProduct = snapshot.rawProduct(this.product);
            this.indexUpdater = this::refreshProduct;
        }

        public String getProductName() {
            this.product = this.data.refreshProductRef(this.product);
            return this.product.strippedName();
        }

        private void ensureInitialized() {
            if (this.listenerRegistered) {
                return;
            }

            this.refreshProduct();
            this.data.addListener(this.updater);
            this.data.addIndexChangeListener(this.indexUpdater);
            this.listenerRegistered = true;
        }

        public Optional<Double> getSellOfferPrice() {
            this.ensureInitialized();

            return this.bazaarProduct.flatMap(
                prod -> Utils.getFirst(prod.getBuySummary()).map(Summary::getPricePerUnit)
            );
        }

        public Optional<Double> getBuyOrderPrice() {
            this.ensureInitialized();

            return this.bazaarProduct.flatMap(
                prod -> Utils.getFirst(prod.getSellSummary()).map(Summary::getPricePerUnit)
            );
        }

        public void destroy() {
            this.bazaarProduct = Optional.empty();
            this.data.removeListener(this.updater);
            this.data.removeIndexChangeListener(this.indexUpdater);
            this.listenerRegistered = false;
        }

        private void refreshProduct() {
            this.product = this.data.refreshProductRef(this.product);
            this.bazaarProduct = this.data.currentSnapshot().rawProduct(this.product);
        }
    }
}
