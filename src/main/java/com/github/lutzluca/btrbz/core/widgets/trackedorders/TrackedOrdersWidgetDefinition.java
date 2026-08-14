package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData.OrdersData;
import com.github.lutzluca.btrbz.core.widgets.data.OrdersWidgetData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import net.minecraft.resources.Identifier;

public final class TrackedOrdersWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "tracked_orders_list"));

    private TrackedOrdersWidgetDefinition() {}

    public static WidgetDefinition<OrdersData, TrackedOrdersWidgetConfig, TrackedOrdersAction> create(
        WidgetDataSource<OrdersData> provider,
        TrackedOrderManager trackedOrders
    ) {
        var config = new WidgetConfigHandle<>(ID,
            () -> ConfigManager.get().widgets.trackedOrders, TrackedOrdersWidgetConfig::new,
            value -> value.frame, TrackedOrdersWidgetConfig::resetPreferences);

        return WidgetDefinition.<OrdersData, TrackedOrdersWidgetConfig, TrackedOrdersAction>builder(
            ID, "Tracked Orders")
            .description("Shows tracked orders inside Bazaar screens with status, fill progress, and queue details. "
                + "The fill-progress bar is a snapshot and may not reflect the current live state.")
            .config(config)
            .supports(TrackedOrdersWidgetDefinition::supportsSession)
            .visibility((data, _, _) -> !data.orders().isEmpty())
            .data(provider)
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(OrdersWidgetData.preview(),
                WidgetPreviewSessions.container(BazaarMenuType.Item), "default"))
            .viewFactory(TrackedOrdersWidgetView::new)
            .actionHandler(new TrackedOrdersActionHandler(trackedOrders))
            .settingsPanel(TrackedOrdersWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(200), 16)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) {
        return session.inBazaarContainer();
    }
}
