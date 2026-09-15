package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.conversions.ConversionIndex;
import com.github.lutzluca.btrbz.data.conversions.ConversionIndexService;
import com.github.lutzluca.btrbz.data.conversions.ConversionProductEntry;
import com.github.lutzluca.btrbz.data.conversions.ProductNameSource;
import com.google.gson.Gson;
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
        publish(market, """
            {"products":{"VALID":{"sell_summary":[{"pricePerUnit":1,"amount":1,"orders":1}]}}}
            """);
        Assertions.assertTrue(market.searchProducts(" ", 10).isEmpty());
        Assertions.assertTrue(market.searchProducts("valid", 0).isEmpty());
        Assertions.assertTrue(market.searchProducts("valid", -1).isEmpty());

        market.clearMarketData();

        Assertions.assertTrue(market.searchProducts("valid", 10).isEmpty());
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
        market.onUpdate(new Gson().fromJson(json, SkyBlockBazaarReply.class).getProducts());
    }

    private static List<String> ids(List<IndexedProduct> products) {
        return products.stream().map(IndexedProduct::productId).toList();
    }
}
