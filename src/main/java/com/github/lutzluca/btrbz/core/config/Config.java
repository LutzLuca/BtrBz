package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.AlertManager.AlertConfig;
import com.github.lutzluca.btrbz.core.OrderCancelRouter.OrderCancelConfig;
import com.github.lutzluca.btrbz.core.ProductInfoProvider.ProductInfoProviderConfig;
import com.github.lutzluca.btrbz.core.OrderManager.OrderManagerConfig;
import com.github.lutzluca.btrbz.core.HighlightManager.HighlightConfig;
import com.github.lutzluca.btrbz.core.FlipHelper.FlipHelperConfig;
import com.github.lutzluca.btrbz.core.modules.BindModule;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule.BookMarkConfig;
import com.github.lutzluca.btrbz.core.modules.OrderLimitModule;
import com.github.lutzluca.btrbz.core.modules.OrderLimitModule.OrderLimitConfig;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule.PriceDiffConfig;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Config {

    @SerialEntry
    @BindModule(OrderLimitModule.class)
    public OrderLimitConfig orderLimit = new OrderLimitConfig();

    @SerialEntry
    @BindModule(BookmarkModule.class)
    public BookMarkConfig bookmark = new BookMarkConfig();

    @SerialEntry
    @BindModule(PriceDiffModule.class)
    public PriceDiffConfig priceDiff = new PriceDiffConfig();

    @SerialEntry
    public ProductInfoProviderConfig productInfo = new ProductInfoProviderConfig();

    @SerialEntry
    public OrderCancelConfig orderCancel = new OrderCancelConfig();

    @SerialEntry
    public OrderManagerConfig trackedOrders = new OrderManagerConfig();

    @SerialEntry
    public HighlightConfig orderHighlight = new HighlightConfig();

    @SerialEntry
    public FlipHelperConfig flipHelper = new FlipHelperConfig();

    @SerialEntry
    public double tax = 1.125;

    @SerialEntry
    public AlertConfig alert = new AlertConfig();
}
