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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;

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
                                .append(Component.literal("Preset ").withStyle(ChatFormatting.GRAY))
                                .append(Component
                                    .literal(String.valueOf(volume))
                                    .withStyle(ChatFormatting.AQUA))
                                .append(Component
                                    .literal(" already exists")
                                    .withStyle(ChatFormatting.GRAY)));
                            return;
                        }

                        presets.add(volume);
                        presets.sort(Integer::compareTo);
                        cfg.orderPresets.presets = presets;

                        Notifier.notifyPlayer(Notifier
                            .prefix()
                            .append(Component.literal("Added preset ").withStyle(ChatFormatting.GRAY))
                            .append(Component
                                .literal(String.valueOf(volume))
                                .withStyle(ChatFormatting.AQUA)));
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
                                    .append(Component.literal("Preset ").withStyle(ChatFormatting.GRAY))
                                    .append(Component
                                        .literal(String.valueOf(volume))
                                        .withStyle(ChatFormatting.RED))
                                    .append(Component.literal(" not found").withStyle(ChatFormatting.GRAY)));
                                return;
                            }

                            presets.remove(Integer.valueOf(volume));
                            cfg.orderPresets.presets = presets;

                            Notifier.notifyPlayer(Notifier
                                .prefix()
                                .append(Component.literal("Removed preset ").withStyle(ChatFormatting.GRAY))
                                .append(Component
                                    .literal(String.valueOf(volume))
                                    .withStyle(ChatFormatting.AQUA)));
                        });

                        return 1;
                    })))

            .then(ClientCommandManager.literal("list").executes(ctx -> {
                var presets = ConfigManager.get().orderPresets.presets;

                if (presets.isEmpty()) {
                    Notifier.notifyPlayer(Notifier
                        .prefix()
                        .append(Component.literal("No presets configured").withStyle(ChatFormatting.GRAY)));
                    return 1;
                }

                var builder = Notifier
                    .prefix()
                    .append(Component.literal("Order Presets (").withStyle(ChatFormatting.GOLD))
                    .append(Component
                        .literal(String.valueOf(presets.size()))
                        .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("):").withStyle(ChatFormatting.GOLD))
                    .append(Component.literal("\n"));

                var sortedPresets = new ArrayList<>(presets);
                sortedPresets.sort(Integer::compareTo);

                for (int i = 0; i < sortedPresets.size(); i++) {
                    if (i > 0) {
                        builder.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
                    }
                    int volume = sortedPresets.get(i);

                    builder.append(Component.literal(String.valueOf(volume)).withStyle(ChatFormatting.AQUA));
                    builder.append(Component.literal(" "));
                    builder.append(Component
                        .literal("[x]")
                        .withStyle(ChatFormatting.RED)
                        .withStyle(style -> style
                            .withClickEvent(new RunCommand("/btrbz preset remove " + volume))
                            .withHoverEvent(new ShowText(Component.literal("Remove preset for " + volume)))));
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
                        .append(Component.literal("Cleared ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" preset(s)").withStyle(ChatFormatting.GRAY)));
                });

                return 1;
            })));
    }
}