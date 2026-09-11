package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.utils.Notifier;
import net.minecraft.network.chat.Component;
import com.github.lutzluca.btrbz.core.commands.alert.AlertCommand;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.github.lutzluca.btrbz.core.widgets.WidgetRuntime;

public class Commands {

    public static final LiteralArgumentBuilder<FabricClientCommandSource> rootCommand = ClientCommands
        .literal("btrbz")
        .executes(_ -> {
            ConfigScreen.open();
            return 1;
        });

    public static void registerAll(BazaarData bazaarData, WidgetRuntime widgetRuntime) {
        rootCommand.then(ClientCommands.literal("enable").executes(context -> {
            context.getSource().sendFeedback(Notifier.prefix().append(Component.literal(BtrBz.setEnabled(true))));
            return 1;
        }));
        rootCommand.then(ClientCommands.literal("disable").executes(context -> {
            context.getSource().sendFeedback(Notifier.prefix().append(Component.literal(BtrBz.setEnabled(false))));
            return 1;
        }));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(rootCommand);
            dispatcher.register(WidgetCommand.get(widgetRuntime));
            dispatcher.register(AlertCommand.get(bazaarData));
            dispatcher.register(ConversionCommand.get(bazaarData));
            dispatcher.register(TrackedOrderCommand.get());
            dispatcher.register(TaxCommand.get());
            dispatcher.register(PresetCommand.get());
        });
    }
}
