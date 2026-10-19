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
import java.util.UUID;
import java.util.function.Supplier;
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
            .description("A small inbox for fired alerts. Open an item or dismiss an event.")
            .config(config)
            .supports(AlertsWidgetDefinition::supportsSession)
            .visibility((snapshot, _, _) -> manager.enabled() && !snapshot.entries().isEmpty())
            .data(new MemoizedWidgetDataSource<>(new AlertsWidgetData(manager, data)))
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(AlertsWidgetData.preview(),
                WidgetPreviewSessions.container(BazaarMenuType.Item), "default"))
            .viewFactory(AlertsWidgetView::new)
            .actionHandler((action, current) -> {
                if (!supportsSession(current) || !manager.enabled()) {
                    return;
                }
                if (current.inSign() && (action instanceof Action.Open || action instanceof Action.Inbox)) {
                    return;
                }
                switch (action) {
                    case Action.Toggle _ -> {
                        config.mutate("alert panel toggled", value -> value.collapsed = !value.collapsed);
                        save.run();
                    }
                    case Action.Inbox _ -> openInbox.run();
                    case Action.Dismiss dismiss -> manager.dismiss(dismiss.id());
                    case Action.Open open -> manager.inbox().stream()
                        .filter(event -> event.id().equals(open.id())).findFirst().ifPresent(event -> {
                            GameUtils.setScreen(null);
                            GameUtils
                                .runCommand("bz " + data.refreshIndexedProduct(event.alert().product).strippedName());
                        });
                }
            })
            .settingsPanel(binding -> {
                var panel = WidgetSettingsPanel.panel();
                WidgetSettingsPanel.integer(panel, "Maximum width", binding, value -> value.contentWidth,
                    (value, width) -> value.contentWidth = width, 80, 160,
                    "Fits short names and truncates long ones. Hover an item for its full condition.");
                WidgetSettingsPanel.bool(panel, "Collapsed", binding, value -> value.collapsed,
                    (value, collapsed) -> value.collapsed = collapsed, "Show only the bell and count.");
                return panel;
            })
            .placementProfile("sign", "Order setup")
            .minSize(40, 22)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) {
        return session.inBazaarContainer() || session.inSign() && session.product().isPresent();
    }

    public sealed interface Action {
        record Toggle() implements Action {}

        record Inbox() implements Action {}

        record Dismiss(UUID id) implements Action {}

        record Open(UUID id) implements Action {}
    }
}
