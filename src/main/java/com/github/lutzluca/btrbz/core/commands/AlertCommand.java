package com.github.lutzluca.btrbz.core.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.github.lutzluca.btrbz.core.alert.AlertManager;
import io.vavr.control.Try;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class AlertCommand {
    private AlertCommand() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> build(
        Runnable openScreen,
        AlertManager manager
    ) {
        return ClientCommands.literal("alert").executes(_ -> {
            openScreen.run();
            return 1;
        }).then(ClientCommands.literal("dismiss").then(ClientCommands.argument("event", StringArgumentType.word())
            .executes(context -> {
                var result = Try.of(() -> UUID.fromString(StringArgumentType.getString(context, "event")))
                    .map(manager::dismiss);
                context.getSource().sendFeedback(Component.literal(result.isFailure()
                    ? "Could not dismiss this alert."
                    : result.get() ? "Alert dismissed." : "This alert has already been dismissed."));
                return result.isSuccess() ? 1 : 0;
            })));
    }
}
