package com.github.lutzluca.btrbz.core.modules;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule.PriceDiffConfig;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Position;
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
import net.minecraft.world.item.ItemStack;

@Slf4j
public class PriceDiffModule extends Module<PriceDiffConfig> {

    private static final int PRODUCT_SLOT = 13;
    private static final int SELL_INSTANTLY_SLOT = 11;
    private final BazaarData bazaarData;

    public PriceDiffModule(BazaarData bazaarData) {
        this.bazaarData = bazaarData;
    }

    @Override
    public boolean shouldDisplay(ScreenInfo info) {
        return this.configState.enabled && info.inMenu(BazaarMenuType.Item);
    }

    @Override
    public Optional<DraggableWidget> createWidget(ScreenInfo info) {
        var difference = this.currentPriceDifference(info);
        if (difference.isEmpty()) {
            return Optional.empty();
        }

        var result = difference.get();
        double totalDiff = result.totalDifference();

        List<Component> lines = List.of(
            Component.literal(result.productName()).withStyle(ChatFormatting.AQUA),
            Component
                .literal("Per-item diff: " + Utils.formatCompact(result.perItemDifference(), 1) + " coins")
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

    public Optional<PriceDifference> currentPriceDifference(ScreenInfo info) {
        var screenOpt = info.getGenericContainerScreen();
        if (screenOpt.isEmpty()) {
            return Optional.empty();
        }

        var screen = screenOpt.get();
        var handler = screen.getMenu();
        var inv = handler.getContainer();

        var productStack = inv.getItem(PRODUCT_SLOT);
        String productName = productStack.getHoverName().getString();

        int listedCount = this.parseListedCount(inv.getItem(SELL_INSTANTLY_SLOT)).orElse(0);
        if (listedCount <= 0) {
            return Optional.empty();
        }

        var product = this.bazaarData.resolveProduct(productStack);
        var priceDiffOpt = this.bazaarData.productSpread(product);
        if (priceDiffOpt.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new PriceDifference(
            product,
            productName,
            productStack,
            priceDiffOpt.get(),
            listedCount
        ));
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

    private Optional<Position> getWidgetPosition(ScreenInfo info, LabelWidget widget) {
        return Optional.ofNullable(this.configState.position).or(() -> info.getHandledScreenBounds().map(bounds -> {
            int x = bounds.x() + (bounds.width() - widget.getWidth()) / 2;
            int y = bounds.y() - widget.getHeight() - 15;
            return new Position(x, y);
        }));
    }

    public record PriceDifference(
        ProductIdentity product,
        String productName,
        ItemStack item,
        double perItemDifference,
        int quantity
    ) {
        public PriceDifference {
            item = item.copy();
        }

        @Override
        public ItemStack item() {
            return this.item.copy();
        }

        public double totalDifference() {
            return this.perItemDifference * this.quantity;
        }
    }

    public static class PriceDiffConfig {

        public boolean enabled = true;
        public Position position;

        public Option.Builder<Boolean> createEnabledOption() {
            return Option
                .<Boolean>createBuilder()
                .name(Component.literal("Enable Price Difference Overlay"))
                .description(ConfigScreen.createDescription(
                    "Show the current market spread (best sell-offer price minus best buy-order price) per item and for the sellable amount in your inventory."))
                .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
                .controller(ConfigScreen::createBooleanController);
        }

        public OptionGroup createGroup() {
            var rootGroup = new OptionGrouping(this.createEnabledOption());

            return OptionGroup
                .createBuilder()
                .name(Component.literal("Price Difference Overlay"))
                .description(ConfigScreen.createDescription(
                    "Display the current Bazaar spread per item and across the sellable amount in your inventory.",
                    ConfigScreen.ConfigImage.PRICE_DIFFERENCE
                ))
                .options(rootGroup.build())
                .collapsed(true)
                .build();
        }
    }
}
