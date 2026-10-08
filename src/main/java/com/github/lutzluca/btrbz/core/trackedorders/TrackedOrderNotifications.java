package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.trackedorders.OrderManagerConfig.Action;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Matched;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Top;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Undercut;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.SoundUtil;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.List;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;

final class TrackedOrderNotifications {

    private TrackedOrderNotifications() {}

    static void notifyOrderStatus(StatusUpdate update, BazaarData bazaarData, OrderManagerConfig cfg) {
        var order = update.order();
        var status = update.curr();

        MutableComponent msg = switch (status) {
            case Top _ -> {
                SoundUtil.playSoundIf(cfg.soundBest, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);

                yield update.prev() instanceof OrderStatus.Unknown
                    ? singleMsg(order, cfg, bazaarData, Component.literal("is the ").withStyle(UiStyles.label())
                        .append(
                            Component.literal("BEST Order!").withStyle(UiStyles.color(UiStyles.palette().success()))))
                    : singleMsg(order, cfg, bazaarData, Component.literal("has ").withStyle(UiStyles.label())
                        .append(Component.literal("REGAINED BEST Order!")
                            .withStyle(UiStyles.color(UiStyles.palette().success()))));
            }
            case Matched _ -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);
                var matchedMsg = singleMsg(order, cfg, bazaarData,
                    Component.literal("was ").withStyle(UiStyles.label())
                        .append(Component.literal("MATCHED!").withStyle(UiStyles.color(UiStyles.palette().matched()))));

                if (cfg.showQueueInfo && !(update.prev() instanceof OrderStatus.Top)) {
                    bazaarData
                        .calculateQueuePosition(order.product, order.type, order.pricePerUnit, true)
                        .ifPresent(info -> appendQueueInfo(matchedMsg,
                            Math.max(0, info.ordersAhead - 1),
                            Math.max(0, info.itemsAhead - order.volume),
                            cfg));
                }
                yield matchedMsg;
            }
            case Undercut undercut -> {
                SoundUtil.playSoundIf(cfg.soundUndercut, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 2);
                var undercutMsg = singleMsg(order, cfg, bazaarData,
                    Component.literal("was ").withStyle(UiStyles.label())
                        .append(Component.literal("UNDERCUT ").withStyle(UiStyles.color(UiStyles.palette().error())))
                        .append(Component.literal("by ").withStyle(UiStyles.label()))
                        .append(Component.literal(Utils.formatDecimal(undercut.amount, 1, true) + " coins!")
                            .withStyle(UiStyles.money())));

                if (cfg.showQueueInfo) {
                    bazaarData
                        .calculateQueuePosition(order.product, order.type, order.pricePerUnit)
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

        Notifier.notifyPlayer(msg);
    }

    static void notifyGroupOrderStatus(
        GroupKey key,
        List<TrackedOrder> allOrders,
        GroupStatus curr,
        GroupStatus prev,
        BazaarData bazaarData,
        OrderManagerConfig cfg
    ) {
        int groupSize = allOrders.size();
        int totalVolume = allOrders.stream().mapToInt(o -> o.volume).sum();

        MutableComponent msg = switch (curr) {
            case GroupStatus.Undercut undercut -> {
                SoundUtil.playSoundIf(cfg.soundUndercut, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 2);
                var undercutMsg = groupMsg(key, groupSize, totalVolume, cfg, bazaarData,
                    Component.literal("were ").withStyle(UiStyles.label())
                        .append(Component.literal("UNDERCUT ").withStyle(UiStyles.color(UiStyles.palette().error())))
                        .append(Component.literal("by ").withStyle(UiStyles.label()))
                        .append(Component.literal(Utils.formatDecimal(undercut.amount(), 1, true) + " coins!")
                            .withStyle(UiStyles.money())));

                if (cfg.showQueueInfo) {
                    bazaarData
                        .calculateQueuePosition(key.product(), key.type(), key.pricePerUnit())
                        .ifPresent(info -> appendQueueInfo(undercutMsg, info.ordersAhead, info.itemsAhead, cfg));
                }

                yield undercutMsg;
            }
            case GroupStatus.Matched _ -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);
                var matchedMsg = groupMsg(key, groupSize, totalVolume, cfg, bazaarData,
                    Component.literal("were ").withStyle(UiStyles.label())
                        .append(Component.literal("MATCHED!").withStyle(UiStyles.color(UiStyles.palette().matched()))));

                if (cfg.showQueueInfo) {
                    bazaarData
                        .calculateQueuePosition(key.product(), key.type(), key.pricePerUnit(), true)
                        .ifPresent(info -> appendQueueInfo(matchedMsg,
                            Math.max(0, info.ordersAhead - groupSize),
                            Math.max(0, info.itemsAhead - totalVolume),
                            cfg));
                }

                yield matchedMsg;
            }
            case GroupStatus.SelfMatched selfMatched -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);

