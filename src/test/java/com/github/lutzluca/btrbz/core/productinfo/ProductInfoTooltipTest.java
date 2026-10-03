package com.github.lutzluca.btrbz.core.productinfo;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProductInfoTooltipTest {

    @Test
    void formatsUnitPricesAndShiftTotalsForTheSuppliedQuantity() {
        var cases = List.of(
            new QuantityCase("multiple items", OptionalInt.of(32),
                "Hold SHIFT for stash total (32 items)", "Stash total (32 items)",
                "Buy Price: 320.0 coins (32x)", "Sell Price: 240.0 coins (32x)"),
            new QuantityCase("empty entry", OptionalInt.of(0), null, null,
                "Buy Price: 10.0 coins", "Sell Price: 7.5 coins"),
            new QuantityCase("one item", OptionalInt.of(1), null, null,
                "Buy Price: 10.0 coins", "Sell Price: 7.5 coins"),
            new QuantityCase("unknown quantity", OptionalInt.empty(),
                "Stash total unavailable", "Stash total unavailable",
                "Buy Price: 10.0 coins", "Sell Price: 7.5 coins"));
        var prices = new ProductInfoTooltip.Prices(10.0, 7.5);

        for (var example : cases) {
            var quantity = new ProductInfoQuantity(ProductInfoQuantity.Source.STASH, example.count());
            List<Component> unit = new ArrayList<>();
            List<Component> total = new ArrayList<>();
            ProductInfoTooltip.append(unit, prices, quantity, false);
            ProductInfoTooltip.append(total, prices, quantity, true);
            var unitText = unit.stream().map(Component::getString).toList();
            var totalText = total.stream().map(Component::getString).toList();

            Assertions.assertTrue(unitText.contains("Buy Price: 10.0 coins"), example.description());
            Assertions.assertTrue(unitText.contains("Sell Price: 7.5 coins"), example.description());
            Assertions.assertTrue(totalText.contains(example.shiftBuy()), example.description());
            Assertions.assertTrue(totalText.contains(example.shiftSell()), example.description());
            Assertions.assertEquals(example.unitHint() == null ? 3 : 4, unitText.size(), example.description());
            Assertions.assertEquals(example.shiftHint() == null ? 3 : 4, totalText.size(), example.description());
            if (example.unitHint() != null) {
                Assertions.assertTrue(unitText.contains(example.unitHint()), example.description());
                Assertions.assertTrue(totalText.contains(example.shiftHint()), example.description());
            }
        }
    }

    private record QuantityCase(
        String description, OptionalInt count, String unitHint, String shiftHint,
        String shiftBuy, String shiftSell
    ) {}
}
