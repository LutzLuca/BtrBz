package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.TextDisplayWidget;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Slf4j
public class OrderLimitModule extends Module<OrderLimitModule.OrderLimitConfig> {

    @Override
    public void onLoad() {
        this.resetOrderLimitOnNewDay();
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(BazaarMenuType.Main);
    }

    @Override
    public List<ClickableWidget> createWidgets(ScreenInfo info) {
        List<Text> lines = List.of(
            Text.literal("Daily Limit:").formatted(Formatting.GOLD),
            Text
                .literal(this.formatAmount(this.configState.usedToday) + " / " + Utils.formatCompact(
                    this.configState.dailyLimit,
                    0
                ))
                .formatted(Formatting.GREEN)
        );

        var position = this
            .getConfigPosition()
            .or(() -> info.getHandledScreenBounds().map(bounds -> {
                var textRenderer = MinecraftClient.getInstance().textRenderer;

                int lineWidth = lines.stream().mapToInt(textRenderer::getWidth).max().getAsInt();
                int textHeight = lines.size() * textRenderer.fontHeight + (lines.size() - 1) * TextDisplayWidget.LINE_SPACING;

                int widgetWidth = lineWidth + 2 * TextDisplayWidget.PADDING_X;
                int widgetHeight = textHeight + 2 * TextDisplayWidget.PADDING_Y;

                int x = bounds.x() + (bounds.width() - widgetWidth) / 2;
                int y = bounds.y() - widgetHeight - 25;
                return new Position(x, y);
            }));

        if (position.isEmpty()) {
            log.warn("Could not determine position for OrderLimitModule widget");
            return List.of();
        }

        var widget = new TextDisplayWidget(
            position.get().x(),
            position.get().y(),
            lines,
            info.getScreen()
        ).onDragEnd((self, pos) -> this.savePosition(pos));

        return List.of(widget);
    }

    public void onTransaction(double transactionAmount) {
        this.resetOrderLimitOnNewDay();

        // TODO have a option to cap at limit
        this.updateConfig(cfg -> cfg.usedToday += transactionAmount);
        log.debug(
            "Added {} coins to daily limit usage (now {})",
            transactionAmount,
            this.configState.usedToday
        );
    }

    private Optional<Position> getConfigPosition() {
        return Utils
            .zipNullables(this.configState.x, this.configState.y)
            .map(pair -> new Position(pair.getLeft(), pair.getRight()));
    }

    private void savePosition(int newX, int newY) {
        log.debug("Saving new position for OrderLimitModule: {}", new Position(newX, newY));

        this.updateConfig((config) -> {
            config.x = newX;
            config.y = newY;
        });
    }

    private void resetOrderLimitOnNewDay() {
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();

        if (this.configState.lastResetEpochDay != today) {
            log.info("Resetting daily order limit usage");

            this.updateConfig(cfg -> {
                cfg.usedToday = 0.0;
                cfg.lastResetEpochDay = today;
            });
        }
    }

    private void savePosition(Position pos) {
        this.savePosition(pos.x(), pos.y());
    }

    public String formatAmount(double amount) {
        if (!configState.useCompact) {
            return String.format("%.0f", amount);
        }

        int places;
        double abs = Math.abs(amount);
        if (abs >= 1_000_000_000) {
            places = 2;
        } else if (abs >= 1_000_000) {
            places = 1;
        } else if (abs >= 1_000) {
            places = 0;
        } else {
            places = 0;
        }

        return Utils.formatCompact(amount, places);
    }

    public static class OrderLimitConfig {

        public Integer x, y;

        public boolean enabled = true;

        public double usedToday = 0.0;
        public long lastResetEpochDay = -1;
        public double dailyLimit = 15E9;

        public boolean useCompact = true;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Order Limit Module"))
                .description(OptionDescription.of(Text.literal(
                    "Enable or disable the Order Limit module that tracks your daily coin spending limit")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public Option.Builder<Boolean> createCompactOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Use Compact Display"))
                .description(OptionDescription.of(Text.literal(
                    "Display the order limit in a compact format that takes up less screen space")))
                .binding(true, () -> this.useCompact, val -> this.useCompact = val)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption()).addOptions(this.createCompactOption());

            return OptionGroup
                .createBuilder()
                .name(Text.literal("Order Limit"))
                .description(OptionDescription.of(Text.literal(
                    "Display and behaviour settings for the Order Limit module")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}
