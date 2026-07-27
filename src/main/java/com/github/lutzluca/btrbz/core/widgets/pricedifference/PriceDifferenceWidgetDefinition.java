package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.data.BazaarData;
import net.minecraft.resources.Identifier;

public final class PriceDifferenceWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "price_diff"));
    private PriceDifferenceWidgetDefinition() {}
    public static WidgetDefinition<PriceDifferenceWidgetData.Snapshot, PriceDifferenceWidgetConfig, Void> create(
        BazaarData market
    ) {
        var provider = new PriceDifferenceWidgetData(market);
        return WidgetDefinition.<PriceDifferenceWidgetData.Snapshot, PriceDifferenceWidgetConfig, Void>builder(ID, "Price Difference")
            .config(() -> ConfigManager.get().widgets.priceDiff, PriceDifferenceWidgetConfig::new,
                config -> config.frame, PriceDifferenceWidgetConfig::resetPreferences)
            .supports(session -> session.inBazaarMenu(BazaarMenuType.Item))
            .visibility((data, _, _) -> data.quantity() > 0)
            .runtimeData(_ -> provider.snapshot())
            .preview(() -> new WidgetPreview<>(PriceDifferenceWidgetData.preview(), WidgetPreviewSessions.container(BazaarMenuType.Item), "default"))
            .viewFactory(PriceDifferenceWidgetView::new)
            .settingsPanel(PriceDifferenceWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(150), 36)
            .build();
    }
}
