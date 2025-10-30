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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Slf4j
public class AlertManager {

    public AlertManager() { }

    public void onBazaarUpdate(Map<String, Product> products) {
        var cfg = Config.get().alert;
        if (!cfg.enabled) {
            return;
        }

        var it = cfg.alerts.iterator();
        while (it.hasNext()) {
            var curr = it.next();
            var priceResult = curr.getAssociatedPrice(products);
            if (priceResult.isFailure()) {
                Notifier.notifyInvalidProduct(curr);
                continue;
            }

            // NOTE: its this even right?
            var price = priceResult.get();
            var reached = price.map(marketPrice -> switch (curr.type) {
                case SellOffer, InstaSell -> marketPrice >= curr.price;
                case BuyOrder, InstaBuy -> marketPrice <= curr.price;
            }).orElse(true);

            if (reached) {
                it.remove();
                Notifier.notifyPriceReached(curr, price);
                continue;
            }

            var now = System.currentTimeMillis();
            var duration = now - curr.createdAt;

            if (duration > Util.WEEK_DURATION_MS && curr.remindedAfter < Util.WEEK_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, "over a week");
                curr.remindedAfter = duration;
                continue;
            }

            if (duration > Util.MONTH_DURATION_MS && curr.remindedAfter < Util.MONTH_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, "over a month");
                curr.remindedAfter = duration;
            }
        }

        Config.HANDLER.save();
    }

    public boolean addAlert(ResolvedAlertArgs args) {
        var alerts = Config.get().alert.alerts;
        var exist = alerts.stream().anyMatch(alert -> alert.matches(args));

        if (!exist) {
            alerts.add(new Alert(args));
            Config.HANDLER.save();
        }

        return !exist;
    }

    public void removeAlert(UUID id) {
        var removed = Util.removeIfAndReturn(
            Config.get().alert.alerts,
            alert -> alert.id.equals(id)
        );
        if (removed.isEmpty()) {
            Notifier.notifyPlayer(Notifier
                .prefix()
                .append(Text
                    .literal("Failed to find an alert associated with " + id + " - it may have already been removed")
                    .formatted(Formatting.GRAY)));
            return;
        }
        if (removed.size() > 1) {
            log.error("Multiple alerts found with identical UUID");
        }

        Notifier.notifyPlayer(Notifier
            .prefix()
            .append(Text.literal("Alert removed successfully!").formatted(Formatting.GRAY)));

        Config.HANDLER.save();
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

        public MutableText format() {
            return Text
                .empty()
                .append(Text.literal(productName).formatted(Formatting.GOLD))
                .append(Text.literal(" @ ").formatted(Formatting.GRAY))
                .append(Text
                    .literal(Util.formatDecimal(this.price, 1, true) + "coins")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" (" + type.format() + ")").formatted(Formatting.DARK_GRAY));
        }

        public boolean matches(ResolvedAlertArgs args) {
            // @formatter:off
            return this.productName.equals(args.productName())
                && this.productId.equals(args.productId())
                && this.type == args.type()
                && Double.compare(this.price, args.price()) == 0;
            // @formatter:on
        }
    }

    // TODO options
    public static class AlertConfig {

        public boolean enabled = true;
        public List<Alert> alerts = new ArrayList<>();


    }
}
