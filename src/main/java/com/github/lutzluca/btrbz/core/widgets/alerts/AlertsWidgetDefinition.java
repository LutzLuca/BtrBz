package com.github.lutzluca.btrbz.core.widgets.alerts;

import com.github.lutzluca.btrbz.core.alert.AlertManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.MemoizedWidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import io.vavr.control.Try;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class AlertsWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "alerts"));

    private AlertsWidgetDefinition() {}

    public static WidgetDefinition<AlertsWidgetData.Snapshot, AlertsWidgetConfig, Action> create(
        AlertManager manager,
        BazaarData data,
        Supplier<AlertsWidgetConfig> configSupplier,
        Runnable save,
        Runnable openInbox
    ) {
        var config = new WidgetConfigHandle<>(ID, configSupplier, AlertsWidgetConfig::new,
            value -> value.frame, AlertsWidgetConfig::resetPreferences);
        return WidgetDefinition.<AlertsWidgetData.Snapshot, AlertsWidgetConfig, Action>builder(ID, "Alerts")
            .description("A small inbox for fired alerts. Open an item, dismiss an event, or postpone reminders.")
            .config(config)
            .supports(AlertsWidgetDefinition::supportsSession)
            .visibility((snapshot, _, _) -> manager.enabled() && !snapshot.entries().isEmpty())
            .data(new MemoizedWidgetDataSource<>(new AlertsWidgetData(manager, data)))
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(AlertsWidgetData.preview(),
                WidgetPreviewSessions.container(BazaarMenuType.Item), "default"))
            .viewFactory(() -> new AlertsWidgetView(manager::remindersEnabled))
            .actionHandler((action, current) -> {
                if (!supportsSession(current) || !manager.enabled()) {
                    return;
                }
                if (current.inSign() && (action instanceof Action.Open || action instanceof Action.Inbox)) {
                    return;
                }
                Try<?> result = switch (action) {
                    case Action.Toggle _ -> {
                        boolean previous = config.current().collapsed;
                        yield Try.run(() -> {
                            config.mutate("alert panel toggled", value -> value.collapsed = !previous);
                            save.run();
                        }).onFailure(_ -> config.mutate("alert panel restored", value -> value.collapsed = previous));
                    }
                    case Action.Inbox _ -> Try.run(openInbox::run);
                    case Action.Snooze _ -> manager.snooze();
                    case Action.Dismiss dismiss -> manager.dismiss(dismiss.id());
                    case Action.Open open -> Try.run(() -> manager.inbox().stream()
                        .filter(event -> event.id().equals(open.id())).findFirst().ifPresent(event -> {
                            GameUtils.setScreen(null);
                            GameUtils
                                .runCommand("bz " + data.refreshIndexedProduct(event.alert().product).strippedName());
                        }));
                };
                result.onFailure(_ -> Notifier.notifyPlayer(Notifier.prefix()
                    .append(Component.literal("Could not update alerts. Please try again."))));
            })
            .settingsPanel(binding -> {
                var panel = WidgetSettingsPanel.panel();
                WidgetSettingsPanel.integer(panel, "Maximum width", binding, value -> value.contentWidth,
                    (value, width) -> value.contentWidth = width, 80, 160,
                    "Fits short names and truncates long ones. Hover an item for its full condition.");
                WidgetSettingsPanel.bool(panel, "Collapsed", binding, value -> value.collapsed,
                    (value, collapsed) -> value.collapsed = collapsed, "Show only the bell, count and reminder clock.");
                return panel;
            })
            .placementProfile("sign", "Order setup")
            .minSize(60, 22)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) {
        return session.inBazaarContainer() || session.inSign() && session.product().isPresent();
    }

    public sealed interface Action {
        record Toggle() implements Action {}

        record Inbox() implements Action {}

        record Snooze() implements Action {}

        record Dismiss(UUID id) implements Action {}

        record Open(UUID id) implements Action {}
    }
}
