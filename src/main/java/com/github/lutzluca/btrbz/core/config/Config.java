package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.AlertManager.AlertConfig;
import com.github.lutzluca.btrbz.core.BazaarOrderActions.OrderActionsConfig;
import com.github.lutzluca.btrbz.core.ChatFilterManager;
import com.github.lutzluca.btrbz.core.fliphelper.FlipHelper.FlipHelperConfig;
import com.github.lutzluca.btrbz.core.OrderHighlightManager.HighlightConfig;
import com.github.lutzluca.btrbz.core.OrderProtectionManager.OrderProtectionConfig;
import com.github.lutzluca.btrbz.core.OrderTooltipProvider.OrderItemTooltipConfig;
import com.github.lutzluca.btrbz.core.OrderTooltipProvider.OrderListTooltipConfig;
import com.github.lutzluca.btrbz.core.ProductInfoProvider.ProductInfoProviderConfig;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager.OrderManagerConfig;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetsConfig;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Config {

    @SerialEntry
    public WidgetsConfig widgets = new WidgetsConfig();

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
    public OrderProtectionConfig orderProtection = new OrderProtectionConfig();

    @SerialEntry
    public double tax = 1.125;

    @SerialEntry
    public AlertConfig alert = new AlertConfig();

    @SerialEntry
    public OrderListTooltipConfig orderListTooltip = new OrderListTooltipConfig();

    @SerialEntry
    public OrderItemTooltipConfig orderItemTooltip = new OrderItemTooltipConfig();

    @SerialEntry
    public ChatFilterManager.ChatFilterConfig chatFilter = new ChatFilterManager.ChatFilterConfig();

}
