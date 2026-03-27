package com.github.lutzluca.btrbz.utils;

import java.util.List;
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

import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.OrderProtectionManager.ValidationResult;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.GroupKey;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.GroupStatus;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.OrderManagerConfig;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.OrderManagerConfig.Action;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.StatusUpdate;
import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.ResolvedAlertArgs;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Matched;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Top;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Undercut;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;

@Slf4j
public class Notifier {

    public static boolean notifyPlayer(Component msg) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(msg, false);
            return true;
        }
        log.info("Failed to send message '{}' to player (client or player null)", msg.getString());
        return false;
    }

    public static void notifyOrderStatus(StatusUpdate update, BazaarData bazaarData) {
        var cfg = ConfigManager.get().trackedOrders;
        var order = update.order();
        var status = update.curr();

        MutableComponent msg = switch (status) {
            case Top ignored -> {
                SoundUtil.playSoundIf(cfg.soundBest, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);

                yield update.prev() instanceof OrderStatus.Unknown
                    ? singleMsg(order, cfg, Component.literal("is the ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("BEST Order!").withStyle(ChatFormatting.GREEN)))
                    : singleMsg(order, cfg, Component.literal("has ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("REGAINED BEST Order!").withStyle(ChatFormatting.GREEN)));
            }
            case Matched ignored -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);
                var matchedMsg = singleMsg(order, cfg, Component.literal("was ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("MATCHED!").withStyle(ChatFormatting.BLUE)));

                if (cfg.showQueueInfo && !(update.prev() instanceof OrderStatus.Top)) {
                    bazaarData.calculateQueuePosition(order.productName, order.type, order.pricePerUnit, true)
                        .ifPresent(info -> appendQueueInfo(matchedMsg,
                            Math.max(0, info.ordersAhead - 1),
                            Math.max(0, info.itemsAhead - order.volume),
                            cfg
                        ));
                }
                yield matchedMsg;
            }
            case Undercut undercut -> {
                SoundUtil.playSoundIf(cfg.soundUndercut, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 2);
                var undercutMsg = singleMsg(order, cfg, Component.literal("was ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("UNDERCUT ").withStyle(ChatFormatting.RED))
                    .append(Component.literal("by ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(Utils.formatDecimal(undercut.amount, 1, true) + " coins!").withStyle(ChatFormatting.GOLD)));

                if (cfg.showQueueInfo) {
                    bazaarData.calculateQueuePosition(order.productName, order.type, order.pricePerUnit)
                        .ifPresent(info -> appendQueueInfo(undercutMsg, info.ordersAhead, info.itemsAhead, cfg));
                }
                yield undercutMsg;
            }
            default -> throw new IllegalArgumentException("Unreachable status: " + status);
        };

        if (status instanceof Matched && cfg.gotoOnMatched != Action.None) {
            applyGotoAction(msg, cfg.gotoOnMatched, order.productName);
        }
        if (status instanceof Undercut && cfg.gotoOnUndercut != Action.None) {
            applyGotoAction(msg, cfg.gotoOnUndercut, order.productName);
        }

        notifyPlayer(msg);
    }

    public static void notifyGroupOrderStatus(
        GroupKey key, List<TrackedOrder> allOrders,
        GroupStatus curr, GroupStatus prev,
        BazaarData bazaarData
    ) {
        var cfg = ConfigManager.get().trackedOrders;
        int groupSize = allOrders.size();
        int totalVolume = allOrders.stream().mapToInt(o -> o.volume).sum();

        MutableComponent msg = switch (curr) {
            case GroupStatus.Undercut undercut -> {
                SoundUtil.playSoundIf(cfg.soundUndercut, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 2);
                var undercutMsg = groupMsg(key, groupSize, totalVolume, cfg,
                    Component.literal("were ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("UNDERCUT ").withStyle(ChatFormatting.RED))
                        .append(Component.literal("by ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(Utils.formatDecimal(undercut.amount(), 1, true) + " coins!").withStyle(ChatFormatting.GOLD)));

                if (cfg.showQueueInfo) {
                    bazaarData.calculateQueuePosition(key.productName(), key.type(), key.pricePerUnit())
                        .ifPresent(info -> appendQueueInfo(undercutMsg, info.ordersAhead, info.itemsAhead, cfg));
                }
                
                yield undercutMsg;
            }
            case GroupStatus.Matched ignored -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);
                var matchedMsg = groupMsg(key, groupSize, totalVolume, cfg,
                    Component.literal("were ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("MATCHED!").withStyle(ChatFormatting.BLUE)));

                if (cfg.showQueueInfo) {
                    bazaarData.calculateQueuePosition(key.productName(), key.type(), key.pricePerUnit(), true)
                        .ifPresent(info -> appendQueueInfo(matchedMsg,
                            Math.max(0, info.ordersAhead - groupSize),
                            Math.max(0, info.itemsAhead - totalVolume),
                            cfg
                        ));
                }
                
                yield matchedMsg;
            }
            case GroupStatus.SelfMatched ignored -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);
                
                yield groupMsg(key, groupSize, totalVolume, cfg,
                    Component.literal("were ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal("SELF-MATCHED!").withStyle(ChatFormatting.BLUE)));
            }
        };

        if ((curr instanceof GroupStatus.Matched || curr instanceof GroupStatus.SelfMatched) && cfg.gotoOnMatched != Action.None) {
            applyGotoAction(msg, cfg.gotoOnMatched, key.productName());
        }
        if (curr instanceof GroupStatus.Undercut && cfg.gotoOnUndercut != Action.None) {
            applyGotoAction(msg, cfg.gotoOnUndercut, key.productName());
        }

        notifyPlayer(msg);
    }

    private static MutableComponent singleMsg(TrackedOrder order, OrderManagerConfig cfg, Component statusPart) {
        var orderString = order.type == OrderType.Buy ? "Buy order" : "Sell offer";
        var msg = prefix()
            .append(Component.literal("Your ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(orderString).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(order.volume + "x").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(order.productName).withStyle(ChatFormatting.YELLOW));

        if (cfg.includePricePerUnit) {
            msg.append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Utils.formatDecimal(order.pricePerUnit, 1, true)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" coins").withStyle(ChatFormatting.GRAY));
        }

        return msg.append(Component.literal(" ").withStyle(ChatFormatting.GRAY)).append(statusPart);
    }

    private static MutableComponent groupMsg(GroupKey key, int groupSize, int totalVolume, OrderManagerConfig cfg, Component statusPart) {
        var orderString = key.type() == OrderType.Buy ? "Buy orders" : "Sell offers";
        
        var msg = prefix()
            .append(Component.literal("Your ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(groupSize + "x ").withStyle(ChatFormatting.AQUA))
            .append(Component.literal(orderString).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" for ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(totalVolume + "x total").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(key.productName()).withStyle(ChatFormatting.YELLOW));

        if (cfg.includePricePerUnit) {
            msg.append(Component.literal(" at ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Utils.formatDecimal(key.pricePerUnit(), 1, true)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" coins").withStyle(ChatFormatting.GRAY));
        }

        return msg.append(Component.literal(" ").withStyle(ChatFormatting.GRAY)).append(statusPart);
    }

    private static void appendQueueInfo(MutableComponent msg, int ordersAhead, int itemsAhead, OrderManagerConfig cfg) {
        if (ordersAhead <= 0 && itemsAhead <= 0) {
            return;
        }

        msg.append(Component.literal(" • queue: ").withStyle(ChatFormatting.GRAY))
            .append(GameUtils.buildQueueComponent(ordersAhead, itemsAhead, cfg.queueDisplayMode));
    }

    private static void applyGotoAction(MutableComponent msg, Action action, String productName) {
        if (action == Action.Item) {
            msg.append(Component.literal(" [Go To Item]")
                .withStyle(ChatFormatting.DARK_AQUA)
                .withStyle(style -> style
                    .withClickEvent(new RunCommand("/bz " + productName))
                    .withHoverEvent(new ShowText(Component.empty()
                        .append(Component.literal("Open ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(productName).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" in the Bazaar").withStyle(ChatFormatting.GRAY))))));
            return;
        }
        msg.append(Component.literal(" [Go To Orders]")
            .withStyle(ChatFormatting.DARK_AQUA)
            .withStyle(style -> style
                .withClickEvent(new RunCommand("/managebazaarorders"))
                .withHoverEvent(new ShowText(Component.literal("Opens the Bazaar order screen")))));
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

    public static void sendBlockedOrderMessage(ValidationResult validation) {
        var reason = validation.reason() == null
            ? "Order blocked."
            : "Order blocked: " + validation.reason();

        var msg = Component
            .literal(reason)
            .withStyle(ChatFormatting.RED)
            .append(Component.literal(" Hold Ctrl to override.").withStyle(ChatFormatting.GRAY));

        notifyPlayer(msg);
    }

    public static MutableComponent prefix() {
        return Component.literal("[BtrBz] ").withStyle(ChatFormatting.GOLD);
    }
}
