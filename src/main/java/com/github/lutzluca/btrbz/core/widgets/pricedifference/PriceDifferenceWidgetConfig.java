package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.github.lutzluca.btrbz.core.widgets.WidgetPlacement;

public final class PriceDifferenceWidgetConfig {
    public enum DiffDisplay { Both, PerItem, Total }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.76, 0.72));
    public int contentWidth = 190;
    public DiffDisplay display = DiffDisplay.Both;
    public NumberStyle numberStyle = NumberStyle.Compact;
    public boolean showItems = true;
    public boolean showProduct = true;

    public int contentWidth() { return this.contentWidth; }
    public DiffDisplay display() { return this.display; }
    public NumberStyle numberStyle() { return this.numberStyle; }
    public boolean showItems() { return this.showItems; }
    public boolean showProduct() { return this.showProduct; }

    public static void resetPreferences(PriceDifferenceWidgetConfig current, PriceDifferenceWidgetConfig defaults) {
        current.contentWidth = defaults.contentWidth;
        current.display = defaults.display;
        current.numberStyle = defaults.numberStyle;
        current.showItems = defaults.showItems;
        current.showProduct = defaults.showProduct;
    }
}
