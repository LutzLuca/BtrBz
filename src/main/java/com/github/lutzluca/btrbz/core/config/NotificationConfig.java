package com.github.lutzluca.btrbz.core.config;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

/** Delivery preferences shared by reached alerts. */
public class NotificationConfig {

    public boolean alsoSendChatMessage;
    public boolean playNotificationSound = true;

    public OptionGroup createGroup() {
        return OptionGroup
            .createBuilder()
            .name(Component.literal("Alert Delivery"))
            .description(ConfigScreen.createDescription(
                "These delivery options currently apply to reached price alerts."))
            .option(Option
                .<Boolean>createBuilder()
                .name(Component.literal("Also Send a Chat Message"))
                .description(ConfigScreen.createDescription(
                    "Also send reached price alerts to chat with a clickable Bazaar link."))
                .binding(false, () -> this.alsoSendChatMessage, value -> this.alsoSendChatMessage = value)
                .controller(ConfigScreen::createBooleanController)
                .build())
            .option(Option
                .<Boolean>createBuilder()
                .name(Component.literal("Play Notification Sound"))
                .description(ConfigScreen.createDescription(
                    "Play a sound when a price target is reached, whether or not chat delivery is enabled."))
                .binding(true, () -> this.playNotificationSound, value -> this.playNotificationSound = value)
                .controller(ConfigScreen::createBooleanController)
                .build())
            .collapsed(true)
            .build();
    }
}
