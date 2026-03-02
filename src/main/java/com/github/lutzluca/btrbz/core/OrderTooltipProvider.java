package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.mixin.AbstractContainerScreenAccessor;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.Utils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class OrderTooltipProvider {

    private final OrderTooltipCache cache;

    private static class OrderTooltipCache {
        private final Map<@NotNull TrackedOrder, @Nullable List<Component>> cache = new HashMap<>();

        public OrderTooltipCache() {
            log.info("Initializing OrderTooltipCache");
            BtrBz.bazaarData().addListener(products -> {
                log.trace("Bazaar data updated, clearing tooltip cache with {} entries", this.cache.size());
                this.cache.clear();
            });
        }

        public List<Component> getOrCompute(@NotNull TrackedOrder order, Supplier<List<Component>> supplier) {
            return this.cache.computeIfAbsent(order, key -> {
                log.trace("Computing tooltip cache for {}", key);
                return supplier.get();
            });
        }
    }

    public OrderTooltipProvider() {
        this.cache = new OrderTooltipCache();

        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            var cfg = ConfigManager.get().orderTooltip;
            if (!cfg.enabled) {
                return;
            }

            if (!ScreenInfoHelper.inMenu(BazaarMenuType.Orders) || !GameUtils.orderScreenNonOrderItemsFilter(stack)) {
                return;
            }

            var screen = ScreenInfoHelper.get().getCurrInfo().getGenericContainerScreen().orElse(null);
            if (screen == null) {
                return;
            }

            var slot = ((AbstractContainerScreenAccessor) screen).getHoveredSlot();
            if (slot == null || GameUtils.isPlayerInventorySlot(slot) || slot.getItem() != stack) {
                return;
            }

            int idx = slot.getContainerSlot();
            var order = BtrBz.highlightManager().getTrackedOrder(idx);
            if (order == null) {
                return;
            }

            var tooltipLines = getCachedTooltip(order, cfg, false);
            lines.addAll(1, tooltipLines);
        });
    }

    public List<Component> getCachedTooltip(TrackedOrder order, TooltipConfig cfg, boolean addCurrOrderLine) {
        return this.cache.getOrCompute(order, () -> buildTooltipLines(order, cfg, addCurrOrderLine));
    }

    public static List<Component> buildTooltipLines(TrackedOrder order, TooltipConfig cfg, boolean addCurrOrderLine) {
        var data = BtrBz.bazaarData();
        var productId = data.nameToId(order.productName);

        if (productId.isEmpty()) {
            return List.of(Component.literal("Unknown Product").withStyle(ChatFormatting.RED));
        }

        List<Component> lines = new ArrayList<>();

        if (cfg.showStatus) {
            lines.add(statusLine(order));
        }

        if (cfg.showQueue && order.status instanceof OrderStatus.Undercut) {
            var queueInfo = data.calculateQueuePosition(
                order.productName,
                order.type,
                order.pricePerUnit
            );

            queueInfo.ifPresent(orderQueueInfo -> lines.add(Component
                    .literal("Queue: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component
                            .literal(String.valueOf(orderQueueInfo.ordersAhead))
                            .withStyle(ChatFormatting.RED))
                    .append(Component.literal(" orders (").withStyle(ChatFormatting.GRAY))
                    .append(Component
                            .literal(Utils.formatDecimal(orderQueueInfo.itemsAhead, 0, true))
                            .withStyle(ChatFormatting.RED))
                    .append(Component.literal(" items)").withStyle(ChatFormatting.GRAY))));
        }

        if (addCurrOrderLine) {
            lines.add(Component.empty());
            lines.addAll(currOrderLines(order));
        }

        if (cfg.showPrices) {
            lines.add(Component.empty());
            lines.addAll(priceLines(data, productId.get()));
        }

        return lines;
    }

    private static List<Component> currOrderLines(TrackedOrder order) {
        var header = Component.literal("Your Order").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        var priceLine = Component
            .literal("Price: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component
                .literal(Utils.formatDecimal(order.pricePerUnit, 1, true))
                .withStyle(ChatFormatting.WHITE));

        var volumeLine = Component
            .literal("Volume: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.valueOf(order.volume)).withStyle(ChatFormatting.WHITE));

        return List.of(header, priceLine, volumeLine);
    }

    private static Component statusLine(TrackedOrder order) {
        return switch (order.status) {
            case OrderStatus.Top ignored -> Component.literal("Best Price!")
                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            case OrderStatus.Matched ignored -> Component.literal("Matched!")
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            case OrderStatus.Undercut ignored -> Component.literal("Undercut!")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
            case OrderStatus.Unknown ignored -> Component.literal("Status Unknown")
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    private static List<Component> priceLines(BazaarData data, String productId) {
        var priceInfo = data.getOrderPrices(productId);

        var header = Component.literal("Current Prices").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        var buyOrderLine = Component
            .literal("Buy Orders: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(priceInfo
                .buyOrderPrice()
                .map(price -> Component
                    .literal(Utils.formatDecimal(price, 1, true))
                    .withStyle(ChatFormatting.WHITE))
                .orElse(Component.literal("N/A").withStyle(ChatFormatting.DARK_GRAY)));

        var sellOfferLine = Component
            .literal("Sell Offers: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(priceInfo
                .sellOfferPrice()
                .map(price -> Component
                    .literal(Utils.formatDecimal(price, 1, true))
                    .withStyle(ChatFormatting.WHITE))
                .orElse(Component.literal("N/A").withStyle(ChatFormatting.DARK_GRAY)));

        return List.of(header, buyOrderLine, sellOfferLine);
    }

    public static class TooltipConfig {
        public boolean enabled = true;
        public boolean showStatus = true;
        public boolean showQueue = true;
        public boolean showPrices = false;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Order Tooltips"))
                .binding(true, () -> this.enabled, val -> this.enabled = val)
                .description(OptionDescription.of(Component.literal("Enable custom tooltips for Bazaar orders.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createStatusOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Status"))
                .binding(true, () -> this.showStatus, val -> this.showStatus = val)
                .description(OptionDescription.of(Component.literal("Show order status (Top, Undercut, etc.)")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createQueueOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Queue Position"))
                .binding(true, () -> this.showQueue, val -> this.showQueue = val)
                .description(OptionDescription.of(Component.literal("Show how many orders are ahead of yours.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createPricesOption() {
            return Option.<Boolean>createBuilder()
                .name(Component.literal("Show Current Prices"))
                .binding(true, () -> this.showPrices, val -> this.showPrices = val)
                .description(OptionDescription.of(Component.literal("Show top buy and sell prices for the item.")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var root = new OptionGrouping(this.createEnabledOption())
                .addOptions(
                    this.createStatusOption(),
                    this.createQueueOption(),
                    this.createPricesOption()
                );

            return OptionGroup.createBuilder()
                .name(Component.literal("Order Tooltips"))
                .description(OptionDescription.of(Component.literal("Settings for the custom order tooltips.")))
                .options(root.build())
                .collapsed(false)
                .build();
        }
    }
}
