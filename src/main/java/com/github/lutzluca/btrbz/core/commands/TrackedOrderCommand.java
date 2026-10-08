package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.orderdisplay.OrderHighlightManager;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.OrderModels.TrackedOrder;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Utils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public class TrackedOrderCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> build(TrackedOrderManager orderManager) {
        return ClientCommands.literal("orders")
            .then(ClientCommands.literal("list").executes(ctx -> {
                var orders = orderManager.getTrackedOrders();

                var builder = Notifier.prefix();
                if (orders.isEmpty()) {
                    builder.append(Component.literal("No tracked orders").withStyle(ChatFormatting.GRAY));
                    Notifier.notifyPlayer(builder);
                    return 1;
                }

                var newline = Component.literal("\n");

                builder = builder
                    .append(Component
                        .literal("Tracked Orders (" + orders.size() + "):")
                        .withStyle(ChatFormatting.GOLD))
                    .append(newline);

                var first = true;
                for (var order : orders) {
                    if (!first) {
                        builder.append(newline);
                    }
                    first = false;
                    builder.append(formatOrder(order));
                }

                Notifier.notifyPlayer(builder);

                return 1;
            }))

            .then(ClientCommands.literal("reset").executes(ctx -> {
                Minecraft.getInstance().execute(() -> {
                    orderManager.resetTrackedOrders();
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Component
                            .literal("Tracked Bazaar orders have been reset.")
                            .withStyle(ChatFormatting.GRAY)));
                });

                return 1;
            }));
    }

    private static MutableComponent formatOrder(TrackedOrder order) {
        var typeStr = switch (order.type) {
            case Buy -> "Buy Order";
            case Sell -> "Sell Offer";
        };
        var visualName = order.product.visualName();
        var productNameComponent = Component.literal(visualName);

        return Component
            .empty()
            .append(Component
                .literal("[" + order.status.toString() + "] ")
                .withStyle(style -> Style.EMPTY.withColor(OrderHighlightManager.colorForStatus(order.status))))
            .append(Component.literal(typeStr).withStyle(
                UiStyles.color(order.type == OrderType.Buy ? UiStyles.palette().buy() : UiStyles.palette().sell())))
            .append(Component.literal(" for ").withStyle(UiStyles.label()))
            .append(Component.literal(order.volume + "x ").withStyle(UiStyles.quantity()))
            .append(productNameComponent)
            .append(Component.literal(" at ").withStyle(UiStyles.label()))
            .append(Component
                .literal(Utils.formatDecimal(order.pricePerUnit, 1, true) + " coins")
                .withStyle(UiStyles.money()));
    }
}
