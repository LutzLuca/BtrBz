package com.github.lutzluca.btrbz.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;

/** One immutable Hypixel Bazaar publication. */
public record BazaarMarketUpdate(long lastUpdatedEpochMillis, Map<String, Product> products) {
    public BazaarMarketUpdate {
        Objects.requireNonNull(products, "products");
        products = Map.copyOf(new LinkedHashMap<>(products));
    }
}
