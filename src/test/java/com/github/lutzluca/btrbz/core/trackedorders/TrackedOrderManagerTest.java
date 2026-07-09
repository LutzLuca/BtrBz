package com.github.lutzluca.btrbz.core.trackedorders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TrackedOrderManagerTest {

    @Nested
    @DisplayName("tracked status")
    class TrackedStatus {

        @Test
        void productSpreadUsesSellOfferMinusBuyOrder() {
            var marketProduct = product("TROUBLED_BUBBLE");
            setSummaries(
                marketProduct,
                List.of(summary(marketProduct, 10.0, 64, 1)),
                List.of(summary(marketProduct, 12.5, 64, 1))
            );
            var data = data(Map.of("TROUBLED_BUBBLE", marketProduct));

            var spread = data.productSpread(ProductIdentity.fromRuntime(
                "Troubled Bubble",
                "TROUBLED_BUBBLE",
                null
            ));

            assertEquals(2.5, spread.orElseThrow());
        }

        @Test
        void usesRuntimeBazaarProductIdForMarketLookup() {
            var marketProduct = product("TROUBLED_BUBBLE");
            setSummaries(
                marketProduct,
                List.of(summary(marketProduct, 10.0, 64, 1)),
                List.of()
            );
            var snapshot = snapshot(Map.of("TROUBLED_BUBBLE", marketProduct));
            var evaluator = new TrackedOrderStatusEvaluator();
            var order = trackedOrder(ProductIdentity.fromRuntime(
                "Troubled Bubble",
                "TROUBLED_BUBBLE",
                ChatFormatting.GOLD + "Troubled Bubble"
            ));

            var updates = evaluator.computeStatusUpdates(List.of(order), snapshot).toList();

            assertEquals(1, updates.size());
            assertInstanceOf(OrderStatus.Top.class, updates.getFirst().curr());
        }

        @Test
        void unresolvedProductWithoutRawIdDoesNotUseMarketLookup() {
            var marketProduct = product("TROUBLED_BUBBLE");
            setSummaries(
                marketProduct,
                List.of(summary(marketProduct, 10.0, 64, 1)),
                List.of()
            );
            var snapshot = snapshot(Map.of("TROUBLED_BUBBLE", marketProduct));
            var evaluator = new TrackedOrderStatusEvaluator();
            var order = trackedOrder(ProductIdentity.fromRuntime(
                "Troubled Bubble",
                null,
                ChatFormatting.GOLD + "Troubled Bubble"
            ));

            assertTrue(evaluator.computeStatusUpdates(List.of(order), snapshot).toList().isEmpty());
        }
    }

    @Nested
    @DisplayName("product grouping")
    class ProductGrouping {

        @Test
        void runtimeProductsWithIdsGroupByBazaarProductIdFirst() {
            var first = TrackedOrderGrouping.productKey(
                ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null),
                "Troubled Bubble"
            );
            var second = TrackedOrderGrouping.productKey(
                ProductIdentity.fromRuntime("Different UI Text", "TROUBLED_BUBBLE", null),
                "Different UI Text"
            );

            assertEquals(first, second);
        }

        @Test
        void unresolvedProductsWithoutRawIdsGroupByNormalizedFallbackName() {
            var first = TrackedOrderGrouping.productKey(ProductIdentity.fromName("Troubled Bubble"), "Troubled Bubble");
            var second = TrackedOrderGrouping.productKey(
                ProductIdentity.fromName("Different UI Text"),
                "  troubled   bubble  "
            );

            assertEquals(first, second);
        }
    }

    @Nested
    @DisplayName("product updater")
    class ProductUpdater {

        @Test
        void upgradesNameOnlyIdentityToRuntimeIdentityWithBazaarProductId() {
            var updater = new TrackedOrderProductUpdater(new BazaarData());
            var current = ProductIdentity.fromName("Troubled Bubble");
            var incoming = ProductIdentity.fromRuntime(
                "Troubled Bubble",
                "TROUBLED_BUBBLE",
                ChatFormatting.GOLD + "Troubled Bubble"
            );

            assertEquals(incoming, updater.strongestProduct(current, incoming, "Troubled Bubble"));
        }

        @Test
        void keepsRuntimeIdentityWhenIncomingEvidenceHasNoBazaarProductId() {
            var updater = new TrackedOrderProductUpdater(new BazaarData());
            var current = ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null);
            var incoming = ProductIdentity.fromName("Troubled Bubble");

            assertEquals(current, updater.strongestProduct(current, incoming, "Troubled Bubble"));
        }
    }

    @Nested
    @DisplayName("self-undercut detector")
    class SelfUndercutDetection {

        @Test
        void emitsOnlyMeaningfulPriceChanges() {
            var marketProduct = product("TROUBLED_BUBBLE");
            setSummaries(
                marketProduct,
                List.of(
                    summary(marketProduct, 10.0, 1, 1),
                    summary(marketProduct, 9.0, 1, 1)
                ),
                List.of()
            );
            var snapshot = snapshot(Map.of("TROUBLED_BUBBLE", marketProduct));
            var detector = new SelfUndercutDetector();
            var orders = List.of(
                trackedOrder(ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null), 10.0),
                trackedOrder(ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null), 9.0)
            );

            var first = detector.resolve(orders, snapshot);
            var second = detector.resolve(orders, snapshot);

            assertEquals(1, first.size());
            assertEquals(10.0, first.getFirst().bestPrice());
            assertEquals(9.0, first.getFirst().secondBestPrice());
            assertTrue(second.isEmpty());
        }
    }

    private static TrackedOrder trackedOrder(ProductIdentity product) {
        return trackedOrder(product, 10.0);
    }

    private static TrackedOrder trackedOrder(ProductIdentity product, double pricePerUnit) {
        return new TrackedOrder(new OrderInfo.UnfilledOrderInfo(
            product,
            "Troubled Bubble",
            OrderType.Buy,
            1,
            pricePerUnit,
            0,
            0,
            0
        ));
    }

    private static MarketSnapshot snapshot(Map<String, Product> products) {
        var data = data(products);
        var snapshot = new AtomicReference<MarketSnapshot>();
        data.addListener(snapshot::set);
        data.onUpdate(products);
        return snapshot.get();
    }

    private static BazaarData data(Map<String, Product> products) {
        var data = new BazaarData();
        data.onUpdate(products);
        return data;
    }

    private static Product product(String productId) {
        var reply = new SkyBlockBazaarReply();
        var product = reply.new Product();
        setField(product, "productId", productId);
        return product;
    }

    private static Summary summary(Product product, double pricePerUnit, long amount, long orders) {
        var summary = product.new Summary();
        setField(summary, "pricePerUnit", pricePerUnit);
        setField(summary, "amount", amount);
        setField(summary, "orders", orders);
        return summary;
    }

    private static void setSummaries(Product product, List<Summary> sellSummary, List<Summary> buySummary) {
        setField(product, "sellSummary", sellSummary);
        setField(product, "buySummary", buySummary);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException err) {
            throw new AssertionError("Failed to set " + name + " on " + target.getClass().getName(), err);
        }
    }
}
