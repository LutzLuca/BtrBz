package com.github.lutzluca.btrbz.core.productinfo;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProductInfoTooltipTest {

    @Test
    void showsBothUnitPricesAndUsesTheMenuQuantityForShiftTotals() {
        for (var source : ProductInfoQuantity.Source.values()) {
            var quantity = new ProductInfoQuantity(source, OptionalInt.of(32));
            var prices = new ProductInfoTooltip.Prices(10.0, 7.5);
            List<Component> unit = new ArrayList<>();
            List<Component> total = new ArrayList<>();

            ProductInfoTooltip.append(unit, prices, quantity, false);
            ProductInfoTooltip.append(total, prices, quantity, true);

            Assertions.assertTrue(unit.get(2).getString().contains("10.0 coins"));
            Assertions.assertTrue(unit.get(3).getString().contains("7.5 coins"));
            Assertions.assertTrue(total.get(2).getString().contains("320.0 coins"));
            Assertions.assertTrue(total.get(3).getString().contains("240.0 coins"));
        }
    }

    @Test
    void offersNoShiftTotalForOneItemOrAnEmptyMenuEntry() {
        for (var source : ProductInfoQuantity.Source.values()) {
            for (var count : List.of(0, 1)) {
                var quantity = new ProductInfoQuantity(source, OptionalInt.of(count));
                var prices = new ProductInfoTooltip.Prices(10.0, 7.5);
                List<Component> unit = new ArrayList<>();
                List<Component> total = new ArrayList<>();

                ProductInfoTooltip.append(unit, prices, quantity, false);
                ProductInfoTooltip.append(total, prices, quantity, true);

                Assertions.assertEquals(3, unit.size());
                Assertions.assertEquals(unit.stream().map(Component::getString).toList(),
                    total.stream().map(Component::getString).toList());
                Assertions.assertTrue(total.get(1).getString().contains("10.0 coins"));
            }
        }
    }

    @Test
    void retainsUnitPricesWhenTheMenuQuantityCannotBeParsed() {
        var quantity = new ProductInfoQuantity(ProductInfoQuantity.Source.STASH, OptionalInt.empty());
        List<Component> lines = new ArrayList<>();

        ProductInfoTooltip.append(lines, new ProductInfoTooltip.Prices(10.0, 7.5), quantity, true);

        Assertions.assertEquals(4, lines.size());
        Assertions.assertTrue(lines.get(2).getString().contains("10.0 coins"));
        Assertions.assertTrue(lines.get(3).getString().contains("7.5 coins"));
    }
}
