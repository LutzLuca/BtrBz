package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
import com.github.lutzluca.btrbz.core.widgets.cache.MemoizedWidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigHandle;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetPreviewSessions;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import net.minecraft.resources.Identifier;

public final class DailyLimitWidgetDefinition {
    public static final WidgetId ID = WidgetId.of(Identifier.fromNamespaceAndPath("btrbz", "order_limit"));
    static final int MINIMUM_CONTENT_WIDTH = 60;
    private DailyLimitWidgetDefinition() {}

    public static WidgetDefinition<DailyLimitWidgetData.Snapshot, DailyLimitWidgetConfig, Void> create(
        DailyLimitComponent component
    ) {
        var config = new WidgetConfigHandle<>(ID,
            () -> ConfigManager.get().widgets.orderLimit, DailyLimitWidgetConfig::new,
            value -> value.frame, DailyLimitWidgetConfig::resetPreferences);
        var data = new MemoizedWidgetDataSource<>(new DailyLimitWidgetData(component, config));
        return WidgetDefinition.<DailyLimitWidgetData.Snapshot, DailyLimitWidgetConfig, Void>builder(ID, "Daily Limit")
            .config(config)
            .supports(DailyLimitWidgetDefinition::supportsSession)
            .data(data)
            .cachePrepared()
            .preview(() -> new WidgetPreview<>(DailyLimitWidgetData.preview(), WidgetPreviewSessions.container(BazaarMenuType.Main), "default"))
            .viewFactory(DailyLimitWidgetView::new)
            .settingsPanel(DailyLimitWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(MINIMUM_CONTENT_WIDTH), 30)
            .build();
    }

    public static boolean supportsSession(com.github.lutzluca.btrbz.core.widgets.session.WidgetSession session) {
        return session.inAnyBazaarMenu(BazaarMenuType.Main, BazaarMenuType.ItemGroup);
    }
}
