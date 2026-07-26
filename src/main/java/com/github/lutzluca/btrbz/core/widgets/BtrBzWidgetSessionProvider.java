package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreen;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.widgets.framework.WidgetCanvas;
import com.github.lutzluca.btrbz.widgets.framework.WidgetScreenSession;
import com.github.lutzluca.btrbz.widgets.framework.WidgetScreenSessionProvider;
import java.util.Optional;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import org.jetbrains.annotations.Nullable;

/** Resolves lightweight sessions from BtrBz's semantic screen tracker. */
public final class BtrBzWidgetSessionProvider implements WidgetScreenSessionProvider {
    private final ProductInfoProvider productInfoProvider;
    private final OrderBookPriceComponent orderBookPrice;
    private final TrackedOrderManager trackedOrders;
    private SemanticKey lastSemanticKey;
    private long semanticSessionId;

    public BtrBzWidgetSessionProvider(
        ProductInfoProvider productInfoProvider,
        OrderBookPriceComponent orderBookPrice,
        TrackedOrderManager trackedOrders
    ) {
        this.productInfoProvider = productInfoProvider;
        this.orderBookPrice = orderBookPrice;
        this.trackedOrders = trackedOrders;
    }

    @Override
    public WidgetScreenSession current(@Nullable Screen screen) {
        var helper = ScreenInfoHelper.get();
        var current = helper.getCurrInfo();
        var previous = helper.getPrevInfo();
        var host = host(screen);
        Optional<String> productId = Optional.empty();
        Optional<OrderType> side = Optional.empty();

        if (screen instanceof OrderBookScreen orderBookScreen) {
            productId = Optional.of(orderBookScreen.productId());
        } else if (screen instanceof SignEditScreen) {
            var workflow = this.orderBookPrice.currentWorkflow();
            productId = workflow.map(OrderBookPriceComponent.Workflow::product).map(this::identity);
            side = workflow.map(OrderBookPriceComponent.Workflow::side);
        } else if (this.productInfoProvider.getOpenedProduct() != null) {
            productId = Optional.of(this.productInfoProvider.getOpenedProduct().productId());
        }

        WidgetCanvas content = current.getHandledScreenBounds()
            .map(bounds -> new WidgetCanvas(bounds.x(), bounds.y(), bounds.width(), bounds.height()))
            .orElse(null);
        var semanticKey = new SemanticKey(
            helper.screenTransitionVersion(), host, current.getMenuType(), previous.getMenuType(),
            current.inventoryLoaded(), productId, side, content
        );
        if (!semanticKey.equals(this.lastSemanticKey)) {
            this.lastSemanticKey = semanticKey;
            this.semanticSessionId++;
        }
        return new BtrBzWidgetSession(
            this.semanticSessionId,
            host,
            current.getMenuType(),
            previous.getMenuType(),
            current.inventoryLoaded(),
            productId,
            side,
            this.trackedOrders.displayRevision(),
            content
        );
    }

    private BtrBzWidgetSession.HostKind host(@Nullable Screen screen) {
        if (screen == null) return BtrBzWidgetSession.HostKind.HUD;
        if (screen instanceof OrderBookScreen) return BtrBzWidgetSession.HostKind.ORDER_BOOK;
        if (screen instanceof SignEditScreen) return BtrBzWidgetSession.HostKind.SIGN;
        if (ScreenInfoHelper.get().getCurrInfo().getGenericContainerScreen().isPresent()) {
            return BtrBzWidgetSession.HostKind.CONTAINER;
        }
        return BtrBzWidgetSession.HostKind.NONE;
    }

    private String identity(ProductIdentity product) {
        return product.bazaarProductId().orElse(product.strippedName());
    }

    private record SemanticKey(
        long screenTransition,
        BtrBzWidgetSession.HostKind host,
        Optional<BazaarMenuType> menu,
        Optional<BazaarMenuType> previousMenu,
        boolean loaded,
        Optional<String> productId,
        Optional<OrderType> side,
        WidgetCanvas contentCanvas
    ) {}
}
