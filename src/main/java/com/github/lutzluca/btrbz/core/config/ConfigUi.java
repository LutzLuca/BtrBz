package com.github.lutzluca.btrbz.core.config;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Shared descriptions and controllers for feature settings. */
public final class ConfigUi {

    private ConfigUi() {}

    public static OptionDescription createDescription(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    public static OptionDescription createDescription(Component text) {
        return OptionDescription.of(text);
    }

    public static OptionDescription createDescription(String text, ConfigImages image) {
        return createDescription(Component.literal(text), image);
    }

    public static OptionDescription createDescription(Component text, ConfigImages image) {
        return image.description(text);
    }

    public static Component paragraphs(Component... paragraphs) {
        var result = Component.empty();
        for (int i = 0; i < paragraphs.length; i++) {
            if (i > 0) {
                result.append(Component.literal("\n\n"));
            }
            result.append(paragraphs[i]);
        }
        return result;
    }

    public static Component text(String text) {
        return Component.literal(text);
    }

    public static Component example(String text) {
        return example(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    public static Component example(Component text) {
        return Component
            .literal("Example: ")
            .withStyle(ChatFormatting.GOLD)
            .append(text);
    }

    public static Component note(String text) {
        return note(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    public static Component note(Component text) {
        return Component
            .literal("Note: ")
            .withStyle(ChatFormatting.YELLOW)
            .append(text);
    }

    public static Component requires(String text) {
        return requires(Component.literal(text).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static Component requires(Component text) {
        return Component
            .literal("Requires: ")
            .withStyle(ChatFormatting.DARK_GRAY)
            .append(text);
    }

    public static Component command(String command) {
        return Component
            .literal(command)
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> option) {
        return BooleanControllerBuilder.create(option).onOffFormatter().coloured(true);
    }
}
