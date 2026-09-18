package com.github.lutzluca.btrbz.core.fliphelper;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndex;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndexService;
import com.github.lutzluca.btrbz.data.conversions.ConversionProductEntry;
import com.github.lutzluca.btrbz.data.conversions.ProductNameSource;
import com.google.gson.Gson;
import java.util.Map;
import java.util.Optional;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FlipProductContextTest {
    @Test
    void runtimeIdUsesTheCurrentSnapshotWithoutAnIndexEntry() {
        var market = new BazaarData(new ConversionIndexService(ConversionIndex.empty()));
        var context = new FlipProductContext(market);
        var product = ProductIdentity.fromRuntime("UI Product", "TEST", null);
        context.selectProduct(product);

        var reply = new Gson().fromJson("""
            {"products":{"TEST":{"buy_summary":[{"pricePerUnit":110}]}}}
            """, SkyBlockBazaarReply.class);
        market.onUpdate(reply.getProducts());

        Assertions.assertTrue(market.resolveIndexedProduct(product).isEmpty());
        Assertions.assertEquals(product, context.getSelectedProduct().orElseThrow());
        Assertions.assertTrue(market.contains(product));
        Assertions.assertFalse(market.contains(ProductIdentity.fromName("UI Product")));
        Assertions.assertEquals(Optional.of(110.0), market.lowestSellOfferPrice(product));

        market.clearMarketData();
        Assertions.assertFalse(market.contains(product));
        Assertions.assertTrue(market.lowestSellOfferPrice(product).isEmpty());
        market.onUpdate(reply.getProducts());
        Assertions.assertEquals(Optional.of(110.0), market.lowestSellOfferPrice(product));

        context.clearProduct();
        Assertions.assertTrue(context.getSelectedProduct().isEmpty());
    }

    @Test
    void indexedMetadataIsOptionalAndUpdatesTheSelectedDisplayName() {
        var market = new BazaarData(new ConversionIndexService(new ConversionIndex(
            ConversionIndex.SCHEMA_VERSION, "now", null,
            Map.of("TEST", new ConversionProductEntry("§aIndexed Product", new ProductNameSource.Derived())))));
        var context = new FlipProductContext(market);
        context.selectProduct(ProductIdentity.fromRuntime("UI Product", "TEST", null));

        var selected = context.getSelectedProduct().orElseThrow();
        Assertions.assertEquals(Optional.of("TEST"), selected.bazaarProductId());
        Assertions.assertEquals("Indexed Product", selected.strippedName());
        Assertions.assertEquals("§aIndexed Product", selected.visualName());
    }
}
