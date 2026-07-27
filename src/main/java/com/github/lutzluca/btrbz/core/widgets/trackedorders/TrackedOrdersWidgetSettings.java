package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetConfigBinding;
import io.wispforest.owo.ui.core.UIComponent;
import static com.github.lutzluca.btrbz.core.widgets.ui.WidgetSettingsPanel.*;

public final class TrackedOrdersWidgetSettings {
    private TrackedOrdersWidgetSettings() {}
    public static UIComponent create(WidgetConfigBinding<TrackedOrdersWidgetConfig> binding) {
        var panel = panel();
        integer(panel, "Content width", binding, c -> c.contentWidth, (c, v) -> c.contentWidth = v, 180, 320);
        integer(panel, "Visible rows", binding, c -> c.visibleRows, (c, v) -> c.visibleRows = v, 1, 10);
        bool(panel, "Fit to content", binding, c -> c.fitToContent, (c, v) -> c.fitToContent = v);
        enumeration(panel, "Density", binding, c -> c.layout, (c, v) -> c.layout = v);
        enumeration(panel, "Sort order", binding, c -> c.sort, (c, v) -> c.sort = v);
        bool(panel, "Abbreviate Enchanted", binding, c -> c.abbreviateEnchanted, (c, v) -> c.abbreviateEnchanted = v);
        bool(panel, "Hide when no active orders", binding, c -> c.hideWhenEmpty, (c, v) -> c.hideWhenEmpty = v);
        bool(panel, "Show filled count", binding, c -> c.showStatusSummary, (c, v) -> c.showStatusSummary = v);
        bool(panel, "Show ItemStacks", binding, c -> c.showItem, (c, v) -> c.showItem = v);
        bool(panel, "Show volume", binding, c -> c.showVolume, (c, v) -> c.showVolume = v);
        enumeration(panel, "Price display", binding, c -> c.priceDisplay, (c, v) -> c.priceDisplay = v);
        bool(panel, "Show market details", binding, c -> c.showMarketInfo, (c, v) -> c.showMarketInfo = v);
        bool(panel, "Show live fill bar", binding, c -> c.showProgress, (c, v) -> c.showProgress = v);
        return panel;
    }
}
