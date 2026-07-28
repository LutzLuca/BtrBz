package com.github.lutzluca.btrbz.core.widgets.hud;

import com.github.lutzluca.btrbz.core.widgets.config.WidgetFrameConfig;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.PriceDisplay;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.QueueDisplay;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.UndercutDetail;
import com.github.lutzluca.btrbz.core.widgets.layout.WidgetPlacement;

public final class BazaarOrdersWidgetConfig {
    public enum HudMode { Detailed, StatusCounts }

    public WidgetFrameConfig frame = new WidgetFrameConfig(WidgetPlacement.topLeft(0.04, 0.05));
    public HudMode mode = HudMode.Detailed;
    public int visibleOrders = 6;
    public int contentWidth = 200;
    public boolean abbreviateEnchanted = false;
    public boolean hideWhenEmpty = true;
    public boolean showItem = true;
    public boolean showVolume = true;
    public PriceDisplay priceDisplay = PriceDisplay.Unit;
    public QueueDisplay queueDisplay = QueueDisplay.Items;
    public UndercutDetail undercutDetail = UndercutDetail.PriceGapAndQueue;
    public static void resetPreferences(BazaarOrdersWidgetConfig current, BazaarOrdersWidgetConfig defaults) {
        current.mode = defaults.mode;
        current.visibleOrders = defaults.visibleOrders;
        current.contentWidth = defaults.contentWidth;
        current.abbreviateEnchanted = defaults.abbreviateEnchanted;
        current.hideWhenEmpty = defaults.hideWhenEmpty;
        current.showItem = defaults.showItem;
        current.showVolume = defaults.showVolume;
        current.priceDisplay = defaults.priceDisplay;
        current.queueDisplay = defaults.queueDisplay;
        current.undercutDetail = defaults.undercutDetail;
    }
}
