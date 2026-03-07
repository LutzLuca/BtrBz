package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.OrderManagerConfig.Action;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.StatusUpdate;
import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.ResolvedAlertArgs;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Matched;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Top;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Undercut;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

@Slf4j
public class Notifier {

    public static boolean notifyPlayer(Component msg) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            client.player.displayClientMessage(msg, false);
            return true;
        }
        log.info("Failed to send message '{}' to player (client or player null)", msg.getString());
        return false;
    }

    public static void notifyAlertRegistered(ResolvedAlertArgs cmd) {
        var msg = prefix()
            .append(Component.literal("Alert registered. ").withStyle(ChatFormatting.GREEN))
            .append(Component.literal("You will be informed once the ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(cmd.type().format()).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" price of ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(cmd.productName()).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" reaches ").withStyle(ChatFormatting.GRAY))
            .append(Component
                .literal(Utils.formatDecimal(cmd.price(), 1, true) + " coins")
                .withStyle(ChatFormatting.YELLOW));

        notifyPlayer(msg);
    }

    public static void notifyPriceReached(Alert alert, Optional<Double> price) {
        SoundUtil.playSoundIf(ConfigManager.get().alert.soundOnAlert, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 2);
        
        String priceText = price
            .map(p -> Utils.formatDecimal(p, 1, true) + " coins. ")
            .orElse("currently has no listed price. ");

        Component msg = prefix()
            .append(Component.literal("Your alert for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(alert.productName).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
            .append(Component
                .literal(Utils.formatDecimal(alert.price, 1, true) + "coins")
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" (" + alert.type.format() + ") ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("has been reached").withStyle(ChatFormatting.GREEN))
            .append(Component.literal(" and is ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(priceText).withStyle(ChatFormatting.GOLD))
            .append(Component
                .literal("[Click to view]")
                .withStyle(style -> style
                    .withClickEvent(new RunCommand("/bz " + alert.productName))
                    .withHoverEvent(new ShowText(Component.literal("Click to go to " + alert.productName + " in the bazaar"))))
                .withStyle(ChatFormatting.RED));

        notifyPlayer(msg);
    }

    public static void notifyAlertAlreadyPresent(ResolvedAlertArgs args) {
        Component msg = prefix()
            .append(Component.literal("You already have an alert for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(args.productName()).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
            .append(Component
                .literal(Utils.formatDecimal(args.price(), 1, true))
                .withStyle(ChatFormatting.YELLOW))
            .append(Component
                .literal(" (" + args.type().name().toLowerCase() + ")")
                .withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(". Use ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("/btrbz alert list").withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" to view them").withStyle(ChatFormatting.GRAY));

        notifyPlayer(msg);
    }


    public static void notifyInvalidProduct(Alert alert) {
        Component msg = prefix()
            .append(Component.literal("The price of ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(alert.productName).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" could not be determined. ").withStyle(ChatFormatting.GRAY))
            .append(clickToRemoveAlert(alert.id, "Click to remove this alert"));
        notifyPlayer(msg);
    }

    public static void notifyOutdatedAlert(Alert alert, String durationText) {
        Component msg = prefix()
            .append(Component.literal("Your alert for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(alert.productName).withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(Utils.formatDecimal(alert.price, 1, true)).withStyle(ChatFormatting.YELLOW))
            .append(Component
                .literal(" has not been reached for " + durationText + ". ")
                .withStyle(ChatFormatting.GRAY))
            .append(clickToRemoveAlert(alert.id, "Click to remove alert"));
        notifyPlayer(msg);
    }

    public static Component clickToRemoveAlert(UUID id, String hoverText) {
        return Component
            .literal("[Click to remove]")
            .withStyle(style -> style
                .withClickEvent(new RunCommand("/btrbz alert remove " + id))
                .withHoverEvent(new ShowText(Component.literal(hoverText))))
            .withStyle(ChatFormatting.RED);
    }

    public static void notifyChatCommand(String displayText, String cmd) {
        MutableComponent msg = Component
            .literal(displayText)
            .withStyle(style -> style
                .withClickEvent(new RunCommand("/" + cmd))
                .withHoverEvent(new ShowText(Component.literal("Run /" + cmd))));
        notifyPlayer(prefix().append(msg.withStyle(ChatFormatting.WHITE)));
    }
    
    /**
     * Assumes the parent notification condition (e.g. notifyBest) is already met by the caller.
     * Only checks the associated sound toggles before playing.
     */
    public static void notifyOrderStatus(StatusUpdate update) {
        var order = update.trackedOrder();
        var status = update.curr();

        var cfg = ConfigManager.get().trackedOrders;

        var msg = switch (status) {
            case Top ignored -> {
                SoundUtil.playSoundIf(cfg.soundBest, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);
                if (update.prev() instanceof OrderStatus.Unknown) {
                    yield bestMsg(order);
                }
                yield reclaimBestMsg(order);
            }
            case Matched ignored -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);
                yield matchedMsg(order, update.prev());
            }
            case Undercut undercut -> {
                SoundUtil.playSoundIf(cfg.soundUndercut, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 2);
                yield undercutMsg(order, undercut.amount);
            }
            default -> throw new IllegalArgumentException("Unreachable curr: " + status);
        };

        if (status instanceof Matched && cfg.gotoOnMatched != Action.None) {
            applyGotoAction(msg, cfg.gotoOnMatched, order);
        }

        if (status instanceof Undercut && cfg.gotoOnUndercut != Action.None) {
            applyGotoAction(msg, cfg.gotoOnUndercut, order);
        }

        notifyPlayer(msg);
    }

    private static void applyGotoAction(MutableComponent msg, Action action, TrackedOrder order) {
        if (action == Action.Item) {
            msg.append(Component.literal(" [Go To Item]")
                .withStyle(ChatFormatting.DARK_AQUA)
                .withStyle(style -> style
                    .withClickEvent(new RunCommand("/bz " + order.productName))
                    .withHoverEvent(new ShowText(Component
                        .literal("Open ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(order.productName).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" in the Bazaar").withStyle(ChatFormatting.GRAY))))));
            return;
        }

        msg.append(Component.literal(" [Go To Orders]")
            .withStyle(ChatFormatting.DARK_AQUA)
            .withStyle(style -> style
                .withClickEvent(new RunCommand("/managebazaarorders"))
                .withHoverEvent(new ShowText(Component.literal("Opens the Bazaar order screen")))));
    }


    private static MutableComponent bestMsg(TrackedOrder order) {
        var status = Component
            .empty()
            .append(Component.literal("is the ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("BEST Order!").withStyle(ChatFormatting.GREEN));
        return fillBaseMessage(order.type, order.volume, order.productName, status);
    }

    private static MutableComponent reclaimBestMsg(TrackedOrder order) {
        var status = Component
            .empty()
            .append(Component.literal("has ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("REGAINED BEST Order!").withStyle(ChatFormatting.GREEN));
        return fillBaseMessage(order.type, order.volume, order.productName, status);
    }

    private static MutableComponent matchedMsg(TrackedOrder order, OrderStatus prevStatus) {
        var status = Component
            .empty()
            .append(Component.literal("was ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("MATCHED!").withStyle(ChatFormatting.BLUE));

        var msg = fillBaseMessage(order.type, order.volume, order.productName, status);

        // Top -> Matched: the player was already the sole best order, so any new same-price
        // orders arrived after and are behind in the FIFO queue. The API summary only gives
        // aggregate (orders, volume) per price bucket with no intra-bucket ordering, so
        // showing them as "ahead" would be misleading. Skip queue info for this transition.
        var cfg = ConfigManager.get().trackedOrders;
        if (cfg.showQueueInfo && !(prevStatus instanceof OrderStatus.Top)) {
            BtrBz.bazaarData().calculateQueuePosition(
                order.productName, order.type, order.pricePerUnit, true
            ).ifPresent(info -> {
                int displayOrders = Math.max(0, info.ordersAhead - 1);
                int displayItems = Math.max(0, info.itemsAhead - order.volume);

                msg.append(Component.literal(" • queue: ").withStyle(ChatFormatting.GRAY));
                msg.append(GameUtils.buildQueueComponent(displayOrders, displayItems, cfg.queueDisplayMode));
            });
        }

        return msg;
    }

    private static MutableComponent undercutMsg(TrackedOrder order, double undercutAmount) {
        var status = Component
            .empty()
            .append(Component.literal("was ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("UNDERCUT ").withStyle(ChatFormatting.RED))
            .append(Component.literal("by ").withStyle(ChatFormatting.GRAY))
            .append(Component
                .literal(Utils.formatDecimal(undercutAmount, 1, true))
                .withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" coins!").withStyle(ChatFormatting.GRAY));

        var msg = fillBaseMessage(order.type, order.volume, order.productName, status);

        var cfg = ConfigManager.get().trackedOrders;
        if (cfg.showQueueInfo) {
            BtrBz.bazaarData().calculateQueuePosition(
                order.productName, order.type, order.pricePerUnit
            ).ifPresent(info -> {
                msg.append(Component.literal(" • queue: ").withStyle(ChatFormatting.GRAY));
                msg.append(GameUtils.buildQueueComponent(info.ordersAhead, info.itemsAhead, cfg.queueDisplayMode));
            });
        }

        return msg;
    }

    public static MutableComponent prefix() {
        return Component.literal("[BtrBz] ").withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent fillBaseMessage(
        OrderType type,
        int volume,
        String productName,
        Component statusPart
    ) {
        var orderString = switch (type) {
            case Buy -> "Buy order";
            case Sell -> "Sell offer";
        };
        return prefix()
            .append(Component.literal("Your ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(orderString).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.valueOf(volume)).withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal("x ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(productName).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(statusPart);
    }
}
