package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.AlertManager;
import com.github.lutzluca.btrbz.core.commands.alert.AlertCommand;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetRuntime;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.Notifier;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

public class Commands {

    public static void registerAll(
        BazaarData bazaarData,
        WidgetRuntime widgetRuntime,
        AlertManager alertManager,
        TrackedOrderManager orderManager,
        Runnable openConfigScreen,
        Function<Boolean, String> setEnabled
    ) {
        var rootCommand = ClientCommands.literal("btrbz").executes(_ -> {
            openConfigScreen.run();
            return 1;
        });
        rootCommand.then(ClientCommands.literal("enable").executes(context -> {
            context.getSource().sendFeedback(Notifier.prefix().append(Component.literal(setEnabled.apply(true))));
            return 1;
        }));
        rootCommand.then(ClientCommands.literal("disable").executes(context -> {
            context.getSource().sendFeedback(Notifier.prefix().append(Component.literal(setEnabled.apply(false))));
            return 1;
        }));
        rootCommand.then(WidgetCommand.build(widgetRuntime));
        rootCommand.then(AlertCommand.build(bazaarData, alertManager));
        rootCommand.then(ConversionCommand.build(bazaarData));
        rootCommand.then(TrackedOrderCommand.build(orderManager));
        rootCommand.then(TaxCommand.build());
        rootCommand.then(PresetCommand.build(widgetRuntime));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(rootCommand);
        });
    }
}
