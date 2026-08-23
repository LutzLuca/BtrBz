package com.github.lutzluca.btrbz.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;
import org.junit.jupiter.api.Test;

class BazaarLiveProductSnapshotTest {
    private static final ProductIdentity IDENTITY = ProductIdentity.fromRuntime("Item", "ITEM", null);

    @Test
    void retainsTimestampMapsRawSidesAndSortsLevels() {
        var product = product();
        setField(product, "sellSummary", List.of(
            summary(product, 9, 3, 2), summary(product, Double.NaN, 99, 9), summary(product, 11, 4, 3)));
        setField(product, "buySummary", List.of(
            summary(product, 15, 5, 4), summary(product, 13, 6, 5)));
        var status = product.new Status();
        setField(status, "sellOrders", 7L);
        setField(status, "sellVolume", 70L);
        setField(status, "buyOrders", 8L);
        setField(status, "buyVolume", 80L);
        setField(product, "quickStatus", status);
        var data = new BazaarData();
        var published = new AtomicReference<BazaarData.MarketSnapshot>();
        data.addListener(published::set);
        Instant updated = Instant.parse("2026-08-20T12:00:00Z");

        data.onUpdate(new BazaarMarketUpdate(updated.toEpochMilli(), Map.of("ITEM", product)));
        var snapshot = published.get().liveProductSnapshot(IDENTITY);

        assertEquals(updated, snapshot.lastUpdated().orElseThrow());
        assertTrue(snapshot.marketDataAvailable());
        assertEquals(List.of(11d, 9d), snapshot.buyOrders().levels().stream().map(PriceLevel::price).toList());
        assertEquals(List.of(13d, 15d), snapshot.sellOffers().levels().stream().map(PriceLevel::price).toList());
        assertEquals(13, snapshot.buyPrice().orElseThrow());
        assertEquals(11, snapshot.sellPrice().orElseThrow());
        assertEquals(new Totals.Available(7, 70), snapshot.buyOrders().totals());
        assertEquals(new Totals.Available(8, 80), snapshot.sellOffers().totals());
        assertThrows(UnsupportedOperationException.class,
            () -> snapshot.buyOrders().levels().add(new PriceLevel(1, 1, 1)));
    }

    @Test
    void distinguishesMissingQuickStatusFromKnownZeroTotals() {
        var withoutStatus = product();
        var data = new BazaarData();
        data.onUpdate(new BazaarMarketUpdate(1, Map.of("ITEM", withoutStatus)));
        assertInstanceOf(Totals.Unavailable.class, data.liveProductSnapshot(IDENTITY).buyOrders().totals());

        var withStatus = product();
        withStatus.getSellSummary();
        var status = withStatus.new Status();
        setField(withStatus, "quickStatus", status);
        data.onUpdate(new BazaarMarketUpdate(2, Map.of("ITEM", withStatus)));

        assertEquals(new Totals.Available(0, 0), data.liveProductSnapshot(IDENTITY).buyOrders().totals());
        assertEquals(new Totals.Available(0, 0), data.liveProductSnapshot(IDENTITY).sellOffers().totals());
    }

    @Test
    void filtersInvalidLevelsAndGroupsDuplicatePrices() {
        var product = product();
        setField(product, "sellSummary", List.of(
            summary(product, 10, 3, 2),
            summary(product, 10, 4, 5),
            summary(product, 0, 99, 9),
            summary(product, 11, -1, 1),
            summary(product, 12, 1, -1)));
        setField(product, "buySummary", List.of(
            summary(product, 13, Long.MAX_VALUE, Integer.MAX_VALUE),
            summary(product, 13, 1, 1)));
        var data = new BazaarData();

        data.onUpdate(new BazaarMarketUpdate(1, Map.of("ITEM", product)));
        var snapshot = data.liveProductSnapshot(IDENTITY);

        assertEquals(List.of(new PriceLevel(10, 7, 7)), snapshot.buyOrders().levels());
        assertEquals(List.of(new PriceLevel(13, Long.MAX_VALUE, Integer.MAX_VALUE)),
            snapshot.sellOffers().levels());
        assertEquals(13, snapshot.buyPrice().orElseThrow());
        assertEquals(10, snapshot.sellPrice().orElseThrow());
    }

    @Test
    void marksNegativeQuickStatusTotalsUnavailable() {
        var product = product();
        var status = product.new Status();
        setField(status, "sellOrders", -1L);
        setField(status, "sellVolume", 10L);
        setField(status, "buyOrders", 2L);
        setField(status, "buyVolume", -3L);
        setField(product, "quickStatus", status);
        var data = new BazaarData();

        data.onUpdate(new BazaarMarketUpdate(1, Map.of("ITEM", product)));
        var snapshot = data.liveProductSnapshot(IDENTITY);

        assertInstanceOf(Totals.Unavailable.class, snapshot.buyOrders().totals());
        assertInstanceOf(Totals.Unavailable.class, snapshot.sellOffers().totals());
        assertTrue(!snapshot.marketDataAvailable());
    }

    @Test
    void presentationModelsRejectInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new PriceLevel(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PriceLevel(1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PriceLevel(1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> new Totals.Available(-1, 1));
    }

    @Test
    void missingProductHasEmptySidesWithoutZeroPrices() {
        var snapshot = new BazaarData().liveProductSnapshot(IDENTITY);

        assertTrue(snapshot.buyOrders().levels().isEmpty());
        assertTrue(snapshot.sellOffers().levels().isEmpty());
        assertTrue(snapshot.buyPrice().isEmpty());
        assertTrue(snapshot.sellPrice().isEmpty());
        assertTrue(snapshot.lastUpdated().isEmpty());
        assertTrue(!snapshot.marketDataAvailable());
    }

    private static Product product() {
        var reply = new SkyBlockBazaarReply();
        var product = reply.new Product();
        setField(product, "productId", "ITEM");
        return product;
    }

    private static Summary summary(Product product, double price, long amount, long orders) {
        var summary = product.new Summary();
        setField(summary, "pricePerUnit", price);
        setField(summary, "amount", amount);
        setField(summary, "orders", orders);
        return summary;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
