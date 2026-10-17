package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NativeOrderBookHintTest {
    @Test
    void parsesOnlyCompleteNativeLevelLinesForTheCorrectSide() {
        Assertions.assertEquals(9_798_500.1,
            NativeOrderBookHint.price("- 9,798,500.1 coins each | 41x in 9 orders", OrderType.Buy).orElseThrow());
        Assertions.assertEquals(1,
            NativeOrderBookHint.price("- 1 coin each | 1x from 1 offer", OrderType.Sell).orElseThrow());
        for (String invalid : List.of(
            "Price per unit: 12.3 coins", "- 12.3 coins each | 1x from 1 offer",
            "- 12.31 coins each | 1x in 1 order", "- 1,23.4 coins each | 1x in 1 order",
            "- 12.3 coins each | 1x in 1 order (2)", "- 12.3 coins each | 1x in 1 order extra",
            "- 9007199254740992.1 coins each | 1x in 1 order",
            "- 12.3k coins each | 1x in 1 order", "- NaN coins each | 1x in 1 order")) {
            Assertions.assertTrue(NativeOrderBookHint.price(invalid, OrderType.Buy).isEmpty(), invalid);
        }
    }

    @Test
    void keepsOriginalStyledDashAndAppendsOnlyCountOrGrayDetailLine() {
        var product = new ProductIdentity("Product", Optional.of("PRODUCT"), null);
        var own = OrderBookMatches.from(product, List.of(
            OrderBookMatchesTest.order(product, OrderType.Buy, 12.3, 20, 8, 10),
            OrderBookMatchesTest.order(product, OrderType.Buy, 12.3, 30, 14, 11)));
        var original = Component.literal("- ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("12.3 coins each | 41x in 9 orders").withStyle(ChatFormatting.GOLD));
        var primary = Style.EMPTY.withColor(0xD8DEF2);
        var compact = new ArrayList<Component>(List.of(original));
        NativeOrderBookHint.annotate(compact, own, OrderType.Buy, false, primary);
        NativeOrderBookHint.annotate(compact, own, OrderType.Buy, false, primary);
        Assertions.assertEquals(List.of("- 12.3 coins each | 41x in 9 orders (2)"),
            compact.stream().map(Component::getString).toList());
        Assertions.assertEquals(0x555555,
            compact.getFirst().getStyle().getColor().getValue());
        Assertions.assertEquals("- 12.3 coins each | 41x in 9 orders", original.getString());
        var expanded = new ArrayList<Component>(List.of(original));
        NativeOrderBookHint.annotate(expanded, own, OrderType.Buy, true, primary);
        NativeOrderBookHint.annotate(expanded, own, OrderType.Buy, true, primary);
        Assertions.assertEquals(2, expanded.size());
        Assertions.assertSame(original, expanded.getFirst());
        Assertions.assertEquals("  - Your orders: 2 · ~28 items left", expanded.get(1).getString());
        Assertions.assertEquals(0xAAAAAA, expanded.get(1).getStyle().getColor().getValue());
        Assertions.assertEquals(0xD8DEF2, expanded.get(1).getSiblings().getFirst().getStyle().getColor().getValue());
    }
}
