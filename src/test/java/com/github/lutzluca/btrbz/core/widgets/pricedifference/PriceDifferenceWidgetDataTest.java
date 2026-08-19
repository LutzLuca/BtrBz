package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
//? if <26.2 {
import net.minecraft.network.chat.TextColor;
//?}

@DisplayName("Price difference widget data")
class PriceDifferenceWidgetDataTest {
    @Test
    @DisplayName("preserve fractional spreads until total calculation")
    void preservesFractionalSpreads() {
        var snapshot = new PriceDifferenceWidgetData.Snapshot(
            Component.literal("Product"), Optional.empty(), 0.4, 100_000);

        assertEquals(0.4, snapshot.perItem());
        assertEquals(40_000.0, snapshot.total());
    }

    @Test
    @DisplayName("preserve product name formatting")
    void preservesProductNameFormatting() {
        var snapshot = new PriceDifferenceWidgetData.Snapshot(
            Component.literal("Enchanted Charcoal").withStyle(ChatFormatting.GREEN),
            Optional.empty(), 1, 1);

        assertEquals("Enchanted Charcoal", snapshot.productName().getString());

        //? if <26.2 {
        assertEquals(ChatFormatting.GREEN.getColor(), snapshot.productName().getStyle().getColor().getValue());
        //?} else {
        /*assertEquals(TextColor.GREEN.getValue(), snapshot.productName().getStyle().getColor().getValue());
        *///?}
    }
}
