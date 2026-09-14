package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.core.Activation;
import com.github.lutzluca.btrbz.core.OrderTooltipProvider;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetRegistry;
import com.github.lutzluca.btrbz.core.widgets.WidgetRuntime;
import com.github.lutzluca.btrbz.utils.GameUtils;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.YetAnotherConfigLib.Builder;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConfigScreen {

    private final WidgetRuntime widgetRuntime;
    private final Activation activation;
    private final OrderTooltipProvider tooltipProvider;

    public ConfigScreen(
        WidgetRuntime widgetRuntime,
        Activation activation,
        OrderTooltipProvider tooltipProvider
    ) {
        this.widgetRuntime = Objects.requireNonNull(widgetRuntime, "widgetRuntime cannot be null");
        this.activation = Objects.requireNonNull(activation, "activation cannot be null");
        this.tooltipProvider = Objects.requireNonNull(tooltipProvider, "tooltipProvider cannot be null");
    }

    public void open() {
        var client = Minecraft.getInstance();
        client.schedule(() -> GameUtils.setScreen(this.create(GameUtils.screen())));
    }

    public Screen create(Screen parent) {
        return YetAnotherConfigLib.create(
            ConfigStore.get().handler(), (_, config, builder) -> {
                builder.title(Component.literal(BtrBz.MOD_ID));
                this.buildCategories(builder, config);

                return builder;
            }).generateScreen(parent);
    }

    private void buildCategories(Builder builder, Config config) {
        var general = ConfigCategory
            .createBuilder()
            .name(Component.literal("General"))
            .tooltip(Component.literal("Configure when BtrBz runs."))
            .group(this.activationGroup(config))
            .build();

        var widgetBuilder = ConfigCategory.createBuilder()
            .name(Component.literal("Widgets"))
            .tooltip(Component.literal("Configure BtrBz widgets and the Widget Manager."))
            .options(this.widgetManagerOptions())
            .options(widgetOptions(
                this.widgetRuntime.registry(),
                (screen, id) -> GameUtils.setScreen(
                    this.widgetRuntime.createManagementScreenForWidget(screen, id))));
        var widgets = widgetBuilder.build();

        var ordersAndNotifications = ConfigCategory
            .createBuilder()
            .name(Component.literal("Orders & Notifications"))
            .tooltip(Component.literal(
                "Configure order-status notifications, highlighting, and price alerts."))
            .groups(config.trackedOrders.createGroups(this.tooltipProvider::onQueueDisplayModeChanged))
            .group(config.orderHighlight.createGroup())
            .group(config.alert.createGroup())
            .build();

        var interfaceAndTooltips = ConfigCategory
            .createBuilder()
            .name(Component.literal("Interface & Tooltips"))
            .tooltip(Component.literal(
                "Configure hover tooltips, product information, price helpers, and Bazaar chat cleanup."))
            .group(config.orderListTooltip.createGroup(
                () -> this.tooltipProvider.onListSettingsChanged("order-list tooltip setting changed")))
            .group(config.orderItemTooltip.createGroup(this.tooltipProvider::onItemSettingsChanged))
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
            .category(general)
            .category(widgets)
            .category(ordersAndNotifications)
            .category(interfaceAndTooltips)
            .category(orderWorkflow);
    }

    private OptionGroup activationGroup(Config config) {
        var enabled = Option
            .<Boolean>createBuilder()
            .name(Component.literal("Enable BtrBz"))
            .description(createDescription("Enable all BtrBz features."))
            .binding(
                true,
                () -> config.enabled,
                value -> {
                    config.enabled = value;
                    this.activation.refresh();
                })
            .controller(ConfigScreen::createBooleanController);
        var alwaysActive = Option
            .<Boolean>createBuilder()
            .name(Component.literal("Always Active"))
            .description(createDescription(
                "Keep BtrBz running everywhere, including outside SkyBlock, without waiting for "
                    + "a Hypixel Mod API location packet."))
            .binding(
                false,
                () -> config.alwaysActive,
                value -> {
                    config.alwaysActive = value;
                    this.activation.refresh();
                })
            .controller(ConfigScreen::createBooleanController);

        return OptionGroup
            .createBuilder()
            .name(Component.literal("Activation"))
            .description(createDescription(
                "Control whether BtrBz runs and whether a confirmed SkyBlock session is required."))
            .options(new OptionGrouping(enabled).addOptions(alwaysActive).build())
            .build();
    }

    static List<ButtonOption> widgetOptions(
        WidgetRegistry registry,
        BiConsumer<Screen, WidgetId> openWidgetManager
    ) {
        return registry.all().stream()
            .map(definition -> widgetOption(definition, openWidgetManager))
            .toList();
    }

    private List<Option<?>> widgetManagerOptions() {
        var openManager = ButtonOption.createBuilder()
            .name(Component.literal("Open Widget Manager"))
            .text(Component.literal("Open"))
            .description(createDescription(
                "Open the widget manager without using the Bazaar quick-access button.",
                ConfigImages.WidgetManagerButton))
            .action((screen, _) -> GameUtils.setScreen(
                this.widgetRuntime.createManagementScreen(screen)))
            .build();

        var resetPosition = ButtonOption.createBuilder()
            .name(Component.literal("Reset Widget Manager Button Position"))
            .text(Component.literal("Reset"))
            .description(createDescription(
                "Restore the Bazaar quick-access button to its default position."))
            .action((_, _) -> this.widgetRuntime.stateStore().resetManagerLauncherPosition(true))
            .build();

        return List.of(openManager, resetPosition);
    }

    private static ButtonOption widgetOption(
        WidgetDefinition<?, ?, ?> definition,
        BiConsumer<Screen, WidgetId> openWidgetManager
    ) {
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
            .action((screen, _) -> openWidgetManager.accept(screen, id))
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
}
