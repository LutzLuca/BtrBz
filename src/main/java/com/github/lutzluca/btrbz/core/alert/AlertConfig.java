package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.config.ConfigUi;
import com.github.lutzluca.btrbz.core.config.ConfigImages;
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
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "Watch saved price and liquidity targets and notify you when one is reached."),
                ConfigUi.note(
                    "Alerts that become valid while this is off may fire immediately when it is enabled again."))))
            .binding(true, () -> this.enabled, val -> this.enabled = val)
            .controller(ConfigUi::createBooleanController);
    }

    public Option.Builder<Boolean> createToastOnAlertOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Show Alert Toasts"))
            .description(ConfigUi.createDescription(
                "Show a toast when an alert hits its target. Find it later in the Reached tab."))
            .binding(true, () -> this.toastOnAlert, val -> this.toastOnAlert = val)
            .controller(ConfigUi::createBooleanController);
    }

    public Option.Builder<Boolean> createDetailedAlertToastsOption() {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Detailed Alert Toasts"))
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text("Show the product and saved price target, or the saved item quantity "
                    + "and price limit, plus the price or quantity that triggered the alert."),
                ConfigUi.example("Liquidity target reached\nDiamond\n"
                    + "Instantly sell 1,200 items at ≥ 4.0 coins each\nObserved: 1,500 qualifying items"),
                ConfigUi.text("Long names and values may wrap. Turn this off to show only the product "
                    + "and which target was reached."))))
            .binding(false, () -> this.detailedAlertToasts, value -> this.detailedAlertToasts = value)
            .controller(ConfigUi::createBooleanController);
    }

    public Option.Builder<Boolean> createChatMessageOption() {
        return Option.<Boolean>createBuilder()
            .name(Component.literal("Also Send a Chat Message"))
            .description(ConfigUi.createDescription(
                "Also send reached alerts to chat with a link to the item in the Bazaar."))
            .binding(false, () -> this.alsoSendChatMessage, value -> this.alsoSendChatMessage = value)
            .controller(ConfigUi::createBooleanController);
    }

    public OptionGroup createGroup(Consumer<Screen> openAlerts) {
        var toasts = new OptionGrouping(this.createToastOnAlertOption())
            .addOptions(this.createDetailedAlertToastsOption());
        var alerts = new OptionGrouping(this.createEnabledOption())
            .addSubgroups(toasts)
            .addOptions(this.createChatMessageOption());

        return OptionGroup.createBuilder()
            .name(Component.literal("Alerts"))
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text("Get an alert when a Bazaar price or available quantity hits your target."),
                ConfigUi.text("Price alerts compare the selected Buy Price or Sell Price with your threshold. "
                    + "Buy Price is the lowest sell offer. Sell Price is the highest buy order. "
                    + "Below and Above include equality."),
                ConfigUi.note("Price alerts need a reference price. An empty order list has no price to compare "
                    + "and cannot trigger alerts watching that side. "
                    + "Missing or invalid prices also keep those alerts waiting."),
                ConfigUi.text("Use liquidity alerts to watch how many items you can buy or sell instantly."),
                ConfigUi.note("Open /btrbz alert to create, edit, or remove alerts.")), ConfigImages.PriceAlert))
            .options(alerts.build())
            .option(ButtonOption.createBuilder()
                .name(Component.literal("Open Alerts"))
                .text(Component.literal("Open"))
                .description(
                    ConfigUi.createDescription("Search Bazaar products and manage price and liquidity alerts."))
                .action((screen, _) -> openAlerts.accept(screen))
                .build())
            .collapsed(true)
            .build();
    }
}
