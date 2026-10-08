package com.github.lutzluca.btrbz.core.trackedorders;

import com.github.lutzluca.btrbz.core.config.ConfigUi;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.config.ConfigImages;
import com.github.lutzluca.btrbz.core.config.OptionGrouping;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class OrderManagerConfig {

    public boolean enabled = true;

    public boolean notifyBest = true;
    public boolean onlyOnPriorityRegain = true;
    public boolean soundBest = false;
    public boolean notifyMatched = true;
    public boolean soundMatched = true;
    public boolean notifyUndercut = true;
    public boolean soundUndercut = true;
    public boolean notifySelfUndercut = true;

    public Action gotoOnMatched = Action.Order;
    public Action gotoOnUndercut = Action.Order;

    public boolean showQueueInfo = true;
    public QueueDisplayMode queueDisplayMode = QueueDisplayMode.Both;

    // Not yet fully tested, ghost order interactions with grouped status transitions
    // (e.g. Matched → SelfMatched) are still unreliable. Disabled by default until resolved.
    public boolean groupOrders = false;
    public boolean includePricePerUnit = false;

    public List<OptionGroup> createGroups(Runnable onQueueDisplayModeChanged) {
        var notifyBestGroup = new OptionGrouping(this.createNotifyBestOption())
            .addOptions(
                this.createNotifyBestOnPriorityRegain(),
                this.createSoundBestOption());

        var notifyMatchedGroup = new OptionGrouping(this.createNotifyMatchedOption())
            .addOptions(
                this.createGotoMatchedOption(),
                this.createSoundMatchedOption());

        var notifyUndercutGroup = new OptionGrouping(this.createNotifyUndercutOption())
            .addOptions(
                this.createGotoUndercutOption(),
                this.createSoundUndercutOption());

        var queueGroup = new OptionGrouping(this.createShowQueueInfoOption())
            .addOptions(this.createQueueDisplayModeOption(onQueueDisplayModeChanged));

        var notifyBestOptions = notifyBestGroup.build();
        var notifyMatchedOptions = notifyMatchedGroup.build();
        var notifyUndercutOptions = notifyUndercutGroup.build();
        var queueOptions = queueGroup.build();

        var rootGroup = new OptionGrouping(this.createEnabledOption())
            .addOptions(
                this.createGroupOrdersOption(),
                this.createIncludePricePerUnitOption(),
                this.createNotifySelfUndercutOption())
            .controlGroups(
                notifyBestGroup,
                notifyMatchedGroup,
                notifyUndercutGroup,
                queueGroup);

        return List.of(
            OptionGroup
                .createBuilder()
                .name(Component.literal("Order Notifications"))
                .description(ConfigUi.createDescription(
                    "Enable order-status notifications and configure behavior shared by every notification type.",
                    ConfigImages.OrderNotification))
                .options(rootGroup.build())
                .collapsed(true)
                .build(),
            OptionGroup
                .createBuilder()
                .name(Component.literal("Top Position Notifications"))
                .description(ConfigUi.createDescription(
                    "Choose when BtrBz reports that an order has reached or regained the best market price."))
                .options(notifyBestOptions)
                .collapsed(true)
                .build(),
            OptionGroup
                .createBuilder()
                .name(Component.literal("Matched Order Notifications"))
                .description(ConfigUi.createDescription(
                    "Configure messages sent when your order shares the best price with competing orders."))
                .options(notifyMatchedOptions)
                .collapsed(true)
                .build(),
            OptionGroup
                .createBuilder()
                .name(Component.literal("Undercut Order Notifications"))
                .description(ConfigUi.createDescription(
                    "Configure messages sent when another buy order or sell offer takes priority over yours."))
                .options(notifyUndercutOptions)
                .collapsed(true)
                .build(),
            OptionGroup
                .createBuilder()
                .name(Component.literal("Notification Queue Information"))
                .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                    ConfigUi.text(
                        "Add estimated competing orders and items ahead of yours to matched and undercut "
                            + "notifications."),
                    ConfigUi.note(
                        "The estimate comes from Hypixel's aggregated order book and is not your exact "
                            + "queue position."))))
                .options(queueOptions)
                .collapsed(true)
                .build());
    }

    private Option.Builder<Action> createGotoMatchedOption() {
        return Option
            .<Action>createBuilder()
            .name(Component.literal("Matched Notification Opens"))
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "Choose where the link at the end of a matched-order notification goes."),
                ConfigUi.example(matchedNotificationExample()),
                notificationLinkNote(),
                ConfigUi.requires("Notify When Order Is Matched"))))
            .binding(
                Action.Order,
                () -> this.gotoOnMatched != null ? this.gotoOnMatched : Action.Order,
                action -> this.gotoOnMatched = action)
            .controller(Action::controller);
    }

    private static Component matchedNotificationExample() {
        return notificationExample(TrackedOrderMessages.matchedStatus(false));
    }

    private static Component undercutNotificationExample() {
        return notificationExample(TrackedOrderMessages.undercutStatus(0.1, false));
    }

    private static Component notificationExample(Component statusPart) {
        var msg = TrackedOrderMessages.single(OrderType.Buy, 4,
            Component.literal("Quick Bite I").withStyle(ChatFormatting.WHITE), null, statusPart);
        TrackedOrderMessages.appendQueueInfo(msg, 1, 29, QueueDisplayMode.Both);
        return msg.append(TrackedOrderMessages.navigationAction(Action.Order));
    }

    private static Component notificationLinkNote() {
        return ConfigUi.note(Component
            .literal("The final link changes between ")
            .withStyle(UiStyles.label())
            .append(TrackedOrderMessages.navigationLabel(Action.Order))
            .append(Component.literal(" and ").withStyle(UiStyles.label()))
            .append(TrackedOrderMessages.navigationLabel(Action.Item))
            .append(Component.literal(".").withStyle(UiStyles.label())));
    }

    private Option.Builder<Action> createGotoUndercutOption() {
        return Option
            .<Action>createBuilder()
            .name(Component.literal("Undercut Notification Opens"))
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "Choose where the link at the end of an undercut-order notification goes."),
                ConfigUi.example(undercutNotificationExample()),
                notificationLinkNote(),
                ConfigUi.requires("Notify When Order Is Undercut"))))
            .binding(
                Action.Order,
                () -> this.gotoOnUndercut != null ? this.gotoOnUndercut : Action.Order,
                action -> this.gotoOnUndercut = action)
            .controller(Action::controller);
    }

    private Option.Builder<Boolean> createShowQueueInfoOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Show Queue Info"))
            .binding(true, () -> this.showQueueInfo, val -> this.showQueueInfo = val)
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "Add estimated competing orders and items ahead of yours to matched and undercut "
                        + "notifications."),
                ConfigUi.note("This is an order-book estimate, not an exact queue position."))))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<QueueDisplayMode> createQueueDisplayModeOption(Runnable onQueueDisplayModeChanged) {
        return Option
            .<QueueDisplayMode>createBuilder()
            .name(Component.literal("Queue Display Mode"))
            .binding(
                QueueDisplayMode.Both,
                () -> this.queueDisplayMode != null ? this.queueDisplayMode : QueueDisplayMode.Both,
                mode -> {
                    this.queueDisplayMode = mode;
                    onQueueDisplayModeChanged.run();
                })
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text("Show item counts only, or both order and item counts."),
                ConfigUi.requires("Show Queue Info"))))
            .controller(QueueDisplayMode::controller);
    }

    private Option.Builder<Boolean> createNotifyBestOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Notify When Order Becomes Top"))
            .binding(true, () -> this.notifyBest, val -> this.notifyBest = val)
            .description(OptionDescription.of(Component.literal(
                "Send a message when your order reaches the best available price.")))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createNotifyBestOnPriorityRegain() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.nullToEmpty("Only When Regaining Top Position"))
            .binding(
                true,
                () -> this.onlyOnPriorityRegain,
                val -> this.onlyOnPriorityRegain = val)
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "Skip the initial top-position message and notify only after an order loses and later "
                        + "regains the best price."),
                ConfigUi.requires("Notify When Order Becomes Top"))))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createNotifyMatchedOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Notify When Order Is Matched"))
            .binding(true, () -> this.notifyMatched, val -> this.notifyMatched = val)
            .description(OptionDescription.of(Component.literal(
                "Send a message when your order shares the best price with one or more competing orders.")))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createNotifyUndercutOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Notify When Order Is Undercut"))
            .binding(true, () -> this.notifyUndercut, val -> this.notifyUndercut = val)
            .description(OptionDescription.of(Component.literal(
                "Send a message when another buy order outbids yours or another sell offer lists for less.")))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createSoundBestOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Play Sound for Top Position"))
            .binding(false, () -> this.soundBest, val -> this.soundBest = val)
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text("Play a sound with the top-position notification."),
                ConfigUi.requires("Notify When Order Becomes Top"))))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createSoundMatchedOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Play Sound for Matched Order"))
            .binding(true, () -> this.soundMatched, val -> this.soundMatched = val)
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text("Play a sound when an order begins sharing the best price."),
                ConfigUi.requires("Notify When Order Is Matched"))))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createSoundUndercutOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Play Sound for Undercut Order"))
            .binding(true, () -> this.soundUndercut, val -> this.soundUndercut = val)
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text("Play a sound when another order takes priority over yours."),
                ConfigUi.requires("Notify When Order Is Undercut"))))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createGroupOrdersOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Group Orders (Experimental)"))
            .binding(false, () -> this.groupOrders, val -> this.groupOrders = val)
            .description(ConfigUi.createDescription(ConfigUi.paragraphs(
                ConfigUi.text(
                    "Combine your orders for the same product, side, and price into one notification."),
                ConfigUi.note(
                    "Some unusual grouped-order status transitions are not fully tested."))))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createNotifySelfUndercutOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Warn When Your Orders Compete"))
            .binding(true, () -> this.notifySelfUndercut, val -> this.notifySelfUndercut = val)
            .description(OptionDescription.of(Component.literal(
                "Send a separate notification when one of your own orders undercuts another order for the "
                    + "same product.")))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createIncludePricePerUnitOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Include Price Per Unit"))
            .binding(false, () -> this.includePricePerUnit, val -> this.includePricePerUnit = val)
            .description(OptionDescription.of(Component.literal(
                "Include each order's unit price in status notification messages.")))
            .controller(ConfigUi::createBooleanController);
    }

    private Option.Builder<Boolean> createEnabledOption() {
        return Option
            .<Boolean>createBuilder()
            .name(Component.literal("Enable Order Notifications"))
            .binding(true, () -> this.enabled, val -> this.enabled = val)
            .description(OptionDescription.of(Component.literal(
                "Send messages when a tracked Bazaar order changes its market position.")))
            .controller(ConfigUi::createBooleanController);
    }

    public enum Action {
        None,
        Item,
        Order;

        public static EnumControllerBuilder<Action> controller(Option<Action> option) {
            return EnumControllerBuilder
                .create(option)
                .enumClass(Action.class)
                .formatValue(action -> switch (action) {
                    case None -> Component.literal("No action");
                    case Item -> Component.literal("Go to Item in Bazaar");
                    case Order -> Component.literal("Open Bazaar Orders Page");
                });
        }
    }

    public enum QueueDisplayMode {
        Both,
        ItemsOnly;

        public static EnumControllerBuilder<QueueDisplayMode> controller(Option<QueueDisplayMode> option) {
            return EnumControllerBuilder
                .create(option)
                .enumClass(QueueDisplayMode.class)
                .formatValue(mode -> switch (mode) {
                    case Both -> Component.literal("Orders and Items");
                    case ItemsOnly -> Component.literal("Items Only");
                });
        }
    }
}
