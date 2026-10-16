package com.github.lutzluca.btrbz.core.widgets.productcontext;

import org.junit.jupiter.api.Assertions;
import com.github.lutzluca.btrbz.data.BazaarData;
import java.util.OptionalInt;
import java.util.List;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Product context widget data")
class ProductContextWidgetDataTest {
    @Test
    @DisplayName("preserve fractional spreads until total calculation")
    void preservesFractionalSpreads() {
        var snapshot = new ProductContextWidgetData.Snapshot(
            Optional.empty(), new BazaarData.MarketPrices(Optional.of(1.0), Optional.of(1.4)),
            Optional.empty(), OptionalInt.of(100_000));

        Assertions.assertEquals(0.4, snapshot.perItem().orElseThrow(), 0.00001);
        Assertions.assertEquals(40_000.0, snapshot.total().orElseThrow(), 0.00001);
    }

    @Test
    void distinguishZeroStockFromUnknownStockAndMissingQuotes() {
        var prices = new BazaarData.MarketPrices(Optional.of(10.0), Optional.of(12.0));
        var zero = new ProductContextWidgetData.Snapshot(Optional.empty(), prices, Optional.empty(),
            OptionalInt.of(0));
        var unknown = new ProductContextWidgetData.Snapshot(Optional.empty(), prices, Optional.empty(),
            OptionalInt.empty());
        var oneSide = new ProductContextWidgetData.Snapshot(Optional.empty(),
            new BazaarData.MarketPrices(Optional.of(10.0), Optional.empty()), Optional.empty(), OptionalInt.of(10));
        Assertions.assertEquals(0.0, zero.total().orElseThrow());
        Assertions.assertEquals(2.0, unknown.perItem().orElseThrow());
        Assertions.assertTrue(unknown.total().isEmpty());
        Assertions.assertTrue(oneSide.perItem().isEmpty());
        Assertions.assertTrue(oneSide.total().isEmpty());
    }

    @Test
    void parsesOnlyCompleteInventoryQuantitiesWithinIntegerRange() {
        Assertions.assertEquals(12_345,
            ProductContextWidgetData.parseInventoryCount("Inventory: 12,345 items").orElseThrow());
        Assertions.assertEquals(0,
            ProductContextWidgetData.parseInventoryCount("Inventory: 0 items").orElseThrow());
        Assertions.assertEquals(1,
            ProductContextWidgetData.parseInventoryCount("Inventory: 1 item").orElseThrow());
        for (var line : List.of("Inventory: 640 items extra", "Inventory: 1.5 items",
            "Inventory: 4,294,967,296 items", "Inventory limit: 12 items", "Inventory: 12,34 items")) {
            Assertions.assertTrue(ProductContextWidgetData.parseInventoryCount(line).isEmpty(), line);
        }
    }

}
