package com.github.lutzluca.btrbz.core.widgets.session;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreen;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceComponent;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** The only boundary that classifies concrete Minecraft and BtrBz screens. */
public final class DefaultWidgetSessionProvider implements WidgetSessionProvider {
    private static final int PRODUCT_SLOT = 13;
    private final ProductInfoProvider productInfoProvider;
    private final OrderBookPriceComponent orderBookPrice;
    private final TrackedOrderManager trackedOrders;
    private @Nullable SemanticKey previousKey;
    private long semanticSessionId;

    public DefaultWidgetSessionProvider(
        ProductInfoProvider productInfoProvider,
        OrderBookPriceComponent orderBookPrice,
        TrackedOrderManager trackedOrders
    ) {
        this.productInfoProvider = Objects.requireNonNull(productInfoProvider, "productInfoProvider");
        this.orderBookPrice = Objects.requireNonNull(orderBookPrice, "orderBookPrice");
        this.trackedOrders = Objects.requireNonNull(trackedOrders, "trackedOrders");
    }

    @Override
    public WidgetSession current(@Nullable Screen screen) {
        var helper = ScreenInfoHelper.get();
        var current = helper.getCurrInfo();
        var previous = helper.getPrevInfo();
        boolean hud = screen == null;
        boolean sign = screen instanceof SignEditScreen;
        boolean orderBook = screen instanceof OrderBookScreen;
        Optional<WidgetProductContext> product = Optional.empty();
        Optional<OrderType> side = Optional.empty();

        if (screen instanceof OrderBookScreen orderBookScreen) {
            product = Optional.of(new WidgetProductContext(
                orderBookScreen.product(),
                Component.literal(orderBookScreen.productName()),
                orderBookScreen.productIcon()
            ));
        } else if (sign) {
            var workflow = this.orderBookPrice.currentWorkflow();
            product = workflow.map(OrderBookPriceComponent.Workflow::product)
                .map(identity -> context(identity, previous.getItemStack(PRODUCT_SLOT)
                    .or(() -> current.getItemStack(PRODUCT_SLOT))
                    .orElse(ItemStack.EMPTY)));
            side = workflow.map(OrderBookPriceComponent.Workflow::side);
        } else if (this.productInfoProvider.getOpenedProduct() != null) {
            product = Optional.of(context(
                ProductIdentity.fromIndex(this.productInfoProvider.getOpenedProduct()),
                current.getItemStack(PRODUCT_SLOT).orElse(ItemStack.EMPTY)
            ));
        }

        var key = new SemanticKey(
            helper.screenTransitionVersion(), hud, sign, orderBook,
            current.getMenuType(), previous.getMenuType(),
            product.map(WidgetProductContext::productId), side
        );
        if (!key.equals(this.previousKey)) {
            this.previousKey = key;
            this.semanticSessionId++;
        }

        return new WidgetSession(
            this.semanticSessionId,
            hud,
            sign,
            orderBook,
            current.getMenuType(),
            previous.getMenuType(),
            product,
            side,
            this.trackedOrders.displayRevision()
        );
    }

    private static WidgetProductContext context(ProductIdentity identity, ItemStack icon) {
        return new WidgetProductContext(identity, Component.literal(identity.visualName()), icon);
    }

    private record SemanticKey(
        long transition,
        boolean hud,
        boolean sign,
        boolean orderBook,
        Optional<BazaarMenuType> menu,
        Optional<BazaarMenuType> previousMenu,
        Optional<String> productId,
        Optional<OrderType> side
    ) {}
}
