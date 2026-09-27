package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.AlertManager;
import com.github.lutzluca.btrbz.core.AlertManager.AlertConfig;
import com.github.lutzluca.btrbz.core.AlertManager.ReachedAlert;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PriceAlertNoticeTest {

    @Test
    void capturesTheTriggeringObservationAndWatchedCondition() {
        var product = new IndexedProduct("ENCHANTED_DIAMOND", "§aEnchanted Diamond");
        var config = new AlertConfig();
        var manager = new AlertManager(new BazaarData(), () -> config, () -> {}, _ -> {});
        var alert = manager.saveAlert(null,
            new AlertDefinition(1_000L, product, new AlertType(PriceSource.Sell, Direction.Above), 1_000)).get();
        var reached = new ReachedAlert(alert, 2_000L, 1_012);

        var notice = PriceAlertNotice.from(reached, product);

        Assertions.assertEquals("§aEnchanted Diamond", notice.formattedProductName());
        Assertions.assertEquals("Sell Price ≥", notice.condition());
        Assertions.assertEquals("1,000.0 coins", notice.targetCoins());
        Assertions.assertEquals("1,012.0 coins", notice.observedCoins());
    }
}
