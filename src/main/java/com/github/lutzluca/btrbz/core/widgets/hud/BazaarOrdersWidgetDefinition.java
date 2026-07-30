package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.data.OrdersWidgetData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import net.minecraft.resources.Identifier;

public final class BazaarOrdersWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "bazaar_orders"));
    private BazaarOrdersWidgetDefinition() {}

    public static WidgetDefinition<BazaarWidgetViewData.OrdersData, BazaarOrdersWidgetConfig, Void> create(
        OrdersWidgetData provider
    ) {
        return WidgetDefinition.<BazaarWidgetViewData.OrdersData, BazaarOrdersWidgetConfig, Void>builder(ID, "Bazaar Orders")
            .config(() -> ConfigManager.get().widgets.bazaarOrders, BazaarOrdersWidgetConfig::new,
                config -> config.frame, BazaarOrdersWidgetConfig::resetPreferences)
            .supports(WidgetSession::inHud)
            .visibility((data, config, _) -> !config.hideWhenEmpty
                || !data.orders().isEmpty() || data.filledOrderCount() > 0)
            .runtimeData(_ -> provider.snapshot())
            .snapshotCopy(BazaarWidgetViewData.OrdersData::detachedCopy)
            .preview(() -> new WidgetPreview<>(OrdersWidgetData.preview(), WidgetPreviewSessions.hud(), "default"))
            .viewFactory(BazaarOrdersWidgetView::new)
            .settingsPanel(BazaarOrdersWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(BazaarHudOptions.MINIMUM_CONTENT_WIDTH), 28)
            .build();
    }
}
