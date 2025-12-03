package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class TrackedOrderCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> get() {
        return Commands.rootCommand.then(ClientCommandManager
            .literal("orders")
            .then(ClientCommandManager.literal("list").executes(ctx -> {
                var orders = BtrBz.orderManager().getTrackedOrders();

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
                    builder.append(order.format());
                }

                Notifier.notifyPlayer(builder);

                return 1;
            }))

            .then(ClientCommandManager.literal("reset").executes(ctx -> {
                Minecraft.getInstance().execute(() -> {
                    BtrBz.orderManager().resetTrackedOrders();
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Component
                            .literal("Tracked Bazaar orders have been reset.")
                            .withStyle(ChatFormatting.GRAY)));
                });

                return 1;
            })));
    }
}
