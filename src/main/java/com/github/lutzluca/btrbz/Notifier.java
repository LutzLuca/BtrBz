package com.github.lutzluca.btrbz;

import com.github.lutzluca.btrbz.BzOrderManager.StatusUpdate;
import com.github.lutzluca.btrbz.TrackedOrder.OrderStatus;
import com.github.lutzluca.btrbz.TrackedOrder.OrderType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Notifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(BtrBz.MOD_ID);

    public static void logDebug(String msg, Object... args) {
        String caller = Utils.getCallingClassName();
        LOGGER.debug(String.format("(%s) %s", caller, msg), args);
    }

    public static void logInfo(String msg, Object... args) {
        String caller = Utils.getCallingClassName();
        LOGGER.info(String.format("(%s) %s", caller, msg), args);
    }

    public static void logWarn(String msg, Object... args) {
        String caller = Utils.getCallingClassName();
        LOGGER.warn(String.format("(%s) %s", caller, msg), args);
    }

    public static void logError(String msg, Object... args) {
        String caller = Utils.getCallingClassName();
        LOGGER.error(String.format("(%s) %s", caller, msg), args);
    }

    public static void notifyPlayer(Text msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(msg, false);
        } else {
            Notifier.logWarn("Failed to send message to player, as it was null " + msg.getString());
        }
    }

    public static void notifyChatCommand(String displayText, String cmd) {
        MutableText msg = Text
            .literal(displayText)
            .styled(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/" + cmd))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Run /" + cmd))));
        Notifier.prefix().append(msg);
    }

    public static void notifyError(String message, Throwable e) {
        Text msg = Text.literal(String.format("[%s Error]: %s", BtrBz.MOD_ID, message));
        Notifier.notifyPlayer(msg);
    }

    public static void notifyOrderStatus(StatusUpdate update) {
        var msg = switch (update.status()) {
            case OrderStatus.Top ignored -> Notifier.bestMsg(update.trackedOrder());
            case OrderStatus.Matched ignored -> Notifier.matchedMsg(update.trackedOrder());
            case OrderStatus.Undercut undercut ->
                Notifier.undercutMsg(update.trackedOrder(), undercut.amount);
            default -> {
                throw new Error("unreachable");
            }
        };
        notifyPlayer(msg);
    }

    private static MutableText prefix() {
        return Text.literal("[BtrBz] ").formatted(Formatting.GOLD);
    }

    private static Text fillBaseMessage(
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

    private static Text bestMsg(TrackedOrder order) {
        var status = Text
            .empty()
            .append(Text.literal("is the ").formatted(Formatting.WHITE))
            .append(Text.literal("BEST Order!").formatted(Formatting.GREEN));
        return fillBaseMessage(order.type, order.volume, order.productName, status);
    }

    private static Text matchedMsg(TrackedOrder order) {
        var status = Text
            .empty()
            .append(Text.literal("has been ").formatted(Formatting.WHITE))
            .append(Text.literal("MATCHED!").formatted(Formatting.BLUE));
        return fillBaseMessage(order.type, order.volume, order.productName, status);
    }

    private static Text undercutMsg(TrackedOrder order, double undercutAmount) {
        var status = Text
            .empty()
            .append(Text.literal("has been ").formatted(Formatting.WHITE))
            .append(Text.literal("UNDERCUT ").formatted(Formatting.RED))
            .append(Text.literal("by ").formatted(Formatting.WHITE))
            .append(Text.literal(Utils.formatDecimal(undercutAmount, 1)).formatted(Formatting.GOLD))
            .append(Text.literal(" coins!").formatted(Formatting.WHITE));
        return fillBaseMessage(order.type, order.volume, order.productName, status);
    }
}
