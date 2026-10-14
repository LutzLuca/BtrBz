package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.appearance.UiColor;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.gui.image.ImageRenderer;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
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

    /** Resolves themed text when YACL recreates its presentation, preserving the existing option state. */
    public static OptionDescription createDescription(Supplier<Component> text) {
        return new SuppliedDescription(text, OptionDescription.EMPTY.image());
    }

    public static OptionDescription createDescription(String text, ConfigImages image) {
        return createDescription(Component.literal(text), image);
    }

    public static OptionDescription createDescription(Component text, ConfigImages image) {
        return image.description(text);
    }

    public static OptionDescription createDescription(Supplier<Component> text, ConfigImages image) {
        return new SuppliedDescription(text, image.description(Component.empty()).image());
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
        return example(Component.literal(text).withStyle(UiStyles.label()));
    }

    public static Component example(Component text) {
        return Component
            .literal("Example: ")
            .withStyle(UiStyles.action())
            .append(text);
    }

    public static Component note(String text) {
        return note(Component.literal(text).withStyle(UiStyles.label()));
    }

    public static Component note(Component text) {
        return Component
            .literal("Note: ")
            .withStyle(UiStyles.color(UiColor.Warning))
            .append(text);
    }

    public static Component requires(String text) {
        return requires(Component.literal(text).withStyle(UiStyles.muted()));
    }

    public static Component requires(Component text) {
        return Component
            .literal("Requires: ")
            .withStyle(UiStyles.muted())
            .append(text);
    }

    public static Component command(String command) {
        return Component
            .literal(command)
            .withStyle(UiStyles.muted().withItalic(true));
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> option) {
        return BooleanControllerBuilder.create(option).onOffFormatter().coloured(true);
    }

    private record SuppliedDescription(
        Supplier<Component> textSupplier,
        CompletableFuture<Optional<ImageRenderer>> image
    ) implements OptionDescription {
        @Override
        public Component text() {
            return this.textSupplier.get();
        }
    }

}
