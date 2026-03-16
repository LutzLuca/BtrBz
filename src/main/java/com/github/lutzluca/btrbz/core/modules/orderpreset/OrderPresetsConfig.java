package com.github.lutzluca.btrbz.core.modules.orderpreset;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.ConfigScreen.OptionGrouping;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.Option.Builder;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

import java.util.List;

public class OrderPresetsConfig {

    public Integer containerX, containerY;
    public Integer signX, signY;
    public boolean enabled = true;
    public boolean enableOnContainer = true;
    public boolean enableOnSign = true;
    public List<Integer> presets = List.of();

    public Builder<Boolean> createEnableOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Order Presets: Master Switch"))
            .description(OptionDescription.of(Component.literal(
                "Master switch to enable or disable the Order Presets module.")))
            .binding(true, () -> this.enabled, enabled -> this.enabled = enabled)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createEnableContainerOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Enable in Bazaar Menu"))
            .description(OptionDescription.of(Component.literal(
                "Show presets when setting up an order volume in the buy order volume setup menu.")))
            .binding(true, () -> this.enableOnContainer, enabled -> this.enableOnContainer = enabled)
            .controller(ConfigScreen::createBooleanController);
    }

    public Builder<Boolean> createEnableSignOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Enable in Sign Screen"))
            .description(OptionDescription.of(Component.literal(
                "Show presets when editing the enter volume amount sign in the order setup flow.")))
            .binding(true, () -> this.enableOnSign, enabled -> this.enableOnSign = enabled)
            .controller(ConfigScreen::createBooleanController);
    }

    public OptionGroup createGroup() {
        var rootGroup = new OptionGrouping(this.createEnableOption())
            .addOptions(
                this.createEnableContainerOption(),
                this.createEnableSignOption()
            );

        return OptionGroup
            .createBuilder()
            .name(Component.nullToEmpty("Order Presets"))
            .description(OptionDescription.of(Component.literal(
                "Lets you have predefined order volume for quick access")))
            .options(rootGroup.build())
            .collapsed(false)
            .build();
    }
}
