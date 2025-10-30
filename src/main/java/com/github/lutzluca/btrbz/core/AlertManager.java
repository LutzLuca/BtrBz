package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.ResolvedAlertArgs;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.AlertType;
import com.github.lutzluca.btrbz.core.config.Config;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Util;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;

@Slf4j
public class AlertManager {

    public AlertManager() { }

    public void onBazaarUpdate(Map<String, Product> products) {
        var it = Config.get().alert.alerts.iterator();
        while (it.hasNext()) {
            var curr = it.next();
            var priceResult = curr.getAssociatedPrice(products);
            if (priceResult.isFailure()) {
                Notifier.notifyInvalidProduct(curr);
                continue;
            }

            var price = priceResult.get();
            var reached = price.map(marketPrice -> curr.price <= marketPrice).orElse(true);

            if (reached) {
                it.remove();
                Notifier.notifyPriceReached(curr, price);
                continue;
            }

            var now = System.currentTimeMillis();
            var duration = now - curr.createdAt;

            if (duration > Util.WEEK_DURATION_MS && curr.remindedAfter < Util.WEEK_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, "over a week");
                curr.remindedAfter = Util.WEEK_DURATION_MS;
                continue;
            }

            if (duration > Util.MONTH_DURATION_MS && curr.remindedAfter < Util.MONTH_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, "over a month");
                curr.remindedAfter = Util.MONTH_DURATION_MS;
            }
        }
    }

    public void removeAlert(UUID id) {
        var removed = Util.removeIfAndReturn(
            Config.get().alert.alerts,
            alert -> alert.id.equals(id)
        );
        if (removed.isEmpty()) {
            Notifier.notifyPlayer(Notifier
                .prefix()
                .append("Failed to find an alert associated with " + id + " — it may have already been removed"));
            return;
        }
        if (removed.size() > 1) {
            log.error("Multiple alerts found with identical UUIDs.");
        }
        Notifier.notifyPlayer(Notifier.prefix().append("Alert removed successfully!"));
    }

    public static class Alert {

        public final UUID id;
        public final long createdAt;
        public final String productName;
        public final String productId;
        public final AlertType type;
        public final double price;

        long remindedAfter = -1;

        private Alert(ResolvedAlertArgs args) {
            this.id = UUID.randomUUID();
            this.createdAt = args.timestamp();
            this.productName = args.productName();
            this.productId = args.productId();
            this.type = args.type();
            this.price = args.price();
        }

        public Try<Optional<Double>> getAssociatedPrice(Map<String, Product> products) {
            var prod = products.get(this.productId);
            if (prod == null) {
                return Try.failure(new Exception("The product \"" + this.productName + "\" could not be found in the bazaar data"));
            }

            var price = switch (this.type) {
                case BuyOrder, InstaSell -> BazaarData.firstSummaryPrice(prod.getSellSummary());
                case SellOffer, InstaBuy -> BazaarData.firstSummaryPrice(prod.getBuySummary());
            };
            return Try.success(price);
        }
    }

    public static class AlertConfig {

        public boolean enabled = true;
        public List<Alert> alerts = new ArrayList<>();


    }
}
