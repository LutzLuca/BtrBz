package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TrackedOrderProductUpdater {

    private final BazaarData bazaarData;

    TrackedOrderProductUpdater(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
    }

    ProductIdentity resolveCurrentProduct(TrackedOrder order) {
        return this.bazaarData
            .resolveIndexedProduct(order.product)
            .map(this.bazaarData::refreshIndexedProduct)
            .map(ProductIdentity::fromIndex)
            .or(() -> order.product.bazaarProductId()
                .map(productId -> this.bazaarData.resolveProduct(productId, order.uiProductName)))
            .orElseGet(() -> this.bazaarData.resolveProductName(order.uiProductName));
    }

    ProductIdentity strongestProduct(
        ProductIdentity current,
        ProductIdentity incoming,
        String uiProductName
    ) {
        var currentIndexed = this.isIndexBacked(current);
        var incomingIndexed = this.isIndexBacked(incoming);

        if (incomingIndexed) {
            return incoming;
        }

        if (currentIndexed) {
            this.logWeaker(uiProductName, current, incoming);
            return current;
        }

        var currentId = current.bazaarProductId();
        var incomingId = incoming.bazaarProductId();

        if (currentId.isEmpty() && incomingId.isPresent()) {
            return incoming;
        }

        if (currentId.isPresent() && incomingId.isEmpty()) {
            this.logWeaker(uiProductName, current, incoming);
            return current;
        }

        if (currentId.isPresent() && incomingId.isPresent() && !currentId.get().equals(incomingId.get())) {
            log.warn(
                "Ignoring conflicting tracked order identity update for UI product '{}': current={}, incoming={}",
                uiProductName,
                current,
                incoming
            );
            return current;
        }

        if (sameIdentity(currentId, incomingId)
            && current.formattedName() == null
            && incoming.formattedName() != null) {
            return incoming;
        }

        return current;
    }

    private boolean isIndexBacked(ProductIdentity product) {
        return this.bazaarData.resolveIndexedProduct(product).isPresent();
    }

    private void logWeaker(String uiProductName, ProductIdentity current, ProductIdentity incoming) {
        if (current.equals(incoming)) {
            return;
        }

        log.warn(
            "Ignoring weaker tracked order identity update for UI product '{}': current={}, incoming={}",
            uiProductName,
            current,
            incoming
        );
    }

    private static boolean sameIdentity(Optional<String> currentId, Optional<String> incomingId) {
        return currentId.equals(incomingId);
    }
}
