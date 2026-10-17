package com.github.lutzluca.btrbz.core.orderbook;

import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.ConfigUi;
import dev.isxander.yacl3.api.Option;
import java.util.List;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import net.minecraft.network.chat.Component;

public final class OrderBookHintConfig {
    @SerialEntry
    public Mode mode = Mode.Detailed;
    @SerialEntry
    public boolean showRemainingInNativeBook;
    @SerialEntry
    public boolean showRemainingInWidgetTooltips;

    public OptionGroup createGroup(Runnable hintsChanged) {
        var modeText = ConfigUi.paragraphs(
            ConfigUi.text("Mark price levels containing your tracked orders in both widgets and the native order book. "
                + "Off hides personal cues everywhere and keeps all public levels and quantities."),
            ConfigUi.text("Highlight marks matching widget prices. Detailed also shows your count in parentheses. "
                + "9 (2) means 9 orders in total, 2 of them yours."));
        var off = ConfigImages.OrderBookHintEmbeddedOff.description(modeText);
        var highlight = ConfigImages.OrderBookHintEmbeddedHighlight.description(modeText);
        var detailed = ConfigImages.OrderBookHintEmbeddedDetailed.description(modeText);
        var modeOption = Option.<Mode>createBuilder()
            .name(Component.literal("Hint Style"))
            .description(ConfigUi.createPreviewDescription(value -> switch (value) {
                case Off -> off;
                case Highlight -> highlight;
                case Detailed -> detailed;
            }))
            .binding(Mode.Detailed, () -> this.mode, value -> {
                this.mode = value;
                hintsChanged.run();
            })
            .controller(option -> EnumControllerBuilder.create(option).enumClass(Mode.class))
            .build();

        var nativeText = ConfigUi.paragraphs(
            ConfigUi.text("Add a line with your order count and remaining items below matching native Bazaar levels. "
                + "When off, append only the count in parentheses. Order Book Hints must be enabled."),
            ConfigUi.note("Uses your last observed Orders-menu state. Remaining items are an estimate, not live."));
        var compact = ConfigImages.OrderBookHintNativeCompact.description(nativeText);
        var remaining = ConfigImages.OrderBookHintNativeRemaining.description(nativeText);
        var nativeOption = Option.<Boolean>createBuilder()
            .name(Component.literal("Show Remaining Items in Native Order Book"))
            .description(ConfigUi.createPreviewDescription(value -> value ? remaining : compact))
            .binding(false, () -> this.showRemainingInNativeBook, value -> this.showRemainingInNativeBook = value)
            .controller(ConfigUi::createBooleanController)
            .build();

        var tooltipText = ConfigUi.paragraphs(
            ConfigUi.text("Show approximate unfilled items when hovering a matching price in either widget. "
                + "Requires Detailed hint style."),
            ConfigUi.note("Uses your last observed Orders-menu state. Remaining items are an estimate, not live."));
        var countTooltip = ConfigImages.OrderBookHintTooltipCompact.description(tooltipText);
        var remainingTooltip = ConfigImages.OrderBookHintTooltipRemaining.description(tooltipText);
        var tooltipOption = Option.<Boolean>createBuilder()
            .name(Component.literal("Show Remaining Items in Widget Tooltips"))
            .description(ConfigUi.createPreviewDescription(value -> value ? remainingTooltip : countTooltip))
            .binding(false, () -> this.showRemainingInWidgetTooltips, value -> {
                this.showRemainingInWidgetTooltips = value;
                hintsChanged.run();
            })
            .controller(ConfigUi::createBooleanController)
            .build();

        return OptionGroup.createBuilder().name(Component.literal("Order Book Hints"))
            .options(List.of(modeOption, nativeOption, tooltipOption)).collapsed(true).build();
    }

    public enum Mode {
        Off,
        Highlight,
        Detailed
    }
}
