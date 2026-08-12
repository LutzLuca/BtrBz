package com.github.lutzluca.btrbz.core.widgets.session;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import java.util.Objects;
import java.util.Optional;

/** Immutable semantic projection of the BtrBz UI context used by widgets. */
public final class WidgetSession {
    public static final String DEFAULT_PLACEMENT_PROFILE = "default";

    private final long id;
    private final boolean hud;
    private final boolean sign;
    private final boolean orderBook;
    private final Optional<BazaarMenuType> menu;
    private final Optional<BazaarMenuType> previousMenu;
    private final Optional<WidgetProductContext> product;
    private final Optional<OrderType> side;
    private final long contextRevision;

    public WidgetSession(
        long id,
        boolean hud,
        boolean sign,
        boolean orderBook,
        Optional<BazaarMenuType> menu,
        Optional<BazaarMenuType> previousMenu,
        Optional<WidgetProductContext> product,
        Optional<OrderType> side,
        long contextRevision
    ) {
        this.id = id;
        this.hud = hud;
        this.sign = sign;
        this.orderBook = orderBook;
        this.menu = Objects.requireNonNull(menu, "menu");
        this.previousMenu = Objects.requireNonNull(previousMenu, "previousMenu");
        this.product = Objects.requireNonNull(product, "product");
        this.side = Objects.requireNonNull(side, "side");
        this.contextRevision = contextRevision;
    }

    public long id() { return this.id; }
    public boolean inHud() { return this.hud; }
    public boolean inSign() { return this.sign; }
    public boolean inOrderBook() { return this.orderBook; }
    public Optional<WidgetProductContext> product() { return this.product; }
    public Optional<OrderType> side() { return this.side; }
    public long contextRevision() { return this.contextRevision; }

    public boolean inBazaarContainer() {
        return this.inContainerBazaarContext() && this.menu.isPresent();
    }

    public boolean inBazaarMenu(BazaarMenuType menu) {
        return this.inBazaarContainer() && this.menu.orElse(null) == menu;
    }

    public boolean inAnyBazaarMenu(BazaarMenuType... menus) {
        if (!this.inBazaarContainer() || this.menu.isEmpty()) return false;
        var current = this.menu.orElseThrow();
        for (var candidate : menus) {
            if (candidate == current) return true;
        }
        return false;
    }

    public boolean inAnyBazaarMenu(BazaarMenuType first, BazaarMenuType second) {
        if (!this.inBazaarContainer() || this.menu.isEmpty()) return false;
        var current = this.menu.orElseThrow();
        return current == first || current == second;
    }

    public boolean previousBazaarMenu(BazaarMenuType menu) {
        return this.previousMenu.orElse(null) == menu;
    }

    public boolean sameWorkflow(WidgetSession other) {
        return other != null
            && this.id == other.id
            && this.sameSemanticContext(other);
    }

    public boolean sameSemanticContext(WidgetSession other) {
        return other != null
            && this.hud == other.hud
            && this.sign == other.sign
            && this.orderBook == other.orderBook
            && this.menu.equals(other.menu)
            && this.previousMenu.equals(other.previousMenu)
            && productId(this.product).equals(productId(other.product))
            && this.side.equals(other.side);
    }

    public boolean samePresentationContext(WidgetSession other) {
        return this.sameSemanticContext(other)
            && this.product.isPresent() == other.product.isPresent()
            && (this.product.isEmpty() || this.product.orElseThrow()
                .samePresentation(other.product.orElseThrow()));
    }

    public String placementProfile() {
        return this.sign ? "sign" : DEFAULT_PLACEMENT_PROFILE;
    }

    public WidgetSession detachedCopy() {
        return new WidgetSession(
            this.id,
            this.hud,
            this.sign,
            this.orderBook,
            this.menu,
            this.previousMenu,
            this.product.map(WidgetProductContext::detachedCopy),
            this.side,
            this.contextRevision
        );
    }

    private static Optional<String> productId(Optional<WidgetProductContext> product) {
        return product.map(WidgetProductContext::productId);
    }

    private boolean inContainerBazaarContext() {
        return !this.hud && !this.sign && !this.orderBook;
    }
}
