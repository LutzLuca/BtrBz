package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.conversions.ConversionStatus;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class ConversionCommand {

    private ConversionCommand() { }

    public static LiteralArgumentBuilder<FabricClientCommandSource> get(BazaarData bazaarData) {
        return Commands.rootCommand
            .then(command("conversions", bazaarData))
            .then(command("conversion", bazaarData));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(
        String name,
        BazaarData bazaarData
    ) {
        return ClientCommands
            .literal(name)
            .then(ClientCommands
                .literal("status")
                .executes(ctx -> {
                    notifyStatus(bazaarData.getConversionStatus());
                    return 1;
                }))
            .then(ClientCommands
                .literal("refresh")
                .executes(ctx -> {
                    if (bazaarData.refreshConversions(true)) {
                        Notifier.notifyPlayer(Notifier
                            .prefix()
                            .append(Component
                                .literal("Started Bazaar conversion refresh")
                                .withStyle(ChatFormatting.GRAY)));
                    } else {
                        Notifier.notifyPlayer(Notifier
                            .prefix()
                            .append(Component
                                .literal("Bazaar conversion refresh is already running")
                                .withStyle(ChatFormatting.GRAY)));
                    }
                    return 1;
                }));
    }

    private static void notifyStatus(ConversionStatus status) {
        var counts = status.sourceCounts();
        var message = Notifier
            .prefix()
            .append(Component.literal("Bazaar conversions").withStyle(ChatFormatting.GOLD))
            .append(Component.literal("\nSource: " + status.activeLoadSource()).withStyle(ChatFormatting.GRAY))
            .append(Component.literal("\nProducts: " + counts.total()).withStyle(ChatFormatting.GRAY))
            .append(Component
                .literal("\nSources: neu=" + counts.neu()
                    + ", derived=" + counts.derived())
                .withStyle(ChatFormatting.GRAY))
            .append(Component.literal("\nGenerated: " + status.generatedAt()).withStyle(ChatFormatting.GRAY))
            .append(Component
                .literal("\nNEU commit: " + status.neuCommit().orElse("<none>"))
                .withStyle(ChatFormatting.GRAY))
            .append(Component
                .literal("\nLast refresh: " + status.lastSuccessfulRefreshAt().orElse("<never>"))
                .withStyle(ChatFormatting.GRAY))
            .append(Component
                .literal("\nRefresh running: " + status.refreshInFlight())
                .withStyle(ChatFormatting.GRAY));

        status.lastFailure().ifPresent(failure -> message.append(Component
            .literal("\nLast failure: " + failure.shortMessage())
            .withStyle(ChatFormatting.RED)));

        Notifier.notifyPlayer(message);
    }
}
