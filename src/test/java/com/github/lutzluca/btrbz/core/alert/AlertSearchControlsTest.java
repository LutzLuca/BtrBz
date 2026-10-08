package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.ui.ProductSearchControls.Results;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.google.gson.Gson;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertSearchControlsTest {
    @Test
    void availabilityChangesEmptyResultEqualityAndHintsInBothDirections() {
        var data = new BazaarData();
        var waiting = Results.lookup(query -> data.searchProducts(query, 12),
            () -> data.hasMarketData() ? "" : "Waiting for market data...", "unmatched");
        Assertions.assertTrue(waiting.matches().isEmpty());
        Assertions.assertFalse(waiting.unavailableHint().isEmpty());
        Assertions.assertFalse(waiting.emptyHint("").isEmpty());

        data.publishSnapshot(BazaarData.MarketSnapshot.fromProducts(new Gson().fromJson("""
            {"products":{"OTHER":{"sell_summary":[],"buy_summary":[]}}}
            """, SkyBlockBazaarReply.class).getProducts()));
        var ready = Results.lookup(query -> data.searchProducts(query, 12),
            () -> data.hasMarketData() ? "" : "Waiting for market data...", "unmatched");

        Assertions.assertEquals(waiting.matches(), ready.matches());
        Assertions.assertNotEquals(waiting, ready);
        Assertions.assertTrue(ready.unavailableHint().isEmpty());
        Assertions.assertNotEquals(waiting.emptyHint("unmatched"), ready.emptyHint("unmatched"));
        Assertions.assertTrue(ready.emptyHint("").isEmpty());
        Assertions.assertEquals(ready, Results.lookup(query -> data.searchProducts(query, 12),
            () -> data.hasMarketData() ? "" : "Waiting for market data...", "unmatched"));

        data.clearMarketData();
        Assertions.assertEquals(waiting, Results.lookup(query -> data.searchProducts(query, 12),
            () -> data.hasMarketData() ? "" : "Waiting for market data...", "unmatched"));
    }
}
