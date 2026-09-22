package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.AlertManager.AlertConfig;
import com.github.lutzluca.btrbz.core.AlertManager.ReachedAlert;
import com.github.lutzluca.btrbz.core.alert.AlertDefinition;
import com.github.lutzluca.btrbz.core.alert.AlertType;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class EmptyBuySideAlertsTest {
    private static final IndexedProduct ITEM = new IndexedProduct("ENCHANTED_DIAMOND", "Enchanted Diamond");
    private final AlertConfig config = new AlertConfig();
    private final BazaarData data = new BazaarData();
    private final List<ReachedAlert> notifications = new ArrayList<>();
    private final AlertManager manager = new AlertManager(this.data, () -> this.config, () -> {},
        this.notifications::add);

    EmptyBuySideAlertsTest() {
        this.data.addListener(this.manager::onBazaarUpdate);
    }

    @Test
    void emptyBuySideReachesOnlyBelowSellPriceAlerts() {
        this.save(PriceSource.Sell, Direction.Below, 0.1);
        this.save(PriceSource.Sell, Direction.Above, 0.1);
        this.save(PriceSource.Buy, Direction.Below, 0.1);
        this.publish(book("[]", "[]"));

        Assertions.assertEquals(1, this.manager.reachedAlerts().size());
        Assertions.assertEquals(0.1, this.manager.reachedAlerts().getFirst().price());
        Assertions.assertEquals(2, this.manager.alerts().size());
        this.publish(book("[]", "[]"));
        Assertions.assertEquals(1, this.notifications.size());
    }

    @Test
    void missingProductAndMalformedBuySideKeepTheAlert() {
        this.save(PriceSource.Sell, Direction.Below, 100);
        this.data.onUpdate(Map.of());
        this.publish(book("[{\"pricePerUnit\":0}]", "[]"));
        this.publish(book("null", "[]"));
        Assertions.assertEquals(1, this.manager.alerts().size());
        Assertions.assertTrue(this.manager.reachedAlerts().isEmpty());

        this.publish(book("[]", "[]"));
        Assertions.assertEquals(1, this.manager.reachedAlerts().size());
    }

    @Test
    void realQuoteUsesTheBuyOrderSide() {
        this.save(PriceSource.Sell, Direction.Below, 10);
        this.save(PriceSource.Buy, Direction.Below, 10);
        this.publish(book("[{\"pricePerUnit\":9,\"amount\":1,\"orders\":1}]",
            "[{\"pricePerUnit\":11,\"amount\":1,\"orders\":1}]"));

        Assertions.assertEquals(1, this.manager.reachedAlerts().size());
        Assertions.assertEquals(PriceSource.Sell, this.manager.reachedAlerts().getFirst().alert().type.source());
        Assertions.assertEquals(9, this.manager.reachedAlerts().getFirst().price());
    }

    private void save(PriceSource source, Direction direction, double threshold) {
        this.manager.saveAlert(null, new AlertDefinition(System.currentTimeMillis(), ITEM,
            new AlertType(source, direction), threshold)).get();
    }

    private void publish(Product product) {
        this.data.onUpdate(Map.of(ITEM.productId(), product));
    }

    private static Product book(String buyOrders, String sellOffers) {
        return new Gson().fromJson("""
            {"sell_summary":%s,"buy_summary":%s}
            """.formatted(buyOrders, sellOffers), Product.class);
    }
}
