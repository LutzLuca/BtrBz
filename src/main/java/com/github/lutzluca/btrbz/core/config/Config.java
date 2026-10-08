package com.github.lutzluca.btrbz.core.config;

import com.github.lutzluca.btrbz.core.alert.AlertConfig;
import com.github.lutzluca.btrbz.core.orderactions.BazaarOrderActions.OrderActionsConfig;
import com.github.lutzluca.btrbz.core.chat.BazaarChatManager.ChatConfig;
import com.github.lutzluca.btrbz.core.fliphelper.FlipHelper.FlipHelperConfig;
import com.github.lutzluca.btrbz.core.orderdisplay.OrderHighlightManager.HighlightConfig;
import com.github.lutzluca.btrbz.core.orderprotection.OrderProtectionConfig;
import com.github.lutzluca.btrbz.core.orderdisplay.OrderItemTooltipConfig;
import com.github.lutzluca.btrbz.core.orderdisplay.OrderListTooltipConfig;
import com.github.lutzluca.btrbz.core.productinfo.ProductInfoConfig;
import com.github.lutzluca.btrbz.core.trackedorders.OrderManagerConfig;
import com.github.lutzluca.btrbz.core.widgets.config.WidgetsConfig;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Config {

    @SerialEntry
    public boolean enabled = true;

    @SerialEntry
    public boolean alwaysActive;

    @SerialEntry
    public WidgetsConfig widgets = new WidgetsConfig();

    @SerialEntry
    public ProductInfoConfig productInfo = new ProductInfoConfig();

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
    public ChatConfig chatFilter = new ChatConfig();

}
