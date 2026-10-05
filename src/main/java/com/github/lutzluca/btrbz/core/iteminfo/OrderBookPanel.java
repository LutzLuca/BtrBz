package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.core.ui.UiControls;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.data.LiveProductSnapshot;
import com.github.lutzluca.btrbz.data.LiveProductSnapshot.MarketSide;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;

/** Live price levels, independently scrollable on each side of the market. */
public final class OrderBookPanel extends FlowLayout {
    private static final int BUY = 0xFF55FF55;
    private static final int SELL = 0xFFFFAA00;
    private final ItemInfoConfig config;
    private final Consumer<String> copyPrice;
    private Optional<LiveProductSnapshot> snapshot = Optional.empty();
    private RestorableVerticalScrollContainer<FlowLayout> buyScroll;
    private RestorableVerticalScrollContainer<FlowLayout> sellScroll;
    private ViewState viewState = new ViewState(0, 0);
    private final Map<PriceFocus, ButtonComponent> prices = new HashMap<>();
    private int availableWidth = 500;
    private int availableHeight = 220;

    public OrderBookPanel(ItemInfoConfig config, Consumer<String> copyPrice) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.config = config;
        this.copyPrice = copyPrice;
        this.refresh();
    }

    public void update(Optional<LiveProductSnapshot> snapshot) {
        if (this.snapshot.equals(snapshot)) {
            return;
        }
        this.snapshot = snapshot;
        this.refresh();
    }

    public void layoutFor(int width, int height) {
        int nextWidth = Math.max(160, width);
        int nextHeight = Math.max(100, height);
        if (this.availableWidth == nextWidth && this.availableHeight == nextHeight) {
            return;
        }
        this.availableWidth = nextWidth;
        this.availableHeight = nextHeight;
        this.refresh();
    }

    public ViewState saveViewState() {
        return new ViewState(this.buyScroll == null ? this.viewState.buyOffset() : this.buyScroll.savedScrollOffset(),
            this.sellScroll == null ? this.viewState.sellOffset() : this.sellScroll.savedScrollOffset());
    }

    public void restoreViewState(ViewState state) {
        this.viewState = state;
        if (this.buyScroll != null) {
            this.buyScroll.restoreScrollOffset(state.buyOffset());
        }
        if (this.sellScroll != null) {
            this.sellScroll.restoreScrollOffset(state.sellOffset());
        }
    }

    public record ViewState(double buyOffset, double sellOffset) {}

    public void refresh() {
        var handler = this.focusHandler();
        PriceFocus focusedPrice = handler == null
            ? null : this.prices.entrySet().stream()
                .filter(entry -> entry.getValue() == handler.focused()).map(Map.Entry::getKey).findFirst().orElse(null);
        var source = handler == null || handler.lastFocusSource() == null
            ? UIComponent.FocusSource.KEYBOARD_CYCLE : handler.lastFocusSource();
        if (focusedPrice != null) {
            handler.focus(null, source);
        }
        this.viewState = this.saveViewState();
        this.clearChildren();
        this.prices.clear();
        this.buyScroll = null;
        this.sellScroll = null;
        if (this.snapshot.isEmpty()) {
            this.child(UiControls.text("Live order book unavailable", UiStyles.palette().muted()));
            return;
        }
        var live = this.snapshot.get();
        int halfWidth = (this.availableWidth - 8) / 2;
        boolean columns = this.availableWidth >= 450
            && this.measureColumns(live.sellOffers()).totalWidth() + 16 <= halfWidth
            && this.measureColumns(live.buyOrders()).totalWidth() + 16 <= halfWidth;
        int width = columns ? (this.availableWidth - 8) / 2 : this.availableWidth;
        int height = Math.max(columns ? 130 : 150, columns ? this.availableHeight : (this.availableHeight - 8) / 2);
        var sides = columns
            ? UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
            : UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        sides.gap(8);
        sides.child(this.side("Buy Price", "Sell offers", live.sellOffers(), BUY, width, height, true));
        sides.child(this.side("Sell Price", "Buy orders", live.buyOrders(), SELL, width, height, false));
        this.child(sides);
        this.restoreViewState(this.viewState);
        if (focusedPrice != null && this.focusHandler() == handler && handler.focused() == null) {
            var replacement = this.prices.get(focusedPrice);
            if (replacement != null && replacement.focusHandler() == handler) {
                handler.focus(replacement, source);
            }
        }
    }

    private FlowLayout side(
        String title,
        String source,
        MarketSide market,
        int accent,
        int width,
        int height,
        boolean buy
    ) {
        var panel = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.fixed(height));
        panel.padding(Insets.of(6));
        panel.gap(4);
        panel.surface(WidgetSurfaces.roundedPanel(0xB51C1A18, 4));
        var heading = UiControls.text(title, accent);
        heading.tooltip(Component.literal(source + ". Click a price to copy it."));
        panel.child(heading);
        market.totals().ifPresentOrElse(value -> {
            panel.child(UiControls.text("Full side: " + integer(value.items()) + " items", UiStyles.palette().muted())
                .maxWidth(width - 12));
            if (this.config.showOrders) {
                panel.child(
                    UiControls.text("Full side: " + integer(value.orders()) + " orders", UiStyles.palette().muted())
                        .maxWidth(width - 12));
            }
        }, () -> panel.child(UiControls.text("Full side totals unavailable", UiStyles.palette().muted())
            .maxWidth(width - 12)));
        panel.child(UiControls.text(market.levels().size() + " returned levels", UiStyles.palette().muted()));
        var columns = this.measureColumns(market);
        int priceWidth = columns.price();
        int valueWidth = columns.items();
        var table = UIContainers.verticalFlow(Sizing.fixed(Math.max(width - 16, columns.totalWidth())),
            Sizing.expand(100));
        table.gap(4);
        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(2);
        header.child(UiControls.text("Price", UiStyles.palette().muted()).horizontalSizing(Sizing.fixed(priceWidth)));
        header.child(UiControls.text("Items", UiStyles.palette().muted()).horizontalSizing(Sizing.fixed(valueWidth)));
        if (this.config.showCumulative) {
            header.child(
                UiControls.text("Cumul.", UiStyles.palette().muted())
                    .horizontalSizing(Sizing.fixed(columns.cumulative())));
        }
        if (this.config.showOrders) {
            header.child(
                UiControls.text("Orders", UiStyles.palette().muted()).horizontalSizing(Sizing.fixed(columns.orders())));
        }
        table.child(header);
        var rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.gap(1);
        long largest = market.levels().stream().mapToLong(LiveProductSnapshot.PriceLevel::items).max().orElse(1);
        long cumulative = 0;
        for (var level : market.levels()) {
            cumulative = cumulative > Long.MAX_VALUE - level.items() ? Long.MAX_VALUE : cumulative + level.items();
            var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
            row.gap(2);
            if (this.config.showBars) {
                double ratio = largest == 0 ? 0 : (double) level.items() / largest;
                row.surface((graphics, component) -> {
                    int left = component.x() + priceWidth + 2;
                    graphics.fill(left, component.y(), left + (int) (valueWidth * ratio),
                        component.y() + component.height(), buy ? 0x1855FF55 : 0x18FFAA00);
                });
            }
            var price = UiControls.button(decimal(level.price(), true),
                () -> this.copyPrice.accept(decimal(level.price(), false)));
            price.sizing(Sizing.fixed(priceWidth), Sizing.fixed(18));
            price.renderer((graphics, button, delta) -> {
                if (button.isHoveredOrFocused()) {
                    graphics.fill(button.getX(), button.getY(), button.getX() + button.getWidth(),
                        button.getY() + button.getHeight(), 0x303E3935);
                }
            });
            price.setMessage(Component.literal(decimal(level.price(), true)).withStyle(UiStyles.color(accent)));
            price.tooltip(Component.literal("Copy price"));
            this.prices.put(new PriceFocus(this.snapshot.orElseThrow().product().bazaarProductId().orElse(null),
                buy, level.price()), price);
            row.child(price);
            row.child(UiControls.text(integer(level.items()), UiStyles.palette().quantity())
                .horizontalSizing(Sizing.fixed(valueWidth)));
            if (this.config.showCumulative) {
                row.child(UiControls.text(integer(cumulative), UiStyles.palette().label())
                    .horizontalSizing(Sizing.fixed(columns.cumulative())));
            }
            if (this.config.showOrders) {
                row.child(UiControls.text(integer(level.orders()), UiStyles.palette().muted())
                    .horizontalSizing(Sizing.fixed(columns.orders())));
            }
            rows.child(row);
        }
        if (market.levels().isEmpty()) {
            rows.child(UiControls.text("No returned levels", UiStyles.palette().muted()));
        }
        var scroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100), rows);
        scroll.scrollbarThiccness(3);
        table.child(scroll);
        if (columns.totalWidth() > width - 16) {
            table.verticalSizing(Sizing.fill(100));
            var horizontalScroll = UIContainers.horizontalScroll(Sizing.fill(100), Sizing.expand(100), table);
            horizontalScroll.scrollbarThiccness(3);
            panel.child(horizontalScroll);
        } else {
            panel.child(table);
        }
        if (buy) {
            this.buyScroll = scroll;
        } else {
            this.sellScroll = scroll;
        }
        return panel;
    }

    private Columns measureColumns(MarketSide market) {
        var font = Minecraft.getInstance().font;
        int price = Math.max(62, font.width("Price") + 12);
        int items = font.width("Items");
        int cumulativeWidth = font.width("Cumul.");
        int orders = font.width("Orders");
        long cumulative = 0;
        for (var level : market.levels()) {
            cumulative = cumulative > Long.MAX_VALUE - level.items() ? Long.MAX_VALUE : cumulative + level.items();
            price = Math.max(price, font.width(decimal(level.price(), true)) + 12);
            items = Math.max(items, font.width(integer(level.items())));
            cumulativeWidth = Math.max(cumulativeWidth, font.width(integer(cumulative)));
            orders = Math.max(orders, font.width(integer(level.orders())));
        }
        return new Columns(price, items + 4, this.config.showCumulative ? cumulativeWidth + 4 : 0,
            this.config.showOrders ? orders + 4 : 0);
    }

    private record Columns(int price, int items, int cumulative, int orders) {
        int totalWidth() {
            return this.price + this.items + this.cumulative + this.orders + 6;
        }
    }

    private record PriceFocus(String productId, boolean buy, double price) {}

    private static String integer(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private static String decimal(double value, boolean grouping) {
        var formatter = new DecimalFormat(grouping ? "#,##0.0" : "0.0", DecimalFormatSymbols.getInstance(Locale.US));
        return formatter.format(value);
    }
}
