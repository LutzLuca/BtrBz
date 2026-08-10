package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.widgets.WidgetRuntime;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

public class WidgetCommand {
    public static LiteralArgumentBuilder<FabricClientCommandSource> get(WidgetRuntime runtime) {
        return ClientCommands.literal("widgets").executes(_ -> {
                var client = Minecraft.getInstance();
                client.schedule(() -> client.setScreen(runtime.createManagementScreen(client.screen)));
                return 1;
            });
    }
}
