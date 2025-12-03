package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.ResolvedAlertArgs;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.AlertType;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Utils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import io.vavr.control.Try;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.api.reply.skyblock.SkyBlockBazaarReply.Product;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Slf4j
public class AlertManager {

    public AlertManager() { }

    public void onBazaarUpdate(Map<String, Product> products) {
        if (!ConfigManager.get().alert.enabled) {
            return;
        }

        ConfigManager.withConfig(cfg -> {
            var it = cfg.alert.alerts.iterator();

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

                if (duration > Utils.WEEK_DURATION_MS && curr.remindedAfter < Utils.WEEK_DURATION_MS) {
                    Notifier.notifyOutdatedAlert(curr, "over a week");
                    curr.remindedAfter = duration;
                    continue;
                }

                if (duration > Utils.MONTH_DURATION_MS && curr.remindedAfter < Utils.MONTH_DURATION_MS) {
                    Notifier.notifyOutdatedAlert(curr, "over a month");
                    curr.remindedAfter = duration;
                }
            }
        });
    }

    public boolean addAlert(ResolvedAlertArgs args) {
        var alerts = ConfigManager.get().alert.alerts;
        var exist = alerts.stream().anyMatch(alert -> alert.matches(args));

        if (!exist) {
            ConfigManager.withConfig(cfg -> {
                cfg.alert.alerts.add(new Alert(args));
            });
        }

        return !exist;
    }

    public void removeAlert(UUID id) {
        var removed = ConfigManager.compute(cfg -> Utils.removeIfAndReturn(
            cfg.alert.alerts,
            alert -> alert.id.equals(id)
        ));

        if (removed.isEmpty()) {
            Notifier.notifyPlayer(Notifier
                .prefix()
                .append(Component
                    .literal("Failed to find an alert associated with " + id + " - it may have already been removed")
                    .withStyle(ChatFormatting.GRAY)));
            return;
        }
        if (removed.size() > 1) {
            log.error("Multiple alerts found with identical UUID");
        }

        Notifier.notifyPlayer(Notifier
            .prefix()
            .append(Component.literal("Alert removed successfully!").withStyle(ChatFormatting.GRAY)));

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

        public MutableComponent format() {
            return Component
                .empty()
                .append(Component.literal(productName).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                .append(Component
                    .literal(Utils.formatDecimal(this.price, 1, true) + "coins")
                    .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + type.format() + ")").withStyle(ChatFormatting.DARK_GRAY));
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

    public static class AlertConfig {

        public boolean enabled = true;
        public List<Alert> alerts = new ArrayList<>();

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Price Alerts"))
                .description(OptionDescription.of(Component.literal(
                    "Enable or disable price alerts. When disabled alerts will not be fired; when re-enabled any alerts whose price is already reached will fire immediately.")))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Price Alerts"))
                .description(OptionDescription.of(Component.literal(
                    "Configure price alerting behavior and manage active alerts")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}
