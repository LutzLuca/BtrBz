package com.github.lutzluca.btrbz.core.widgets.config;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.BazaarWidgets;
import com.github.lutzluca.btrbz.widgets.framework.WidgetId;
import com.github.lutzluca.btrbz.widgets.framework.ui.ScrollSafeDiscreteSliderComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Builds feature-owned controls for the widget manager. */
public final class BazaarConfigPanels {
    private static final Pattern ENUM_WORD_BOUNDARY = Pattern.compile(
        "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"
    );

    public UIComponent create(WidgetId id) {
        if (id.equals(BazaarWidgets.BAZAAR_ORDERS_ID)) return this.hud();
        if (id.equals(BazaarWidgets.TRACKED_ORDERS_ID)) return this.trackedOrders();
        if (id.equals(BazaarWidgets.ORDER_VALUE_ID)) return this.orderValue();
        if (id.equals(BazaarWidgets.ORDER_BOOK_SCREEN_ID)) return this.orderBook();
        if (id.equals(BazaarWidgets.ORDER_BOOK_PRICE_ID)) return this.embeddedOrderBook();
        if (id.equals(BazaarWidgets.BOOKMARKS_ID)) return this.bookmarks();
        if (id.equals(BazaarWidgets.ORDER_PRESETS_ID)) return this.presets();
        if (id.equals(BazaarWidgets.ORDER_LIMIT_ID)) return this.orderLimit();
        if (id.equals(BazaarWidgets.PRICE_DIFF_ID)) return this.priceDiff();
        return null;
    }

    private UIComponent hud() {
        var panel = panel();
        var values = ConfigManager.get().widgets.bazaarOrders;
        this.enumeration(panel, "Display mode", () -> values.mode, value -> values.mode = value);
        this.integer(panel, "Visible orders", () -> values.visibleOrders, value -> values.visibleOrders = value, 1, 10);
        this.integer(panel, "Content width", () -> values.contentWidth, value -> values.contentWidth = value, 180, 320);
        this.bool(panel, "Abbreviate Enchanted", () -> values.abbreviateEnchanted, value -> values.abbreviateEnchanted = value);
        this.bool(panel, "Hide when empty", () -> values.hideWhenEmpty, value -> values.hideWhenEmpty = value);
        this.bool(panel, "Show ItemStacks", () -> values.showItem, value -> values.showItem = value);
        this.bool(panel, "Show volume", () -> values.showVolume, value -> values.showVolume = value);
        this.enumeration(panel, "Price display", () -> values.priceDisplay, value -> values.priceDisplay = value);
        this.enumeration(panel, "Queue display", () -> values.queueDisplay, value -> values.queueDisplay = value);
        this.enumeration(panel, "Undercut detail", () -> values.undercutDetail, value -> values.undercutDetail = value);
        return panel;
    }

    private UIComponent trackedOrders() {
        var panel = panel();
        var values = ConfigManager.get().widgets.trackedOrders;
        this.integer(panel, "Content width", () -> values.contentWidth, value -> values.contentWidth = value, 180, 320);
        this.integer(panel, "Visible rows", () -> values.visibleRows, value -> values.visibleRows = value, 1, 10);
        this.bool(panel, "Fit to content", () -> values.fitToContent, value -> values.fitToContent = value);
        this.enumeration(panel, "Density", () -> values.layout, value -> values.layout = value);
        this.enumeration(panel, "Sort order", () -> values.sort, value -> values.sort = value);
        this.bool(panel, "Abbreviate Enchanted", () -> values.abbreviateEnchanted, value -> values.abbreviateEnchanted = value);
        this.bool(panel, "Hide when no active orders", () -> values.hideWhenEmpty, value -> values.hideWhenEmpty = value);
        this.bool(panel, "Show filled count", () -> values.showStatusSummary, value -> values.showStatusSummary = value);
        this.bool(panel, "Show ItemStacks", () -> values.showItem, value -> values.showItem = value);
        this.bool(panel, "Show volume", () -> values.showVolume, value -> values.showVolume = value);
        this.enumeration(panel, "Price display", () -> values.priceDisplay, value -> values.priceDisplay = value);
        this.bool(panel, "Show market details", () -> values.showMarketInfo, value -> values.showMarketInfo = value);
        this.bool(panel, "Show live fill bar", () -> values.showProgress, value -> values.showProgress = value);
        return panel;
    }

    private UIComponent orderValue() {
        var panel = panel();
        var values = ConfigManager.get().widgets.orderValue;
        this.integer(panel, "Content width", () -> values.contentWidth, value -> values.contentWidth = value, 170, 280);
        this.enumeration(panel, "Display", () -> values.display, value -> values.display = value);
        this.enumeration(panel, "Number format", () -> values.numberStyle, value -> values.numberStyle = value);
        this.bool(panel, "Show coins suffix", () -> values.showCoinsSuffix, value -> values.showCoinsSuffix = value);
        this.enumeration(panel, "Colors", () -> values.colorMode, value -> values.colorMode = value);
        this.bool(panel, "Buy-order coins", () -> values.buyLocked, value -> values.buyLocked = value);
        this.bool(panel, "Buy-order items", () -> values.buyItems, value -> values.buyItems = value);
        this.bool(panel, "Sell claimable", () -> values.sellClaimable, value -> values.sellClaimable = value);
        this.bool(panel, "Sell pending", () -> values.sellPending, value -> values.sellPending = value);
        return panel;
    }

