package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule.PriceDiffConfig;
import com.github.lutzluca.btrbz.utils.GameUtils;
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
    public Optional<DraggableWidget> createWidget(ScreenInfo info) {
        var screenOpt = info.getGenericContainerScreen();
        if (screenOpt.isEmpty()) {
            return Optional.empty();
        }

        var screen = screenOpt.get();
        var handler = screen.getMenu();
        var inv = handler.getContainer();

        String productName = inv.getItem(PRODUCT_SLOT).getHoverName().getString();

        int listedCount = this.parseListedCount(inv.getItem(SELL_INSTANTLY_SLOT)).orElse(0);
        if (listedCount <= 0) {
            return Optional.empty();
        }

        var priceDiffOpt = this.computePriceDiff(inv.getItem(PRODUCT_SLOT));
        if (priceDiffOpt.isEmpty()) {
            return Optional.empty();
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
            return Optional.empty();
        }

        widget.setPosition(position.get().x(), position.get().y());
        widget.onDragEnd((self, pos) -> this.updateConfig(cfg -> cfg.position = pos));

        return Optional.of(widget);
    }

    private Optional<Integer> parseListedCount(ItemStack sellStack) {
        return GameUtils
            .getLore(sellStack)
            .stream()
            .filter(line -> line.startsWith("Inventory"))
            .findFirst()
            .flatMap(line -> Utils
                .parseUsFormattedNumber(line.replace("Inventory:", "").replace("items", "").trim())
                .toJavaOptional())
            .map(Number::intValue);
    }

    private Optional<Double> computePriceDiff(ItemStack productStack) {
        // TODO maybe respect "filling orders" when one would sell it instantly
        var bazaarData = this.context().bazaarData();

        return bazaarData.productSpread(bazaarData.resolveProduct(productStack));
    }

    private Optional<Position> getWidgetPosition(ScreenInfo info, LabelWidget widget) {
        return Optional.ofNullable(this.configState.position).or(() -> info.getHandledScreenBounds().map(bounds -> {
            int x = bounds.x() + (bounds.width() - widget.getWidth()) / 2;
            int y = bounds.y() - widget.getHeight() - 15;
            return new Position(x, y);
        }));
    }

    public static class PriceDiffConfig {

        public boolean enabled = true;
        public Position position;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Price Difference"))
                .description(OptionDescription.of(Component.literal(
                    "Show the difference between the selected Bazaar item's reference price and the price you are entering, per item and in total.")))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Price Difference"))
                .description(ConfigScreen.createDescription(
                    "Display per-item and total price differences while setting up a Bazaar order.",
                    ConfigScreen.ConfigImage.PRICE_DIFFERENCE
                ))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }
}
