package com.github.lutzluca.btrbz.core.commands.alert;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class AlertCommand {
    private AlertCommand() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> build(Runnable openScreen) {
        return ClientCommands.literal("alert").executes(_ -> {
            openScreen.run();
            return 1;
        });
    }
}
