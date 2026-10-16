package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel;
import io.wispforest.owo.ui.core.UIComponent;

public final class PriceDifferenceWidgetSettings {
    private PriceDifferenceWidgetSettings() {}

    public static UIComponent create(WidgetConfigBinding<PriceDifferenceWidgetConfig> binding) {
        var panel = WidgetSettingsPanel.panel();

        WidgetSettingsPanel.enumeration(panel, "Display", binding, c -> c.mode, (c, v) -> c.mode = v,
            "Compact shows both quotes and their difference. Detailed adds side-by-side market context. "
                + "Difference is the lowest sell offer minus the highest buy order, in coins per item, before fees.");

        WidgetSettingsPanel.integer(panel, "Widget width", binding, c -> c.contentWidth,
            (c, v) -> c.contentWidth = v, 180, 420,
            "Preferred content width. Two columns keep the widget short. Quotes, labels and numbers set "
                + "the minimum width, so narrow settings can grow. Product names stay on one line.");

        WidgetSettingsPanel.enumeration(panel, "Large numbers", binding, c -> c.numberFormat,
            (c, v) -> c.numberFormat = v,
            "Short abbreviates totals and activity with k, M and B. Full shows grouped numbers. "
                + "Best quotes and per-item difference always show one decimal. Calculations use full precision.");

        WidgetSettingsPanel.bool(panel, "Inventory difference", binding, c -> c.showInventoryTotal,
            (c, v) -> c.showInventoryTotal = v,
            "Sellable inventory count times the per-item difference, before fees. Zero stock stays visible. "
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
