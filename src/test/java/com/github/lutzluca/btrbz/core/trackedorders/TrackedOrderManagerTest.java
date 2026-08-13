package com.github.lutzluca.btrbz.core.trackedorders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrderId;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product.Summary;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TrackedOrderManagerTest {

    @Nested
    @DisplayName("stable identity and ordering")
    class StableIdentityAndOrdering {

        @Test
        void keepsSessionIdentityAcrossSnapshots() {
            var manager = new TrackedOrderManager(new BazaarData());
            var order = trackedOrder(ProductIdentity.fromName("Troubled Bubble"));
            var otherOrder = trackedOrder(ProductIdentity.fromName("Other Product"));
            manager.addTrackedOrder(order);
            manager.addTrackedOrder(otherOrder);

            var firstSnapshot = manager.currentOrders().getFirst();
            var secondSnapshot = manager.currentOrders().getFirst();

            assertEquals(order.id(), firstSnapshot.id());
            assertEquals(firstSnapshot.id(), secondSnapshot.id());
            assertNotEquals(otherOrder.id(), firstSnapshot.id());
        }

        @Test
        void reordersByStableIdAndDropIndex() {
            var manager = new TrackedOrderManager(new BazaarData());
            var first = trackedOrder(ProductIdentity.fromName("First"), 1.0);
            var second = trackedOrder(ProductIdentity.fromName("Second"), 2.0);
            var third = trackedOrder(ProductIdentity.fromName("Third"), 3.0);
            manager.addTrackedOrder(first);
            manager.addTrackedOrder(second);
            manager.addTrackedOrder(third);

            assertTrue(manager.reorder(first.id(), 2));

            assertEquals(
                List.of(second.id(), first.id(), third.id()),
                manager.currentOrders().stream().map(TrackedOrderManager.TrackedOrderSnapshot::id).toList());
            assertFalse(manager.reorder(new TrackedOrderId(UUID.randomUUID()), 0));
        }

        @Test
        void preservesDuplicateOrderIdentityAcrossDisplayReorderAndSync() {
            var manager = new TrackedOrderManager(new BazaarData());
            var product = ProductIdentity.fromName("Duplicate Product");
            var firstInfo = unfilledOrder(product, 2, 5);
            var secondInfo = unfilledOrder(product, 7, 6);
            var first = new TrackedOrder(firstInfo);
            var second = new TrackedOrder(secondInfo);
            manager.addTrackedOrder(first);
            manager.addTrackedOrder(second);

            assertTrue(manager.reorder(first.id(), 2));
            manager.syncOrders(List.of(firstInfo, secondInfo));

            assertEquals(List.of(second.id(), first.id()), manager
                .currentOrders()
                .stream()
                .map(TrackedOrderManager.TrackedOrderSnapshot::id)
                .toList());
            var snapshotsById = manager.currentOrders().stream().collect(Collectors.toMap(
                TrackedOrderManager.TrackedOrderSnapshot::id,
                snapshot -> snapshot));
            assertEquals(5, snapshotsById.get(first.id()).slot());
            assertEquals(2, snapshotsById.get(first.id()).fillAmountSnapshot());
            assertEquals(6, snapshotsById.get(second.id()).slot());
            assertEquals(7, snapshotsById.get(second.id()).fillAmountSnapshot());
        }
    }

    @Nested
    @DisplayName("tracked status")
    class TrackedStatus {

        @Test
        void productSpreadUsesSellOfferMinusBuyOrder() {
            var marketProduct = product("TROUBLED_BUBBLE");
            setSummaries(
                marketProduct,
                List.of(summary(marketProduct, 10.0, 64, 1)),
                List.of(summary(marketProduct, 12.5, 64, 1)));
            var data = data(Map.of("TROUBLED_BUBBLE", marketProduct));

            var spread = data.productSpread(ProductIdentity.fromRuntime(
                "Troubled Bubble",
                "TROUBLED_BUBBLE",
                null));

            assertEquals(2.5, spread.orElseThrow());
        }

        @Test
        void usesRuntimeBazaarProductIdForMarketLookup() {
            var marketProduct = product("TROUBLED_BUBBLE");
            setSummaries(
                marketProduct,
                List.of(summary(marketProduct, 10.0, 64, 1)),
                List.of());
            var snapshot = snapshot(Map.of("TROUBLED_BUBBLE", marketProduct));
            var evaluator = new TrackedOrderStatusEvaluator();
            var order = trackedOrder(ProductIdentity.fromRuntime(
                "Troubled Bubble",
                "TROUBLED_BUBBLE",
                ChatFormatting.GOLD + "Troubled Bubble"));

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
                List.of());
            var snapshot = snapshot(Map.of("TROUBLED_BUBBLE", marketProduct));
            var evaluator = new TrackedOrderStatusEvaluator();
            var order = trackedOrder(ProductIdentity.fromRuntime(
                "Troubled Bubble",
                null,
                ChatFormatting.GOLD + "Troubled Bubble"));

            assertTrue(evaluator.computeStatusUpdates(List.of(order), snapshot).toList().isEmpty());
        }

        @Test
        void emitsChangedAmountForAnAlreadyUndercutOrder() {
            var marketProduct = product("TROUBLED_BUBBLE");
            setSummaries(
                marketProduct,
                List.of(summary(marketProduct, 12.0, 64, 1)),
                List.of());
            var snapshot = snapshot(Map.of("TROUBLED_BUBBLE", marketProduct));
            var evaluator = new TrackedOrderStatusEvaluator();
            var order = trackedOrder(ProductIdentity.fromRuntime(
                "Troubled Bubble",
                "TROUBLED_BUBBLE",
                null));
            order.status = new OrderStatus.Undercut(1.0);

            var updates = evaluator.computeStatusUpdates(List.of(order), snapshot).toList();

            assertEquals(1, updates.size());
            var current = assertInstanceOf(OrderStatus.Undercut.class, updates.getFirst().curr());
            assertEquals(2.0, current.amount);
            assertTrue(updates.getFirst().prev().sameVariant(current));
        }

        @Test
        void treatsMultipleOrdersAtTheBestPriceAsMatchedRegardlessOfReportedAmount() {
            var marketProduct = product("TROUBLED_BUBBLE");
            setSummaries(
                marketProduct,
                List.of(summary(marketProduct, 10.0, 0, 2)),
                List.of());
            var snapshot = snapshot(Map.of("TROUBLED_BUBBLE", marketProduct));
            var evaluator = new TrackedOrderStatusEvaluator();
            var order = trackedOrder(ProductIdentity.fromRuntime(
                "Troubled Bubble",
                "TROUBLED_BUBBLE",
                null));

            var updates = evaluator.computeStatusUpdates(List.of(order), snapshot).toList();

            assertEquals(1, updates.size());
            assertInstanceOf(OrderStatus.Matched.class, updates.getFirst().curr());
        }
    }

    @Nested
    @DisplayName("product grouping")
    class ProductGrouping {

        @Test
        void runtimeProductsWithIdsGroupByBazaarProductIdFirst() {
            var first = TrackedOrderGrouping.productKey(
                ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null),
                "Troubled Bubble");
            var second = TrackedOrderGrouping.productKey(
                ProductIdentity.fromRuntime("Different UI Text", "TROUBLED_BUBBLE", null),
                "Different UI Text");

            assertEquals(first, second);
        }

        @Test
        void unresolvedProductsWithoutRawIdsGroupByNormalizedFallbackName() {
            var first = TrackedOrderGrouping.productKey(ProductIdentity.fromName("Troubled Bubble"), "Troubled Bubble");
            var second = TrackedOrderGrouping.productKey(
                ProductIdentity.fromName("Different UI Text"),
                "  troubled   bubble  ");

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
                ChatFormatting.GOLD + "Troubled Bubble");

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
                    summary(marketProduct, 9.0, 1, 1)),
                List.of());
            var snapshot = snapshot(Map.of("TROUBLED_BUBBLE", marketProduct));
            var detector = new SelfUndercutDetector();
            var orders = List.of(
                trackedOrder(ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null), 10.0),
                trackedOrder(ProductIdentity.fromRuntime("Troubled Bubble", "TROUBLED_BUBBLE", null), 9.0));

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
            0));
    }

    private static OrderInfo.UnfilledOrderInfo unfilledOrder(
        ProductIdentity product,
        int filledAmount,
        int slot
    ) {
        return new OrderInfo.UnfilledOrderInfo(
            product,
            "Duplicate Product",
            OrderType.Buy,
            10,
            10.0,
            filledAmount,
            0,
            slot);
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
