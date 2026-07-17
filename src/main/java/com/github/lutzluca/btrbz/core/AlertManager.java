package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.ResolvedAlertArgs;
import com.github.lutzluca.btrbz.core.commands.alert.PriceExpression.AlertType;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.BazaarData.MarketSnapshot;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GsonUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Utils;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import io.vavr.control.Try;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

@Slf4j
public class AlertManager {

    private final BazaarData bazaarData;

    public AlertManager(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
        ConfigManager.get().alert.alerts.removeIf(Objects::isNull);
    }

    public void onBazaarUpdate(MarketSnapshot snapshot) {
        var cfg = ConfigManager.get().alert;
        if (!cfg.enabled) {
            return;
        }

        boolean changed = false;
        var it = cfg.alerts.iterator();

        while (it.hasNext()) {
            var curr = it.next();
            var priceResult = curr.getAssociatedPrice(snapshot);
            if (priceResult.isFailure()) {
                it.remove();
                changed = true;
                Notifier.notifyInvalidProduct(curr, this.bazaarData);
                continue;
            }

            var price = priceResult.get();
            var reached = price.map(marketPrice -> switch (curr.type) {
                case SellOffer, InstaSell -> marketPrice >= curr.price;
                case BuyOrder, InstaBuy -> marketPrice <= curr.price;
            }).orElse(false);

            if (reached) {
                it.remove();
                changed = true;
                Notifier.notifyPriceReached(curr, price, this.bazaarData);
                continue;
            }

            var now = System.currentTimeMillis();
            var duration = now - curr.createdAt;

            if (duration > Utils.MONTH_DURATION_MS && curr.remindedAfter < Utils.MONTH_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, "over a month", this.bazaarData);
                curr.remindedAfter = duration;
                changed = true;
            }

            if (duration > Utils.WEEK_DURATION_MS && curr.remindedAfter < Utils.WEEK_DURATION_MS) {
                Notifier.notifyOutdatedAlert(curr, "over a week", this.bazaarData);
                curr.remindedAfter = duration;
                changed = true;
                continue;
            }
        }

