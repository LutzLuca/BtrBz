package com.github.lutzluca.btrbz.utils;

import com.github.lutzluca.btrbz.core.ui.UiStyles;

import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.chat.MutableComponent;

import com.github.lutzluca.btrbz.core.OrderProtectionManager.ValidationResult;

@Slf4j
public class Notifier {

    public static boolean notifyPlayer(Component msg) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(msg);
            return true;
        }
        log.info("Failed to send message '{}' to player (client or player null)", msg.getString());
        return false;
    }

    public static void notifyChatCommand(String displayText, String cmd) {
        MutableComponent msg = Component
            .literal(displayText)
            .withStyle(style -> style
                .withClickEvent(new RunCommand("/" + cmd))
                .withHoverEvent(new ShowText(Component.literal("Run /" + cmd))));
        notifyPlayer(prefix().append(msg.withStyle(ChatFormatting.WHITE)));
    }

    public static void sendBlockedOrderMessage(ValidationResult validation) {
        var reason = "Order blocked: " + validation.reasonLines().stream()
            .map(Component::getString).collect(Collectors.joining(" "));

        var msg = Component
            .literal(reason)
            .withStyle(UiStyles.color(UiStyles.palette().error()))
            .append(Component.literal(" Hold Ctrl to override.").withStyle(UiStyles.label()));

        notifyPlayer(msg);
    }

    public static MutableComponent prefix() {
        return Component.literal("[BtrBz] ").withStyle(UiStyles.modLabel());
    }
}
