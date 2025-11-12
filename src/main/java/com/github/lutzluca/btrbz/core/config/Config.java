package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.AlertManager.AlertConfig;
import com.github.lutzluca.btrbz.core.BazaarOrderActions.OrderActionsConfig;
import com.github.lutzluca.btrbz.core.FlipHelper.FlipHelperConfig;
import com.github.lutzluca.btrbz.core.OrderHighlightManager.HighlightConfig;
import com.github.lutzluca.btrbz.core.ProductInfoProvider.ProductInfoProviderConfig;
import com.github.lutzluca.btrbz.core.TrackedOrderManager.OrderManagerConfig;
import com.github.lutzluca.btrbz.core.modules.BindModule;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule;
import com.github.lutzluca.btrbz.core.modules.BookmarkModule.BookMarkConfig;
import com.github.lutzluca.btrbz.core.modules.OrderLimitModule;
import com.github.lutzluca.btrbz.core.modules.OrderLimitModule.OrderLimitConfig;
import com.github.lutzluca.btrbz.core.modules.OrderPresetsModule;
import com.github.lutzluca.btrbz.core.modules.OrderPresetsModule.OrderPresetsConfig;
import com.github.lutzluca.btrbz.core.modules.OrderValueModule;
import com.github.lutzluca.btrbz.core.modules.OrderValueModule.OrderValueOverlayConfig;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule;
import com.github.lutzluca.btrbz.core.modules.PriceDiffModule.PriceDiffConfig;
import com.github.lutzluca.btrbz.core.modules.TrackedOrdersListModule;
import com.github.lutzluca.btrbz.core.modules.TrackedOrdersListModule.OrderListConfig;
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
    @BindModule(OrderValueModule.class)
    public OrderValueOverlayConfig orderValueOverlay = new OrderValueOverlayConfig();

    @SerialEntry
    @BindModule(TrackedOrdersListModule.class)
    public OrderListConfig orderList = new OrderListConfig();

    @SerialEntry
    @BindModule(OrderPresetsModule.class)
    public OrderPresetsConfig orderPresets = new OrderPresetsConfig();

    @SerialEntry
    public ProductInfoProviderConfig productInfo = new ProductInfoProviderConfig();

    @SerialEntry
    public OrderActionsConfig orderActions = new OrderActionsConfig();

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
