package com.github.lutzluca.btrbz.core.widgets.presets;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import net.minecraft.resources.Identifier;

public final class OrderPresetsWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "order_presets"));
    private OrderPresetsWidgetDefinition() {}
    public static WidgetDefinition<OrderPresetsWidgetData.Snapshot, OrderPresetsWidgetConfig, OrderPresetsAction> create(
        OrderPresetsComponent component
    ) {
        var data = new OrderPresetsWidgetData(component);
        return WidgetDefinition.<OrderPresetsWidgetData.Snapshot, OrderPresetsWidgetConfig, OrderPresetsAction>builder(ID, "Presets")
            .config(() -> ConfigManager.get().widgets.orderPresets, OrderPresetsWidgetConfig::new,
                config -> config.frame, OrderPresetsWidgetConfig::resetPreferences)
            .supports(session -> session.inBazaarMenu(BazaarMenuType.BuyOrderSetupVolume)
                || session.inSign() && session.previousBazaarMenu(BazaarMenuType.BuyOrderSetupVolume))
            .runtimeData(_ -> data.snapshot())
            .preview(() -> new WidgetPreview<>(OrderPresetsWidgetData.preview(), WidgetPreviewSessions.container(BazaarMenuType.BuyOrderSetupVolume), "default"))
            .viewFactory(OrderPresetsWidgetView::new)
            .actionHandler(new OrderPresetsActionHandler(component))
            .settingsPanel(OrderPresetsWidgetSettings::create)
            .placementProfile("default", "Container")
            .placementProfile("sign", "Sign")
            .minSize(WidgetLayoutTokens.panelWidth(40), 42)
            .build();
    }
}
