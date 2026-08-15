package com.github.lutzluca.btrbz.core.commands;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.core.widgets.presets.OrderPresetsWidgetDefinition;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;

public class PresetCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> get() {
        return Commands.rootCommand.then(ClientCommands
            .literal("preset")
            .then(ClientCommands.literal("add").then(ClientCommands
                .argument(
                    "volume",
                    IntegerArgumentType.integer(1, GameUtils.GLOBAL_MAX_ORDER_VOLUME))
                .executes(ctx -> {
                    int volume = IntegerArgumentType.getInteger(ctx, "volume");

                    boolean added = ConfigManager.updateIfChanged(cfg -> {
                        var presets = cfg.widgets.orderPresets.volumes;
                        if (presets.contains(volume)) {
                            return false;
                        }

                        presets.add(volume);
                        presets.sort(Integer::compareTo);
                        return true;
                    });

                    if (added) {
                        BtrBz.widgetRuntime().invalidateWidgetContent(
                            OrderPresetsWidgetDefinition.ID, "preset volume added by command");
                        Notifier.notifyPlayer(Notifier
                            .prefix()
                            .append(Component.literal("Added preset ").withStyle(ChatFormatting.GRAY))
                            .append(Component
                                .literal(String.valueOf(volume))
                                .withStyle(ChatFormatting.AQUA)));
                    } else {
                        Notifier.notifyPlayer(Notifier
                            .prefix()
                            .append(Component.literal("Preset ").withStyle(ChatFormatting.GRAY))
                            .append(Component
                                .literal(String.valueOf(volume))
                                .withStyle(ChatFormatting.AQUA))
                            .append(Component
                                .literal(" already exists")
                                .withStyle(ChatFormatting.GRAY)));
                    }

                    return 1;
                })))

            .then(ClientCommands
                .literal("remove")
                .then(ClientCommands
                    .argument("volume", IntegerArgumentType.integer())
                    .executes(ctx -> {
                        int volume = IntegerArgumentType.getInteger(ctx, "volume");

                        boolean removed = ConfigManager
                            .updateIfChanged(cfg -> cfg.widgets.orderPresets.volumes.remove(Integer.valueOf(volume)));

                        if (removed) {
                            BtrBz.widgetRuntime().invalidateWidgetContent(
                                OrderPresetsWidgetDefinition.ID, "preset volume removed by command");
                            Notifier.notifyPlayer(Notifier
                                .prefix()
                                .append(Component.literal("Removed preset ").withStyle(ChatFormatting.GRAY))
                                .append(Component
                                    .literal(String.valueOf(volume))
                                    .withStyle(ChatFormatting.AQUA)));
                        } else {
                            Notifier.notifyPlayer(Notifier
                                .prefix()
                                .append(Component.literal("Preset ").withStyle(ChatFormatting.GRAY))
                                .append(Component
                                    .literal(String.valueOf(volume))
                                    .withStyle(ChatFormatting.RED))
                                .append(Component.literal(" not found").withStyle(ChatFormatting.GRAY)));
                        }

                        return 1;
                    })))

            .then(ClientCommands.literal("list").executes(ctx -> {
                var presets = ConfigManager.get().widgets.orderPresets.volumes;

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

                for (int i = 0; i < presets.size(); i++) {
                    if (i > 0) {
                        builder.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
                    }
                    int volume = presets.get(i);

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

            .then(ClientCommands.literal("clear").executes(ctx -> {
                int count = ConfigManager.get().widgets.orderPresets.volumes.size();
                boolean cleared = ConfigManager.updateIfChanged(cfg -> {
                    if (cfg.widgets.orderPresets.volumes.isEmpty()) {
                        return false;
                    }

                    cfg.widgets.orderPresets.volumes.clear();
                    return true;
                });
                if (cleared) {
                    BtrBz.widgetRuntime().invalidateWidgetContent(
                        OrderPresetsWidgetDefinition.ID, "preset volumes cleared by command");
                }

                Notifier.notifyPlayer(Notifier
                    .prefix()
                    .append(Component.literal("Cleared ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.valueOf(count)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" preset(s)").withStyle(ChatFormatting.GRAY)));

                return 1;
            })));
    }
}
