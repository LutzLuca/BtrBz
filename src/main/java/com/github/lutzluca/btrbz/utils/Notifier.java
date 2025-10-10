package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.core.BzOrderManager.StatusUpdate;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Matched;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Top;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Undercut;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
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

        log.info("Failed to send message '{}' to player, as it was null", msg.getString());
        return false;
    }

    public static void notifyChatCommand(String displayText, String cmd) {
        MutableText msg = Text
            .literal(displayText)
            .styled(style -> style
                .withClickEvent(new RunCommand("/" + cmd))
                .withHoverEvent(new ShowText(Text.literal("Run /" + cmd))));

        notifyPlayer(Notifier.prefix().append(msg.formatted(Formatting.WHITE)));
    }


    public static void notifyOrderStatus(StatusUpdate update) {
        var msg = switch (update.status()) {
            case Top ignored -> Notifier.bestMsg(update.trackedOrder());
            case Matched ignored -> Notifier.matchedMsg(update.trackedOrder());
            case Undercut undercut -> Notifier.undercutMsg(update.trackedOrder(), undercut.amount);
            default -> throw new IllegalArgumentException("unreachable status: " + update.status());
        };

        notifyPlayer(msg);
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
            .append(Text
                .literal(Util.formatDecimal(undercutAmount, 1, true))
                .formatted(Formatting.GOLD))
            .append(Text.literal(" coins!").formatted(Formatting.WHITE));

        return fillBaseMessage(order.type, order.volume, order.productName, status);
    }

    public static MutableText prefix() {
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
}
