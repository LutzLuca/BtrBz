package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.runtime.ProfileTracker;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class ProfileCommand {
    public static LiteralArgumentBuilder<FabricClientCommandSource> build(ProfileTracker tracker) {
        return ClientCommands.literal("profile")
            .executes(context -> {
                context.getSource().sendFeedback(Notifier.prefix()
                    .append(Component.literal(tracker.status().description()).withStyle(UiStyles.label())));
                return 1;
            })
            .then(ClientCommands.literal("reset").executes(_ -> {
                tracker.forceReset();
                return 1;
            }));
    }
}
