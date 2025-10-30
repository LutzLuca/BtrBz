package com.github.lutzluca.btrbz.core.commands.alert;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.commands.Commands;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.vavr.control.Try;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AlertCommand {

    private static final AlertCommandParser PARSER = new AlertCommandParser();

    public static LiteralArgumentBuilder<FabricClientCommandSource> get(
        CommandDispatcher<FabricClientCommandSource> dispatcher
    ) {
        return Commands.rootCommand.then(ClientCommandManager
            .literal("alert")
            .then(ClientCommandManager
                .literal("remove")
                .then(ClientCommandManager
                    .argument("id", StringArgumentType.string())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");
                        // todo: implement alert removal by UUID
                        return 1;
                    })))
            .then(ClientCommandManager.literal("list").executes(ctx -> {
                // todo: implement alert listing
                return 1;
            }))
            .then(ClientCommandManager
                .argument("args", StringArgumentType.greedyString())
                .executes(ctx -> {
                    var args = StringArgumentType.getString(ctx, "args");
                    var result = Try
                        .of(() -> PARSER.parse(args))
                        .flatMap(alertCmd -> alertCmd.resolve(BtrBz.bazaarData()))
                        .onSuccess(resolved -> {
                            BtrBz.alertManager().;
                            Notifier.notifyAlertRegistered(resolved);
                        })
                        .onFailure(err -> {
                            var msg = Notifier
                                .prefix()
                                .append(Text
                                    .literal("Alert setup failed: ")
                                    .formatted(Formatting.RED))
                                .append(Text.literal(err.getMessage()).formatted(Formatting.GRAY));
                            Notifier.notifyPlayer(msg);
                        });

                    return result.isSuccess() ? 1 : -1;
                })));

    }
}
