package com.github.lutzluca.btrbz.utils.slot;

import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.ItemStack;
import com.github.lutzluca.btrbz.utils.ClickOutcome;

public record SlotInterceptorRegistration(
    String name,
    Matcher matcher,
    @Nullable ItemOverrideHandler itemOverrideHandler,
    @Nullable ClickHandler clickHandler
) {

    public SlotInterceptorRegistration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(matcher, "matcher");

        if (itemOverrideHandler == null && clickHandler == null) {
            throw new IllegalArgumentException(
                "At least one handler must be provided for slot interceptor '" + name + "'"
            );
        }
    }

    public static Builder named(@NotNull String name) {
        return new Builder(name);
    }

    @FunctionalInterface
    public interface Matcher {
        boolean matches(SlotInterceptorContext ctx);
    }

    @FunctionalInterface
    public interface ItemOverrideHandler {
        Optional<ItemStack> override(ItemOverrideContext ctx);
    }

    @FunctionalInterface
    public interface ClickHandler {
        ClickOutcome onClick(SlotClickContext ctx);
    }

    public static final class Builder {

        private final String name;
        private Matcher matcher = ctx -> true;
        private @Nullable ItemOverrideHandler itemOverrideHandler;
        private @Nullable ClickHandler clickHandler;

        private Builder(@NotNull String name) {
            this.name = name;
        }

        public @NotNull Builder matches(@NotNull Matcher matcher) {
            this.matcher = matcher;
            return this;
        }

        public @NotNull Builder overrideItem(@NotNull ItemOverrideHandler itemOverrideHandler) {
            this.itemOverrideHandler = itemOverrideHandler;
            return this;
        }

        public @NotNull Builder onClick(@NotNull ClickHandler clickHandler) {
            this.clickHandler = clickHandler;
            return this;
        }

        public @NotNull SlotInterceptorRegistration build() {
            return new SlotInterceptorRegistration(
                this.name,
                this.matcher,
                this.itemOverrideHandler,
                this.clickHandler
            );
        }
    }
}