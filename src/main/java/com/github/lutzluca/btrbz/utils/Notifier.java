package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.OrderManagerConfig.Action;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.StatusUpdate;
import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.ResolvedAlertArgs;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Matched;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Top;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Undercut;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent.RunCommand;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Slf4j
public class Notifier {

    public static boolean notifyPlayer(Text msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(msg, false);
            return true;
        }
        log.info("Failed to send message '{}' to player (client or player null)", msg.getString());
        return false;
    }

    public static void notifyAlertRegistered(ResolvedAlertArgs cmd) {
        var msg = prefix()
            .append(Text.literal("Alert registered. ").formatted(Formatting.GREEN))
            .append(Text.literal("You will be informed once the ").formatted(Formatting.GRAY))
            .append(Text.literal(cmd.type().format()).formatted(Formatting.AQUA))
            .append(Text.literal(" price of ").formatted(Formatting.GRAY))
            .append(Text.literal(cmd.productName()).formatted(Formatting.GOLD))
            .append(Text.literal(" reaches ").formatted(Formatting.GRAY))
            .append(Text
                .literal(Util.formatDecimal(cmd.price(), 1, true) + " coins")
                .formatted(Formatting.YELLOW));

        notifyPlayer(msg);
    }

    public static void notifyPriceReached(Alert alert, Optional<Double> price) {
        String priceText = price
            .map(p -> Util.formatDecimal(p, 1, true) + " coins. ")
            .orElse("currently has no listed price. ");

        Text msg = prefix()
            .append(Text.literal("Your alert for ").formatted(Formatting.GRAY))
            .append(Text.literal(alert.productName).formatted(Formatting.GOLD))
            .append(Text.literal(" at ").formatted(Formatting.GRAY))
            .append(Text
                .literal(Util.formatDecimal(alert.price, 1, true) + "coins")
                .formatted(Formatting.YELLOW))
            .append(Text.literal(" (" + alert.type.format() + ") ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("has been reached").formatted(Formatting.GREEN))
            .append(Text.literal(" and is ").formatted(Formatting.GRAY))
            .append(Text.literal(priceText).formatted(Formatting.GOLD))
            .append(Text
                .literal("[Click to view]")
                .styled(style -> style
                    .withClickEvent(new RunCommand("/bz " + alert.productName))
                    .withHoverEvent(new ShowText(Text.literal("Click to go to " + alert.productName + " in the bazaar"))))
                .formatted(Formatting.RED));

        notifyPlayer(msg);
    }

    public static void notifyAlertAlreadyPresent(ResolvedAlertArgs args) {
        Text msg = prefix()
            .append(Text.literal("You already have an alert for ").formatted(Formatting.GRAY))
            .append(Text.literal(args.productName()).formatted(Formatting.GOLD))
            .append(Text.literal(" at ").formatted(Formatting.GRAY))
            .append(Text
                .literal(Util.formatDecimal(args.price(), 1, true))
                .formatted(Formatting.YELLOW))
            .append(Text
                .literal(" (" + args.type().name().toLowerCase() + ")")
                .formatted(Formatting.DARK_GRAY))
            .append(Text.literal(". Use ").formatted(Formatting.GRAY))
            .append(Text.literal("/btrbz alert list").formatted(Formatting.AQUA))
            .append(Text.literal(" to view them").formatted(Formatting.GRAY));

        notifyPlayer(msg);
    }


    public static void notifyInvalidProduct(Alert alert) {
        Text msg = prefix()
            .append(Text.literal("The price of ").formatted(Formatting.GRAY))
            .append(Text.literal(alert.productName).formatted(Formatting.AQUA))
            .append(Text.literal(" could not be determined. ").formatted(Formatting.GRAY))
            .append(clickToRemoveAlert(alert.id, "Click to remove this alert"));
        notifyPlayer(msg);
    }

    public static void notifyOutdatedAlert(Alert alert, String durationText) {
        Text msg = prefix()
            .append(Text.literal("Your alert for ").formatted(Formatting.GRAY))
            .append(Text.literal(alert.productName).formatted(Formatting.GOLD))
            .append(Text.literal(" at ").formatted(Formatting.GRAY))
            .append(Text.literal(String.format("%.1f", alert.price)).formatted(Formatting.YELLOW))
            .append(Text
                .literal(" has not been reached for " + durationText + ". ")
                .formatted(Formatting.GRAY))
            .append(clickToRemoveAlert(alert.id, "Click to remove alert"));
        notifyPlayer(msg);
    }

    public static Text clickToRemoveAlert(UUID id, String hoverText) {
        return Text
            .literal("[Click to remove]")
            .styled(style -> style
                .withClickEvent(new RunCommand("/btrbz alert remove " + id))
                .withHoverEvent(new ShowText(Text.literal(hoverText))))
            .formatted(Formatting.RED);
    }

    public static void notifyChatCommand(String displayText, String cmd) {
        MutableText msg = Text
            .literal(displayText)
            .styled(style -> style
                .withClickEvent(new RunCommand("/" + cmd))
                .withHoverEvent(new ShowText(Text.literal("Run /" + cmd))));
        notifyPlayer(prefix().append(msg.formatted(Formatting.WHITE)));
    }

    public static void notifyOrderStatus(StatusUpdate update) {
        var order = update.trackedOrder();
        var status = update.status();

        var msg = switch (status) {
            case Top ignored -> bestMsg(order);
            case Matched ignored -> matchedMsg(order);
            case Undercut undercut -> undercutMsg(order, undercut.amount);
            default -> throw new IllegalArgumentException("Unreachable status: " + status);
        };

        var cfg = ConfigManager.get().trackedOrders;
        if (status instanceof Matched && cfg.gotoOnMatched != Action.None) {
            msg.append(makeGotoAction(cfg.gotoOnMatched, order));
        }

        if (status instanceof Undercut && cfg.gotoOnUndercut != Action.None) {
            msg.append(makeGotoAction(cfg.gotoOnUndercut, order));
        }

        notifyPlayer(msg);
    }

    private static MutableText makeGotoAction(Action action, TrackedOrder order) {
        var base = (action == Action.Item) ? Text
            .literal(" [Go To Item]")
            .styled(style -> style
                .withClickEvent(new RunCommand("/bz " + order.productName))
                .withHoverEvent(new ShowText(Text
                    .literal("Open ")
                    .formatted(Formatting.GRAY)
                    .append(Text.literal(order.productName).formatted(Formatting.AQUA))
                    .append(Text.literal(" in the Bazaar").formatted(Formatting.GRAY))))) : Text
            .literal(" [Go To Orders]")
            .styled(style -> style
                .withClickEvent(new RunCommand("/managebazaarorders"))
                .withHoverEvent(new ShowText(Text.literal("Opens the Bazaar order screen"))));

        return base.formatted(Formatting.DARK_AQUA);
    }


    private static MutableText bestMsg(TrackedOrder order) {
        var status = Text
            .empty()
            .append(Text.literal("is the ").formatted(Formatting.WHITE))
            .append(Text.literal("BEST Order!").formatted(Formatting.GREEN));
        return fillBaseMessage(order.type, order.volume, order.productName, status);
    }

    private static MutableText matchedMsg(TrackedOrder order) {
        var status = Text
            .empty()
            .append(Text.literal("has been ").formatted(Formatting.WHITE))
            .append(Text.literal("MATCHED!").formatted(Formatting.BLUE));
        return fillBaseMessage(order.type, order.volume, order.productName, status);
    }

    private static MutableText undercutMsg(TrackedOrder order, double undercutAmount) {
        var status = Text
            .empty()
            .append(Text.literal("has been ").formatted(Formatting.WHITE))
            .append(Text.literal("UNDERCUT ").formatted(Formatting.RED))
            .append(Text.literal("by ").formatted(Formatting.WHITE))
            .append(Text
                .literal(Util.formatDecimal(undercutAmount, 1, true))
                .formatted(Formatting.GOLD))
            .append(Text.literal(" coins!").formatted(Formatting.WHITE));
        return fillBaseMessage(order.type, order.volume, order.productName, status);
    }

    public static MutableText prefix() {
        return Text.literal("[BtrBz] ").formatted(Formatting.GOLD);
    }

    private static MutableText fillBaseMessage(
        OrderType type,
        int volume,
        String productName,
        Text statusPart
    ) {
        var orderString = switch (type) {
            case Buy -> "Buy order";
            case Sell -> "Sell offer";
        };
        return prefix()
            .append(Text.literal("Your ").formatted(Formatting.WHITE))
            .append(Text.literal(orderString).formatted(Formatting.AQUA))
            .append(Text.literal(" for ").formatted(Formatting.WHITE))
            .append(Text.literal(String.valueOf(volume)).formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("x ").formatted(Formatting.WHITE))
            .append(Text.literal(productName).formatted(Formatting.YELLOW))
            .append(Text.literal(" ").formatted(Formatting.WHITE))
            .append(statusPart);
    }
}
