package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.utils.Utils;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.config.ConfigStore;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.utils.GameUtils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent.RunCommand;
import net.minecraft.network.chat.HoverEvent.ShowText;

public class ChatFilterManager {

    private static final List<String> TRANSIENT_MESSAGES = List.of(
        "[Bazaar] Cancelling order...",
        "[Bazaar] Putting goods in escrow...",
        "[Bazaar] Submitting buy order...",
        "[Bazaar] Claiming order...",
        "[Bazaar] Submitting sell offer...",
        "[Bazaar] Executing instant sell...",
        "[Bazaar] Executing instant buy...",
        "[Bazaar] Claiming orders...");

    public ChatFilterManager() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!BtrBz.isActive() || !ConfigStore.get().config().chatFilter.enabled) {
                return true;
            }

            String content = Utils.stripFormattingCodes(message.getString());
            return TRANSIENT_MESSAGES.stream().noneMatch(content::startsWith);
        });
    }

    public static Component withFilledOrderShortcut(Component message, boolean overlay, boolean enabled) {
        if (!enabled || overlay) {
            return message;
        }
        var content = Utils.stripFormattingCodes(message.getString());
        if (!content.startsWith("[Bazaar]") || !content.endsWith("was filled!")) {
            return message;
        }
        return message.copy()
            .withStyle(style -> style
                .withClickEvent(new RunCommand("/managebazaarorders"))
                .withHoverEvent(new ShowText(Component.literal("Opens the Bazaar order screen"))))
            .append(Component.literal(" [Go To Orders]").withStyle(ChatFormatting.DARK_AQUA));
    }

    public static class ChatFilterConfig {

        public boolean enabled = true;
        public boolean filledOrderShortcut = true;

        public OptionGroup createGroup() {
            return OptionGroup
                .createBuilder()
                .name(Component.literal("Bazaar Chat Filter"))
                .description(ConfigScreen.createDescription(ConfigScreen.paragraphs(
                    ConfigScreen.text(
                        "Hide temporary Bazaar progress messages while keeping confirmations, warnings, "
                            + "and errors visible."),
                    Component
                        .literal("Examples hidden:\n")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component
                            .literal("• [Bazaar] Submitting buy order...\n"
                                + "• [Bazaar] Claiming orders...")
                            .withStyle(ChatFormatting.GRAY)))))
                .options(List.of(
                    Option.<Boolean>createBuilder()
                        .name(Component.literal("Filter Transient Messages"))
                        .description(ConfigScreen.createDescription(
                            "Hide short-lived progress messages that do not report a result. "
                                + "Completed-order messages, warnings, and errors remain visible."))
                        .binding(
                            true,
                            () -> this.enabled,
                            val -> this.enabled = val)
                        .controller(ConfigScreen::createBooleanController)
                        .build(),
                    Option.<Boolean>createBuilder()
                        .name(Component.literal("Filled Order Chat Shortcut"))
                        .description(ConfigScreen.createDescription(
                            "Make filled Bazaar messages clickable and append [Go To Orders]. "
                                + "Order tracking continues when this is off."))
                        .binding(true, () -> this.filledOrderShortcut, val -> this.filledOrderShortcut = val)
                        .controller(ConfigScreen::createBooleanController)
                        .build()))
                .collapsed(true)
                .build();
        }
    }
}
