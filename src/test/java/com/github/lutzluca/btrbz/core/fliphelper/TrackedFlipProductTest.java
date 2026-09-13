package com.github.lutzluca.btrbz.core.fliphelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndex;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndexService;
import com.github.lutzluca.btrbz.data.conversions.ConversionProductEntry;
import com.github.lutzluca.btrbz.data.conversions.ProductNameSource;
import com.google.gson.Gson;
import java.util.Map;
import java.util.Optional;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Tracked flip product market subscription")
class TrackedFlipProductTest {
    private final IndexedProduct product = new IndexedProduct("TEST", "Test Product");
    private final BazaarData market = new BazaarData(new ConversionIndexService(new ConversionIndex(
        ConversionIndex.SCHEMA_VERSION,
        "2026-09-09T00:00:00Z",
        null,
        Map.of("TEST", new ConversionProductEntry("Test Product", new ProductNameSource.Derived())))));

    @Test
    void clearsSubscribedPricesAndReceivesTheNextReply() {
        var tracked = new TrackedFlipProduct(this.market, this.product);
        assertFalse(this.market.hasMarketData());

        this.publish(100);
        assertEquals(Optional.of(100.0), tracked.getBuyOrderPrice());
        assertEquals(Optional.of(110.0), tracked.getSellOfferPrice());

        this.market.clearMarketData();

        assertTrue(tracked.getBuyOrderPrice().isEmpty());
        assertTrue(tracked.getSellOfferPrice().isEmpty());

        this.publish(105);

        assertEquals(Optional.of(105.0), tracked.getBuyOrderPrice());
        assertEquals(Optional.of(110.0), tracked.getSellOfferPrice());
        tracked.destroy();
    }

    private void publish(double buyPrice) {
        var reply = new Gson().fromJson("""
            {"products":{"TEST":{
                "sell_summary":[{"pricePerUnit":%s,"amount":100,"orders":2}],
                "buy_summary":[{"pricePerUnit":110,"amount":100,"orders":2}]
            }}}
            """.formatted(buyPrice), SkyBlockBazaarReply.class);
        this.market.onUpdate(reply.getProducts());
    }
}
