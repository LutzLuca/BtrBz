package com.github.lutzluca.btrbz.core.widgets.dailylimit;

import com.github.lutzluca.btrbz.core.config.ConfigManager;
import com.github.lutzluca.btrbz.core.widgets.WidgetDefinition;
import com.github.lutzluca.btrbz.core.widgets.WidgetId;
import com.github.lutzluca.btrbz.core.widgets.WidgetPreview;
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
        var data = new DailyLimitWidgetData(component);
        return WidgetDefinition.<DailyLimitWidgetData.Snapshot, DailyLimitWidgetConfig, Void>builder(ID, "Daily Limit")
            .config(() -> ConfigManager.get().widgets.orderLimit, DailyLimitWidgetConfig::new,
                config -> config.frame, DailyLimitWidgetConfig::resetPreferences)
            .supports(session -> session.inAnyBazaarMenu(BazaarMenuType.Main, BazaarMenuType.ItemGroup))
            .runtimeData(_ -> data.snapshot())
            .preview(() -> new WidgetPreview<>(DailyLimitWidgetData.preview(), WidgetPreviewSessions.container(BazaarMenuType.Main), "default"))
            .viewFactory(DailyLimitWidgetView::new)
            .settingsPanel(DailyLimitWidgetSettings::create)
            .minSize(WidgetLayoutTokens.panelWidth(MINIMUM_CONTENT_WIDTH), 30)
            .build();
    }
}
