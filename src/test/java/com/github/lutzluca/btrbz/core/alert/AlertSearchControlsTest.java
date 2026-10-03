package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertSearchControls.Results;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.google.gson.Gson;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertSearchControlsTest {
    @Test
    void availabilityChangesEmptyResultEqualityAndHintsInBothDirections() {
        var data = new BazaarData();
        var waiting = Results.lookup(data, "unmatched");
        Assertions.assertTrue(waiting.matches().isEmpty());
        Assertions.assertFalse(waiting.marketAvailable());
        Assertions.assertFalse(waiting.emptyHint("").isEmpty());

        data.onUpdate(new Gson().fromJson("""
            {"products":{"OTHER":{"sell_summary":[],"buy_summary":[]}}}
            """, SkyBlockBazaarReply.class).getProducts());
        var ready = Results.lookup(data, "unmatched");

        Assertions.assertEquals(waiting.matches(), ready.matches());
        Assertions.assertNotEquals(waiting, ready);
        Assertions.assertTrue(ready.marketAvailable());
        Assertions.assertNotEquals(waiting.emptyHint("unmatched"), ready.emptyHint("unmatched"));
        Assertions.assertTrue(ready.emptyHint("").isEmpty());
        Assertions.assertEquals(ready, Results.lookup(data, "unmatched"));

        data.clearMarketData();
        Assertions.assertEquals(waiting, Results.lookup(data, "unmatched"));
    }
}
