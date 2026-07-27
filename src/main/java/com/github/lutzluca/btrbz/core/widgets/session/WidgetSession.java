package com.github.lutzluca.btrbz.core.widgets.session;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.core.widgets.WidgetAnchorSpace;
import com.github.lutzluca.btrbz.core.widgets.WidgetCanvas;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** Immutable semantic projection of the BtrBz UI context used by widgets. */
public final class WidgetSession {
    public static final String DEFAULT_PLACEMENT_PROFILE = "default";

    private final long id;
    private final boolean hud;
    private final boolean sign;
    private final boolean orderBook;
    private final boolean priceGraph;
    private final Optional<BazaarMenuType> menu;
    private final Optional<BazaarMenuType> previousMenu;
    private final boolean loaded;
    private final Optional<WidgetProductContext> product;
    private final Optional<OrderType> side;
    private final long trackedRevision;
    private final @Nullable WidgetCanvas contentCanvas;

    public WidgetSession(
        long id,
        boolean hud,
        boolean sign,
        boolean orderBook,
        boolean priceGraph,
        Optional<BazaarMenuType> menu,
        Optional<BazaarMenuType> previousMenu,
        boolean loaded,
        Optional<WidgetProductContext> product,
        Optional<OrderType> side,
        long trackedRevision,
        @Nullable WidgetCanvas contentCanvas
    ) {
        this.id = id;
        this.hud = hud;
        this.sign = sign;
        this.orderBook = orderBook;
        this.priceGraph = priceGraph;
        this.menu = Objects.requireNonNull(menu, "menu");
        this.previousMenu = Objects.requireNonNull(previousMenu, "previousMenu");
        this.loaded = loaded;
        this.product = Objects.requireNonNull(product, "product");
        this.side = Objects.requireNonNull(side, "side");
        this.trackedRevision = trackedRevision;
        this.contentCanvas = contentCanvas;
    }

    public long id() { return this.id; }
    public boolean inHud() { return this.hud; }
    public boolean inSign() { return this.sign; }
    public boolean inOrderBook() { return this.orderBook; }
    public boolean inPriceGraph() { return this.priceGraph; }
    public boolean inventoryLoaded() { return this.loaded; }
    public Optional<BazaarMenuType> menu() { return this.menu; }
    public Optional<BazaarMenuType> previousMenu() { return this.previousMenu; }
    public Optional<WidgetProductContext> product() { return this.product; }
    public Optional<OrderType> side() { return this.side; }
    public long trackedRevision() { return this.trackedRevision; }

    public boolean inBazaarContainer() {
        return this.inContainerBazaarContext() && this.menu.isPresent();
    }

    public boolean inBazaarMenu(BazaarMenuType menu) {
        return this.inBazaarContainer() && this.menu.filter(menu::equals).isPresent();
    }

    public boolean inAnyBazaarMenu(BazaarMenuType... menus) {
        return this.inBazaarContainer()
            && this.menu.filter(current -> Arrays.asList(menus).contains(current)).isPresent();
    }

    public boolean previousBazaarMenu(BazaarMenuType menu) {
        return this.previousMenu.filter(menu::equals).isPresent();
    }

    public boolean sameWorkflow(WidgetSession other) {
        return other != null
            && this.id == other.id
            && this.hud == other.hud
            && this.sign == other.sign
            && this.orderBook == other.orderBook
            && this.priceGraph == other.priceGraph
            && this.menu.equals(other.menu)
            && this.previousMenu.equals(other.previousMenu)
            && productId(this.product).equals(productId(other.product))
            && this.side.equals(other.side);
    }

    public String placementProfile() {
        return this.sign ? "sign" : DEFAULT_PLACEMENT_PROFILE;
    }

    public WidgetCanvas anchorCanvas(WidgetAnchorSpace space, WidgetCanvas screenCanvas) {
        return space == WidgetAnchorSpace.Content && this.contentCanvas != null
            ? this.contentCanvas
            : screenCanvas;
    }

    private static Optional<String> productId(Optional<WidgetProductContext> product) {
        return product.map(WidgetProductContext::productId);
    }

    private boolean inContainerBazaarContext() {
        return !this.hud && !this.sign && !this.orderBook && !this.priceGraph;
    }
}
