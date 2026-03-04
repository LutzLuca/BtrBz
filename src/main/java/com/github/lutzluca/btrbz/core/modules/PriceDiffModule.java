package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule.PriceDiffConfig;
import com.github.lutzluca.btrbz.data.OrderInfoParser;
import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.btrbz.widgets.base.DraggableWidget;
import com.github.lutzluca.btrbz.widgets.LabelWidget;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

@Slf4j
public class PriceDiffModule extends Module<PriceDiffConfig> {

    private static final int PRODUCT_SLOT = 13;
    private static final int SELL_INSTANTLY_SLOT = 11;

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(BazaarMenuType.Item);
    }

    @Override
    public List<DraggableWidget> createWidgets(ScreenInfo info) {
        var screenOpt = info.getGenericContainerScreen();
        if (screenOpt.isEmpty()) {
            return List.of();
        }

        var screen = screenOpt.get();
        var handler = screen.getMenu();
        var inv = handler.getContainer();

        String productName = inv.getItem(PRODUCT_SLOT).getHoverName().getString();

        int listedCount = this.parseListedCount(inv.getItem(SELL_INSTANTLY_SLOT)).orElse(0);
        if (listedCount <= 0) {
            return List.of();
        }

        var priceDiffOpt = this.computePriceDiff(productName);
        if (priceDiffOpt.isEmpty()) {
            return List.of();
        }

        double perItemDiff = priceDiffOpt.get();
        double totalDiff = perItemDiff * listedCount;

        List<Component> lines = List.of(
            Component.literal(productName).withStyle(ChatFormatting.AQUA),
            Component
                .literal("Per-item diff: " + Utils.formatCompact(perItemDiff, 1) + " coins")
                .withStyle(ChatFormatting.GOLD),
            Component
                .literal("Total diff: " + Utils.formatCompact(totalDiff, 1) + " coins")
                .withStyle(ChatFormatting.YELLOW)
        );

        var widget = new LabelWidget(0, 0, lines);
        widget.setAutoSize(true);
        widget.setAlignment(LabelWidget.Alignment.CENTER);

        var position = this.getWidgetPosition(info, widget);
        if (position.isEmpty()) {
            return List.of();
        }

        widget.setPosition(position.get().x(), position.get().y());
        widget.onDragEnd((self, pos) -> this.savePosition(pos));

        return List.of(widget);
    }

    private Optional<Integer> parseListedCount(ItemStack sellStack) {
        return OrderInfoParser
            .getLore(sellStack)
            .stream()
            .filter(line -> line.startsWith("Inventory"))
            .findFirst()
            .flatMap(line -> Utils
                .parseUsFormattedNumber(line.replace("Inventory:", "").replace("items", "").trim())
                .toJavaOptional())
            .map(Number::intValue);
    }

    private Optional<Double> computePriceDiff(String productName) {
        // TODO maybe respect "filling orders" when one would sell it instantly
        return BtrBz
            .bazaarData()
            .nameToId(productName)
            .flatMap(id -> Utils.zipOptionals(
                BtrBz.bazaarData().lowestSellPrice(id),
                BtrBz.bazaarData().highestBuyPrice(id)
            ))
            .map(pair -> pair.getLeft() - pair.getRight());
    }

    private Optional<Position> getWidgetPosition(ScreenInfo info, LabelWidget widget) {
        return this.getConfigPosition().or(() -> info.getHandledScreenBounds().map(bounds -> {
            int x = bounds.x() + (bounds.width() - widget.getWidth()) / 2;
            int y = bounds.y() - widget.getHeight() - 15;
            return new Position(x, y);
        }));
    }

    private Optional<Position> getConfigPosition() {
        return Utils
            .zipNullables(this.configState.x, this.configState.y)
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

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Price Diff Module"))
                .description(OptionDescription.of(Component.literal(
                    "Show per-item and total price difference for the currently selected bazaar item")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Price Diff"))
                .description(OptionDescription.of(Component.literal(
                    "Show per-item and total price difference for selected item")))
                .options(rootGroup.build())
                .collapsed(false)
                .build();
        }
    }
}
