package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.conversions.ConversionIndex;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndexService;
import com.github.lutzluca.btrbz.data.conversions.ConversionProductEntry;
import com.github.lutzluca.btrbz.data.conversions.ProductNameSource;
import com.google.gson.Gson;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BazaarDataSearchTest {

    @Test
    void filtersTheLiveQuoteUniverseBeforeApplyingTheLimit() {
        var market = marketWithProducts(Map.of(
            "GHOST", "Diamond",
            "VALID", "Diamond Fragment"));
        publish(market, """
            {"products":{
                "GHOST":{"sell_summary":[],"buy_summary":[]},
                "VALID":{"sell_summary":[{"pricePerUnit":10,"amount":1,"orders":1}],"buy_summary":[]}
            }}
            """);

        Assertions.assertEquals(List.of("VALID"), ids(market.searchProducts("diamond", 1)));
    }

    @Test
    void acceptsOneSidedPositiveFiniteQuotesAndRejectsUnusableOnes() {
        var names = new LinkedHashMap<String, String>();
        names.put("BUY_ONLY", "Test Buy Only");
        names.put("SELL_ONLY", "Test Sell Only");
        names.put("EMPTY", "Test Empty");
        names.put("ZERO", "Test Zero");
        names.put("NEGATIVE", "Test Negative");
        names.put("NAN", "Test Nan");
        names.put("INFINITE", "Test Infinite");
        var market = marketWithProducts(names);
        publish(market, """
            {"products":{
                "BUY_ONLY":{"sell_summary":[{"pricePerUnit":2,"amount":1,"orders":1}],"buy_summary":[]},
                "SELL_ONLY":{"sell_summary":[],"buy_summary":[{"pricePerUnit":3,"amount":1,"orders":1}]},
                "EMPTY":{"sell_summary":[],"buy_summary":[]},
                "ZERO":{"sell_summary":[{"pricePerUnit":0,"amount":1,"orders":1}],"buy_summary":[]},
                "NEGATIVE":{"sell_summary":[{"pricePerUnit":-1,"amount":1,"orders":1}],"buy_summary":[]},
                "NAN":{"sell_summary":[{"pricePerUnit":"NaN","amount":1,"orders":1}],"buy_summary":[]},
                "INFINITE":{"sell_summary":[{"pricePerUnit":"Infinity","amount":1,"orders":1}],"buy_summary":[]}
            }}
            """);

        Assertions.assertEquals(List.of("BUY_ONLY", "SELL_ONLY"), ids(market.searchProducts("test", 20)));
    }

    @Test
    void returnsNothingWithoutMarketDataOrForInvalidInput() {
        var market = marketWithProducts(Map.of("VALID", "Valid Product"));

        Assertions.assertTrue(market.searchProducts("valid", 10).isEmpty());
        Assertions.assertEquals(List.of("VALID"), ids(market.searchIndexedProducts("valid", 10)));
        publish(market, """
            {"products":{"VALID":{"sell_summary":[{"pricePerUnit":1,"amount":1,"orders":1}]}}}
            """);
        Assertions.assertTrue(market.searchProducts(" ", 10).isEmpty());
        Assertions.assertTrue(market.searchProducts("valid", 0).isEmpty());
        Assertions.assertTrue(market.searchProducts("valid", -1).isEmpty());

        market.clearMarketData();

        Assertions.assertTrue(market.searchProducts("valid", 10).isEmpty());
    }

    @Test
    void copiesSortedBooksWithSourceTimeAndFullSideTotals() {
        var reply = new Gson().fromJson("""
            {"products":{"TEST":{
                "sell_summary":[{"pricePerUnit":0.1,"amount":3,"orders":2},
                                {"pricePerUnit":0.2,"amount":5,"orders":1},
                                {"pricePerUnit":0.1,"amount":4,"orders":1}],
                "buy_summary":[{"pricePerUnit":0.4,"amount":2,"orders":1},
                               {"pricePerUnit":0.3,"amount":8,"orders":2},
                               {"pricePerUnit":0,"amount":8,"orders":2}],
                "quick_status":{"sellVolume":123,"sellOrders":12,"buyVolume":456,"buyOrders":34}
            },"EMPTY":{"sell_summary":[],"buy_summary":[]}}}
            """, SkyBlockBazaarReply.class);
        var sourceTime = Instant.parse("2026-10-05T10:00:00Z");
        var snapshot = BazaarData.MarketSnapshot.fromProducts(reply.getProducts(), sourceTime);
        var live = snapshot.liveProduct(ProductIdentity.fromRuntime("Test", "TEST", null)).orElseThrow();

        Assertions.assertEquals(sourceTime, live.sourceUpdatedAt().orElseThrow());
        Assertions.assertEquals(0.3, live.buyPrice().orElseThrow());
        Assertions.assertEquals(0.2, live.sellPrice().orElseThrow());
        Assertions.assertEquals(List.of(new LiveProductSnapshot.PriceLevel(0.2, 5, 1),
            new LiveProductSnapshot.PriceLevel(0.1, 7, 3)), live.buyOrders().levels());
        Assertions.assertEquals(new LiveProductSnapshot.Totals(123, 12), live.buyOrders().totals().orElseThrow());
        Assertions.assertEquals(new LiveProductSnapshot.Totals(456, 34), live.sellOffers().totals().orElseThrow());
        var empty = snapshot.liveProduct(ProductIdentity.fromRuntime("Empty", "EMPTY", null)).orElseThrow();
        Assertions.assertTrue(empty.buyOrders().totals().isEmpty());
        Assertions.assertTrue(empty.buyPrice().isEmpty());
        reply.getProducts().get("TEST").getSellSummary().clear();
        Assertions.assertEquals(2, live.buyOrders().levels().size());
        var data = new BazaarData();
        data.publishSnapshot(snapshot);
        data.clearMarketData();
        Assertions.assertTrue(data.currentSnapshot().sourceUpdatedAt().isEmpty());
    }

    private static BazaarData marketWithProducts(Map<String, String> names) {
        var entries = new LinkedHashMap<String, ConversionProductEntry>();
        names.forEach((id, name) -> entries.put(id, new ConversionProductEntry(name, new ProductNameSource.Derived())));
        return new BazaarData(new ConversionIndexService(new ConversionIndex(
            ConversionIndex.SCHEMA_VERSION,
            "now",
            null,
            entries)));
    }

    private static void publish(BazaarData market, String json) {
        market.publishSnapshot(
            BazaarData.MarketSnapshot.fromProducts(new Gson().fromJson(json, SkyBlockBazaarReply.class).getProducts()));
    }

    private static List<String> ids(List<IndexedProduct> products) {
        return products.stream().map(IndexedProduct::productId).toList();
    }
}
