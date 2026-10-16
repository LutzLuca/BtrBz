package com.github.lutzluca.btrbz.core.widgets.productcontext;

import org.junit.jupiter.api.Assertions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetProductContext;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.OptionalInt;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
//? if >=26.2 {
/*import net.minecraft.network.chat.TextColor;
*///?}

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
    @DisplayName("preserve product name formatting")
    void preservesProductNameFormatting() {
        var snapshot = new ProductContextWidgetData.Snapshot(
            Optional.of(new WidgetProductContext(ProductIdentity.fromName("Enchanted Charcoal"),
                Component.literal("Enchanted Charcoal").withStyle(ChatFormatting.GREEN), Optional.empty())),
            new BazaarData.MarketPrices(Optional.of(1.0), Optional.of(2.0)), Optional.empty(), OptionalInt.of(1));

        Assertions.assertEquals("Enchanted Charcoal", snapshot.product().orElseThrow().displayName().getString());

        //? if <26.2 {
        Assertions.assertEquals(ChatFormatting.GREEN.getColor(),
            snapshot.product().orElseThrow().displayName().getStyle().getColor().getValue());
        //?} else {
        /*Assertions.assertEquals(TextColor.GREEN.getValue(),
            snapshot.product().orElseThrow().displayName().getStyle().getColor().getValue());
        *///?}
    }
}
