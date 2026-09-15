package com.github.lutzluca.btrbz.core.commands.alert;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AlertCommandTest {
    @Test
    void bareCommandOpensScreenAndOldSubcommandsAreRejected() throws CommandSyntaxException {
        var opened = new AtomicInteger();
        var dispatcher = new CommandDispatcher<FabricClientCommandSource>();
        dispatcher.register(ClientCommands.literal("btrbz").then(AlertCommand.build(opened::incrementAndGet)));

        Assertions.assertEquals(1, dispatcher.execute("btrbz alert", null));
        Assertions.assertEquals(1, opened.get());
        for (var obsolete : new String[]{"add ITEM buy-order 100", "list", "remove example-id"}) {
            Assertions.assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("btrbz alert " + obsolete, null));
        }
        Assertions.assertEquals(1, opened.get());
    }
}
