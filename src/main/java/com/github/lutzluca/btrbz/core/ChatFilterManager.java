package com.github.lutzluca.btrbz.core;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import java.util.List;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
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

            String content = ChatFormatting.stripFormatting(message.getString());
            return TRANSIENT_MESSAGES.stream().noneMatch(content::contains);
        });
    }

    public static class ChatFilterConfig {

        public boolean enabled = true;

        public OptionGroup createGroup() {
            return OptionGroup
                .createBuilder()
                .name(Component.literal("Chat Filter"))
                .description(OptionDescription.of(Component.literal(
                    "Settings for filtering useless Bazaar messages from chat")))
                .options(List.of(
                    Option.<Boolean>createBuilder()
                          .name(Component.literal("Filter Transient Messages"))
                          .description(OptionDescription.of(Component.literal(
                              "Filters out [Bazaar] messages such as 'Submitting order...' or 'Claiming orders...'")))
                          .binding(
                              true,
                              () -> this.enabled,
                              val -> this.enabled = val
                          )
                          .controller(ConfigScreen::createBooleanController)
                          .build()
                ))
                .collapsed(false)
                .build();
        }
    }
}
