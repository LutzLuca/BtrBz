package com.github.lutzluca.btrbz.core.orderdisplay;

import com.github.lutzluca.btrbz.core.config.ConfigUi;
import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

public class OrderItemTooltipConfig {
    public boolean enabled = true;
    public boolean showStatus = true;
    public boolean showQueue = true;
    public boolean showPrices = false;
    public boolean showOnlyWhenUndercut = true;
    public boolean showEstimatedTime = false;

    private Option.Builder<Boolean> createEnabledOption(Runnable invalidateCache) {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Enable Order Item Tooltips"))
            .binding(true, () -> this.enabled, val -> {
                this.enabled = val;
                invalidateCache.run();
            })
            .description(OptionDescription.of(Component.literal(
                "Show detailed information when hovering an order item on the Bazaar Orders page.")))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createStatusOption(Runnable invalidateCache) {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Show Status"))
            .binding(true, () -> this.showStatus, val -> {
                this.showStatus = val;
                invalidateCache.run();
            })
            .description(OptionDescription.of(Component.literal(
                "Show whether the order is top, matched at the best price, undercut, or currently unknown.")))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createQueueOption(Runnable invalidateCache) {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Show Order Queue Estimate"))
            .binding(true, () -> this.showQueue, val -> {
                this.showQueue = val;
                invalidateCache.run();
            })
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "When an order is undercut, show estimated competing orders and items ahead of it."),
                ConfigUi.note("This is an order-book estimate, not an exact queue position."))))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createPricesOption(Runnable invalidateCache) {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Show Current Prices"))
            .binding(false, () -> this.showPrices, val -> {
                this.showPrices = val;
                invalidateCache.run();
            })
            .description(OptionDescription.of(Component.literal(
                "Show the best current buy-order and sell-offer prices for the product.")))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createOnlyWhenUndercutOption(Runnable invalidateCache) {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Only When Undercut"))
            .binding(true, () -> this.showOnlyWhenUndercut, val -> {
                this.showOnlyWhenUndercut = val;
                invalidateCache.run();
            })
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text("Show current market prices only after this order is undercut."),
                ConfigUi.requires("Show Current Prices"))))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createEstimatedTimeOption(Runnable invalidateCache) {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Show Estimated Fill Time"))
            .binding(false, () -> this.showEstimatedTime, val -> {
                this.showEstimatedTime = val;
                invalidateCache.run();
            })
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "Estimate how long a top-position order may take to fill using its remaining volume and "
                        + "the product's weekly moving volume."),
                ConfigUi.note(
                    "Market changes and delayed UI updates can make this inaccurate. Treat it as a rough "
                        + "guide, not a countdown."))))
            .controller(ConfigUi::createBooleanController);
    }

    public OptionGroup createGroup(Runnable invalidateCache) {
        var pricesGroup = new OptionGrouping(this.createPricesOption(invalidateCache))
            .addOptions(this.createOnlyWhenUndercutOption(invalidateCache));

        var root = new OptionGrouping(this.createEnabledOption(invalidateCache))
            .addOptions(
                this.createStatusOption(invalidateCache),
                this.createQueueOption(invalidateCache),
                this.createEstimatedTimeOption(invalidateCache))
            .addSubgroups(pricesGroup);

        return OptionGroup.createBuilder()
            .name(Component.literal("Order Item Tooltips"))
            .description(ConfigUi.createDescription(
                "Choose which status, estimated queue, market, and fill-time details appear when hovering an "
                    + "order item on the Bazaar Orders page.",
                ConfigImages.OrderTooltip))
            .options(root.build())
            .collapsed(true)
            .build();
    }
}
