package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.core.iteminfo.charts.QuantityMetric;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import com.github.lutzluca.btrbz.core.config.ConfigScreen;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionGroup;
import net.minecraft.network.chat.Component;

/** Display preferences. Inspection and request state live only in the open session. */
public final class ItemInfoConfig {
    @SerialEntry
    public boolean enabled = true;
    @SerialEntry
    public ItemInfoRange range = ItemInfoRange.Week;
    @SerialEntry
    public boolean showBuy = true;
    @SerialEntry
    public boolean showSell = true;
    @SerialEntry
    public boolean showBands;
    @SerialEntry
    public boolean showMayors = true;
    @SerialEntry
    public boolean showQuantity = true;
    @SerialEntry
    public QuantityMetric quantityMetric = QuantityMetric.MovingWeek;
    @SerialEntry
    public boolean showCumulative = true;
    @SerialEntry
    public boolean showOrders = true;
    @SerialEntry
    public boolean showBars = true;

    public OptionGroup createGroup() {
        return OptionGroup.createBuilder()
            .name(Component.literal("Item Info"))
            .description(ConfigScreen.createDescription("Explore historical prices, mayors and the live order book "
                + "in game. Press the Item Info key while hovering a product, or use /btrbz info. "
                + "Chart and column preferences are available inside the screen."))
            .option(Option.<Boolean>createBuilder()
                .name(Component.literal("Enable Item Info"))
                .binding(true, () -> this.enabled, value -> this.enabled = value)
                .controller(ConfigScreen::createBooleanController).build())
            .collapsed(true).build();
    }
}
