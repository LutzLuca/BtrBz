package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule.PriceDiffConfig;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Util;
import com.github.lutzluca.btrbz.widgets.TextDisplayWidget;
import java.util.List;
import java.util.Optional;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Slf4j
public class PriceDiffModule extends Module<PriceDiffConfig> {

    private static final int PRODUCT_SLOT = 13;
    private static final int SELL_INSTANTLY_SLOT = 11;

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return configState.enabled && info.inMenu(BazaarMenuType.Item);
    }

    @Override
    public List<ClickableWidget> createWidgets(ScreenInfo info) {
        var screenOpt = info.getGenericContainerScreen();
        if (screenOpt.isEmpty()) {
            return List.of();
        }

        var screen = screenOpt.get();
        var handler = screen.getScreenHandler();
        var inv = handler.getInventory();

        String productName = inv.getStack(PRODUCT_SLOT).getName().getString();

        int listedCount = this.parseListedCount(inv.getStack(SELL_INSTANTLY_SLOT)).orElse(0);
        if (listedCount <= 0) {
            return List.of();
        }

        var priceDiffOpt = this.computePriceDiff(productName);
        if (priceDiffOpt.isEmpty()) {
            return List.of();
        }

        double perItemDiff = priceDiffOpt.get();
        double totalDiff = perItemDiff * listedCount;

        List<Text> lines = List.of(Text.literal(productName).formatted(Formatting.AQUA),
                Text.literal("Per-item diff: " + Util.formatCompact(perItemDiff, 1) + " coins")
                        .formatted(Formatting.GOLD),
                Text.literal("Total diff: " + Util.formatCompact(totalDiff, 1) + " coins")
                        .formatted(Formatting.YELLOW));

        var position = this.getWidgetPosition(info, lines);
        if (position.isEmpty()) {
            return List.of();
        }

        var widget = new TextDisplayWidget(position.get().x(), position.get().y(), lines,
                info.getScreen()).onDragEnd((self, pos) -> this.savePosition(pos));

        return List.of(widget);
    }

    private Optional<Integer> parseListedCount(ItemStack sellStack) {
        return OrderInfoParser.getLore(sellStack).stream()
                .filter(line -> line.startsWith("Inventory")).findFirst()
                .flatMap(line -> Util
                        .parseUsFormattedNumber(
                                line.replace("Inventory:", "").replace("items", "").trim())
                        .toJavaOptional())
                .map(Number::intValue);
    }

    private Optional<Double> computePriceDiff(String productName) {
        return BtrBz.bazaarData().nameToId(productName)
                .flatMap(id -> Util.zipOptionals(BtrBz.bazaarData().lowestSellPrice(id),
                        BtrBz.bazaarData().highestBuyPrice(id)))
                .map(pair -> pair.getLeft() - pair.getRight());
    }

    private Optional<Position> getWidgetPosition(ScreenInfo info, List<Text> lines) {
        return this.getConfigPosition().or(() -> info.getHandledScreenBounds().map(bounds -> {
            var textRenderer = MinecraftClient.getInstance().textRenderer;
            int maxWidth = lines.stream().mapToInt(textRenderer::getWidth).max().orElse(0);
            int textHeight = lines.size() * textRenderer.fontHeight
                    + (lines.size() - 1) * TextDisplayWidget.LINE_SPACING;

            int widgetWidth = maxWidth + 2 * TextDisplayWidget.PADDING_X;
            int widgetHeight = textHeight + 2 * TextDisplayWidget.PADDING_Y;

            int x = bounds.x() + (bounds.width() - widgetWidth) / 2;
            int y = bounds.y() - widgetHeight - 15;
            return new Position(x, y);
        }));
    }

    private Optional<Position> getConfigPosition() {
        return Util.zipNullables(this.configState.x, this.configState.y)
                .map(pair -> new Position(pair.getLeft(), pair.getRight()));
    }

    private void savePosition(Position pos) {
        this.updateConfig(cfg -> {
            cfg.x = pos.x();
            cfg.y = pos.y();
        });
    }

    public static class PriceDiffConfig {

        public boolean enabled = true;
        public Integer x, y;

        public Option<Boolean> createEnabledOption() {
            return Option.<Boolean>createBuilder().name(Text.literal("Price Diff Module"))
                    .description(OptionDescription.of(Text.literal(
                            "Show per-item and total price difference for the currently selected bazaar item")))
                    .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                    .controller(ConfigScreen::createBooleanController).build();
        }
    }
}
