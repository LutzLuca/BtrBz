package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import io.wispforest.owo.ui.core.UIComponent;

public final class PriceDifferenceWidgetSettings {
    private PriceDifferenceWidgetSettings() {}

    public static UIComponent create(WidgetConfigBinding<PriceDifferenceWidgetConfig> binding) {
        var panel = WidgetSettingsPanel.panel();

        WidgetSettingsPanel.enumeration(panel, "Display", binding, c -> c.mode, (c, v) -> c.mode = v,
            "Compact focuses on best quotes and spread. Detailed compares both market sides, "
                + "stacking them when the widget is narrow.");

        WidgetSettingsPanel.integer(panel, "Widget width", binding, c -> c.contentWidth,
            (c, v) -> c.contentWidth = v, 180, 420,
            "Preferred content width. Detailed uses columns from 260, otherwise stacked sides. "
                + "Full numbers can grow the widget to keep values readable.");

        WidgetSettingsPanel.enumeration(panel, "Large numbers", binding, c -> c.numberFormat,
            (c, v) -> c.numberFormat = v,
            "Short abbreviates totals and activity with k, M and B. Full shows grouped numbers. "
                + "Best quotes and per-item spread always show one decimal. Calculations use full precision.");

        WidgetSettingsPanel.bool(panel, "Inventory quote difference", binding, c -> c.showInventoryTotal,
            (c, v) -> c.showInventoryTotal = v,
            "Sellable inventory count times spread, before fees. Zero stock stays visible. "
                + "An unreadable count is unavailable. This is not a depth-aware batch quote.");

        WidgetSettingsPanel.bool(panel, "Detailed: whole-side totals", binding, c -> c.showBookTotals,
            (c, v) -> c.showBookTotals = v,
            "Active orders and listed items across each whole market side, beyond the returned price rows.");

        WidgetSettingsPanel.bool(panel, "Detailed: weekly activity", binding, c -> c.showWeeklyActivity,
            (c, v) -> c.showWeeklyActivity = v,
            "Items per hour from API weekly movement divided by 168. The API includes seven-day "
                + "transacted volume plus live state. This is not measured recent-hour trading.");

        return panel;
    }
}
