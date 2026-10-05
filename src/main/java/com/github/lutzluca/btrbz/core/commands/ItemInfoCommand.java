package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class ItemInfoCommand {
    public static LiteralArgumentBuilder<FabricClientCommandSource> build(BazaarData data, Consumer<String> open) {
        return ClientCommands.literal("info")
            .executes(_ -> {
                open.accept("");
                return 1;
            })
            .then(ClientCommands.argument("product", StringArgumentType.greedyString())
                .suggests((_, builder) -> {
                    data.searchIndexedProducts(builder.getRemaining(), 12)
                        .forEach(product -> builder.suggest(product.productId()));
                    return builder.buildFuture();
                })
                .executes(context -> {
                    open.accept(StringArgumentType.getString(context, "product"));
                    return 1;
                }));
    }
}
