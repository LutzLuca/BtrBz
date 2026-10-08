package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.trackedorders.OrderManagerConfig.Action;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Matched;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Top;
import com.github.lutzluca.btrbz.data.OrderModels.OrderStatus.Undercut;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.SoundUtil;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

final class TrackedOrderNotifications {

    private TrackedOrderNotifications() {}

    static void notifyOrderStatus(StatusUpdate update, BazaarData bazaarData, OrderManagerConfig cfg) {
        var order = update.order();
        var status = update.curr();
        var productName = productNameComponent(order.product, bazaarData);
        Double pricePerUnit = cfg.includePricePerUnit ? order.pricePerUnit : null;

        MutableComponent msg = switch (status) {
            case Top _ -> {
                SoundUtil.playSoundIf(cfg.soundBest, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);

                yield TrackedOrderMessages.single(order.type, order.volume, productName, pricePerUnit,
                    TrackedOrderMessages.topStatus(!(update.prev() instanceof OrderStatus.Unknown)));
            }
            case Matched _ -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);
                var matchedMsg = TrackedOrderMessages.single(order.type, order.volume, productName, pricePerUnit,
                    TrackedOrderMessages.matchedStatus(false));

                if (cfg.showQueueInfo && !(update.prev() instanceof OrderStatus.Top)) {
                    bazaarData
                        .calculateQueuePosition(order.product, order.type, order.pricePerUnit, true)
                        .ifPresent(info -> TrackedOrderMessages.appendQueueInfo(matchedMsg,
                            Math.max(0, info.ordersAhead - 1),
                            Math.max(0, info.itemsAhead - order.volume),
                            cfg.queueDisplayMode));
                }
                yield matchedMsg;
            }
            case Undercut undercut -> {
                SoundUtil.playSoundIf(cfg.soundUndercut, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 2);
                var undercutMsg = TrackedOrderMessages.single(order.type, order.volume, productName, pricePerUnit,
                    TrackedOrderMessages.undercutStatus(undercut.amount, false));

                if (cfg.showQueueInfo) {
                    bazaarData
                        .calculateQueuePosition(order.product, order.type, order.pricePerUnit)
                        .ifPresent(info -> TrackedOrderMessages.appendQueueInfo(undercutMsg, info.ordersAhead,
                            info.itemsAhead, cfg.queueDisplayMode));
                }
                yield undercutMsg;
            }
            default -> throw new IllegalArgumentException("Unreachable status: " + status);
        };

        if (status instanceof Matched && cfg.gotoOnMatched != Action.None) {
            TrackedOrderMessages.appendGotoAction(msg, cfg.gotoOnMatched, order.productName);
        }
        if (status instanceof Undercut && cfg.gotoOnUndercut != Action.None) {
            TrackedOrderMessages.appendGotoAction(msg, cfg.gotoOnUndercut, order.productName);
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
        var productName = productNameComponent(key.product(), bazaarData);
        Double pricePerUnit = cfg.includePricePerUnit ? key.pricePerUnit() : null;

        MutableComponent msg = switch (curr) {
            case GroupStatus.Undercut undercut -> {
                SoundUtil.playSoundIf(cfg.soundUndercut, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 2);
                var undercutMsg = TrackedOrderMessages.group(key.type(), groupSize, totalVolume, productName,
                    pricePerUnit, TrackedOrderMessages.undercutStatus(undercut.amount(), true));

                if (cfg.showQueueInfo) {
                    bazaarData
                        .calculateQueuePosition(key.product(), key.type(), key.pricePerUnit())
                        .ifPresent(info -> TrackedOrderMessages.appendQueueInfo(undercutMsg, info.ordersAhead,
                            info.itemsAhead, cfg.queueDisplayMode));
                }

                yield undercutMsg;
            }
            case GroupStatus.Matched _ -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);
                var matchedMsg = TrackedOrderMessages.group(key.type(), groupSize, totalVolume, productName,
                    pricePerUnit, TrackedOrderMessages.matchedStatus(true));

                if (cfg.showQueueInfo) {
                    bazaarData
                        .calculateQueuePosition(key.product(), key.type(), key.pricePerUnit(), true)
                        .ifPresent(info -> TrackedOrderMessages.appendQueueInfo(matchedMsg,
                            Math.max(0, info.ordersAhead - groupSize),
                            Math.max(0, info.itemsAhead - totalVolume),
                            cfg.queueDisplayMode));
                }

                yield matchedMsg;
            }
            case GroupStatus.SelfMatched selfMatched -> {
                SoundUtil.playSoundIf(cfg.soundMatched, SoundEvents.NOTE_BLOCK_CHIME, 0.5f, 1);

                yield TrackedOrderMessages.group(key.type(), selfMatched.orderCount(), totalVolume, productName,
                    pricePerUnit, TrackedOrderMessages.selfMatchedStatus());
            }
        };

        if ((curr instanceof GroupStatus.Matched || curr instanceof GroupStatus.SelfMatched)
            && cfg.gotoOnMatched != Action.None) {
            TrackedOrderMessages.appendGotoAction(msg, cfg.gotoOnMatched, key.productName());
        }
        if (curr instanceof GroupStatus.Undercut && cfg.gotoOnUndercut != Action.None) {
            TrackedOrderMessages.appendGotoAction(msg, cfg.gotoOnUndercut, key.productName());
        }

        Notifier.notifyPlayer(msg);
    }

    static void notifySelfUndercut(
        SelfUndercutKey key,
        double bestPrice,
        double secondBestPrice,
        BazaarData bazaarData
    ) {
        var msg = TrackedOrderMessages.selfUndercut(key.type(), productNameComponent(key.product(), bazaarData),
            bestPrice, secondBestPrice);

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
}
