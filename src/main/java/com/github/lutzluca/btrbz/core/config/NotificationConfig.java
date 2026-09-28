package com.github.lutzluca.btrbz.core.config;

import dev.isxander.yacl3.api.Option;
import net.minecraft.network.chat.Component;

/** Delivery preferences shared by reached alerts. */
public class NotificationConfig {

    public boolean alsoSendChatMessage;
    public boolean detailedAlertToasts;

    public Option.Builder<Boolean> createDetailedAlertToastsOption() {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Detailed Alert Toasts"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text("Show the product and saved price target, or the saved item quantity "
                    + "and price limit, plus the price or quantity that triggered the alert."),
                ConfigScreen.example("Liquidity target reached\nDiamond\n"
                    + "Instantly sell 1,200 items at ≥ 4.0 coins each\nObserved: 1,500 qualifying items"),
                ConfigScreen.text("Long names and values may wrap. Turn this off to show only the product "
                    + "and which target was reached."))))
            .binding(false, () -> this.detailedAlertToasts, value -> this.detailedAlertToasts = value)
            .controller(ConfigScreen::createBooleanController);
    }

    public Option.Builder<Boolean> createChatMessageOption() {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Also Send a Chat Message"))
            .description(ConfigScreen.createDescription(
                "Also send reached alerts to chat with a link to the item in the Bazaar."))
            .binding(false, () -> this.alsoSendChatMessage, value -> this.alsoSendChatMessage = value)
            .controller(ConfigScreen::createBooleanController);
    }

}
