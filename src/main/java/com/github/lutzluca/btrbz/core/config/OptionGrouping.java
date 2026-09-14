package com.github.lutzluca.btrbz.core.config;

import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionEventListener.Event;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OptionGrouping {

    private final @NotNull Option.Builder<Boolean> controllerBuilder;
    private final @NotNull List<GroupChild> children;
    private final @NotNull List<OptionGrouping> controlledGroups;
    private @Nullable Option<Boolean> controllerOption = null;

    public OptionGrouping(@NotNull Option.Builder<Boolean> controllerBuilder) {
        this.controllerBuilder = controllerBuilder;
        this.children = new ArrayList<>();
        this.controlledGroups = new ArrayList<>();
    }

    public OptionGrouping addOptions(Option.Builder<?>... optBuilders) {
        Arrays
            .stream(optBuilders)
            .map(Option.Builder::build)
            .map(GroupChild.SingleOption::new)
            .forEach(this.children::add);
        return this;
    }

    public OptionGrouping addSubgroups(OptionGrouping... subgroups) {
        Arrays.stream(subgroups).map(GroupChild.Subgroup::new).forEach(this.children::add);
        return this;
    }

    public OptionGrouping controlGroups(OptionGrouping... groups) {
        this.controlledGroups.addAll(Arrays.asList(groups));
        return this;
    }

    public List<Option<?>> build() {
        if (this.controllerOption != null) {
            throw new IllegalStateException("OptionGrouping already built");
        }

        var opts = this.children
            .stream()
            .flatMap(child -> child.build().stream())
            .collect(Collectors.toList());

        this.controllerBuilder.addListener((option, event) -> {
            if (event == Event.STATE_CHANGE || event == Event.AVAILABILITY_CHANGE) {
                this.propagateAvailability();
            }
        });

        this.controllerOption = this.controllerBuilder.build();
        this.propagateAvailability();

        opts.addFirst(this.controllerOption);
        return opts;
    }

    void setAvailable(boolean available) {
        if (this.controllerOption == null) {
            throw new IllegalStateException("Must call `build` before `setAvailable`");
        }
        this.controllerOption.setAvailable(available);
    }

    private void propagateAvailability() {
        if (this.controllerOption == null) {
            return;
        }

        boolean childAvailable = this.controllerOption.available() && this.controllerOption.pendingValue();
        this.children.forEach(child -> child.setAvailable(childAvailable));
        this.controlledGroups.forEach(group -> group.setAvailable(childAvailable));
    }

    private sealed interface GroupChild {

        List<Option<?>> build();

        void setAvailable(boolean available);

        record SingleOption(Option<?> opt) implements GroupChild {

            @Override
            public List<Option<?>> build() {
                return List.of(this.opt);
            }

            @Override
            public void setAvailable(boolean available) {
                this.opt.setAvailable(available);
            }
        }

        record Subgroup(OptionGrouping subgroup) implements GroupChild {

            @Override
            public List<Option<?>> build() {
                return this.subgroup.build();
            }

            @Override
            public void setAvailable(boolean available) {
                this.subgroup.setAvailable(available);
            }
        }
    }
}
