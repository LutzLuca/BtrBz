package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.widgets.WidgetRuntime;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

public class WidgetCommand {


    //? if <26.2 {
    public static LiteralArgumentBuilder<FabricClientCommandSource> get(WidgetRuntime runtime) {
        return Commands.rootCommand.then(ClientCommands
            .literal("widgets")
            .executes(_ -> {
                var client = Minecraft.getInstance();
                client.schedule(() -> client.setScreen(runtime.createManagementScreen(client.screen)));
                return 1;
            }));
    }
    //?} else {
    /*public static LiteralArgumentBuilder<FabricClientCommandSource> get(WidgetRuntime runtime) {
        return Commands.rootCommand.then(ClientCommands
            .literal("widgets")
            .executes(_ -> {
                var client = Minecraft.getInstance();
                client.schedule(() -> client.gui.setScreen(runtime.createManagementScreen(client.gui.screen())));
                return 1;
            }));
    }
    *///?}
}
