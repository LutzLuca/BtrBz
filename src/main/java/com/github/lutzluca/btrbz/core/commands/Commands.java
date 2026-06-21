package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.commands.alert.AlertCommand;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public class Commands {

    public static final LiteralArgumentBuilder<FabricClientCommandSource> rootCommand = ClientCommands
        .literal("btrbz")
        .executes((ctx) -> {
            ConfigScreen.open();
            return 1;
        });

    public static void registerAll(BazaarData bazaarData) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(rootCommand);
            dispatcher.register(AlertCommand.get(bazaarData));
            dispatcher.register(TrackedOrderCommand.get());
            dispatcher.register(TaxCommand.get());
            dispatcher.register(PresetCommand.get());
        });
    }
}