    private UIComponent orderBook() {
        var panel = panel();
        var values = ConfigManager.get().widgets.orderBookScreen;
        this.integer(panel, "Content width", () -> values.contentWidth, value -> values.contentWidth = value, 220, 440);
        this.integer(panel, "Levels per side", () -> values.visibleRows, value -> values.visibleRows = value, 1, 10);
        this.enumeration(panel, "Layout", () -> values.layout, value -> values.layout = value);
        this.enumeration(panel, "Volume format", () -> values.numberStyle, value -> values.numberStyle = value);
        this.bool(panel, "Show order count", () -> values.showOrderCount, value -> values.showOrderCount = value);
        this.bool(panel, "Show product header", () -> values.showHeader, value -> values.showHeader = value);
        this.bool(panel, "Show ItemStack", () -> values.showItem, value -> values.showItem = value);
        return panel;
    }

    private UIComponent embeddedOrderBook() {
        var panel = panel();
        var values = ConfigManager.get().widgets.orderBookPrice;
        this.integer(panel, "Content width", () -> values.contentWidth, value -> values.contentWidth = value, 240, 360);
        this.integer(panel, "Levels per side", () -> values.visibleRows, value -> values.visibleRows = value, 1, 10);
        this.bool(panel, "Show buy offers", () -> values.showBuy, value -> values.showBuy = value);
        this.bool(panel, "Show sell offers", () -> values.showSell, value -> values.showSell = value);
        this.bool(panel, "Show amounts", () -> values.showAmounts, value -> values.showAmounts = value);
        this.bool(panel, "Show order count", () -> values.showOrderCount, value -> values.showOrderCount = value);
        this.bool(panel, "Show product header", () -> values.showHeader, value -> values.showHeader = value);
        this.bool(panel, "Show ItemStack", () -> values.showItem, value -> values.showItem = value);
        this.enumeration(panel, "Sides", () -> values.sideDisplay, value -> values.sideDisplay = value);
        return panel;
    }

    private UIComponent bookmarks() {
        var panel = panel();
        var values = ConfigManager.get().widgets.bookmarks;
        this.integer(panel, "Content width", () -> values.contentWidth, value -> values.contentWidth = value, 150, 300);
        this.integer(panel, "Visible rows", () -> values.visibleRows, value -> values.visibleRows = value, 1, 12);
        this.bool(panel, "Fit to content", () -> values.fitToContent, value -> values.fitToContent = value);
        this.enumeration(panel, "Sort order", () -> values.sort, value -> values.sort = value);
        this.bool(panel, "Hide when no bookmarks", () -> values.hideWhenEmpty, value -> values.hideWhenEmpty = value);
        this.bool(panel, "Show ItemStacks", () -> values.showItems, value -> values.showItems = value);
        this.bool(panel, "Show order indicators", () -> values.showIndicators, value -> values.showIndicators = value);
        this.bool(panel, "Abbreviate Enchanted", () -> values.abbreviateEnchanted, value -> values.abbreviateEnchanted = value);
        return panel;
    }

    private UIComponent presets() {
        var panel = panel();
        var values = ConfigManager.get().widgets.orderPresets;
        this.integer(panel, "Content width", () -> values.contentWidth, value -> values.contentWidth = value, 90, 110);
        this.bool(panel, "Maximum preset", () -> values.maximum, value -> values.maximum = value);
        this.bool(panel, "Clipboard preset", () -> values.clipboard, value -> values.clipboard = value);
        this.bool(panel, "Show disabled presets", () -> values.showDisabled, value -> values.showDisabled = value);
        this.bool(panel, "Show tooltips", () -> values.showTooltips, value -> values.showTooltips = value);
        return panel;
    }

    private UIComponent orderLimit() {
        var panel = panel();
        var values = ConfigManager.get().widgets.orderLimit;
        this.integer(panel, "Content width", () -> values.contentWidth, value -> values.contentWidth = value, 140, 280);
        this.enumeration(panel, "Display", () -> values.display, value -> values.display = value);
        this.enumeration(panel, "Number format", () -> values.numberStyle, value -> values.numberStyle = value);
        this.bool(panel, "Show header", () -> values.showHeader, value -> values.showHeader = value);
        panel.child(UIComponents.label(Component.literal("Daily coin limit")));
        var limit = UIComponents.textBox(Sizing.fill(100));
        limit.setMaxLength(18);
        limit.setFilter(text -> text.matches("[0-9]*"));
        limit.text(Long.toString(Math.round(values.dailyLimit)));
        limit.onChanged().subscribe(text -> {
            if (text.isBlank()) return;
            try {
                long parsed = Long.parseLong(text);
                if (parsed <= 0) return;
                values.dailyLimit = parsed;
                ConfigManager.save();
            } catch (NumberFormatException _) { }
        });
        panel.child(limit);
        this.thresholds(panel, values);
        return panel;
    }

