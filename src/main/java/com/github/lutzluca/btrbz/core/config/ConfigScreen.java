package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.BtrBz;
import dev.isxander.yacl3.api.ConfigCategory;
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
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfigScreen {

    public static void open() {
        var client = Minecraft.getInstance();
        client.schedule(() -> client.setScreen(ConfigScreen.create(
            client.screen,
            ConfigManager.get()
        )));
    }

    public static Screen create(Screen parent, Config config) {
        return YetAnotherConfigLib.create(
            ConfigManager.HANDLER, (defaults, cfg, builder) -> {
                builder.title(Component.literal(BtrBz.MOD_ID));
                buildCategories(builder, config);

                return builder;
            }
        ).generateScreen(parent);
    }

    private static void buildCategories(Builder builder, Config config) {
        var ordersAndNotifications = ConfigCategory
            .createBuilder()
            .name(Component.literal("Orders & Notifications"))
            .tooltip(Component.literal(
                "Configure order-status notifications, highlighting, and price alerts."))
            .groups(config.trackedOrders.createGroups())
            .group(config.orderHighlight.createGroup())
            .group(config.alert.createGroup())
            .build();

        var interfaceAndOverlays = ConfigCategory
            .createBuilder()
            .name(Component.literal("Interface & Overlays"))
            .tooltip(Component.literal(
                "Configure Bazaar overlays, hover tooltips, product information, price helpers, and chat cleanup."))
            .group(config.orderList.getGroup())
            .group(config.orderListTooltip.createGroup())
            .group(config.orderItemTooltip.createGroup())
            .group(config.bookmark.createGroup())
            .group(config.productInfo.createGroup())
            .group(config.priceDiff.createGroup())
            .group(config.orderValueOverlay.createGroup())
            .group(config.orderBookPrice.createGroup())
            .group(config.chatFilter.createGroup())
            .build();

        var orderWorkflow = ConfigCategory
            .createBuilder()
            .name(Component.literal("Order Workflow"))
            .tooltip(Component.literal(
                "Configure tools that assist with creating, cancelling, reopening, and flipping orders."))
            .groups(config.orderActions.createGroups())
            .group(config.flipHelper.createGroup())
            .group(config.orderPresets.createGroup())
            .group(config.orderBook.createGroup())
            .build();

        var safetyAndLimits = ConfigCategory
            .createBuilder()
            .name(Component.literal("Safety & Limits"))
            .tooltip(Component.literal(
                "Prevent risky order prices and configure the daily transaction-limit display."))
            .group(config.orderProtection.createGroup())
            .group(config.orderLimit.createGroup())
            .build();

        builder
            .category(ordersAndNotifications)
            .category(interfaceAndOverlays)
            .category(orderWorkflow)
            .category(safetyAndLimits);
    }

    public static OptionDescription createDescription(String text) {
        return OptionDescription.of(Component.literal(text));
    }

    public static OptionDescription createDescription(Component text) {
        return OptionDescription.of(text);
    }

    public static OptionDescription createDescription(String text, ConfigImage image) {
        return createDescription(Component.literal(text), image);
    }

    public static OptionDescription createDescription(Component text, ConfigImage image) {
        return OptionDescription
            .createBuilder()
            .text(text)
            .image(image.identifier, image.width, image.height)
            .build();
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

    public enum ConfigImage {
        PRICE_ALERT("alert-registration-and-firing.png", 658, 202),
        BOOKMARKS("bookmark-with-order-indicators.png", 497, 373),
        ORDER_BOOK("custom-order-book-overlay.png", 1472, 828),
        DAILY_LIMIT("daily-limit-overlay.png", 547, 410),
        FLIP_HELPER("flip-helper.png", 994, 654),
        ORDER_LIST_TOOLTIP("order-list-tooltips.png", 710, 399),
        ORDER_NOTIFICATION("order-notifications.png", 661, 235),
        ORDER_PRESETS("order-presets.png", 619, 348),
        ORDER_PROTECTION("order-protection-blocking.png", 1092, 614),
        ORDER_STATUS("order-status-highlight.png", 423, 238),
        ORDER_TOOLTIP("order-tooltip.png", 542, 305),
        ORDER_VALUE("order-value-overlay.png", 550, 412),
        PRICE_DIFFERENCE("price-diff-overlay.png", 687, 515),
        PRICE_ENTRY_ORDER_BOOK("price-entry-order-book.png", 1212, 682),
        PRODUCT_INFO_PAPER("product-info-paper.png", 622, 350),
        PRODUCT_INFO("product-info.png", 588, 218),
        REOPEN_LAST_ORDER("reopen-last-order.png", 578, 325),
        TRACKED_ORDER_OVERLAY("tracked-order-overlay.png", 490, 368);

        private final Identifier identifier;
        private final int width;
        private final int height;

        ConfigImage(String fileName, int width, int height) {
            this.identifier = Identifier.fromNamespaceAndPath(
                BtrBz.MOD_ID,
                "textures/gui/config/" + fileName
            );
            this.width = width;
            this.height = height;
        }
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
            if (this.controllerOption == null) { return; }

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
