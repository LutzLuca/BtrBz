package com.github.lutzluca.btrbz.core.modules.orderpreset;

import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

public class OrderPresetsConfig {

    public Position containerPosition;
    public Position signPosition;
    public boolean enabled = true;
    public boolean enableOnContainer = true;
    public boolean enableOnSign = true;
    public boolean hideUnaffordablePresets = false;
    public List<Integer> presets = List.of();

    public Builder<Boolean> createEnableOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Enable Order Presets Overlay"))
            .description(OptionDescription.of(Component.literal(
                "Show reusable order amounts while creating a new Bazaar buy order.")))
            .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createEnableContainerOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Show in Order Amount Menu"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Show preset amounts beside the menu used to choose a buy-order amount."),
                ConfigScreen.note(
                    "This setting is independent from Show Beside Order Amount Sign.")
            )))
            .binding(true, () -> this.enableOnContainer, enabled -> this.enableOnContainer = enabled)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createEnableSignOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Show Beside Order Amount Sign"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Show preset amounts while entering the buy-order amount on the sign."),
                ConfigScreen.note(
                    "This can remain enabled when Show in Order Amount Menu is off.")
            )))
            .binding(true, () -> this.enableOnSign, enabled -> this.enableOnSign = enabled)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createHideUnaffordablePresetsOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Hide Unaffordable Presets"))
            .description(OptionDescription.of(Component.literal(
                "Hide preset amounts that cost more coins than are currently available in your purse.")))
            .binding(false, () -> this.hideUnaffordablePresets, hide -> this.hideUnaffordablePresets = hide)
            .controller(ConfigScreen::createBooleanController);
    }

    public OptionGroup createGroup() {
        var rootGroup = new OptionGrouping(this.createEnableOption())
            .addOptions(
                this.createEnableContainerOption(),
                this.createEnableSignOption(),
                this.createHideUnaffordablePresetsOption()
            );

        return OptionGroup
            .createBuilder()
            .name(Component.nullToEmpty("Order Presets Overlay"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text("Choose common order amounts while setting up a buy order."),
                Component
                    .literal("Add a reusable amount with ")
                    .append(ConfigScreen.command("/btrbz preset add <amount>"))
                    .append(Component.literal(".")),
                ConfigScreen.note(Component
                    .literal("The overlay includes ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Max").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(", plus ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("Clipboard").withStyle(ChatFormatting.BLUE))
                    .append(Component.literal(" when it contains a valid amount.").withStyle(ChatFormatting.GRAY)))
            ),
                ConfigScreen.ConfigImage.ORDER_PRESETS
            ))
            .options(rootGroup.build())
            .collapsed(true)
            .build();
    }
}