    private void thresholds(FlowLayout panel, WidgetsConfig.DailyLimitConfig values) {
        var warning = new ScrollSafeDiscreteSliderComponent(Sizing.fill(100), 1, 100);
        var critical = new ScrollSafeDiscreteSliderComponent(Sizing.fill(100), 1, 100);
        warning.decimalPlaces(0);
        critical.decimalPlaces(0);
        warning.setFromDiscreteValue(values.warningThreshold);
        critical.setFromDiscreteValue(values.criticalThreshold);
        warning.message(value -> Component.literal("Warning threshold " + value));
        critical.message(value -> Component.literal("Critical threshold " + value));
        var synchronizing = new boolean[] {false};
        warning.onChanged().subscribe(value -> {
            if (synchronizing[0]) return;
            values.warningThreshold = (int) Math.round(value);
            if (values.criticalThreshold < values.warningThreshold) {
                values.criticalThreshold = values.warningThreshold;
                synchronizing[0] = true;
                critical.setFromDiscreteValue(values.criticalThreshold);
                synchronizing[0] = false;
            }
            ConfigManager.save();
        });
        critical.onChanged().subscribe(value -> {
            if (synchronizing[0]) return;
            values.criticalThreshold = (int) Math.round(value);
            if (values.warningThreshold > values.criticalThreshold) {
                values.warningThreshold = values.criticalThreshold;
                synchronizing[0] = true;
                warning.setFromDiscreteValue(values.warningThreshold);
                synchronizing[0] = false;
            }
            ConfigManager.save();
        });
        panel.child(warning);
        panel.child(critical);
    }

    private UIComponent priceDiff() {
        var panel = panel();
        var values = ConfigManager.get().widgets.priceDiff;
        this.integer(panel, "Content width", () -> values.contentWidth, value -> values.contentWidth = value, 150, 300);
        this.enumeration(panel, "Display", () -> values.display, value -> values.display = value);
        this.enumeration(panel, "Number format", () -> values.numberStyle, value -> values.numberStyle = value);
        this.bool(panel, "Show ItemStack", () -> values.showItems, value -> values.showItems = value);
        this.bool(panel, "Show product name", () -> values.showProduct, value -> values.showProduct = value);
        return panel;
    }

    private void integer(
        FlowLayout panel,
        String label,
        IntSupplier getter,
        IntConsumer setter,
        int minimum,
        int maximum
    ) {
        var slider = new ScrollSafeDiscreteSliderComponent(Sizing.fill(100), minimum, maximum);
        slider.decimalPlaces(0);
        slider.setFromDiscreteValue(getter.getAsInt());
        slider.message(value -> Component.literal(label + " " + value));
        slider.onChanged().subscribe(value -> {
            setter.accept((int) Math.round(value));
            ConfigManager.save();
        });
        panel.child(slider);
    }

    private void bool(
        FlowLayout panel,
        String label,
        Supplier<Boolean> getter,
        Consumer<Boolean> setter
    ) {
        var checkbox = UIComponents.smallCheckbox(Component.literal(label));
        checkbox.checked(getter.get());
        checkbox.onChanged().subscribe(value -> {
            setter.accept(value);
            ConfigManager.save();
        });
        panel.child(checkbox);
    }

    private <E extends Enum<E>> void enumeration(
        FlowLayout panel,
        String label,
        Supplier<E> getter,
        Consumer<E> setter
    ) {
        var control = UIComponents.button(enumMessage(label, getter.get()), button -> {
            var current = getter.get();
            var values = current.getDeclaringClass().getEnumConstants();
            var next = values[(current.ordinal() + 1) % values.length];
            setter.accept(next);
            ConfigManager.save();
            button.setMessage(enumMessage(label, next));
        });
        control.renderer(ButtonComponent.Renderer.flat(0xFF2C3340, 0xFF384252, 0xFF20242D));
        control.textShadow(false);
        control.sizing(Sizing.fill(100), Sizing.fixed(20));
        panel.child(control);
    }

    private static FlowLayout panel() {
        var panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        panel.gap(5);
        return panel;
    }

    private static Component enumMessage(String label, Enum<?> value) {
        return Component.literal(label + ": " + enumLabel(value));
    }

    static String enumLabel(Enum<?> value) {
        var words = ENUM_WORD_BOUNDARY.split(value.name().replace('_', ' '));
        var display = new StringBuilder();
        for (var word : words) {
            if (!display.isEmpty()) display.append(' ');
            if (word.equalsIgnoreCase("and") || word.equalsIgnoreCase("or")) {
                display.append(word.toLowerCase(java.util.Locale.ROOT));
            } else {
                display.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return display.toString();
    }
}
