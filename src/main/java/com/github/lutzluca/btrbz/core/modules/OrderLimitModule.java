package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
import com.github.lutzluca.btrbz.widgets.TextDisplayWidget;
import dev.isxander.yacl3.api.Option;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

// track last reset in config & amount used 
// Limit reset at 12:00am GMT
// on startup check the last resetted day; if mistmatch reset used to 0
// have a sheduled task to reset at 12:00am GMT (idk about that one tho)
// use total fron the bazaar transactions (Instal Sell/Buy, Buy/Sell Order, Flipped Order) (should be sufficent)
// when insta selling use `coins * (1 - tax / 100)` to calculate limit usage

// TODO make this nicer with multiple lines
// Daily Limit:
// {amount used} / {total limit}

@Slf4j
public class OrderLimitModule extends Module<OrderLimitModule.OrderLimitConfig> {

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(BazaarMenuType.Main);
    }

    @Override
    public List<ClickableWidget> createWidgets(ScreenInfo info) {
        var msg = Text.literal("Order Limit: .../...").formatted(Formatting.GOLD);

        var position = this
            .getConfigPosition()
            .or(() -> info.getHandledScreenBounds().map(bounds -> {
                var textRenderer = MinecraftClient.getInstance().textRenderer;
                var textWidth = textRenderer.getWidth(msg);
                var textHeight = textRenderer.fontHeight;

                var x = bounds.x() + (bounds.width() - textWidth - 2 * TextDisplayWidget.PADDING_X) / 2;
                var y = bounds.y() - textHeight - 4 - TextDisplayWidget.PADDING_Y;

                return new Position(x, y);
            }));

        if (position.isEmpty()) {
            log.warn("Could not determine position for OrderLimitModule widget");
            return List.of();
        }

        var widget = new TextDisplayWidget(
            position.get().x(),
            position.get().y(),
            msg,
            info.getScreen()
        ).onDragEnd((self, pos) -> this.savePosition(pos));

        return List.of(widget);
    }

    private Optional<Position> getConfigPosition() {
        return Util
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

    private void savePosition(Position pos) {
        this.savePosition(pos.x(), pos.y());
    }

    public static class OrderLimitConfig {

        public Integer x, y;

        public boolean enabled = true;

        public Option<Boolean> createOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Text.literal("Order Limit Module"))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController)
                .build();
        }
    }
}
