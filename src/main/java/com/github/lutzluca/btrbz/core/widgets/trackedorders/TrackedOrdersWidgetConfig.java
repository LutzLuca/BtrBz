package com.github.lutzluca.btrbz.core.widgets.trackedorders;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.PriceDisplay;
import com.github.lutzluca.btrbz.core.widgets.WidgetPlacement;

public final class TrackedOrdersWidgetConfig {
    public enum TrackedLayout { Standard, Compact }
    public enum TrackedSort { Manual, Newest, Oldest, Status, Side, Product, Value }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.04, 0.18));
    public int contentWidth = 218;
    public int visibleRows = 6;
    public boolean fitToContent = true;
    public TrackedLayout layout = TrackedLayout.Standard;
    public TrackedSort sort = TrackedSort.Manual;
    public boolean abbreviateEnchanted = false;
    public boolean hideWhenEmpty = true;
    public boolean showStatusSummary = true;
    public boolean showItem = true;
    public boolean showVolume = true;
    public PriceDisplay priceDisplay = PriceDisplay.Unit;
    public boolean showMarketInfo = true;
    public boolean showProgress = true;

    public int contentWidth() { return this.contentWidth; }
    public int visibleRows() { return this.visibleRows; }
    public boolean fitToContent() { return this.fitToContent; }
    public TrackedLayout layout() { return this.layout; }
    public TrackedSort sort() { return this.sort; }
    public boolean abbreviateEnchanted() { return this.abbreviateEnchanted; }
    public boolean hideWhenEmpty() { return this.hideWhenEmpty; }
    public boolean showStatusSummary() { return this.showStatusSummary; }
    public boolean showItem() { return this.showItem; }
    public boolean showVolume() { return this.showVolume; }
    public PriceDisplay priceDisplay() { return this.priceDisplay; }
    public boolean showMarketInfo() { return this.showMarketInfo; }
    public boolean showProgress() { return this.showProgress; }

    public static void resetPreferences(TrackedOrdersWidgetConfig current, TrackedOrdersWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.visibleRows = defaults.visibleRows;
        current.fitToContent = defaults.fitToContent;
        current.layout = defaults.layout;
        current.sort = defaults.sort;
        current.abbreviateEnchanted = defaults.abbreviateEnchanted;
        current.hideWhenEmpty = defaults.hideWhenEmpty;
        current.showStatusSummary = defaults.showStatusSummary;
        current.showItem = defaults.showItem;
        current.showVolume = defaults.showVolume;
        current.priceDisplay = defaults.priceDisplay;
        current.showMarketInfo = defaults.showMarketInfo;
        current.showProgress = defaults.showProgress;
    }
}
