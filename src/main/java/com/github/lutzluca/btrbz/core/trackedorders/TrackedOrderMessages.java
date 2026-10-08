package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.core.trackedorders.OrderManagerConfig.Action;
import com.github.lutzluca.btrbz.core.trackedorders.OrderManagerConfig.QueueDisplayMode;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Utils;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

/** Message formatting shared by live notifications and passive settings examples. */
final class TrackedOrderMessages {

    private TrackedOrderMessages() {}

    static Component topStatus(boolean regained) {
        return regained
            ? Component.literal("has ").withStyle(UiStyles.label())
                .append(Component.literal("REGAINED BEST Order!")
                    .withStyle(UiStyles.color(UiStyles.palette().success())))
            : Component.literal("is the ").withStyle(UiStyles.label())
                .append(Component.literal("BEST Order!").withStyle(UiStyles.color(UiStyles.palette().success())));
    }

    static Component matchedStatus(boolean plural) {
        return Component.literal(plural ? "were " : "was ").withStyle(UiStyles.label())
            .append(Component.literal("MATCHED!").withStyle(UiStyles.color(UiStyles.palette().matched())));
    }

    static Component undercutStatus(double amount, boolean plural) {
        return Component.literal(plural ? "were " : "was ").withStyle(UiStyles.label())
            .append(Component.literal("UNDERCUT ").withStyle(UiStyles.color(UiStyles.palette().error())))
            .append(Component.literal("by ").withStyle(UiStyles.label()))
            .append(Component.literal(Utils.formatDecimal(amount, 1, true) + " coins!").withStyle(UiStyles.money()));
    }

    static Component selfMatchedStatus() {
        return Component.literal("were ").withStyle(UiStyles.label())
            .append(Component.literal("SELF-MATCHED!").withStyle(UiStyles.color(UiStyles.palette().matched())));
    }

    static MutableComponent single(
        OrderType type,
        int volume,
        Component productName,
        @Nullable Double pricePerUnit,
        Component statusPart
    ) {
        var msg = Notifier.prefix()
            .append(Component.literal("Your ").withStyle(UiStyles.label()))
            .append(orderTypeComponent(type, false))
            .append(Component.literal(" for ").withStyle(UiStyles.label()))
            .append(quantityComponent(volume))
            .append(Component.literal(" ").withStyle(UiStyles.label()))
            .append(productName);

        if (pricePerUnit != null) {
            msg.append(Component.literal(" at ").withStyle(UiStyles.label()))
                .append(UiStyles.coins(pricePerUnit));
        }

        return msg.append(Component.literal(" ").withStyle(UiStyles.label())).append(statusPart);
    }

    static MutableComponent group(
        OrderType type,
        int groupSize,
        int totalVolume,
        Component productName,
        @Nullable Double pricePerUnit,
        Component statusPart
    ) {
        var msg = Notifier.prefix()
            .append(Component.literal("Your ").withStyle(UiStyles.label()))
            .append(quantityComponent(groupSize))
            .append(Component.literal(" ").withStyle(UiStyles.label()))
            .append(orderTypeComponent(type, true))
            .append(Component.literal(" for ").withStyle(UiStyles.label()))
            .append(quantityComponent(totalVolume))
            .append(Component.literal(" total").withStyle(UiStyles.label()))
            .append(Component.literal(" ").withStyle(UiStyles.label()))
            .append(productName);

        if (pricePerUnit != null) {
            msg.append(Component.literal(" at ").withStyle(UiStyles.label()))
                .append(UiStyles.coins(pricePerUnit));
        }

        return msg.append(Component.literal(" ").withStyle(UiStyles.label())).append(statusPart);
    }

    static void appendQueueInfo(MutableComponent msg, int ordersAhead, int itemsAhead, QueueDisplayMode mode) {
        if (ordersAhead <= 0 && itemsAhead <= 0) {
            return;
        }

        msg.append(Component.literal(" • queue: ").withStyle(UiStyles.label()))
            .append(GameUtils.buildQueueComponent(ordersAhead, itemsAhead, mode));
    }

    static MutableComponent navigationLabel(Action action) {
        return Component.literal(action == Action.Item ? "[Go To Item]" : "[Go To Orders]")
            .withStyle(UiStyles.action());
    }

    static MutableComponent navigationAction(Action action) {
        return Component.literal(" ").withStyle(UiStyles.action()).append(navigationLabel(action));
    }

    static void appendGotoAction(MutableComponent msg, Action action, String productName) {
        var link = navigationAction(action);
        if (action == Action.Item) {
            link.withStyle(style -> style
                .withClickEvent(new RunCommand("/bz " + productName))
                .withHoverEvent(new ShowText(Component.empty()
                    .append(Component.literal("Open ").withStyle(UiStyles.label()))
                    .append(Component.literal(productName).withStyle(UiStyles.primary()))
                    .append(Component.literal(" in the Bazaar").withStyle(UiStyles.label())))));
        } else {
            link.withStyle(style -> style
                .withClickEvent(new RunCommand("/managebazaarorders"))
                .withHoverEvent(new ShowText(Component.literal("Opens the Bazaar order screen"))));
        }
        msg.append(link);
    }

    static MutableComponent selfUndercut(
        OrderType type,
        Component productName,
        double bestPrice,
        double secondBestPrice
    ) {
        return Notifier.prefix()
            .append(Component.literal("Your ").withStyle(UiStyles.label()))
            .append(orderTypeComponent(type, false))
            .append(Component.literal(" for ").withStyle(UiStyles.label()))
            .append(productName)
            .append(Component.literal(" was ").withStyle(UiStyles.label()))
            .append(Component.literal("SELF-UNDERCUT").withStyle(UiStyles.color(UiStyles.palette().error())))
            .append(Component.literal(" from ").withStyle(UiStyles.label()))
            .append(UiStyles.coins(bestPrice))
            .append(Component.literal(" to ").withStyle(UiStyles.label()))
            .append(UiStyles.coins(secondBestPrice));
    }

    private static MutableComponent quantityComponent(int count) {
        return Component
            .literal(String.valueOf(count))
            .withStyle(UiStyles.quantity())
            .append(Component.literal("x").withStyle(UiStyles.muted()));
    }

    private static MutableComponent orderTypeComponent(OrderType type, boolean plural) {
        var label = switch (type) {
            case Buy -> plural ? "Buy Orders" : "Buy Order";
            case Sell -> plural ? "Sell Offers" : "Sell Offer";
        };
        return Component.literal(label).withStyle(orderTypeStyle(type));
    }

    private static Style orderTypeStyle(OrderType type) {
        return switch (type) {
            case Buy -> UiStyles.color(UiStyles.palette().buy());
            case Sell -> UiStyles.color(UiStyles.palette().sell());
        };
    }
}
