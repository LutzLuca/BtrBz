package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ToastNotifications;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;

public final class AlertNotifications {
    private AlertNotifications() {}

    public static boolean notifyInbox(int pending, ToastNotifications toasts) {
        if (!BtrBz.isActive() || Minecraft.getInstance().player == null) {
            return false;
        }
        var config = ConfigStore.get().config().alert;
        if (!config.alsoSendChatMessage && !config.toastOnAlert) {
            return true;
        }
        String summary = pending + (pending == 1 ? " alert needs" : " alerts need") + " your attention";
        boolean delivered = config.alsoSendChatMessage && Notifier.notifyPlayer(Notifier.prefix()
            .append(Component.literal(summary + ". ").withStyle(UiStyles.label()))
            .append(link("Inbox", "/btrbz alert", "Open your pending alerts"))
            .append(" ").append(link("10 min", "/btrbz alert snooze", "Postpone reminder summaries")));
        if (config.toastOnAlert) {
            toasts.show(Component.literal("BtrBz Alerts").withStyle(UiStyles.modLabel()),
                List.of(Component.literal(summary).withStyle(UiStyles.label())),
                java.util.Optional.empty());
        }
        return delivered || config.toastOnAlert;
    }

    public static void notifyReached(ReachedAlert reached, BazaarData bazaarData, ToastNotifications toasts) {
        if (!BtrBz.isActive() || Minecraft.getInstance().player == null) {
            return;
        }

        var alert = reached.alert();
        var product = bazaarData.refreshIndexedProduct(alert.product);
        var notice = AlertNotice.from(reached, product);
        var config = ConfigStore.get().config().alert;

        if (config.toastOnAlert) {
            Component productName = UiStyles.productName(notice.formattedProductName());
            if (config.detailedAlertToasts) {
                toasts.show(
                    Component.literal("BtrBz").withStyle(UiStyles.modLabel())
                        .append(Component.literal(": " + notice.title()).withStyle(UiStyles.label())),
                    List.of(productName, notice.condition().copy(), notice.observation().copy()),
                    bazaarData.productStack(product));
            } else {
                toasts.show(Component.literal("BtrBz: " + notice.title()).withStyle(UiStyles.label()),
                    List.of(productName),
                    bazaarData.productStack(product));
            }
        }

        if (config.alsoSendChatMessage) {
            Notifier.notifyPlayer(Notifier.prefix()
                .append(Component.literal(notice.title() + " for ").withStyle(UiStyles.label()))
                .append(UiStyles.productName(product.formattedName()))
                .append(Component.literal(": ").withStyle(UiStyles.label()))
                .append(notice.condition().copy())
                .append(Component.literal(". ").withStyle(UiStyles.label()))
                .append(notice.observation().copy())
                .append(Component.literal(". ").withStyle(UiStyles.label()))
                .append(link("Bazaar", "/bz " + product.strippedName(), "Open the item in the Bazaar"))
                .append(" ").append(link("Dismiss", "/btrbz alert dismiss " + reached.id(),
                    "Move this event to History and stop its reminders"))
                .append(" ").append(link("Inbox", "/btrbz alert", "Open your pending alerts")));
        }
    }

    public static void notifyOutdated(
        Alert alert,
        AlertCondition.Price condition,
        String durationText,
        BazaarData bazaarData
    ) {
        Component msg = Notifier.prefix()
            .append(Component.literal("Your alert for ").withStyle(UiStyles.label()))
            .append(UiStyles.productName(bazaarData.refreshIndexedProduct(alert.product).formattedName()))
            .append(Component.literal(" at ")
                .withStyle(UiStyles.label()))
            .append(UiStyles.coins(condition.price()))
            .append(Component
                .literal(" has not been reached for " + durationText + ". ")
                .withStyle(UiStyles.label()))
            .append(Component.literal("[Manage alerts]")
                .withStyle(style -> style
                    .withClickEvent(new RunCommand("/btrbz alert"))
                    .withHoverEvent(new ShowText(Component.literal("Open Alerts to edit or delete alerts"))))
                .withStyle(UiStyles.action()));

        Notifier.notifyPlayer(msg);
    }

    private static Component link(String label, String command, String description) {
        return Component.literal("[" + label + "]").withStyle(style -> style
            .withClickEvent(new RunCommand(command))
            .withHoverEvent(new ShowText(Component.literal(description))))
            .withStyle(UiStyles.action());
    }
}
