package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.widgets.action.BazaarAction;
import com.github.lutzluca.btrbz.core.widgets.config.BazaarWidgetOptions;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderListComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarOrderRowComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi.*;

public final class OrderBookWidget {
    private OrderBookWidget() {}

    public static UIComponent full(
        BazaarWidgetViewData.OrderBookData snapshot,
        BazaarWidgetOptions.OrderBook options,
        boolean interactive,
        WidgetScrollState buyScroll,
        WidgetScrollState sellScroll,
        Consumer<BazaarAction> actions
    ) {
        int contentWidth = contentWidth(options);
        int sideWidth = sideWidth(options);
        var layout = UIContainers.verticalFlow(Sizing.fixed(contentWidth), Sizing.content());
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.SECTION_GAP);

        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.allowOverflow(true);
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(WidgetLayoutTokens.HEADER_GAP);
        header.child(icon(snapshot.iconCopy()));
        header.child(label(snapshot.itemName(), BazaarStyles.PRIMARY_TEXT));
        header.child(label("Order Book", BazaarStyles.MUTED_TEXT));
        if (options.showHeader()) layout.child(header);

        int rowHeight = rowHeight();
        int viewportHeight = WidgetLayoutTokens.listViewportHeight(rowHeight, options.visibleRows());
        int sideHeight = Minecraft.getInstance().font.lineHeight + WidgetLayoutTokens.LINE_GAP + viewportHeight;
        var lists = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(sideHeight));
        lists.allowOverflow(true);
        lists.gap(2);
        if (options.layout() != BazaarWidgetOptions.BookLayout.SellOnly) {
            lists.child(fullSide(
                "Buy Offers", snapshot.buyOffers(), interactive, rowHeight, viewportHeight,
                buyScroll, options, sideWidth < 190, actions
            ));
        }
        if (options.layout() != BazaarWidgetOptions.BookLayout.BuyOnly) {
            lists.child(fullSide(
                "Sell Offers", snapshot.sellOffers(), interactive, rowHeight, viewportHeight,
                sellScroll, options, sideWidth < 190, actions
            ));
        }
        layout.child(lists);
        return layout;
    }

    public static UIComponent embedded(
        BazaarWidgetViewData.OrderBookData book,
        BazaarWidgetOptions.EmbeddedOrderBook options,
        boolean interactive,
        WidgetScrollState buyScroll,
        WidgetScrollState sellScroll,
        Consumer<BazaarAction> actions
    ) {
        int effectiveWidth = options.layout() == BazaarWidgetOptions.EmbeddedBookLayout.Stacked
            ? Math.max(180, (options.contentWidth() - 4) / 2)
            : options.contentWidth();
        var root = panel(effectiveWidth);
        if (options.showHeader()) {
            root.child(line(
                icon(book.iconCopy()),
                text(book.itemName(), BazaarStyles.PRIMARY_TEXT),
                text("Prices", BazaarStyles.MUTED_TEXT)
            ));
        }
        var sides = options.layout() == BazaarWidgetOptions.EmbeddedBookLayout.Split
            ? UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
            : UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        sides.gap(4);
        if (options.showBuy()) sides.child(embeddedSide(
            "Buy", book.buyOffers(), BazaarStyles.BUY_ACCENT, options, buyScroll, interactive, actions
        ));
        if (options.showSell()) sides.child(embeddedSide(
            "Sell", book.sellOffers(), BazaarStyles.SELL_ACCENT, options, sellScroll, interactive, actions
        ));
        root.child(sides);
        return root;
    }

    public static int contentWidth(BazaarWidgetOptions.OrderBook options) {
        return options.contentWidth();
    }

    public static int sideWidth(BazaarWidgetOptions.OrderBook options) {
        return options.layout() == BazaarWidgetOptions.BookLayout.Split
            ? Math.max(1, (contentWidth(options) - 2) / 2)
            : contentWidth(options);
    }

    private static UIComponent fullSide(
        String title,
        List<BazaarWidgetViewData.OrderBookEntry> entries,
        boolean interactive,
        int rowHeight,
        int viewportHeight,
        WidgetScrollState scrollState,
        BazaarWidgetOptions.OrderBook options,
        boolean compactMetadata,
        Consumer<BazaarAction> actions
    ) {
        var layout = UIContainers.verticalFlow(Sizing.expand(50), Sizing.fill(100));
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.LINE_GAP);
        layout.child(label(title, BazaarStyles.SECONDARY_TEXT));
        var rows = new ArrayList<BazaarOrderRowComponent.BazaarRow>();
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            rows.add(new BazaarOrderRowComponent.BazaarRow(
                title + "-" + index,
                entry.priceText(),
                entry.side().accentColor(),
                "",
                compactMetadata
                    ? number(entry.quantity(), options.numberStyle()) + "v"
                        + (options.showOrderCount() ? " · " + entry.orders() + "o" : "")
                    : "Vol: " + number(entry.quantity(), options.numberStyle())
                        + (options.showOrderCount() ? "  Ord: " + entry.orders() : ""),
                BazaarStyles.MUTED_TEXT,
                0,
                List.of(),
                copyOnly -> actions.accept(new BazaarAction.SelectPrice(entry.price(), copyOnly)),
                true
            ));
        }
        layout.child(new BazaarOrderListComponent(rows, interactive, rowHeight, viewportHeight, scrollState));
        return layout;
    }

    private static FlowLayout embeddedSide(
        String title,
        List<BazaarWidgetViewData.OrderBookEntry> entries,
        int color,
        BazaarWidgetOptions.EmbeddedOrderBook options,
        WidgetScrollState scrollState,
        boolean interactive,
        Consumer<BazaarAction> actions
    ) {
        var side = UIContainers.verticalFlow(
            options.layout() == BazaarWidgetOptions.EmbeddedBookLayout.Split
                ? Sizing.expand(50)
                : Sizing.fill(100),
            Sizing.content()
        );
        side.gap(WidgetLayoutTokens.LINE_GAP);
        side.child(text(title, color));
        var rows = new ArrayList<BazaarOrderRowComponent.BazaarRow>();
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            String tail = (options.showAmounts() ? entry.quantityText() : "")
                + (options.showOrderCount() ? " ×" + entry.orders() : "");
            rows.add(new BazaarOrderRowComponent.BazaarRow(
                title + "-price-" + index,
                entry.priceText(),
                color,
                "",
                tail,
                BazaarStyles.MUTED_TEXT,
                0,
                interactive
                    ? List.of(Component.literal("Click to use " + entry.priceText() + "; Ctrl-click to copy"))
                    : List.of(),
                interactive
                    ? copyOnly -> actions.accept(new BazaarAction.SelectPrice(entry.price(), copyOnly))
                    : null,
                true
            ));
        }
        int rowHeight = rowHeight();
        side.child(new BazaarOrderListComponent(
            rows,
            interactive,
            rowHeight,
            WidgetLayoutTokens.listViewportHeight(rowHeight, options.visibleRows()),
            scrollState
        ));
        return side;
    }

    private static int rowHeight() {
        return WidgetLayoutTokens.singleLineRowHeight(Minecraft.getInstance().font.lineHeight);
    }

    private static String number(long value, BazaarWidgetOptions.NumberStyle style) {
        return style == BazaarWidgetOptions.NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }
}
