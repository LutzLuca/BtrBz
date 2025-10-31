package com.github.lutzluca.btrbz.core.commands.alert;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.commands.Commands;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.vavr.control.Try;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AlertCommand {

    private static final AlertCommandParser PARSER = new AlertCommandParser();

    public static LiteralArgumentBuilder<FabricClientCommandSource> get() {
        return Commands.rootCommand.then(ClientCommandManager
            .literal("alert")
            .then(ClientCommandManager
                .literal("remove")
                .then(ClientCommandManager
                    .argument("id", StringArgumentType.string())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");

                        Try
                            .of(() -> UUID.fromString(id))
                            .onSuccess(BtrBz.alertManager()::removeAlert)
                            .onFailure(err -> Notifier.notifyPlayer(Notifier
                                .prefix()
                                .append(Text.literal("Invalid input ").formatted(Formatting.GRAY))
                                .append(Text.literal(id).formatted(Formatting.RED))
                                .append(Text
                                    .literal(" is not a valid UUID")
                                    .formatted(Formatting.GRAY))));

                        return 1;
                    })))

            .then(ClientCommandManager.literal("list").executes(ctx -> {
                var alerts = ConfigManager.get().alert.alerts;
                if (alerts.isEmpty()) {
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Text.literal("No active alerts.").formatted(Formatting.GRAY)));
                    return 1;
                }

                final var newline = Text.literal("\n");
                var builder = Notifier
                    .prefix()
                    .append(Text
                        .literal("Active Alerts (" + alerts.size() + "):")
                        .formatted(Formatting.GOLD))
                    .append(newline);

                var first = true;
                for (var alert : alerts) {
                    if (!first) {
                        builder.append(newline);
                    }

                    builder.append(alert
                        .format()
                        .append(Text.literal(" "))
                        .append(Notifier.clickToRemoveAlert(alert.id, "Remove this alert")));
                    first = false;
                }

                Notifier.notifyPlayer(builder);
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
                            var registered = BtrBz.alertManager().addAlert(resolved);
                            if (registered) {
                                Notifier.notifyAlertRegistered(resolved);
                                return;
                            }

                            Notifier.notifyAlertAlreadyPresent(resolved);
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
