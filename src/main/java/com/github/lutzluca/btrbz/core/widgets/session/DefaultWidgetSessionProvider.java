package com.github.lutzluca.btrbz.core.widgets.session;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreen;
import com.github.lutzluca.btrbz.core.widgets.cache.CacheToken;
import com.github.lutzluca.btrbz.core.widgets.cache.InvalidationReason;
import com.github.lutzluca.btrbz.core.widgets.orderbook.OrderBookPriceComponent;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
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

    private final CacheToken contextChanges = CacheToken.named("widget-session.context");

    private @Nullable Screen cachedScreen;
    private long cachedTransitionRevision = Long.MIN_VALUE;
    private long cachedInventoryRevision = Long.MIN_VALUE;
    private long cachedIndexRevision = Long.MIN_VALUE;
    private long cachedProductRevision = Long.MIN_VALUE;

    private long semanticSessionId;
    private @Nullable WidgetSession cachedSession;

    public DefaultWidgetSessionProvider(
        BazaarData market,
        ProductInfoProvider productInfoProvider,
        OrderBookPriceComponent orderBookPrice
    ) {
        this.market = Objects.requireNonNull(market, "market");
        this.productInfoProvider = Objects.requireNonNull(productInfoProvider, "productInfoProvider");
        this.orderBookPrice = Objects.requireNonNull(orderBookPrice, "orderBookPrice");
    }

    @Override
    public WidgetSession current(@Nullable Screen screen) {
        var helper = ScreenInfoHelper.get();
        long transitionRevision = helper.screenTransitions().revision();
        long inventoryRevision = helper.inventoryChanges().revision();
        long indexRevision = this.market.indexChanges().revision();
        long productRevision = this.productInfoProvider.changes().revision();

        var cached = this.cachedSession;

        if (cached != null
            && this.cachedScreen == screen
            && this.cachedTransitionRevision == transitionRevision
            && this.cachedInventoryRevision == inventoryRevision
            && this.cachedIndexRevision == indexRevision
            && this.cachedProductRevision == productRevision) {
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
                orderBookScreen.product(), Component.literal(orderBookScreen.productName()),
                previous.getItemStack(PRODUCT_SLOT).or(() -> current.getItemStack(PRODUCT_SLOT))
            ));
        } else if (sign) {
            var workflow = this.orderBookPrice.currentWorkflow();
            product = workflow.map(OrderBookPriceComponent.Workflow::product)
                .map(identity -> this.context(
                    identity, previous.getItemStack(PRODUCT_SLOT).or(() -> current.getItemStack(PRODUCT_SLOT))
                ));
            side = workflow.map(OrderBookPriceComponent.Workflow::side);
        } else if (this.productInfoProvider.getOpenedProduct() != null) {
            product = Optional.of(this.context(
                ProductIdentity.fromIndex(this.productInfoProvider.getOpenedProduct()),
                current.getItemStack(PRODUCT_SLOT)
            ));
        }

        var candidate = new WidgetSession(
            this.semanticSessionId, hud, sign, orderBook,
            current.getMenuType(), previous.getMenuType(), product, side,
            this.contextChanges.revision()
        );

        if (cached == null || !candidate.sameSemanticContext(cached)) {
            this.semanticSessionId++;
        }

        if (cached == null || !candidate.samePresentationContext(cached)) {
            this.contextChanges.invalidate(InvalidationReason.of("widget session presentation changed"));
        }

        var session = new WidgetSession(
            this.semanticSessionId, hud, sign, orderBook,
            current.getMenuType(), previous.getMenuType(), product, side,
            this.contextChanges.revision()
        );

        this.cachedScreen = screen;
        this.cachedTransitionRevision = transitionRevision;
        this.cachedInventoryRevision = inventoryRevision;
        this.cachedIndexRevision = indexRevision;
        this.cachedProductRevision = productRevision;
        this.cachedSession = session;

        return session;
    }

    public CacheToken contextChanges() {
        return this.contextChanges;
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
            identity, displayName,
            this.market.productStack(identity).or(() -> observedStack.map(ItemStack::copy))
        );
    }
}
