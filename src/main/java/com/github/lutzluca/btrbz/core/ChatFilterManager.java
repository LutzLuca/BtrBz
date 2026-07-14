package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import com.github.lutzluca.btrbz.utils.GameUtils;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.List;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;

public class ChatFilterManager {

    private static final List<String> TRANSIENT_MESSAGES = List.of(
        "[Bazaar] Cancelling order...",
        "[Bazaar] Putting goods in escrow...",
        "[Bazaar] Submitting buy order...",
        "[Bazaar] Claiming order...",
        "[Bazaar] Submitting sell offer...",
        "[Bazaar] Executing instant sell...",
        "[Bazaar] Executing instant buy...",
        "[Bazaar] Claiming orders..."
    );

    public ChatFilterManager() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!ConfigManager.get().chatFilter.enabled) {
                return true;
            }

            String content = GameUtils.stripFormattingCodes(message.getString());
            return TRANSIENT_MESSAGES.stream().noneMatch(content::startsWith);
        });
    }

    public static class ChatFilterConfig {

        public boolean enabled = true;

        public OptionGroup createGroup() {
            return OptionGroup
                .createBuilder()
                .name(Component.literal("Bazaar Chat Filter"))
                .description(ConfigScreen.createDescription(
                    "Hide temporary Bazaar progress messages while keeping confirmations, warnings, and errors visible.\n\n"
                    + "Examples that are hidden:\n"
                    + "• [Bazaar] Submitting buy order...\n"
                    + "• [Bazaar] Claiming orders..."))
                .options(List.of(
                    Option.<Boolean>createBuilder()
                          .name(Component.literal("Filter Transient Messages"))
                          .description(OptionDescription.of(Component.literal(
                              "Hide short-lived progress messages that do not report a result. Completed-order messages, warnings, and errors remain visible.")))
                          .binding(
                              true,
                              () -> this.enabled,
                              val -> this.enabled = val
                          )
                          .controller(ConfigScreen::createBooleanController)
                          .build()
                ))
                .collapsed(true)
                .build();
        }
    }
}
