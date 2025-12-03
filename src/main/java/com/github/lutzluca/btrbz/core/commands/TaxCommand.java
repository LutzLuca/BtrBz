package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class TaxCommand {

    private static final SuggestionProvider<FabricClientCommandSource> RATE_SUGGESTIONS = (ctx, builder) -> {
        List<String> validRates = List.of("1.0", "1.125", "1.25");
        for (String rate : validRates) {
            if (rate.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(rate);
            }
        }
        return builder.buildFuture();
    };

    public static LiteralArgumentBuilder<FabricClientCommandSource> get() {
        return Commands.rootCommand.then(ClientCommandManager
            .literal("tax")
            .then(ClientCommandManager
                .literal("set")
                .then(ClientCommandManager
                    .argument("rate", FloatArgumentType.floatArg())
                    .suggests(RATE_SUGGESTIONS)
                    .executes(ctx -> {
                        var rate = FloatArgumentType.getFloat(ctx, "rate");

                        if (!List.of(1.25F, 1.125F, 1.0F).contains(rate)) {
                            var msg = Notifier
                                .prefix()
                                .append(Component.literal("Invalid rate").withStyle(ChatFormatting.RED))
                                .append(Component
                                    .literal(" (" + rate + ")")
                                    .withStyle(ChatFormatting.DARK_GRAY))
                                .append(Component.literal(": must be ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("1, 1.125").withStyle(ChatFormatting.AQUA))
                                .append(Component.literal(", or ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("1.25").withStyle(ChatFormatting.AQUA))
                                .append(Component
                                    .literal(
                                        " depending on your Bazaar Flipper level in the Community Shop")
                                    .withStyle(ChatFormatting.GRAY));

                            Notifier.notifyPlayer(msg);
                            return 1;
                        }

                        ConfigManager.withConfig(cfg -> cfg.tax = rate);
                        Notifier.notifyPlayer(Notifier
                            .prefix()
                            .append(Component
                                .literal("Successfully set tax rate to ")
                                .withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(+rate + "%").withStyle(ChatFormatting.AQUA)));
                        return 1;
                    })))

            .then(ClientCommandManager.literal("show").executes(ctx -> {
                Notifier.notifyPlayer(Notifier
                    .prefix()
                    .append(Component.literal("Your tax rate is ").withStyle(ChatFormatting.GRAY))
                    .append(Component
                        .literal(ConfigManager.get().tax + "%")
                        .withStyle(ChatFormatting.AQUA)));
                return 1;
            })));
    }
}
