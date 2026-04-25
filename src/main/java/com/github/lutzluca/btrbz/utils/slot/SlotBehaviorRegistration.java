package com.github.lutzluca.btrbz.utils.slot;

import java.util.Objects;
import java.util.Optional;

import com.github.lutzluca.btrbz.utils.ClickOutcome;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public record SlotBehaviorRegistration(
    String name,
    Matcher matcher,
    @Nullable ItemOverrideHandler itemOverrideHandler,
    @Nullable ClickHandler clickHandler
) {

    public SlotBehaviorRegistration {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(matcher, "matcher");
        if (itemOverrideHandler == null && clickHandler == null) {
            throw new IllegalArgumentException(
                "At least one handler must be provided for slot behavior '" + name + "'"
            );
        }
    }

    public static Builder named(String name) {
        return new Builder(name);
    }

    @FunctionalInterface
    public interface Matcher {
        boolean matches(SlotBehaviorContext context);
    }

    @FunctionalInterface
    public interface ItemOverrideHandler {
        Optional<ItemStack> override(ItemOverrideContext context);
    }

    @FunctionalInterface
    public interface ClickHandler {
        ClickOutcome onClick(SlotClickContext context);
    }

    public static final class Builder {

        private final String name;
        private Matcher matcher = context -> true;
        private @Nullable ItemOverrideHandler itemOverrideHandler;
        private @Nullable ClickHandler clickHandler;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder matches(Matcher matcher) {
            this.matcher = Objects.requireNonNull(matcher, "matcher");
            return this;
        }

        public Builder overrideItem(ItemOverrideHandler itemOverrideHandler) {
            this.itemOverrideHandler = Objects.requireNonNull(
                itemOverrideHandler,
                "itemOverrideHandler"
            );
            return this;
        }

        public Builder onClick(ClickHandler clickHandler) {
            this.clickHandler = Objects.requireNonNull(clickHandler, "clickHandler");
            return this;
        }

        public SlotBehaviorRegistration build() {
            return new SlotBehaviorRegistration(
                this.name,
                this.matcher,
                this.itemOverrideHandler,
                this.clickHandler
            );
        }
    }
}