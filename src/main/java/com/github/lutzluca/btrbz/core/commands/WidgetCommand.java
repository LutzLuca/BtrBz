package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.widgets.WidgetRuntime;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

public class WidgetCommand {
    public static LiteralArgumentBuilder<FabricClientCommandSource> get(WidgetRuntime runtime) {
        return Commands.rootCommand.then(ClientCommands
            .literal("widgets")
            .executes(_ -> {
                Minecraft.getInstance().schedule(() -> GameUtils.setScreen(runtime.createManagementScreen(GameUtils.screen())));
                return 1;
            }));
    }
}
