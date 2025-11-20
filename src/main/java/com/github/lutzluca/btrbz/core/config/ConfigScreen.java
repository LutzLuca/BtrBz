package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionEventListener.Event;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.YetAnotherConfigLib.Builder;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigScreen {

    public static void open() {
        var client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(ConfigScreen.create(
            client.currentScreen,
            ConfigManager.get()
        )));
    }

    public static Screen create(Screen parent, Config config) {
        return YetAnotherConfigLib.create(
            ConfigManager.HANDLER, (defaults, cfg, builder) -> {
                builder.title(Text.literal(BtrBz.MOD_ID));
                buildGeneralConfig(builder, config);

                return builder;
            }
        ).generateScreen(parent);
    }

    private static void buildGeneralConfig(Builder builder, Config config) {
        var general = ConfigCategory
            .createBuilder()
            .name(Text.literal("General"))
            .group(config.trackedOrders.createGroup())
            .group(config.alert.createGroup())
            .group(config.orderLimit.createGroup())
            .group(config.bookmark.createGroup())
            .group(config.priceDiff.createGroup())
            .group(config.productInfo.createGroup())
            .group(config.orderActions.createGroup())
            .group(config.orderHighlight.createGroup())
            .group(config.flipHelper.createGroup())
            .group(config.orderPresets.createGroup())
            .group(config.orderValueOverlay.createGroup())
            .group(config.orderList.getGroup())
            .group(config.orderProtection.createGroup())
            .build();

        builder.category(general);
    }

    public static BooleanControllerBuilder createBooleanController(Option<Boolean> option) {
        return BooleanControllerBuilder.create(option).onOffFormatter().coloured(true);
    }

    public static final class OptionGrouping {

        private final @NotNull Option.Builder<Boolean> controllerBuilder;
        private final @NotNull List<GroupChild> children;
        private @Nullable Option<Boolean> controllerOption = null;

        public OptionGrouping(@NotNull Option.Builder<Boolean> controllerBuilder) {
            this.controllerBuilder = controllerBuilder;
            this.children = new ArrayList<>();
        }

        public OptionGrouping addOptions(Option.Builder<?>... optBuilders) {
            Arrays
                .stream(optBuilders)
                .map(Option.Builder::build)
                .map(GroupChild.SingleOption::new)
                .forEach(children::add);
            return this;
        }

        public OptionGrouping addSubgroups(OptionGrouping... subgroups) {
            Arrays.stream(subgroups).map(GroupChild.Subgroup::new).forEach(children::add);
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
            if (this.controllerOption == null) { return; }

            boolean childAvailable = this.controllerOption.available() && this.controllerOption.pendingValue();
            this.children.forEach(child -> child.setAvailable(childAvailable));
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
}
