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
        var productIcon = snapshot.iconCopy();
        if (options.showItem() && !productIcon.isEmpty()) header.child(icon(productIcon));
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
        int effectiveWidth = embeddedContentWidth(options, book);
        var root = panel(effectiveWidth);
        if (options.showHeader()) {
            var productIcon = book.iconCopy();
            root.child(line(
                options.showItem() && !productIcon.isEmpty() ? icon(productIcon) : null,
                text(book.itemName(), BazaarStyles.PRIMARY_TEXT),
                text("Prices", BazaarStyles.MUTED_TEXT)
            ));
        }
        var sides = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        sides.gap(4);
        boolean singleVisibleSide = embeddedVisibleSideCount(options, book) <= 1;
        if (showsEmbeddedSide(options, book, BazaarWidgetViewData.OrderSide.Buy)) sides.child(embeddedSide(
            "Buy", book.buyOffers(), BazaarStyles.BUY_ACCENT, options, buyScroll,
            singleVisibleSide, interactive, actions
        ));
        if (showsEmbeddedSide(options, book, BazaarWidgetViewData.OrderSide.Sell)) sides.child(embeddedSide(
            "Sell", book.sellOffers(), BazaarStyles.SELL_ACCENT, options, sellScroll,
            singleVisibleSide, interactive, actions
        ));
        root.child(sides);
        return root;
    }

    static boolean showsEmbeddedSide(
        BazaarWidgetOptions.EmbeddedOrderBook options,
        BazaarWidgetViewData.OrderBookData book,
        BazaarWidgetViewData.OrderSide side
    ) {
        boolean configured = side == BazaarWidgetViewData.OrderSide.Buy
            ? options.showBuy()
            : options.showSell();
        return configured && (options.sideDisplay() == BazaarWidgetOptions.EmbeddedSideDisplay.Both
            || book.appropriateSide().isEmpty()
            || book.appropriateSide().filter(side::equals).isPresent());
    }

    static int embeddedContentWidth(
        BazaarWidgetOptions.EmbeddedOrderBook options,
        BazaarWidgetViewData.OrderBookData book
    ) {
        return embeddedVisibleSideCount(options, book) <= 1
            ? Math.max(1, (options.contentWidth() - 4) / 2)
            : options.contentWidth();
    }

    static int embeddedVisibleSideCount(
        BazaarWidgetOptions.EmbeddedOrderBook options,
        BazaarWidgetViewData.OrderBookData book
    ) {
        int visibleSides = 0;
        if (showsEmbeddedSide(options, book, BazaarWidgetViewData.OrderSide.Buy)) visibleSides++;
        if (showsEmbeddedSide(options, book, BazaarWidgetViewData.OrderSide.Sell)) visibleSides++;
        return visibleSides;
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
        boolean singleVisibleSide,
        boolean interactive,
        Consumer<BazaarAction> actions
    ) {
        var side = UIContainers.verticalFlow(
            singleVisibleSide ? Sizing.fill(100) : Sizing.expand(50),
            Sizing.content()
        );
        side.gap(WidgetLayoutTokens.LINE_GAP);
        side.child(text(title, color));
        var rows = new ArrayList<BazaarOrderRowComponent.BazaarRow>();
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            String tail = embeddedMetadata(entry, options);
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

    static String embeddedMetadata(
        BazaarWidgetViewData.OrderBookEntry entry,
        BazaarWidgetOptions.EmbeddedOrderBook options
    ) {
        var parts = new ArrayList<String>();
        if (options.showAmounts()) parts.add(entry.quantityText());
        if (options.showOrderCount()) parts.add(entry.orders() + " ord");
        return String.join(" · ", parts);
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
