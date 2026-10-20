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

    @Test
    void showsEssenceAcquisitionTotalsWithoutTruncatingUnitPrices() {
        List<Component> lines = new ArrayList<>();
        ProductInfoTooltip.appendEssenceCost(lines, new ProductInfoTooltip.Prices(3_415.75, 3_105.125), 100);

        Assertions.assertEquals(List.of("", "Essence cost estimate",
            "Instant buy: 341,575.0 coins (3,415.8 each)", "Buy order: 310,512.5 coins (3,105.1 each)"),
            lines.stream().map(Component::getString).toList());
    }

    @Test
    void keepsMissingEssenceQuotesSeparateAndSupportsAZeroCost() {
        var examples = List.of(
            new CostCase(new ProductInfoTooltip.Prices(null, 617.0), 10_000,
                "Instant buy: Not Available", "Buy order: 6,170,000.0 coins (617.0 each)"),
            new CostCase(new ProductInfoTooltip.Prices(684.9, null), 10_000,
                "Instant buy: 6,849,000.0 coins (684.9 each)", "Buy order: Not Available"),
            new CostCase(new ProductInfoTooltip.Prices(684.9, 617.0), 0,
                "Instant buy: 0.0 coins (684.9 each)", "Buy order: 0.0 coins (617.0 each)"));

        for (var example : examples) {
            List<Component> lines = new ArrayList<>();
            ProductInfoTooltip.appendEssenceCost(lines, example.prices(), example.count());
            Assertions.assertEquals(List.of("", "Essence cost estimate", example.instantBuy(), example.buyOrder()),
                lines.stream().map(Component::getString).toList());
        }
    }

    @Test
    void showsUnitPricesAndShiftTotalsForOwnedEssence() {
        var quantity = new ProductInfoQuantity(ProductInfoQuantity.Source.ESSENCE_BALANCE, OptionalInt.of(3_115));
        var prices = new ProductInfoTooltip.Prices(684.9, 617.0);
        List<Component> unit = new ArrayList<>();
        List<Component> total = new ArrayList<>();
        ProductInfoTooltip.append(unit, prices, quantity, false);
        ProductInfoTooltip.append(total, prices, quantity, true);

        Assertions.assertEquals(List.of("", "Hold SHIFT for owned essence total (3,115 essence)",
            "Buy Price: 684.9 coins", "Sell Price: 617.0 coins"),
            unit.stream().map(Component::getString).toList());
        Assertions.assertEquals(List.of("", "Owned essence total (3,115 essence)",
            "Buy Price: 2,133,463.5 coins (684.9 each)", "Sell Price: 1,921,955.0 coins (617.0 each)"),
            total.stream().map(Component::getString).toList());
    }

    private record CostCase(ProductInfoTooltip.Prices prices, int count, String instantBuy, String buyOrder) {}

    private record QuantityCase(
        String description, OptionalInt count, String unitHint, String shiftHint,
        String shiftBuy, String shiftSell
    ) {}
}
