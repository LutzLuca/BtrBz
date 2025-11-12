package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.ClickEvent.RunCommand;
import net.minecraft.text.HoverEvent.ShowText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class PresetCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> get() {
        return Commands.rootCommand.then(ClientCommandManager
            .literal("preset")
            .then(ClientCommandManager.literal("add").then(ClientCommandManager
                .argument(
                    "volume",
                    IntegerArgumentType.integer(1, GameUtils.GLOBAL_MAX_ORDER_VOLUME)
                )
                .executes(ctx -> {
                    int volume = IntegerArgumentType.getInteger(ctx, "volume");

                    ConfigManager.withConfig(cfg -> {
                        var presets = new ArrayList<>(cfg.orderPresets.presets);

                        if (presets.contains(volume)) {
                            Notifier.notifyPlayer(Notifier
                                .prefix()
                                .append(Text.literal("Preset ").formatted(Formatting.GRAY))
                                .append(Text
                                    .literal(String.valueOf(volume))
                                    .formatted(Formatting.AQUA))
                                .append(Text
                                    .literal(" already exists")
                                    .formatted(Formatting.GRAY)));
                            return;
                        }

                        presets.add(volume);
                        presets.sort(Integer::compareTo);
                        cfg.orderPresets.presets = presets;

                        Notifier.notifyPlayer(Notifier
                            .prefix()
                            .append(Text.literal("Added preset ").formatted(Formatting.GRAY))
                            .append(Text
                                .literal(String.valueOf(volume))
                                .formatted(Formatting.AQUA)));
                    });

                    return 1;
                })))

            .then(ClientCommandManager
                .literal("remove")
                .then(ClientCommandManager
                    .argument("volume", IntegerArgumentType.integer())
                    .executes(ctx -> {
                        int volume = IntegerArgumentType.getInteger(ctx, "volume");

                        ConfigManager.withConfig(cfg -> {
                            var presets = new ArrayList<>(cfg.orderPresets.presets);

                            if (!presets.contains(volume)) {
                                Notifier.notifyPlayer(Notifier
                                    .prefix()
                                    .append(Text.literal("Preset ").formatted(Formatting.GRAY))
                                    .append(Text
                                        .literal(String.valueOf(volume))
                                        .formatted(Formatting.RED))
                                    .append(Text.literal(" not found").formatted(Formatting.GRAY)));
                                return;
                            }

                            presets.remove(Integer.valueOf(volume));
                            cfg.orderPresets.presets = presets;

                            Notifier.notifyPlayer(Notifier
                                .prefix()
                                .append(Text.literal("Removed preset ").formatted(Formatting.GRAY))
                                .append(Text
                                    .literal(String.valueOf(volume))
                                    .formatted(Formatting.AQUA)));
                        });

                        return 1;
                    })))

            .then(ClientCommandManager.literal("list").executes(ctx -> {
                var presets = ConfigManager.get().orderPresets.presets;

                if (presets.isEmpty()) {
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Text.literal("No presets configured").formatted(Formatting.GRAY)));
                    return 1;
                }

                var builder = Notifier
                    .prefix()
                    .append(Text.literal("Order Presets (").formatted(Formatting.GOLD))
                    .append(Text
                        .literal(String.valueOf(presets.size()))
                        .formatted(Formatting.YELLOW))
                    .append(Text.literal("):").formatted(Formatting.GOLD))
                    .append(Text.literal("\n"));

                var sortedPresets = new ArrayList<>(presets);
                sortedPresets.sort(Integer::compareTo);

                for (int i = 0; i < sortedPresets.size(); i++) {
                    if (i > 0) {
                        builder.append(Text.literal("  ").formatted(Formatting.DARK_GRAY));
                    }
                    int volume = sortedPresets.get(i);

                    builder.append(Text.literal(String.valueOf(volume)).formatted(Formatting.AQUA));
                    builder.append(Text.literal(" "));
                    builder.append(Text
                        .literal("[x]")
                        .formatted(Formatting.RED)
                        .styled(style -> style
                            .withClickEvent(new RunCommand("/btrbz preset remove " + volume))
                            .withHoverEvent(new ShowText(Text.literal("Remove preset for " + volume)))));
                }

                Notifier.notifyPlayer(builder);
                return 1;
            }))

            .then(ClientCommandManager.literal("clear").executes(ctx -> {
                ConfigManager.withConfig(cfg -> {
                    int count = cfg.orderPresets.presets.size();
                    cfg.orderPresets.presets = List.of();

                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Text.literal("Cleared ").formatted(Formatting.GRAY))
                        .append(Text.literal(String.valueOf(count)).formatted(Formatting.AQUA))
                        .append(Text.literal(" preset(s)").formatted(Formatting.GRAY)));
                });

                return 1;
            })));
    }
}