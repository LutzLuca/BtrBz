package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketPrices;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.Optional;
import java.util.function.Consumer;

final class TrackedFlipProduct {

    private IndexedProduct product;
    private final BazaarData data;
    private final Consumer<MarketSnapshot> updater;
    private final Runnable indexUpdater;
    private MarketPrices prices;
    private boolean listenerRegistered = false;

    TrackedFlipProduct(BazaarData data, IndexedProduct product) {
        this.data = data;
        this.product = product;
        this.prices = emptyPrices();
        this.updater = snapshot -> this.prices = snapshot.getMarketPrices(this.identity());
        this.indexUpdater = this::refreshProduct;
    }

    IndexedProduct getProduct() {
        return this.product;
    }

    String getProductName() {
        this.product = this.data.refreshIndexedProduct(this.product);
        return this.product.strippedName();
    }

    Optional<Double> getSellOfferPrice() {
        this.ensureInitialized();
        return this.prices.lowestSellOfferPrice();
    }

    Optional<Double> getBuyOrderPrice() {
        this.ensureInitialized();
        return this.prices.highestBuyOrderPrice();
    }

    void destroy() {
        this.prices = emptyPrices();
        this.data.removeListener(this.updater);
        this.data.removeIndexChangeListener(this.indexUpdater);
        this.listenerRegistered = false;
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

    private void refreshProduct() {
        this.product = this.data.refreshIndexedProduct(this.product);
        this.prices = this.data.getMarketPrices(this.identity());
    }

    private ProductIdentity identity() {
        return ProductIdentity.fromIndex(this.product);
    }

    private static MarketPrices emptyPrices() {
        return new MarketPrices(Optional.empty(), Optional.empty());
    }
}
