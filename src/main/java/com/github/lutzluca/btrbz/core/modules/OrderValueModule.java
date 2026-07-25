package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.FilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.UnfilledOrderInfo;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.LabelWidget;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

@Slf4j
public class OrderValueModule extends Module<OrderValueModule.OrderValueOverlayConfig> {

    private LabelWidget widget;
    private List<UnfilledOrderInfo> unfilledOrders = List.of();
    private List<FilledOrderInfo> filledOrders = List.of();

    @Override
    public void onLoad() {
        ScreenInfoHelper.registerOnSwitch(info -> {
            this.unfilledOrders = List.of();
            this.filledOrders = List.of();
        });
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(BazaarMenuType.Orders);
    }

    public void sync(
        List<UnfilledOrderInfo> unfilledOrders,
        List<FilledOrderInfo> filledOrders
    ) {
        log.debug("Syncing values with updated order information");
        this.unfilledOrders = List.copyOf(unfilledOrders);
        this.filledOrders = List.copyOf(filledOrders);

        if (this.widget == null) {
            return;
        }

        var lines = this.getLines(this.currentBreakdown());
        this.widget.setLines(lines);
    }

    @Override
    public Optional<DraggableWidget> createWidget(ScreenInfo info) {
        var lines = this.getLines(this.currentBreakdown());

        this.widget = new LabelWidget(0, 0, lines);
        this.widget.setAutoSize(true)
            .setAlignment(LabelWidget.Alignment.CENTER)
            .onDragEnd((self, pos) -> this.updateConfig(cfg -> cfg.position = pos));

        var position = this.getWidgetPosition(info, this.widget);
        if (position.isEmpty()) {
            return Optional.empty();
        }

        this.widget.setPosition(position.get().x(), position.get().y());

        return Optional.of(this.widget);
    }

    public OrderValueBreakdown currentBreakdown() {
        return calculateBreakdown(this.unfilledOrders, this.filledOrders);
    }

    public static OrderValueBreakdown calculateBreakdown(
        List<UnfilledOrderInfo> unfilledOrders,
        List<FilledOrderInfo> filledOrders
    ) {
        log.debug(
            "Calculating breakdown with unfilled orders: {} - filled orders: {}",
            unfilledOrders,
            filledOrders
        );
        double lockedInBuyOrders = 0.0;
        double itemsFromBuyOrders = 0.0;
        double coinsFromSellOffers = 0.0;
        double pendingSellOffers = 0.0;

        for (var order : unfilledOrders) {
            int unfilledVolume = order.volume() - order.filledAmountSnapshot();

            switch (order.type()) {
                case Buy -> {
                    lockedInBuyOrders += unfilledVolume * order.pricePerUnit();
                    itemsFromBuyOrders += order.unclaimed() * order.pricePerUnit();
                }
                case Sell -> {
                    pendingSellOffers += unfilledVolume * order.pricePerUnit();
                    coinsFromSellOffers += order.unclaimed();
                }
            }
        }

        for (var order : filledOrders) {
            switch (order.type()) {
                case Buy -> itemsFromBuyOrders += order.unclaimed() * order.pricePerUnit();
                case Sell -> coinsFromSellOffers += order.unclaimed();
            }
        }

        return new OrderValueBreakdown(
            lockedInBuyOrders,
            itemsFromBuyOrders,
            coinsFromSellOffers,
            pendingSellOffers
        );
    }

    private List<Component> getLines(OrderValueBreakdown breakdown) {

        return List.of(
            Component.literal("Bazaar Overview").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
            Component
                .literal("Buy Orders (Locked): " + Utils.formatCompact(
                    breakdown.lockedInBuyOrders(),
                    1
                ) + " coins")
                .withStyle(ChatFormatting.YELLOW),
            Component
                .literal("Buy Orders (Items): " + Utils.formatCompact(
                    breakdown.itemsFromBuyOrders(),
                    1
                ) + " coins")
                .withStyle(ChatFormatting.AQUA),
            Component
                .literal("Sell Offers (Claimable): " + Utils.formatCompact(
                    breakdown.coinsFromSellOffers(),
                    1
                ) + " coins")
                .withStyle(ChatFormatting.GREEN),
            Component
                .literal("Sell Offers (Pending): " + Utils.formatCompact(
                    breakdown.pendingSellOffers(),
                    1
                ) + " coins")
                .withStyle(ChatFormatting.YELLOW),
            Component
                .literal("Total Worth: " + Utils.formatCompact(breakdown.total(), 1) + " coins")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        );
    }

    public record OrderValueBreakdown(
        double lockedInBuyOrders,
        double itemsFromBuyOrders,
        double coinsFromSellOffers,
        double pendingSellOffers
    ) {
        public double total() {
            return this.lockedInBuyOrders
                + this.itemsFromBuyOrders
                + this.coinsFromSellOffers
                + this.pendingSellOffers;
        }
    }

    private Optional<Position> getWidgetPosition(ScreenInfo info, LabelWidget widget) {
        return Optional.ofNullable(this.configState.position).or(() -> info.getHandledScreenBounds().map(bounds -> {
            int x = bounds.x() + (bounds.width() - widget.getWidth()) / 2;
            int y = bounds.y() - widget.getHeight() - 15;
            return new Position(x, y);
        }));
    }

    public static class OrderValueOverlayConfig {

        public Position position;

        public boolean enabled = false;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Order Value Overlay"))
                .binding(false, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(ConfigScreen.createDescription(
                    "Show the combined coin value of your active and filled orders on the Bazaar Orders page."))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Order Value Overlay"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Summarize coins tied up in active orders and available from filled orders."),
                    ConfigScreen.note(
                        "Pending and claimable items are valued at their own order price, not the current market price.")
                ),
                    ConfigScreen.ConfigImage.ORDER_VALUE
                ))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }
}
