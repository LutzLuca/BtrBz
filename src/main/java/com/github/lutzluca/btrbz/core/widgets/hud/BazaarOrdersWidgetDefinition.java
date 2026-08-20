package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.data.OrdersWidgetData;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class BazaarOrdersWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "bazaar_orders"));

    private BazaarOrdersWidgetDefinition() {}

    public static WidgetDefinition<BazaarWidgetViewData.OrdersData, BazaarOrdersWidgetConfig, Void> create(
        WidgetDataSource<BazaarWidgetViewData.OrdersData> provider,
        Supplier<Component> toggleKeyLabel
    ) {
        var config = new WidgetConfigHandle<>(ID,
            () -> ConfigManager.get().widgets.bazaarOrders, BazaarOrdersWidgetConfig::new,
            value -> value.frame, BazaarOrdersWidgetConfig::resetPreferences);

        return WidgetDefinition.<BazaarWidgetViewData.OrdersData, BazaarOrdersWidgetConfig, Void>builder(ID,
            "Bazaar Orders")
            .description(
                "Shows tracked Bazaar orders on the HUD with their status, amount, price, and market position.")
            .config(config)
            .supports(BazaarOrdersWidgetDefinition::supportsSession)
            .visibility((data, _, _) -> !data.orders().isEmpty() || data.filledOrderCount() > 0)
            .data(provider)
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(OrdersWidgetData.preview(), WidgetPreviewSessions.hud(), "default"))
            .viewFactory(() -> new BazaarOrdersWidgetView(toggleKeyLabel))
            .settingsPanel(BazaarOrdersWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(BazaarHudOptions.MINIMUM_CONTENT_WIDTH), 28)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) {
        return session.inHud();
    }
}
