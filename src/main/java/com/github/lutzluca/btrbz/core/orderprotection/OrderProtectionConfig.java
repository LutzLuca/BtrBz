package com.github.lutzluca.btrbz.core.orderprotection;

import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.DoubleSliderControllerBuilder;
import net.minecraft.network.chat.Component;

public class OrderProtectionConfig {

    public boolean enabled = true;
    public boolean showChatMessage = true;
    public boolean soundOnBlocked = true;

    public boolean blockUndercutPercentage = true;
    public double maxBuyOrderUndercut = 15.0;
    public double maxSellOfferUndercut = 15.0;

    public boolean blockUndercutOfOpposing = true;

    public Option.Builder<Boolean> createEnabledOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Enable Order Protection"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Check new order prices and stop submissions that match an enabled safety rule."),
                ConfigScreen.note("Hold Ctrl while confirming to override a block."))))
            .binding(true, () -> this.enabled, val -> this.enabled = val)
            .controller(ConfigScreen::createBooleanController);
    }

    public Option.Builder<Boolean> createShowChatMessageOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Show Chat Messages"))
            .description(OptionDescription.of(Component.literal(
                "Explain in chat which safety rule blocked an order and which price caused the warning.")))
            .binding(true, () -> this.showChatMessage, val -> this.showChatMessage = val)
            .controller(ConfigScreen::createBooleanController);
    }

    public Option.Builder<Boolean> createSoundOnBlockedOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Play Blocked-Order Sound"))
            .description(OptionDescription.of(Component.literal(
                "Play a warning sound whenever Order Protection stops a submission.")))
            .binding(true, () -> this.soundOnBlocked, val -> this.soundOnBlocked = val)
            .controller(ConfigScreen::createBooleanController);
    }

    public Option.Builder<Boolean> createBlockUndercutPercentageOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Limit Price Undercutting"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Block prices that improve on the current best order by the configured percentage or more."),
                ConfigScreen.example(
                    "At a best price of 15M, a 100K change is about 0.67%. With a 15% limit, "
                        + "the blocked difference begins at 2.25M."),
                ConfigScreen.note(
                    "A single minimum price step (0.1 coins) is always exempt from this percentage check."))))
            .binding(
                true,
                () -> this.blockUndercutPercentage,
                val -> this.blockUndercutPercentage = val)
            .controller(ConfigScreen::createBooleanController);
    }

    public Option.Builder<Double> createMaxBuyOrderUndercutOption() {
        return Option
            .<Double>createBuilder()
            .name(Component.literal("Maximum Buy-Order Increase (%)"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Block a buy order when it is this percentage or more above the best current buy-order price."),
                ConfigScreen.example(
                    "With a best price of 15M and a 5% limit, 15.75M or more is blocked."))))
            .binding(
                15.0,
                () -> this.maxBuyOrderUndercut,
                val -> this.maxBuyOrderUndercut = val)
            .controller(opt -> DoubleSliderControllerBuilder
                .create(opt)
                .range(0.0, 100.0)
                .step(0.5));
    }

    public Option.Builder<Double> createMaxSellOfferUndercutOption() {
        return Option
            .<Double>createBuilder()
            .name(Component.literal("Maximum Sell-Offer Reduction (%)"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Block a sell offer when it is this percentage or more below the best current "
                        + "sell-offer price."),
                ConfigScreen.example(
                    "With a best price of 15M and a 5% limit, 14.25M or less is blocked."))))
            .binding(
                15.0,
                () -> this.maxSellOfferUndercut,
                val -> this.maxSellOfferUndercut = val)
            .controller(opt -> DoubleSliderControllerBuilder
                .create(opt)
                .range(0.0, 100.0)
                .step(0.5));
    }

    public Option.Builder<Boolean> createBlockUndercutOfOpposingOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Block Orders at Instant-Trade Prices"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Block buy orders priced at or above the best sell offer, and sell offers priced at or below "
                        + "the best buy order. Use an instant trade instead."),
                ConfigScreen.example(
                    "If the best buy order is 100 and the best sell offer is 105, a sell offer of 100 or less "
                        + "and a buy order of 105 or more are blocked."))))
            .binding(
                true,
                () -> this.blockUndercutOfOpposing,
                val -> this.blockUndercutOfOpposing = val)
            .controller(ConfigScreen::createBooleanController);
    }

    public OptionGroup createGroup() {
        var undercutGroup = new OptionGrouping(this.createBlockUndercutPercentageOption()).addOptions(
            this.createMaxSellOfferUndercutOption(),
            this.createMaxBuyOrderUndercutOption());

        var rootGroup = new OptionGrouping(this.createEnabledOption())
            .addOptions(
                this.createShowChatMessageOption(),
                this.createSoundOnBlockedOption(),
                this.createBlockUndercutOfOpposingOption())
            .addSubgroups(undercutGroup);

        return OptionGroup
            .createBuilder()
            .name(Component.literal("Order Protection"))
            .description(ConfigScreen.createDescription(
                "Prevent accidental orders at unusually aggressive prices before they are submitted to the Bazaar.",
                ConfigImages.OrderProtection))
            .options(rootGroup.build())
            .collapsed(true)
            .build();
    }
}
