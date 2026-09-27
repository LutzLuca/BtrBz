package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.AlertManager.ReachedAlert;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Utils;

/** Price-alert text captured from the observation that reached the target. */
public record PriceAlertNotice(
    String formattedProductName, String condition, String targetCoins, String observedCoins
) {

    public static PriceAlertNotice from(ReachedAlert reached, IndexedProduct product) {
        var alert = reached.alert();
        return new PriceAlertNotice(
            product.formattedName(),
            alert.type.format(),
            coins(alert.price),
            coins(reached.price()));
    }

    private static String coins(double price) {
        return Utils.formatDecimal(price, 1, true) + " coins";
    }
}
