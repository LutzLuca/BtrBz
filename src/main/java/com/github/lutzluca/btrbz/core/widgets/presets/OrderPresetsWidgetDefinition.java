package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.MemoizedWidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import net.minecraft.resources.Identifier;

public final class OrderPresetsWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "order_presets"));

    private OrderPresetsWidgetDefinition() {
    }

    public static WidgetDefinition<OrderPresetsWidgetData.Snapshot, OrderPresetsWidgetConfig, OrderPresetsAction> create(
        OrderPresetsComponent component
    ) {
        var config = new WidgetConfigHandle<>(ID,
            () -> ConfigManager.get().widgets.orderPresets, OrderPresetsWidgetConfig::new,
            value -> value.frame, OrderPresetsWidgetConfig::resetPreferences);
        var data = new MemoizedWidgetDataSource<>(
            new OrderPresetsWidgetData(component, config)
        );

        return WidgetDefinition.<OrderPresetsWidgetData.Snapshot, OrderPresetsWidgetConfig, OrderPresetsAction>builder(ID, "Presets")
            .description("Offers reusable quantity presets when choosing how many items to buy or sell.")
            .config(config)
            .supports(OrderPresetsWidgetDefinition::supportsSession)
            .data(data)
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(OrderPresetsWidgetData.preview(), WidgetPreviewSessions.container(BazaarMenuType.BuyOrderSetupVolume), "default"))
            .viewFactory(OrderPresetsWidgetView::new)
            .actionHandler(new OrderPresetsActionHandler(component))
            .settingsPanel(OrderPresetsWidgetSettings::create)
            .placementProfile("default", "Container")
            .placementProfile("sign", "Sign")
            .minSize(WidgetLayoutTokens.panelWidth(40), 42)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) {
        return session.inBazaarMenu(BazaarMenuType.BuyOrderSetupVolume)
            || session.inSign() && session.previousBazaarMenu(BazaarMenuType.BuyOrderSetupVolume);
    }
}
