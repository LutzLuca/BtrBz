package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
                .argument("rate", FloatArgumentType.floatArg())
                .suggests(RATE_SUGGESTIONS)
                .executes(ctx -> {
                    var rate = FloatArgumentType.getFloat(ctx, "rate");

                    if (!List.of(1.25F, 1.125F, 1.0F).contains(rate)) {
                        var msg = Notifier
                            .prefix()
                            .append(Text.literal("Invalid rate").formatted(Formatting.RED))
                            .append(Text.literal(" (" + rate + ")").formatted(Formatting.DARK_GRAY))
                            .append(Text.literal(": must be ").formatted(Formatting.GRAY))
                            .append(Text.literal("1, 1.125").formatted(Formatting.AQUA))
                            .append(Text.literal(", or ").formatted(Formatting.GRAY))
                            .append(Text.literal("1.25").formatted(Formatting.AQUA))
                            .append(Text
                                .literal(
                                    " depending on your Bazaar Flipper level in the Community Shop")
                                .formatted(Formatting.GRAY));

                        Notifier.notifyPlayer(msg);
                        return 1;
                    }

                    ConfigManager.withConfig(cfg -> cfg.tax = rate);
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Text
                            .literal("Successfully set tax rate to ")
                            .formatted(Formatting.GRAY))
                        .append(Text.literal(+rate + "%").formatted(Formatting.AQUA)));
                    return 1;
                }))
                
            .then(ClientCommandManager.literal("show").executes(ctx -> {
                Notifier.notifyPlayer(Notifier
                    .prefix()
                    .append(Text.literal("Your tax rate is ").formatted(Formatting.GRAY))
                    .append(Text
                        .literal(ConfigManager.get().tax + "%")
                        .formatted(Formatting.AQUA)));
                return 1;
            })));
    }
}