        if (changed) {
            ConfigManager.save();
        }
    }

    public boolean addAlert(ResolvedAlertArgs args) {
        var alerts = ConfigManager.get().alert.alerts;
        var exist = alerts.stream().anyMatch(alert -> alert.matches(args));

        if (!exist) {
            ConfigManager.withConfig(cfg -> cfg.alert.alerts.add(new Alert(args)));
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
            Notifier.notifyPlayer(Notifier
                .prefix()
                .append(Component
                    .literal("Wait, what? Multiple alerts with the same ID? ")
                    .withStyle(ChatFormatting.GRAY))
                .append(Component
                    .literal("You're either 1 in 5.3 undecillion (that's a 1 with 36 zeros) lucky, or you've been messin' with the config. ")
                    .withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.ITALIC))
                .append(Component
                    .literal("Either way, they're all history now!")
                    .withStyle(ChatFormatting.GRAY)));
            log.warn("Multiple alerts found with identical UUID: {}", id);
        }

        Notifier.notifyPlayer(Notifier
            .prefix()
            .append(Component.literal("Alert removed successfully!").withStyle(ChatFormatting.GRAY)));

    }

    public static class Alert {

        public final UUID id;
        public final long createdAt;
        public final IndexedProduct product;
        public final AlertType type;
        public final double price;

        long remindedAfter = -1;

        private Alert(ResolvedAlertArgs args) {
            this.id = UUID.randomUUID();
            this.createdAt = args.timestamp();
            this.product = args.product();
            this.type = args.type();
            this.price = args.price();
        }

        private Alert(
            UUID id,
            long createdAt,
            IndexedProduct product,
            AlertType type,
            double price,
            long remindedAfter
        ) {
            this.id = id;
            this.createdAt = createdAt;
            this.product = product;
            this.type = type;
            this.price = price;
            this.remindedAfter = remindedAfter;
        }

        public String productName() {
            return this.product.strippedName();
        }

        public String productId() {
            return this.product.productId();
        }

        public Try<Optional<Double>> getAssociatedPrice(MarketSnapshot snapshot) {
            var identity = ProductIdentity.fromIndex(this.product);
            if (!snapshot.contains(identity)) {
                return Try.failure(new Exception("The product \"" + this.productName() + "\" could not be found in the bazaar data"));
            }

            var prices = snapshot.getMarketPrices(identity);
            var price = switch (this.type) {
                case BuyOrder, InstaSell -> prices.highestBuyOrderPrice();
                case SellOffer, InstaBuy -> prices.lowestSellOfferPrice();
            };
            return Try.success(price);
        }

        public MutableComponent format(BazaarData bazaarData) {
            var refreshedProduct = bazaarData.refreshIndexedProduct(this.product);
            var productName = Component.literal(refreshedProduct.formattedName());
            return Component
                .empty()
                .append(productName)
                .append(Component.literal(" @ ").withStyle(ChatFormatting.GRAY))
                .append(Component
                    .literal(Utils.formatDecimal(this.price, 1, true) + "coins")
                    .withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" (" + this.type.format() + ")").withStyle(ChatFormatting.DARK_GRAY));
        }

        public boolean matches(ResolvedAlertArgs args) {
            // @formatter:off
            return this.productId().equals(args.productId())
                && this.type == args.type()
                && Double.compare(this.price, args.price()) == 0;
            // @formatter:on
        }

        public static final class GsonAdapter implements JsonSerializer<Alert>, JsonDeserializer<Alert> {

            @Override
            public JsonElement serialize(
                Alert src,
                Type typeOfSrc,
                JsonSerializationContext ctx
            ) {
                var obj = new JsonObject();
                obj.addProperty("id", src.id.toString());
                obj.addProperty("createdAt", src.createdAt);
                obj.add("product", ctx.serialize(src.product, IndexedProduct.class));
                obj.add("type", ctx.serialize(src.type));
                obj.addProperty("price", src.price);
                obj.addProperty("remindedAfter", src.remindedAfter);
                return obj;
            }

            @Override
            public Alert deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext ctx
            ) throws JsonParseException {
                if (json == null || !json.isJsonObject()) {
                    log.warn("Skipping malformed alert entry");
                    return null;
                }
                var obj = json.getAsJsonObject();
                var product = product(obj, ctx).orElse(null);
                if (product == null) {
                    return null;
                }

                return new Alert(
                    UUID.fromString(GsonUtils.required(obj, "id", "Alert").getAsString()),
                    GsonUtils.required(obj, "createdAt", "Alert").getAsLong(),
                    product,
                    ctx.deserialize(GsonUtils.required(obj, "type", "Alert"), AlertType.class),
                    GsonUtils.required(obj, "price", "Alert").getAsDouble(),
                    GsonUtils.optionalLong(obj, "remindedAfter").orElse(-1L)
                );
            }

            private static Optional<IndexedProduct> product(JsonObject obj, JsonDeserializationContext ctx) {
                try {
                    return Optional.of(ctx.deserialize(
                        GsonUtils.required(obj, "product", "Alert"),
                        IndexedProduct.class
                    ));
                } catch (RuntimeException err) {
                    log.warn("Skipping alert with invalid product", err);
                    return Optional.empty();
                }
            }
        }
    }

    public static class AlertConfig {

        public boolean enabled = true;
        public boolean soundOnAlert = true;
        public List<Alert> alerts = new ArrayList<>();

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Price Alerts"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Check configured price targets and notify you when a target is reached."),
                    ConfigScreen.note(
                        "Alerts that become valid while this is off may fire immediately when it is enabled again.")
                )))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createSoundOnAlertOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Play Alert Sound"))
                .description(OptionDescription.of(Component.literal(
                    "Play a sound together with the chat notification when a price target is reached.")))
                .binding(true, () -> this.soundOnAlert, val -> this.soundOnAlert = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption()).addOptions(this.createSoundOnAlertOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Price Alerts"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text("Notify you when a Bazaar price reaches a configured target."),
                    ConfigScreen.example(Component
                        .empty()
                        .append(ConfigScreen.command(
                            "/btrbz alert add ENCHANTMENT_ULTIMATE_FLASH_1 buy-order 4m"))
                        .append(Component
                            .literal(" notifies when the best buy-order price for ")
                            .withStyle(ChatFormatting.GRAY))
                        .append(Component
                            .literal("Flash I")
                            .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                        .append(Component
                            .literal(" reaches 4M coins or less.")
                            .withStyle(ChatFormatting.GRAY)))
                ),
                    ConfigScreen.ConfigImage.PRICE_ALERT
                ))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }
}
