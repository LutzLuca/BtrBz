package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetCacheKey;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.data.OrdersWidgetData;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import net.minecraft.resources.Identifier;

public final class TrackedOrdersWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "tracked_orders_list"));
    private TrackedOrdersWidgetDefinition() {}
    public static WidgetDefinition<BazaarWidgetViewData.OrdersData, TrackedOrdersWidgetConfig, TrackedOrdersAction> create(
        OrdersWidgetData provider,
        TrackedOrderManager trackedOrders
    ) {
        return WidgetDefinition.<BazaarWidgetViewData.OrdersData, TrackedOrdersWidgetConfig, TrackedOrdersAction>builder(ID, "Tracked Orders")
            .config(() -> ConfigManager.get().widgets.trackedOrders, TrackedOrdersWidgetConfig::new,
                config -> config.frame, TrackedOrdersWidgetConfig::resetPreferences)
            .supports(WidgetSession::inBazaarContainer)
            .visibility((data, _, _) -> !data.orders().isEmpty())
            .runtimeData(_ -> provider.snapshot())
            .cacheKey(_ -> new CacheKey(
                provider.snapshotKey(),
                TrackedOrdersWidgetConfig.Snapshot.of(ConfigManager.get().widgets.trackedOrders)
            ))
            .preview(() -> new WidgetPreview<>(OrdersWidgetData.preview(), WidgetPreviewSessions.container(BazaarMenuType.Item), "default"))
            .viewFactory(TrackedOrdersWidgetView::new)
            .actionHandler(new TrackedOrdersActionHandler(trackedOrders))
            .settingsPanel(TrackedOrdersWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(200), 16)
            .build();
    }

    private record CacheKey(
        OrdersWidgetData.SnapshotKey data,
        TrackedOrdersWidgetConfig.Snapshot config
    ) implements WidgetCacheKey {}
}
