package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class Commands {

    public static final LiteralArgumentBuilder<FabricClientCommandSource> rootCommand =
            ClientCommandManager.literal("btrbz").executes((ctx) -> {
                ConfigScreen.open();
                return 1;
            });

    public static void registerAll() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

        });
    }
}
