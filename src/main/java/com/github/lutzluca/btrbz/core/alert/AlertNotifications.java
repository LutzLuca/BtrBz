package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ToastNotifications;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;

public final class AlertNotifications {
    private AlertNotifications() {}

    public static void notifyReached(ReachedAlert reached, BazaarData bazaarData, ToastNotifications toasts) {
        if (!BtrBz.isActive() || Minecraft.getInstance().player == null) {
            return;
        }

        var alert = reached.alert();
        var product = bazaarData.refreshIndexedProduct(alert.product);
        var notice = AlertNotice.from(reached, product);
        var config = ConfigStore.get().config().alert;

        if (config.toastOnAlert) {
            Component productName = GameUtils.legacyFormattedComponent(notice.formattedProductName());
            if (config.detailedAlertToasts) {
                toasts.show(
                    Component.literal("BtrBz").withStyle(UiStyles.modLabel())
                        .append(Component.literal(": " + notice.title()).withStyle(UiStyles.label())),
                    List.of(productName, notice.condition().copy(), notice.observation().copy()),
                    bazaarData.productStack(product));
            } else {
                toasts.show(Component.empty().append(productName)
                    .append(Component.literal(": " + notice.title()).withStyle(UiStyles.label())));
            }
        }

        if (config.alsoSendChatMessage) {
            Notifier.notifyPlayer(Notifier.prefix()
                .append(Component.literal(notice.title() + " for ").withStyle(UiStyles.label()))
                .append(Component.literal(product.formattedName()))
                .append(Component.literal(": ").withStyle(UiStyles.label()))
                .append(notice.condition().copy())
                .append(Component.literal(". ").withStyle(UiStyles.label()))
                .append(notice.observation().copy())
                .append(Component.literal(". ").withStyle(UiStyles.label()))
                .append(Component.literal("[Click to view]")
                    .withStyle(style -> style
                        .withClickEvent(new RunCommand("/bz " + product.strippedName()))
                        .withHoverEvent(new ShowText(Component.literal("Open in the Bazaar"))))
                    .withStyle(UiStyles.action())));
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
            .append(Component.literal(bazaarData.refreshIndexedProduct(alert.product).formattedName()))
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
}
