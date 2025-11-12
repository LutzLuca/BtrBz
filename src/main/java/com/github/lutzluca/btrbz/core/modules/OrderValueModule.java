package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.FilledOrderInfo;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo.UnfilledOrderInfo;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.TextDisplayWidget;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Slf4j
public class OrderValueModule extends Module<OrderValueModule.OrderValueOverlayConfig> {

    private TextDisplayWidget widget;
    private List<UnfilledOrderInfo> unfilledOrders;
    private List<FilledOrderInfo> filledOrders;

    @Override
    public void onLoad() {
        ScreenInfoHelper.registerOnSwitch(info -> {
            this.unfilledOrders = null;
            this.filledOrders = null;
        });
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(BazaarMenuType.Orders);
    }

    public void sync(
        List<OrderInfo.UnfilledOrderInfo> unfilledOrders,
        List<OrderInfo.FilledOrderInfo> filledOrders
    ) {
        log.debug("Syncing values with updated order information");
        this.unfilledOrders = unfilledOrders;
        this.filledOrders = filledOrders;

        if (this.widget == null) {
            return;
        }

        var lines = this.getLines();
        this.widget.setLines(lines);
    }

    @Override
    public List<ClickableWidget> createWidgets(ScreenInfo info) {
        var lines = this.getLines();

        var position = this.getWidgetPosition(info, lines);
        if (position.isEmpty()) {
            return List.of();
        }

        this.widget = (TextDisplayWidget) new TextDisplayWidget(
            position.get().x(),
            position.get().y(),
            lines,
            info.getScreen()
        ).onDragEnd((self, pos) -> this.savePosition(pos));

        return List.of(this.widget);
    }

    private List<Text> getLines() {
        log.debug(
            "Getting lines with unfilled lines: {} - filled lines: {}",
            this.unfilledOrders,
            this.filledOrders
        );
        double lockedInBuyOrders = 0.0;
        double itemsFromBuyOrders = 0.0;
        double coinsFromSellOffers = 0.0;
        double pendingSellOffers = 0.0;

        if (this.unfilledOrders != null) {
            for (var order : this.unfilledOrders) {
                int unfilledVolume = order.volume() - order.filledAmount();

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
        }

        if (this.filledOrders != null) {
            for (var order : this.filledOrders) {
                switch (order.type()) {
                    case Buy -> itemsFromBuyOrders += order.unclaimed() * order.pricePerUnit();
                    case Sell -> coinsFromSellOffers += order.unclaimed();
                }
            }
        }
        var total = lockedInBuyOrders + itemsFromBuyOrders + coinsFromSellOffers + pendingSellOffers;

        return List.of(
            Text.literal("Bazaar Overview").formatted(Formatting.GOLD, Formatting.BOLD),
            Text
                .literal("Buy Orders (Locked): " + Utils.formatCompact(
                    lockedInBuyOrders,
                    1
                ) + " coins")
                .formatted(Formatting.YELLOW),
            Text
                .literal("Buy Orders (Items): " + Utils.formatCompact(
                    itemsFromBuyOrders,
                    1
                ) + " coins")
                .formatted(Formatting.AQUA),
            Text
                .literal("Sell Offers (Claimable): " + Utils.formatCompact(
                    coinsFromSellOffers,
                    1
                ) + " coins")
                .formatted(Formatting.GREEN),
            Text
                .literal("Sell Offers (Pending): " + Utils.formatCompact(
                    pendingSellOffers,
                    1
                ) + " coins")
                .formatted(Formatting.YELLOW),
            Text
                .literal("Total Worth: " + Utils.formatCompact(total, 1) + " coins")
                .formatted(Formatting.GOLD, Formatting.BOLD)
        );
    }

    private void savePosition(Position pos) {
        this.updateConfig(cfg -> {
            cfg.x = pos.x();
            cfg.y = pos.y();
        });
    }

    private Optional<Position> getWidgetPosition(ScreenInfo info, List<Text> lines) {
        return this.getConfigPosition().or(() -> info.getHandledScreenBounds().map(bounds -> {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int maxWidth = lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
            int textHeight = lines.size() * textRenderer.fontHeight + (lines.size() - 1) * TextDisplayWidget.LINE_SPACING;

            int widgetWidth = maxWidth + 2 * TextDisplayWidget.PADDING_X;
            int widgetHeight = textHeight + 2 * TextDisplayWidget.PADDING_Y;

            int x = bounds.x() + (bounds.width() - widgetWidth) / 2;
            int y = bounds.y() - widgetHeight - 15;
            return new Position(x, y);
        }));
    }

    private Optional<Position> getConfigPosition() {
        return Utils
            .zipNullables(this.configState.x, this.configState.y)
            .map(pair -> new Position(pair.getLeft(), pair.getRight()));
    }

    public static class OrderValueOverlayConfig {

        Integer x, y;

        boolean enabled = false;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Order Value Overlay"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .description(OptionDescription.of(Text.literal(
                    "Enable or disable the overlay that displays how much money your orders in the bazaar are worth")))
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var enabled = this.createEnabledOption();

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order Value Overlay"))
                .option(enabled.build())
                .collapsed(false)
                .build();
        }
    }
}
