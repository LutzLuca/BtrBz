package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TrackedOrderCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> get() {
        return Commands.rootCommand.then(ClientCommandManager
            .literal("orders")
            .then(ClientCommandManager.literal("list").executes(ctx -> {
                var orders = BtrBz.orderManager().getTrackedOrders();

                var builder = Notifier.prefix();
                if (orders.isEmpty()) {
                    builder.append(Text.literal("No tracked orders").formatted(Formatting.GRAY));
                    Notifier.notifyPlayer(builder);
                    return 1;
                }

                var first = true;
                for (var order : orders) {
                    if (!first) {
                        builder.append(Text.literal("\n"));
                    }
                    first = false;
                    builder.append(order.format());
                }

                Notifier.notifyPlayer(builder);

                return 1;
            }))
            .then(ClientCommandManager.literal("reset").executes(ctx -> {
                MinecraftClient.getInstance().execute(() -> {
                    BtrBz.orderManager().resetTrackedOrders();
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Text
                            .literal("Tracked Bazaar orders have been reset.")
                            .formatted(Formatting.GRAY)));
                });

                return 1;
            })));
    }
}