                yield groupMsg(key, selfMatched.orderCount(), totalVolume, cfg, bazaarData,
                    Component.literal("were ").withStyle(UiStyles.label())
                        .append(Component.literal("SELF-MATCHED!")
                            .withStyle(UiStyles.color(UiStyles.palette().matched()))));
            }
        };

        if ((curr instanceof GroupStatus.Matched || curr instanceof GroupStatus.SelfMatched)
            && cfg.gotoOnMatched != Action.None) {
            applyGotoAction(msg, cfg.gotoOnMatched, key.productName());
        }
        if (curr instanceof GroupStatus.Undercut && cfg.gotoOnUndercut != Action.None) {
            applyGotoAction(msg, cfg.gotoOnUndercut, key.productName());
        }

        Notifier.notifyPlayer(msg);
    }

    private static MutableComponent singleMsg(
        TrackedOrder order,
        OrderManagerConfig cfg,
        BazaarData bazaarData,
        Component statusPart
    ) {
        var msg = Notifier.prefix()
            .append(Component.literal("Your ").withStyle(UiStyles.label()))
            .append(orderTypeComponent(order.type, false))
            .append(Component.literal(" for ").withStyle(UiStyles.label()))
            .append(quantityComponent(order.volume))
            .append(Component.literal(" ").withStyle(UiStyles.label()))
            .append(productNameComponent(order.product, bazaarData));

        if (cfg.includePricePerUnit) {
            msg.append(Component.literal(" at ").withStyle(UiStyles.label()))
                .append(UiStyles.coins(order.pricePerUnit));
        }

        return msg.append(Component.literal(" ").withStyle(UiStyles.label())).append(statusPart);
    }

    private static MutableComponent groupMsg(
        GroupKey key,
        int groupSize,
        int totalVolume,
        OrderManagerConfig cfg,
        BazaarData bazaarData,
        Component statusPart
    ) {
        var msg = Notifier.prefix()
            .append(Component.literal("Your ").withStyle(UiStyles.label()))
            .append(quantityComponent(groupSize))
            .append(Component.literal(" ").withStyle(UiStyles.label()))
            .append(orderTypeComponent(key.type(), true))
            .append(Component.literal(" for ").withStyle(UiStyles.label()))
            .append(quantityComponent(totalVolume))
            .append(Component.literal(" total").withStyle(UiStyles.label()))
            .append(Component.literal(" ").withStyle(UiStyles.label()))
            .append(productNameComponent(key.product(), bazaarData));

        if (cfg.includePricePerUnit) {
            msg.append(Component.literal(" at ").withStyle(UiStyles.label()))
                .append(UiStyles.coins(key.pricePerUnit()));
        }

        return msg.append(Component.literal(" ").withStyle(UiStyles.label())).append(statusPart);
    }

    private static void appendQueueInfo(MutableComponent msg, int ordersAhead, int itemsAhead, OrderManagerConfig cfg) {
        if (ordersAhead <= 0 && itemsAhead <= 0) {
            return;
        }

        msg.append(Component.literal(" • queue: ").withStyle(UiStyles.label()))
            .append(GameUtils.buildQueueComponent(ordersAhead, itemsAhead, cfg.queueDisplayMode));
    }

    private static void applyGotoAction(MutableComponent msg, Action action, String productName) {
        if (action == Action.Item) {
            msg.append(Component.literal(" [Go To Item]")
                .withStyle(UiStyles.action())
                .withStyle(style -> style
                    .withClickEvent(new RunCommand("/bz " + productName))
                    .withHoverEvent(new ShowText(Component.empty()
                        .append(Component.literal("Open ").withStyle(UiStyles.label()))
                        .append(Component.literal(productName).withStyle(UiStyles.primary()))
                        .append(Component.literal(" in the Bazaar").withStyle(UiStyles.label()))))));
            return;
        }
        msg.append(Component.literal(" [Go To Orders]")
            .withStyle(UiStyles.action())
            .withStyle(style -> style
                .withClickEvent(new RunCommand("/managebazaarorders"))
                .withHoverEvent(new ShowText(Component.literal("Opens the Bazaar order screen")))));
    }

    static void notifySelfUndercut(
        SelfUndercutKey key,
        double bestPrice,
        double secondBestPrice,
        BazaarData bazaarData
    ) {
        var msg = Notifier.prefix()
            .append(Component.literal("Your ").withStyle(UiStyles.label()))
            .append(orderTypeComponent(key.type(), false))
            .append(Component.literal(" for ").withStyle(UiStyles.label()))
            .append(productNameComponent(key.product(), bazaarData))
            .append(Component.literal(" was ").withStyle(UiStyles.label()))
            .append(Component.literal("SELF-UNDERCUT").withStyle(UiStyles.color(UiStyles.palette().error())))
            .append(Component.literal(" from ").withStyle(UiStyles.label()))
            .append(UiStyles.coins(bestPrice))
            .append(Component.literal(" to ").withStyle(UiStyles.label()))
            .append(UiStyles.coins(secondBestPrice));

        Notifier.notifyPlayer(msg);
    }

    private static MutableComponent productNameComponent(
        ProductIdentity product,
        BazaarData bazaarData
    ) {
        return bazaarData
            .resolveIndexedProduct(product)
            .<MutableComponent>map(ref -> productNameComponent(ref, bazaarData))
            .orElseGet(() -> Component.literal(product.visualName()).withStyle(UiStyles.primary()));
    }

    private static MutableComponent productNameComponent(
        IndexedProduct product,
        BazaarData bazaarData
    ) {
        var refreshed = bazaarData.refreshIndexedProduct(product);
        return Component.literal(refreshed.formattedName());
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
