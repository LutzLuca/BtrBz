package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.conversions.ConversionStatus;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class ConversionCommand {

    private ConversionCommand() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> build(BazaarData bazaarData) {
        return command("conversions", bazaarData);
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
                .executes(ctx -> startRefresh(bazaarData, false))
                .then(ClientCommands
                    .literal("force")
                    .executes(ctx -> startRefresh(bazaarData, true))));
    }

    private static int startRefresh(BazaarData bazaarData, boolean force) {
        if (bazaarData.refreshConversions(true, force)) {
            var message = force
                ? "Started forced Bazaar conversion refresh"
                : "Started Bazaar conversion refresh";
            Notifier.notifyPlayer(Notifier
                .prefix()
                .append(Component.literal(message).withStyle(UiStyles.label())));
        } else {
            Notifier.notifyPlayer(Notifier
                .prefix()
                .append(Component
                    .literal("Bazaar conversion refresh is already running")
                    .withStyle(UiStyles.label())));
        }
        return 1;
    }

    private static void notifyStatus(ConversionStatus status) {
        var counts = status.sourceCounts();
        var message = Notifier
            .prefix()
            .append(Component.literal("Bazaar conversions").withStyle(UiStyles.action()))
            .append(Component.literal("\nSource: " + status.activeLoadSource()).withStyle(UiStyles.label()))
            .append(Component.literal("\nProducts: " + counts.total()).withStyle(UiStyles.label()))
            .append(Component
                .literal("\nMissing products: " + status.missingProductCount())
                .withStyle(status.missingProductCount() == 0
                    ? UiStyles.label() : UiStyles.color(UiStyles.palette().warning())))
            .append(Component
                .literal("\nSources: neu=" + counts.neu()
                    + ", derived=" + counts.derived())
                .withStyle(UiStyles.label()))
            .append(Component.literal("\nGenerated: " + status.generatedAt()).withStyle(UiStyles.label()))
            .append(Component
                .literal("\nNEU commit: " + status.neuCommit().orElse("<none>"))
                .withStyle(UiStyles.label()))
            .append(Component
                .literal("\nLast refresh: " + status.lastSuccessfulRefreshAt().orElse("<never>"))
                .withStyle(UiStyles.label()))
            .append(Component
                .literal("\nRefresh running: " + status.refreshInFlight())
                .withStyle(UiStyles.label()));

        status.lastFailure().ifPresent(failure -> message.append(Component
            .literal("\nLast failure: " + failure.shortMessage())
            .withStyle(UiStyles.color(UiStyles.palette().error()))));

        Notifier.notifyPlayer(message);
    }
}
