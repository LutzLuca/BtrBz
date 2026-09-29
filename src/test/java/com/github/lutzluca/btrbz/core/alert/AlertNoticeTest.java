package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertNoticeTest {

    @Test
    void capturesTheTriggeringObservationAndWatchedCondition() {
        var product = new IndexedProduct("ENCHANTED_DIAMOND", "§aEnchanted Diamond");
        var config = new AlertConfig();
        var manager = new AlertManager(new BazaarData(), () -> config, () -> {}, _ -> {});
        var alert = manager.saveAlert(null,
            new AlertDefinition(1_000L, product,
                new AlertCondition.Price(new AlertType(PriceSource.Sell, Direction.Above), 1_000)))
            .get();
        var reached = new ReachedAlert(alert, 2_000L, new AlertCondition.Observation.Price(1_012));

        var notice = AlertNotice.from(reached, product);

        Assertions.assertEquals("§aEnchanted Diamond", notice.formattedProductName());
        Assertions.assertEquals("Sell Price ≥ 1,000.0 coins", notice.condition().getString());
        Assertions.assertEquals("Price target reached", notice.title());
        Assertions.assertEquals("Reached at 1,012.0 coins", notice.observation().getString());
        Assertions.assertEquals(TextColor.fromRgb(BazaarStyles.SELL_ACCENT),
            notice.condition().getSiblings().getFirst().getStyle().getColor());
        Assertions.assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD),
            notice.condition().getSiblings().getLast().getStyle().getColor());
        Assertions.assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD),
            notice.observation().getSiblings().getLast().getStyle().getColor());
    }

    @Test
    void liquidityDetailsDescribeTheSavedBoundAndCapturedQuantity() {
        var product = new IndexedProduct("ENCHANTED_DIAMOND", "§aEnchanted Diamond");
        var config = new AlertConfig();
        var manager = new AlertManager(new BazaarData(), () -> config, () -> {}, _ -> {});
        var condition = new AlertCondition.Liquidity(AlertCondition.LiquiditySide.BuyOrders, 12, 12.0);
        var alert = manager.saveAlert(null, new AlertDefinition(1_000L, product, condition)).get();
        var reached = new ReachedAlert(alert, 2_000L, new AlertCondition.Observation.Liquidity(180));

        var notice = AlertNotice.from(reached, product);

        Assertions.assertEquals("Liquidity target reached", notice.title());
        Assertions.assertEquals("Instantly sell 12 items at ≥ 12.0 coins each", notice.condition().getString());
        Assertions.assertEquals("Observed: 180 qualifying items", notice.observation().getString());
        Assertions.assertTrue(notice.condition().getSiblings().stream().noneMatch(part -> part.getStyle().isBold()));
        Assertions.assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY),
            notice.condition().getSiblings().get(2).getStyle().getColor());
        Assertions.assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD),
            notice.condition().getSiblings().get(3).getStyle().getColor());
        Assertions.assertEquals("Instantly buy 12 items at ≤ 12.0 coins each",
            AlertNotice.conditionText(new AlertCondition.Liquidity(
                AlertCondition.LiquiditySide.SellOffers, 12, 12.0)).getString());
    }
}
