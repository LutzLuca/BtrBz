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
                ConfigScreen.text("Show the product, saved price target or liquidity quantity and price bound, "
                    + "plus the price or quantity observed when the alert triggered."),
                ConfigScreen.example("Liquidity target reached\nDiamond\n"
                    + "Instantly sell 1,200 items at ≥ 4.0 coins each\nObserved: 1,500 qualifying items"),
                ConfigScreen.text("Detailed toasts can look a little crowded or wrap unevenly with long names "
                    + "and values. When off (the default), a compact message shows just the product "
                    + "and which target was reached."))))
            .binding(false, () -> this.detailedAlertToasts, value -> this.detailedAlertToasts = value)
            .controller(ConfigScreen::createBooleanController);
    }

    public Option.Builder<Boolean> createChatMessageOption() {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Also Send a Chat Message"))
            .description(ConfigScreen.createDescription(
                "Also send reached alerts to chat with a clickable Bazaar link."))
            .binding(false, () -> this.alsoSendChatMessage, value -> this.alsoSendChatMessage = value)
            .controller(ConfigScreen::createBooleanController);
    }

}
