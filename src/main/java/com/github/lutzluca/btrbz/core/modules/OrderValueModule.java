package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.data.OrderModels.OrderInfo;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
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
    private List<OrderInfo> orders = null;

    @Override
    public void onLoad() {
        ScreenInfoHelper.registerOnSwitch(info -> this.orders = null);
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(BazaarMenuType.Orders);
    }

    // Note: when the inv did not load properly and new ordes where
    // placed the current interface would fallback to the previously known
    // orders. Could you enhanced by listening on places / filled orders
    // but partially filled orders would not be disregarded in that case, so
    // it seems unnecessary to do so, as there is no way around the desync.
    public void update(List<OrderInfo> orders) {
        this.orders = orders;

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
        double pending = 0.0;
        double filled = 0.0;
        double invested = 0.0;

        if (this.orders != null) {
            for (var order : this.orders) {
                pending += (order.volume() - order.filledAmount()) * order.pricePerUnit();
                switch (order.type()) {
                    case Sell -> filled += order.unclaimed();
                    case Buy -> invested += order.unclaimed() * order.pricePerUnit();
                }
            }
        }
        var total = pending + filled + invested;

        return List.of(
            Text.literal("Order Value Overview").formatted(Formatting.GOLD, Formatting.BOLD),
            Text
                .literal("Pending: " + Util.formatCompact(pending, 1) + " coins")
                .formatted(Formatting.YELLOW),
            Text
                .literal("Filled: " + Util.formatCompact(filled, 1) + " coins")
                .formatted(Formatting.GREEN),
            Text
                .literal("Invested: " + Util.formatCompact(invested, 1) + " coins")
                .formatted(Formatting.GREEN),
            Text
                .literal("Total: " + Util.formatCompact(total, 1) + " coins")
                .formatted(Formatting.AQUA)
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
        return Util
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
