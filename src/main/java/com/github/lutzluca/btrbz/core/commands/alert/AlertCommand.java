package com.github.lutzluca.btrbz.core.commands.alert;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.commands.Commands;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.core.commands.alert.AlertCommandParser.ResolvedAlertArgs;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Utils;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.vavr.control.Try;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class AlertCommand {

    private static final AlertCommandParser PARSER = new AlertCommandParser();
    private static final SuggestionProvider<FabricClientCommandSource> TYPE_SUGGESTIONS = (ctx, builder) -> {
        for (var type : List.of("buy", "sell", "instabuy", "instasell")) {
            if (type.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(type);
            }
        }
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<FabricClientCommandSource> get(BazaarData bazaarData) {
        return Commands.rootCommand.then(ClientCommands
            .literal("alert")
            .then(ClientCommands
                .literal("remove")
                .then(ClientCommands
                    .argument("id", StringArgumentType.string())
                    .executes(ctx -> {
                        String id = StringArgumentType.getString(ctx, "id");

                        Try
                            .of(() -> UUID.fromString(id))
                            .onSuccess(BtrBz.alertManager()::removeAlert)
                            .onFailure(err -> Notifier.notifyPlayer(Notifier
                                .prefix()
                                .append(Component.literal("Invalid input ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal(id).withStyle(ChatFormatting.RED))
                                .append(Component
                                    .literal(" is not a valid UUID")
                                    .withStyle(ChatFormatting.GRAY))));

                        return 1;
                    })))

            .then(ClientCommands.literal("list").executes(ctx -> {
                var alerts = ConfigManager.get().alert.alerts;
                if (alerts.isEmpty()) {
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Component.literal("No active alerts.").withStyle(ChatFormatting.GRAY)));
                    return 1;
                }

                final var newline = Component.literal("\n");
                var builder = Notifier
                    .prefix()
                    .append(Component
                        .literal("Active Alerts (" + alerts.size() + "):")
                        .withStyle(ChatFormatting.GOLD))
                    .append(newline);

                var first = true;
                for (var alert : alerts) {
                    if (!first) {
                        builder.append(newline);
                    }

                    builder.append(alert
                        .format(bazaarData)
                        .append(Component.literal(" "))
                        .append(Notifier.clickToRemoveAlert(alert.id, "Remove this alert")));
                    first = false;
                }

                Notifier.notifyPlayer(builder);
                return 1;
            }))

            .then(ClientCommands
                .literal("add")
                .then(ClientCommands
                    .argument("productId", StringArgumentType.string())
                    .suggests(productSuggestions(bazaarData))
                    .then(ClientCommands
                        .argument("type", StringArgumentType.string())
                        .suggests(TYPE_SUGGESTIONS)
                        .then(ClientCommands
                            .argument("expression", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                var productId = StringArgumentType.getString(ctx, "productId");
                                var type = StringArgumentType.getString(ctx, "type");
                                var expression = StringArgumentType.getString(ctx, "expression");
                                var result = Try
                                    .of(() -> PARSER.parse(productId, type, expression))
                                    .flatMap(alertCmd -> alertCmd.resolve(bazaarData))
                                    .flatMap(ResolvedAlertArgs::validate)
                                    .onSuccess(resolved -> {
                                        var registered = BtrBz.alertManager().addAlert(resolved);
                                        if (registered) {
                                            Notifier.notifyAlertRegistered(resolved, bazaarData);
                                            return;
                                        }

                                        Notifier.notifyAlertAlreadyPresent(resolved, bazaarData);
                                    })
                                    .onFailure(err -> {
                                        var msg = Notifier
                                            .prefix()
                                            .append(Component
                                                .literal("Alert setup failed: ")
                                                .withStyle(ChatFormatting.RED))
                                            .append(Component
                                                .literal(err.getMessage())
                                                .withStyle(ChatFormatting.GRAY));

                                        Notifier.notifyPlayer(msg);
                                    });

                                return result.isSuccess() ? 1 : -1;
                            }))))));

    }

    private static SuggestionProvider<FabricClientCommandSource> productSuggestions(BazaarData bazaarData) {
        return (ctx, builder) -> {
            var remaining = builder.getRemainingLowerCase();
            var normalizedRemaining = Utils.normalizeDisplayName(remaining);
            bazaarData
                .allProducts()
                .stream()
                .filter(product -> matches(product.productId(), product.strippedName(), remaining, normalizedRemaining))
                .limit(100)
                .forEach(product -> builder.suggest(
                    product.productId(),
                    new LiteralMessage(product.strippedName())
                ));
            return builder.buildFuture();
        };
    }

    private static boolean matches(
        String productId,
        String strippedName,
        String remaining,
        String normalizedRemaining
    ) {
        if (remaining.isBlank()) {
            return true;
        }

        return productId.toLowerCase(Locale.US).contains(remaining)
            || Utils.normalizeDisplayName(strippedName).contains(normalizedRemaining);
    }
}
