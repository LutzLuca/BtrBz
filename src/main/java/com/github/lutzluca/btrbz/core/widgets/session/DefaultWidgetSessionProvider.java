package com.github.lutzluca.btrbz.core.widgets.session;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreen;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceComponent;
import com.github.lutzluca.btrbz.data.BazaarData;
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
    private final BazaarData market;
    private final ProductInfoProvider productInfoProvider;
    private final OrderBookPriceComponent orderBookPrice;
    private final TrackedOrderManager trackedOrders;
    private @Nullable SemanticKey previousKey;
    private long semanticSessionId;
    private @Nullable SessionKey cachedKey;
    private @Nullable WidgetSession cachedSession;

    public DefaultWidgetSessionProvider(
        BazaarData market,
        ProductInfoProvider productInfoProvider,
        OrderBookPriceComponent orderBookPrice,
        TrackedOrderManager trackedOrders
    ) {
        this.market = Objects.requireNonNull(market, "market");
        this.productInfoProvider = Objects.requireNonNull(productInfoProvider, "productInfoProvider");
        this.orderBookPrice = Objects.requireNonNull(orderBookPrice, "orderBookPrice");
        this.trackedOrders = Objects.requireNonNull(trackedOrders, "trackedOrders");
    }

    @Override
    public WidgetSession current(@Nullable Screen screen) {
        var helper = ScreenInfoHelper.get();
        var sessionKey = new SessionKey(
            screen,
            helper.screenTransitionVersion(),
            helper.inventoryVersion(),
            this.trackedOrders.displayRevision()
        );
        var cached = this.cachedSession;
        if (cached != null && sessionKey.equals(this.cachedKey)) {
            return cached;
        }

        var current = helper.getCurrInfo();
        var previous = helper.getPrevInfo();
        boolean hud = screen == null;
        boolean sign = screen instanceof SignEditScreen;
        boolean orderBook = screen instanceof OrderBookScreen;
        Optional<WidgetProductContext> product = Optional.empty();
        Optional<OrderType> side = Optional.empty();

        if (screen instanceof OrderBookScreen orderBookScreen) {
            product = Optional.of(this.context(
                orderBookScreen.product(),
                Component.literal(orderBookScreen.productName()),
                previous.getItemStack(PRODUCT_SLOT).or(() -> current.getItemStack(PRODUCT_SLOT))
            ));
        } else if (sign) {
            var workflow = this.orderBookPrice.currentWorkflow();
            product = workflow.map(OrderBookPriceComponent.Workflow::product)
                .map(identity -> this.context(
                    identity,
                    previous.getItemStack(PRODUCT_SLOT).or(() -> current.getItemStack(PRODUCT_SLOT))
                ));
            side = workflow.map(OrderBookPriceComponent.Workflow::side);
        } else if (this.productInfoProvider.getOpenedProduct() != null) {
            product = Optional.of(this.context(
                ProductIdentity.fromIndex(this.productInfoProvider.getOpenedProduct()),
                current.getItemStack(PRODUCT_SLOT)
            ));
        }

        var currMenu = current.getMenuType();
        var prevMenu = previous.getMenuType();
        var key = new SemanticKey(
            helper.screenTransitionVersion(), hud, sign, orderBook,
            currMenu, prevMenu,
            product.map(WidgetProductContext::productId), side
        );
        if (!key.equals(this.previousKey)) {
            this.previousKey = key;
            this.semanticSessionId++;
        }

        var session = new WidgetSession(
            this.semanticSessionId,
            hud,
            sign,
            orderBook,
            currMenu,
            prevMenu,
            product,
            side,
            this.trackedOrders.displayRevision()
        );
        this.cachedKey = sessionKey;
        this.cachedSession = session;
        return session;
    }

    private WidgetProductContext context(ProductIdentity identity, Optional<ItemStack> observedStack) {
        return this.context(identity, Component.literal(identity.visualName()), observedStack);
    }

    private WidgetProductContext context(
        ProductIdentity identity,
        Component displayName,
        Optional<ItemStack> observedStack
    ) {
        return new WidgetProductContext(
            identity,
            displayName,
            this.market.productStack(identity).or(() -> observedStack.map(ItemStack::copy))
        );
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

    private record SessionKey(@Nullable Screen screen, long transition, long inventory, long trackedRevision) {}
}
