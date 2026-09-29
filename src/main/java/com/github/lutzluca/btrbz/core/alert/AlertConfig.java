package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AlertConfig {

    public boolean enabled = true;
    public boolean toastOnAlert = true;
    public boolean detailedAlertToasts;
    public boolean alsoSendChatMessage;
    public List<Alert> alerts = new ArrayList<>();
    public List<ReachedAlert> reachedAlerts = new ArrayList<>();

    public Option.Builder<Boolean> createEnabledOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Enable Alerts"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text(
                    "Watch saved price and liquidity targets and notify you when one is reached."),
                ConfigScreen.note(
                    "Alerts that become valid while this is off may fire immediately when it is enabled again."))))
            .binding(true, () -> this.enabled, val -> this.enabled = val)
            .controller(ConfigScreen::createBooleanController);
    }

    public Option.Builder<Boolean> createToastOnAlertOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Show Alert Toasts"))
            .description(ConfigScreen.createDescription(
                "Show a toast when an alert hits its target. Find it later in the Reached tab."))
            .binding(true, () -> this.toastOnAlert, val -> this.toastOnAlert = val)
            .controller(ConfigScreen::createBooleanController);
    }

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

    public OptionGroup createGroup(Consumer<Screen> openAlerts) {
        var toasts = new OptionGrouping(this.createToastOnAlertOption())
            .addOptions(this.createDetailedAlertToastsOption());
        var alerts = new OptionGrouping(this.createEnabledOption())
            .addSubgroups(toasts)
            .addOptions(this.createChatMessageOption());

        return OptionGroup.createBuilder()
            .name(Component.literal("Alerts"))
            .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                ConfigScreen.text("Get an alert when a Bazaar price or available quantity hits your target."),
                ConfigScreen.note("Open /btrbz alert to create, edit, or remove alerts.")), ConfigImages.PriceAlert))
            .options(alerts.build())
            .option(ButtonOption.createBuilder()
                .name(Component.literal("Open Alerts"))
                .text(Component.literal("Open"))
                .description(
                    ConfigScreen.createDescription("Search Bazaar products and manage price and liquidity alerts."))
                .action((screen, _) -> openAlerts.accept(screen))
                .build())
            .collapsed(true)
            .build();
    }
}
