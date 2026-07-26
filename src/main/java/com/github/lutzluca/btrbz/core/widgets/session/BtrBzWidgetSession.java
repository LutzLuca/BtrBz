package com.github.lutzluca.btrbz.core.widgets.session;

import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.widgets.framework.WidgetAnchorSpace;
import com.github.lutzluca.btrbz.widgets.framework.WidgetCanvas;
import com.github.lutzluca.btrbz.widgets.framework.WidgetScreenSession;
import java.util.Optional;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** Immutable semantic facts used for eligibility and stale-action rejection. */
public record BtrBzWidgetSession(
    long id,
    HostKind host,
    Optional<BazaarMenuType> menu,
    Optional<BazaarMenuType> previousMenu,
    boolean loaded,
    Optional<String> productId,
    Optional<OrderType> side,
    long trackedRevision,
    @Nullable WidgetCanvas contentCanvas
) implements WidgetScreenSession {
    public BtrBzWidgetSession {
        menu = Objects.requireNonNull(menu, "menu");
        previousMenu = Objects.requireNonNull(previousMenu, "previousMenu");
        productId = Objects.requireNonNull(productId, "productId");
        side = Objects.requireNonNull(side, "side");
    }

    @Override
    public String placementProfile() {
        return this.host == HostKind.Sign ? "sign" : DEFAULT_PLACEMENT_PROFILE;
    }

    @Override
    public WidgetCanvas anchorCanvas(WidgetAnchorSpace space, WidgetCanvas screenCanvas) {
        return space == WidgetAnchorSpace.Content && this.contentCanvas != null
            ? this.contentCanvas
            : screenCanvas;
    }

    public boolean sameWorkflow(BtrBzWidgetSession other) {
        return other != null
            && this.id == other.id
            && this.host == other.host
            && this.productId.equals(other.productId)
            && this.side.equals(other.side)
            && this.menu.equals(other.menu)
            && this.previousMenu.equals(other.previousMenu);
    }

    public enum HostKind { Hud, Container, Sign, OrderBook, None }
}
