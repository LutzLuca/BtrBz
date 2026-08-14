package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.MemoizedWidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import net.minecraft.resources.Identifier;

public final class PriceDifferenceWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "price_diff"));

    private PriceDifferenceWidgetDefinition() {}

    public static WidgetDefinition<PriceDifferenceWidgetData.Snapshot, PriceDifferenceWidgetConfig, Void> create(
        BazaarData market
    ) {
        var config = new WidgetConfigHandle<>(ID,
            () -> ConfigManager.get().widgets.priceDiff, PriceDifferenceWidgetConfig::new,
            value -> value.frame, PriceDifferenceWidgetConfig::resetPreferences);
        var provider = new MemoizedWidgetDataSource<>(new PriceDifferenceWidgetData(market));

        return WidgetDefinition.<PriceDifferenceWidgetData.Snapshot, PriceDifferenceWidgetConfig, Void>builder(ID,
            "Price Difference")
            .description(
                "Shows the per-item and total difference between the entered price and the current market price.")
            .config(config)
            .supports(PriceDifferenceWidgetDefinition::supportsSession)
            .visibility((data, _, _) -> PriceDifferenceWidgetDefinition.isVisible(data))
            .data(provider)
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(PriceDifferenceWidgetData.preview(),
                WidgetPreviewSessions.container(BazaarMenuType.Item), "default"))
            .viewFactory(PriceDifferenceWidgetView::new)
            .settingsPanel(PriceDifferenceWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(80), 36)
            .build();
    }

    public static boolean supportsSession(WidgetSession session) {
        return session.inBazaarMenu(BazaarMenuType.Item);
    }

    public static boolean isVisible(PriceDifferenceWidgetData.Snapshot data) {
        return data.quantity() > 0;
    }
}
