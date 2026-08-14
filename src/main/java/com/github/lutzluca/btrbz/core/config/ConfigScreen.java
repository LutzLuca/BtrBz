package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionEventListener.Event;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.YetAnotherConfigLib.Builder;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigScreen {

    //? if <26.2 {
    public static void open() {
        var client = Minecraft.getInstance();
        client.schedule(() -> client.setScreen(ConfigScreen.create(
            client.screen,
            ConfigManager.get())));
    }
    //?} else {
    /*public static void open() {
        var client = Minecraft.getInstance();
        client.schedule(() -> client.gui.setScreen(ConfigScreen.create(
            client.gui.screen(),
            ConfigManager.get()
        )));
    }
    *///?}

    public static Screen create(Screen parent, Config config) {
        return YetAnotherConfigLib.create(
            ConfigManager.HANDLER, (_, _, builder) -> {
                builder.title(Component.literal(BtrBz.MOD_ID));
                buildCategories(builder, config);

                return builder;
            }).generateScreen(parent);
    }

    private static void buildCategories(Builder builder, Config config) {
        var widgetBuilder = ConfigCategory.createBuilder()
            .name(Component.literal("Widgets"))
            .tooltip(Component.literal("Configure BtrBz widgets and the Widget Manager."))
            .options(widgetManagerOptions())
            .options(widgetOptions(BtrBz.widgetRuntime().registry()));
        var widgets = widgetBuilder.build();

        var ordersAndNotifications = ConfigCategory
            .createBuilder()
            .name(Component.literal("Orders & Notifications"))
            .tooltip(Component.literal(
                "Configure order-status notifications, highlighting, and price alerts."))
            .groups(config.trackedOrders.createGroups())
            .group(config.orderHighlight.createGroup())
            .group(config.alert.createGroup())
            .build();

        var interfaceAndTooltips = ConfigCategory
            .createBuilder()
            .name(Component.literal("Interface & Tooltips"))
            .tooltip(Component.literal(
                "Configure hover tooltips, product information, price helpers, and Bazaar chat cleanup."))
            .group(config.orderListTooltip.createGroup())
            .group(config.orderItemTooltip.createGroup())
            .group(config.productInfo.createGroup())
            .group(config.chatFilter.createGroup())
            .build();

        var orderWorkflow = ConfigCategory
            .createBuilder()
            .name(Component.literal("Order Workflow"))
            .tooltip(Component.literal(
                "Configure tools that assist with creating, cancelling, reopening, flipping, and protecting orders."))
            .groups(config.orderActions.createGroups())
            .group(config.flipHelper.createGroup())
            .group(config.orderProtection.createGroup())
            .build();

        builder
            .category(widgets)
            .category(ordersAndNotifications)
            .category(interfaceAndTooltips)
            .category(orderWorkflow);
    }

    static List<ButtonOption> widgetOptions(WidgetRegistry registry) {
        return registry.all().stream()
            .map(ConfigScreen::widgetOption)
            .toList();
    }

    static List<Option<?>> widgetManagerOptions() {
        var widgetRuntime = BtrBz.widgetRuntime();

        var openManager = ButtonOption.createBuilder()
            .name(Component.literal("Open Widget Manager"))
            .text(Component.literal("Open"))
            .description(createDescription(
                "Open the widget manager without using the Bazaar quick-access button.",
                ConfigImages.WidgetManagerButton))
            .action((screen, _) -> Minecraft.getInstance().setScreen(
                widgetRuntime.createManagementScreen(screen)))
            .build();

        var resetPosition = ButtonOption.createBuilder()
            .name(Component.literal("Reset Widget Manager Button Position"))
            .text(Component.literal("Reset"))
            .description(createDescription(
                "Restore the Bazaar quick-access button to its default position."))
            .action((_, _) -> widgetRuntime.stateStore().resetManagerLauncherPosition(true))
            .build();

        return List.of(openManager, resetPosition);
    }

    private static ButtonOption widgetOption(WidgetDefinition<?, ?, ?> definition) {
        String name = definition.getDisplayName();
        WidgetId id = definition.getId();

        String responsibility = definition.getDescription().isBlank()
            ? "Open the Widget Manager focused on " + name + "."
            : definition.getDescription();
        Component description = paragraphs(
            Component.literal(responsibility),
            Component.literal("Configure its placement and settings in the Widget Manager."));
        var image = ConfigImages.forWidget(id);

        var optionDescription = image == null
            ? createDescription(description)
            : createDescription(description, image);

        return ButtonOption.createBuilder()
            .name(Component.literal(name))
            .text(Component.literal("Configure"))
            .description(optionDescription)
            .action((screen, _) -> Minecraft.getInstance().setScreen(
                BtrBz.widgetRuntime().createManagementScreenForWidget(screen, id)))
            .build();
    }

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

    public static final class OptionGrouping {

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
                .forEach(children::add);
            return this;
        }

        public OptionGrouping addSubgroups(OptionGrouping... subgroups) {
            Arrays.stream(subgroups).map(GroupChild.Subgroup::new).forEach(children::add);
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
}
