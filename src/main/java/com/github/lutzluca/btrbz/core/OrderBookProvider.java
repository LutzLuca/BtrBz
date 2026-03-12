package com.github.lutzluca.btrbz.core;

import java.util.Optional;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.ProductInfoProvider.ProductNameInfo;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.Utils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Slf4j
public class OrderBookProvider {

    public OrderBookProvider() {
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            var cfg = ConfigManager.get().orderBookProvider;
            if (!cfg.enabled) {
                return;
            }

            var title = stack.getHoverName().getString();
            var orderType = switch (title) {
                case "Create Buy Order" -> OrderType.Buy;
                case "Create Sell Offer" -> OrderType.Sell;
                default -> null;
            };
            if (orderType == null) {
                return;
            }

            var productName = Optional.ofNullable(ProductInfoProvider.get().getOpenedProductNameInfo())
                    .map(ProductNameInfo::productName);
            if (productName.isEmpty()) {
                return;
            }

            var trackedOrders = BtrBz.orderManager().getTrackedOrders(order -> order.productName.equals(productName.get()) && order.type == orderType);
            if (trackedOrders.isEmpty()) {
                return;
            }

            for (int idx = 0; idx < lines.size(); idx++) {
                var lineComponent = lines.get(idx);
                var line = lineComponent.getString();
                // - {price per unit} coins each | {volume}x in {order count} order
                if (!line.contains(" coins each |")) {
                    continue;
                }

                var priceStr = line.substring(0, line.indexOf(" coins")).trim();
                if (priceStr.startsWith("- ")) {
                    priceStr = priceStr.substring(2).trim();
                }

                var parsed = Utils.parseUsFormattedNumber(priceStr);
                if (parsed.isFailure()) {
                    log.error("Failed to parse price from '{}': {}", priceStr, parsed.getCause());
                    continue;
                }

                double price = parsed.get().doubleValue();
                var matches = trackedOrders.stream()
                        .filter(order -> Double.compare(order.pricePerUnit, price) == 0)
                        .toList();

                if (matches.isEmpty()) {
                    continue;
                }

                int orderCount = matches.size();
                var indicator = Component.literal(" (")
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(String.valueOf(orderCount)).withStyle(ChatFormatting.DARK_AQUA))
                        .append(Component.literal(")").withStyle(ChatFormatting.AQUA));

                lines.set(idx, lineComponent.copy().append(indicator));
            }
        });
    }

    public static class Config {
        public boolean enabled = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option.<Boolean>createBuilder()
                    .name(Component.literal("Order Book Provider"))
                    .binding(true, () -> this.enabled, val -> this.enabled = val)
                    .description(OptionDescription.of(Component.literal("Add a summary of your active orders to the 'Create Order' item tooltips.")))
                    .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var root = new OptionGrouping(this.createEnabledOption());

            return OptionGroup.createBuilder()
                    .name(Component.literal("Order Book Provider"))
                    .description(OptionDescription.of(Component.literal("Settings for the Order Book Provider feature.")))
                    .options(root.build())
                    .collapsed(false)
                    .build();
        }
    }
}
