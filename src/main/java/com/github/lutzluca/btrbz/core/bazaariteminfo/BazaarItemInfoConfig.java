package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import net.minecraft.network.chat.Component;

/** Persisted defaults shared by every Bazaar Item Info screen. */
public final class BazaarItemInfoConfig {
    public enum ActivityMode {
        Off("Off"),
        IntervalItems("Items per interval");

        private final String label;

        ActivityMode(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    public BazaarItemInfoRange selectedRange = BazaarItemInfoRange.Day;
    public boolean showBuy = true;
    public boolean showSell = true;
    public boolean showBands = true;
    public ActivityMode activityMode = ActivityMode.IntervalItems;
    public int visibleOrderBookRows = 10;
    public NumberStyle volumeNumberStyle = NumberStyle.Exact;
    public boolean showPerLevelOrderCount = true;
    public boolean showCumulativeVolume = true;
    public boolean showBazaarEntry = true;

    public OptionGroup createGroup() {
        return OptionGroup.createBuilder()
            .name(Component.literal("Bazaar Item Info"))
            .description(ConfigScreen.createDescription(
                "Set History defaults and the Order Book layout for the unified Item Info screen."))
            .option(enumOption(
                "Default History Range", BazaarItemInfoRange.Day,
                () -> this.selectedRange, value -> this.selectedRange = value,
                value -> value.label()))
            .option(booleanOption("Show Buy History", true, () -> this.showBuy, value -> this.showBuy = value))
            .option(booleanOption("Show Sell History", true, () -> this.showSell, value -> this.showSell = value))
            .option(booleanOption("Show Price Bands", true, () -> this.showBands, value -> this.showBands = value))
            .option(enumOption(
                "Market Activity", ActivityMode.IntervalItems,
                () -> this.activityMode, value -> this.activityMode = value,
                ActivityMode::label))
            .option(Option.<Integer>createBuilder()
                .name(Component.literal("Minimum Visible Order Book Rows"))
                .description(OptionDescription.of(Component.literal(
                    "Set the minimum height of each side. Wide screens use spare height for more levels.")))
                .binding(10, () -> this.visibleOrderBookRows, value -> this.visibleOrderBookRows = value)
                .controller(option -> IntegerSliderControllerBuilder.create(option).range(3, 30).step(1))
                .build())
            .option(enumOption(
                "Item Count Format", NumberStyle.Exact,
                () -> this.volumeNumberStyle, value -> this.volumeNumberStyle = value,
                value -> value.name()))
            .option(booleanOption(
                "Show Cumulative Volume", true,
                () -> this.showCumulativeVolume,
                value -> this.showCumulativeVolume = value))
            .option(booleanOption(
                "Show Orders Column", true,
                () -> this.showPerLevelOrderCount,
                value -> this.showPerLevelOrderCount = value))
            .option(booleanOption(
                "Show Bazaar Entry", true,
                () -> this.showBazaarEntry,
                value -> this.showBazaarEntry = value))
            .collapsed(true)
            .build();
    }

    private static Option<Boolean> booleanOption(
        String name,
        boolean defaultValue,
        java.util.function.Supplier<Boolean> getter,
        java.util.function.Consumer<Boolean> setter
    ) {
        return Option.<Boolean>createBuilder()
            .name(Component.literal(name))
            .binding(defaultValue, getter, setter)
            .controller(ConfigScreen::createBooleanController)
            .build();
    }

    private static <T extends Enum<T>> Option<T> enumOption(
        String name,
        T defaultValue,
        java.util.function.Supplier<T> getter,
        java.util.function.Consumer<T> setter,
        java.util.function.Function<T, String> formatter
    ) {
        return Option.<T>createBuilder()
            .name(Component.literal(name))
            .binding(defaultValue, getter, setter)
            .controller(option -> EnumControllerBuilder.create(option)
                .formatValue(value -> Component.literal(formatter.apply(value))))
            .build();
    }
}
