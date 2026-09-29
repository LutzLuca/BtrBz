package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ToastNotifications;
import java.util.List;
import net.minecraft.ChatFormatting;
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
                    Component.literal("BtrBz").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(": " + notice.title()).withStyle(ChatFormatting.GRAY)),
                    List.of(productName, notice.condition().copy(), notice.observation().copy()),
                    bazaarData.productStack(product));
            } else {
                toasts.show(Component.empty().append(productName)
                    .append(Component.literal(": " + notice.title()).withStyle(ChatFormatting.GRAY)));
            }
        }

        if (config.alsoSendChatMessage) {
            Notifier.notifyPlayer(Notifier.prefix()
                .append(Component.literal(notice.title() + " for ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(product.formattedName()))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(notice.condition().copy())
                .append(Component.literal(". ").withStyle(ChatFormatting.GRAY))
                .append(notice.observation().copy())
                .append(Component.literal(". ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[Click to view]")
                    .withStyle(style -> style
                        .withClickEvent(new RunCommand("/bz " + product.strippedName()))
                        .withHoverEvent(new ShowText(Component.literal("Open in the Bazaar"))))
                    .withStyle(ChatFormatting.AQUA)));
        }
    }

    public static void notifyOutdated(
        Alert alert,
        AlertCondition.Price condition,
        String durationText,
        BazaarData bazaarData
    ) {
        Component msg = Notifier.prefix()
            .append(Component.literal("Your alert for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(bazaarData.refreshIndexedProduct(alert.product).formattedName()))
            .append(Component.literal(" at ")
                .withStyle(ChatFormatting.GRAY))
            .append(AlertNotice.coins(condition.price()))
            .append(Component
                .literal(" has not been reached for " + durationText + ". ")
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal("[Manage alerts]")
                .withStyle(style -> style
                    .withClickEvent(new RunCommand("/btrbz alert"))
                    .withHoverEvent(new ShowText(Component.literal("Open Alerts to edit or delete alerts"))))
                .withStyle(ChatFormatting.AQUA));

        Notifier.notifyPlayer(msg);
    }
}
