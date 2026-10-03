package com.github.lutzluca.btrbz.data;

import com.github.lutzluca.btrbz.data.BazaarMessageDispatcher.BazaarMessage;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.OutstandingOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class OrderModelsTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("statusVariants")
    void comparesStatusVariants(
        String description,
        OrderStatus current,
        OrderStatus other,
        boolean expected
    ) {
        Assertions.assertEquals(expected, current.sameVariant(other));
    }

    private static Stream<Arguments> statusVariants() {
        return Stream.of(
            Arguments.of("same unknown variant", new OrderStatus.Unknown(), new OrderStatus.Unknown(), true),
            Arguments.of("same undercut variant with different amounts",
                new OrderStatus.Undercut(5.0), new OrderStatus.Undercut(10.0), true),
            Arguments.of("different variants", new OrderStatus.Unknown(), new OrderStatus.Top(), false),
            Arguments.of("null has no variant", new OrderStatus.Unknown(), null, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trackedOrders")
    void matchesTrackedOrderShape(
        String description,
        OrderInfo.UnfilledOrderInfo tracked,
        OrderInfo incoming,
        boolean expected
    ) {
        Assertions.assertEquals(expected, new TrackedOrder(tracked).matches(incoming));
    }

    private static Stream<Arguments> trackedOrders() {
        var tracked = new OrderInfo.UnfilledOrderInfo(
            "Enchanted Hopper", OrderType.Buy, 64, 1234.5, 12, 0, 3);
        return Stream.of(
            Arguments.of("equivalent shape despite fill and slot differences", tracked,
                unfilledInfo("Enchanted Hopper", OrderType.Buy, 64, 1234.5), true),
            Arguments.of("different product name", tracked,
                unfilledInfo("Wrong Item", OrderType.Buy, 64, 1234.5), false),
            Arguments.of("different order type", tracked,
                unfilledInfo("Enchanted Hopper", OrderType.Sell, 64, 1234.5), false),
            Arguments.of("different volume", tracked,
                unfilledInfo("Enchanted Hopper", OrderType.Buy, 63, 1234.5), false),
            Arguments.of("exact price difference", tracked,
                unfilledInfo("Enchanted Hopper", OrderType.Buy, 64, 1234.5001), false),
            Arguments.of("positive and negative zero are different prices",
                new OrderInfo.UnfilledOrderInfo("Heat Core", OrderType.Sell, 1, 0.0, 0, 0, 7),
                unfilledInfo("Heat Core", OrderType.Sell, 1, -0.0), false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("trackedProductIdentities")
    void matchesResolvedProductsByIdWithUiNameFallback(
        String description,
        ProductIdentity trackedProduct,
        String trackedUiName,
        ProductIdentity incomingProduct,
        String incomingUiName,
        boolean expected
    ) {
        var tracked = new TrackedOrder(new OrderInfo.UnfilledOrderInfo(
            trackedProduct, trackedUiName, OrderType.Buy, 64, 1234.5, 0, 0, 3));
        var incoming = new OrderInfo.UnfilledOrderInfo(
            incomingProduct, incomingUiName, OrderType.Buy, 64, 1234.5, 0, 0, 3);

        Assertions.assertEquals(expected, tracked.matches(incoming));
    }

    private static Stream<Arguments> trackedProductIdentities() {
        return Stream.of(
            Arguments.of("same id wins over changed display and UI names",
                indexedIdentity("ENCHANTED_HOPPER", "Old Display"), "Enchanted Hopper",
                indexedIdentity("ENCHANTED_HOPPER", "New Display"), "Different UI Name", true),
            Arguments.of("different ids win over equal UI names",
                indexedIdentity("ENCHANTED_HOPPER", "Enchanted Hopper"), "Enchanted Hopper",
                indexedIdentity("OTHER_HOPPER", "Enchanted Hopper"), "Enchanted Hopper", false),
            Arguments.of("one resolved product falls back to equal UI names",
                indexedIdentity("ENCHANTED_HOPPER", "Enchanted Hopper"), "Enchanted Hopper",
                ProductIdentity.fromName("Enchanted Hopper"), "Enchanted Hopper", true));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "equivalent setup, Sell, 12, Summoning Eye, 9900000, true",
        "different product name, Sell, 12, Heat Core, 9900000, false",
        "different type, Buy, 12, Summoning Eye, 9900000, false",
        "different volume, Sell, 11, Summoning Eye, 9900000, false",
        "different total, Sell, 12, Summoning Eye, 9900001, false"
    })
    void matchesOutstandingSetupShape(
        String description,
        OrderType type,
        int volume,
        String productName,
        double total,
        boolean expected
    ) {
        var outstanding = new OutstandingOrderInfo(
            "Summoning Eye", OrderType.Sell, 12, 825000.0, 9_900_000.0);
        var setup = new BazaarMessage.OrderSetup(type, volume, productName, total);

        Assertions.assertEquals(expected, outstanding.matches(setup));
    }

    @Test
    void matchesIndexedOutstandingOrderByUiProductName() {
        var outstanding = new OutstandingOrderInfo(
            indexedIdentity("AOTE_STONE", "Warped Stone"),
            "Warped Stone", OrderType.Sell, 2, 5_649_851.4, 11_299_702.8);
        var setup = new BazaarMessage.OrderSetup(OrderType.Sell, 2, "Warped Stone", 11_299_702.8);

        Assertions.assertTrue(outstanding.matches(setup));
    }

    private static OrderInfo.UnfilledOrderInfo unfilledInfo(
        String productName,
        OrderType type,
        int volume,
        double pricePerUnit
    ) {
        return new OrderInfo.UnfilledOrderInfo(productName, type, volume, pricePerUnit, 0, 0, 0);
    }

    private static ProductIdentity indexedIdentity(String productId, String formattedName) {
        return ProductIdentity.fromIndex(new IndexedProduct(productId, formattedName));
    }
}
