package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertNoticeTest {

    @Test
    void describesTheSavedConditionAndCapturedObservation() {
        var product = new IndexedProduct("ENCHANTED_DIAMOND", "§aEnchanted Diamond");
        var cases = List.of(
            new NoticeCase("sell-price target",
                new AlertCondition.Price(new AlertType(PriceSource.Sell, Direction.Above), 1_000),
                new AlertCondition.Observation.Price(1_012), "Price target reached",
                "Sell Price ≥ 1,000.0 coins", "Reached at 1,012.0 coins"),
            new NoticeCase("buy-order liquidity",
                new AlertCondition.Liquidity(AlertCondition.LiquiditySide.BuyOrders, 12, 12.0),
                new AlertCondition.Observation.Liquidity(180), "Liquidity target reached",
                "Instantly sell 12 items at ≥ 12.0 coins each", "Observed: 180 qualifying items"),
            new NoticeCase("sell-offer liquidity",
                new AlertCondition.Liquidity(AlertCondition.LiquiditySide.SellOffers, 12, 12.0),
                new AlertCondition.Observation.Liquidity(180), "Liquidity target reached",
                "Instantly buy 12 items at ≤ 12.0 coins each", "Observed: 180 qualifying items"));

        for (var example : cases) {
            var alert = new Alert(UUID.randomUUID(), new AlertDefinition(1_000L, product, example.condition()), -1L);
            var notice = AlertNotice.from(new ReachedAlert(alert, 2_000L, example.observation()), product);

            Assertions.assertEquals("§aEnchanted Diamond", notice.formattedProductName(), example.description());
            Assertions.assertEquals(example.title(), notice.title(), example.description());
            Assertions.assertEquals(example.conditionText(), notice.condition().getString(), example.description());
            Assertions.assertEquals(example.observationText(), notice.observation().getString(), example.description());
        }
    }

    private record NoticeCase(
        String description, AlertCondition condition, AlertCondition.Observation observation,
        String title, String conditionText, String observationText
    ) {}
}
