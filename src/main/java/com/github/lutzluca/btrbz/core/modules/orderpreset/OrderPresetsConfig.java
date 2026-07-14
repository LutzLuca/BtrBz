package com.github.lutzluca.btrbz.core.modules.orderpreset;

import com.github.lutzluca.btrbz.utils.Position;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
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
            .name(Component.nullToEmpty("Enable Order Presets"))
            .description(OptionDescription.of(Component.literal(
                "Show reusable quantity buttons while choosing the amount for a new Bazaar order.")))
            .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createEnableContainerOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Show in Quantity Menu"))
            .description(OptionDescription.of(Component.literal(
                "Show preset quantities beside the Bazaar menu used to choose an order amount.")))
            .binding(true, () -> this.enableOnContainer, enabled -> this.enableOnContainer = enabled)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createEnableSignOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Show Beside Quantity Sign"))
            .description(OptionDescription.of(Component.literal(
                "Show preset quantities while manually entering an order amount on a sign.")))
            .binding(true, () -> this.enableOnSign, enabled -> this.enableOnSign = enabled)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createHideUnaffordablePresetsOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Hide Unaffordable Presets"))
            .description(OptionDescription.of(Component.literal(
                "Hide quantity presets that cost more coins than are currently available in your purse.")))
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
            .name(Component.nullToEmpty("Order Presets"))
            .description(OptionDescription.of(Component.literal(
                "Choose common order quantities with one click while setting up a buy order or sell offer.")))
            .options(rootGroup.build())
            .collapsed(true)
            .build();
    }
}
