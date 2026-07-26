package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.BazaarUi.*;

final class BazaarComponents {
    private BazaarComponents() {}

    static UIComponent bazaarOrdersHud(
        BazaarData.OrdersData data,
        int availablePanelHeight,
        BazaarWidgetOptions.Hud options
    ) {
        if (options.mode() == BazaarWidgetOptions.HudMode.STATUS_COUNTS) {
            return statusCountsHud(data, options.contentWidth());
        }
        var orders = data.orders();
        var layout = UIContainers.verticalFlow(Sizing.fixed(options.contentWidth()), Sizing.content());
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.SECTION_GAP);

        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.allowOverflow(true);
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.child(label("Bazaar Orders", BazaarStyles.PRIMARY_TEXT).shadow(false));
        header.child(spacer());
        header.child(label(orderCountText(data), BazaarStyles.MUTED_TEXT));
        layout.child(header);

        if (orders.isEmpty()) {
            layout.child(label(
                emptyHudText(data),
                BazaarStyles.MUTED_TEXT
            ));
            return layout;
        }

        int rowHeight = BazaarHudOrderRowComponent.HEIGHT;
        int visibleRows = BazaarOrderCountPolicy.visibleCount(
            options.visibleOrders(),
            orders.size(),
            availablePanelHeight,
            rowHeight,
            Minecraft.getInstance().font.lineHeight
        );
        var rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.allowOverflow(true);
        rows.gap(WidgetLayoutTokens.LIST_GAP);
        for (int i = 0; i < visibleRows; i++) {
            rows.child(new BazaarHudOrderRowComponent(orders.get(i), options));
        }

        layout.child(rows);
        if (orders.size() > visibleRows) {
            layout.child(overflowLegend(orders.subList(visibleRows, orders.size())));
        }

        return layout;
    }

    static UIComponent trackedOrdersList(BazaarData.OrdersData data,
                                         BazaarWidgetOptions.TrackedOrders options,
                                         boolean interactive,
                                         WidgetScrollState scrollState,
                                         BazaarData.DragController drag,
                                         BazaarData.HoverController hover,
                                         Consumer<BazaarAction> actions) {
        var sorted = sortedTrackedOrders(data.orders(), options.sort());
        var layout = UIContainers.verticalFlow(Sizing.fixed(options.contentWidth()), Sizing.content());
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.SECTION_GAP);

        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.allowOverflow(true);
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.child(label("Tracked Orders", BazaarStyles.PRIMARY_TEXT));
        header.child(spacer());
        header.child(label(sorted.size() + " active", BazaarStyles.MUTED_TEXT));
        layout.child(header);

        if (options.showStatusSummary()) {
            layout.child(statusCountsStrip(data, options.contentWidth()));
        }

        layout.child(new BazaarTrackedOrderListComponent(
            sorted,
            options,
            interactive,
            BazaarData.Order::tooltipLines,
            scrollState,
            drag,
            hover,
            actions
        ));

        return layout;
    }

    static String emptyHudText(BazaarData.OrdersData data) {
        return data.filledOrderCount() == 0 ? "No active or filled orders" : "No active orders";
    }

    static List<BazaarData.Order> sortedTrackedOrders(
        List<BazaarData.Order> orders,
        BazaarWidgetOptions.TrackedSort sort
    ) {
        var sorted = new ArrayList<>(orders);
        switch (sort) {
            case NEWEST -> sorted.sort(java.util.Comparator.comparingLong(BazaarData.Order::creationSequence).reversed());
            case OLDEST -> sorted.sort(java.util.Comparator.comparingLong(BazaarData.Order::creationSequence));
            case STATUS -> sorted.sort(java.util.Comparator.comparing(order -> order.status().ordinal()));
            case SIDE -> sorted.sort(java.util.Comparator.comparing(order -> order.side().ordinal()));
            case PRODUCT -> sorted.sort(java.util.Comparator.comparing(BazaarData.Order::itemName));
            case VALUE -> sorted.sort(java.util.Comparator.comparingLong((BazaarData.Order order) -> order.unitPrice() * order.amount()).reversed());
            case MANUAL -> { }
        }
        return sorted;
    }

    static UIComponent orderValue(
        BazaarData.OrderValueData value,
        BazaarWidgetOptions.OrderValue options
    ) {
        var layout = UIContainers.verticalFlow(Sizing.fixed(options.contentWidth()), Sizing.content());
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.LINE_GAP);
        layout.child(label("Bazaar Overview", BazaarStyles.SELL_ACCENT));
        if (options.display() == BazaarWidgetOptions.ValueDisplay.DETAILED) {
            if (options.buyLocked()) layout.child(valueLine("Buy Orders (Locked)", value.buyLocked(), semantic(options.colorMode(), BazaarStyles.BUY_ACCENT), false, options.numberStyle(), options.showCoinsSuffix()));
            if (options.buyItems()) layout.child(valueLine("Buy Orders (Items)", value.buyItems(), semantic(options.colorMode(), BazaarStyles.BUY_ACCENT), false, options.numberStyle(), options.showCoinsSuffix()));
            if (options.sellClaimable()) layout.child(valueLine("Sell Offers (Claimable)", value.sellClaimable(), semantic(options.colorMode(), BazaarStyles.SELL_ACCENT), false, options.numberStyle(), options.showCoinsSuffix()));
            if (options.sellPending()) layout.child(valueLine("Sell Offers (Pending)", value.sellPending(), semantic(options.colorMode(), BazaarStyles.SELL_ACCENT), false, options.numberStyle(), options.showCoinsSuffix()));
        }
        layout.child(valueLine("Total Worth", value.total(), BazaarStyles.PRIMARY_TEXT, true, options.numberStyle(), options.showCoinsSuffix()));
        return layout;
    }

    static UIComponent orderBook(BazaarData.OrderBookData snapshot,
                                 BazaarWidgetOptions.OrderBook options,
                                 boolean interactive,
                                 WidgetScrollState buyScroll,
                                 WidgetScrollState sellScroll,
                                 Consumer<BazaarAction> actions) {
        int contentWidth = orderBookContentWidth(options);
        int sideWidth = orderBookSideWidth(options);
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

        int rowHeight = activeRowHeight();
        int viewportHeight = WidgetLayoutTokens.listViewportHeight(rowHeight, options.visibleRows());
        int sideHeight = Minecraft.getInstance().font.lineHeight + WidgetLayoutTokens.LINE_GAP + viewportHeight;
        var lists = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(sideHeight));
        lists.allowOverflow(true);
        lists.gap(2);
        if (options.layout() != BazaarWidgetOptions.BookLayout.SELL_ONLY) lists.child(bookSide(
            "Buy Offers",
            snapshot.buyOffers(),
            interactive,
            rowHeight,
            viewportHeight,
            buyScroll, options, sideWidth < 190, actions
        ));
        if (options.layout() != BazaarWidgetOptions.BookLayout.BUY_ONLY) lists.child(bookSide(
            "Sell Offers",
            snapshot.sellOffers(),
            interactive,
            rowHeight,
            viewportHeight,
            sellScroll, options, sideWidth < 190, actions
        ));
        layout.child(lists);

        return layout;
    }

    private static UIComponent bookSide(
        String title,
        List<BazaarData.OrderBookEntry> entries,
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
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            rows.add(new BazaarOrderRowComponent.BazaarRow(
                title + "-" + i,
                entry.priceText(),
                entry.side().accentColor(),
                "",
                compactMetadata
                    ? format(entry.quantity(), options.numberStyle()) + "v" + (options.showOrderCount() ? " · " + entry.orders() + "o" : "")
                    : "Vol: " + format(entry.quantity(), options.numberStyle()) + (options.showOrderCount() ? "  Ord: " + entry.orders() : ""),
                BazaarStyles.MUTED_TEXT,
                0,
                List.of(),
                copyOnly -> actions.accept(new BazaarAction.SelectPrice(entry.price(), copyOnly)),
                true
            ));
        }

        layout.child(new BazaarOrderListComponent(
            rows,
            interactive,
            rowHeight,
            viewportHeight,
            scrollState
        ));
        return layout;
    }

    private static int activeRowHeight() {
        return WidgetLayoutTokens.singleLineRowHeight(Minecraft.getInstance().font.lineHeight);
    }

    private static UIComponent statusCountsHud(BazaarData.OrdersData data, int contentWidth) {
        var layout = UIContainers.verticalFlow(Sizing.fixed(contentWidth), Sizing.content());
        layout.allowOverflow(true);
        layout.gap(WidgetLayoutTokens.SECTION_GAP);

        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.allowOverflow(true);
        header.child(label("Bazaar Orders", BazaarStyles.PRIMARY_TEXT));
        header.child(spacer());
        header.child(label(data.counts().total() + " active", BazaarStyles.MUTED_TEXT));
        layout.child(header);

        layout.child(statusCountsStrip(data, contentWidth));
        return layout;
    }

    static int orderBookContentWidth(BazaarWidgetOptions.OrderBook options) {
        return options.contentWidth();
    }

    static int orderBookSideWidth(BazaarWidgetOptions.OrderBook options) {
        int contentWidth = orderBookContentWidth(options);
        return options.layout() == BazaarWidgetOptions.BookLayout.SPLIT
            ? Math.max(1, (contentWidth - 2) / 2)
            : contentWidth;
    }

    private static UIComponent statusCountsStrip(BazaarData.OrdersData data, int contentWidth) {
        var entries = visibleStatusEntries(data);
        if (entries.isEmpty()) return label("No active or filled orders", BazaarStyles.MUTED_TEXT);

        int columnWidth = Math.max(1, (contentWidth - WidgetLayoutTokens.HEADER_GAP) / 2);
        var grid = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        grid.gap(WidgetLayoutTokens.LINE_GAP);
        for (int index = 0; index < entries.size(); index += 2) {
            var right = index + 1 < entries.size() ? entries.get(index + 1) : null;
            grid.child(statusCountsRow(entries.get(index), right, columnWidth));
        }
        return grid;
    }

    static List<StatusEntry> visibleStatusEntries(BazaarData.OrdersData data) {
        var counts = data.counts();
        var entries = new ArrayList<StatusEntry>();
        addStatusEntry(entries, "Undercut", counts.undercut(), BazaarStyles.STATUS_UNDERCUT);
        addStatusEntry(entries, "Matched", counts.matched(), BazaarStyles.STATUS_MATCHED);
        addStatusEntry(entries, "Best", counts.top(), BazaarStyles.STATUS_TOP);
        addStatusEntry(entries, "Filled", data.filledOrderCount(), BazaarStyles.STATUS_FILLED);
        addStatusEntry(entries, "Unknown", counts.unknown(), BazaarStyles.STATUS_UNKNOWN);
        return List.copyOf(entries);
    }

    private static UIComponent statusCountsRow(StatusEntry left, StatusEntry right, int columnWidth) {
        var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(9));
        row.allowOverflow(true);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.child(statusSummary(left, columnWidth));
        row.child(spacer());
        if (right != null) row.child(statusSummary(right, columnWidth));
        return row;
    }

    private static void addStatusEntry(List<StatusEntry> entries, String label, int count, int color) {
        if (count > 0) entries.add(new StatusEntry(label, count, color));
    }

    private static UIComponent statusSummary(StatusEntry status, int width) {
        var entry = UIContainers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(9));
        entry.allowOverflow(true);
        entry.verticalAlignment(VerticalAlignment.CENTER);
        entry.child(boldLabel(status.label(), status.color()));
        entry.child(spacer());
        entry.child(boldLabel(status.count() + "x", status.color()));
        return entry;
    }

    private static String orderCountText(BazaarData.OrdersData data) {
        return data.counts().total() + " active · " + data.filledOrderCount() + " filled";
    }

    record StatusEntry(String label, int count, int color) {}

    private static UIComponent valueLine(String name, long value, int color, boolean bold,
                                         BazaarWidgetOptions.NumberStyle format, boolean coins) {
        var line = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        line.allowOverflow(true);
        line.child(label(name, BazaarStyles.SECONDARY_TEXT));
        line.child(spacer());
        line.child(bold
            ? boldLabel(format(value, format) + (coins ? " coins" : ""), color)
            : label(format(value, format) + (coins ? " coins" : ""), color));
        return line;
    }

    private static String format(long value, BazaarWidgetOptions.NumberStyle style) {
        return style == BazaarWidgetOptions.NumberStyle.COMPACT ? BazaarData.formatCompact(value) : BazaarData.formatInt(value);
    }

    private static int semantic(BazaarWidgetOptions.ColorMode mode, int semantic) {
        return mode == BazaarWidgetOptions.ColorMode.SEMANTIC ? semantic : BazaarStyles.SECONDARY_TEXT;
    }

    private static UIComponent overflowLegend(List<BazaarData.Order> overflow) {
        long undercut = overflow.stream().filter(order -> order.status() == BazaarData.OrderStatus.UNDERCUT).count();
        long matched = overflow.stream().filter(order -> order.status() == BazaarData.OrderStatus.MATCHED).count();
        long top = overflow.stream().filter(order -> order.status() == BazaarData.OrderStatus.TOP).count();
        long unknown = overflow.stream().filter(order -> order.status() == BazaarData.OrderStatus.UNKNOWN).count();

        var legend = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        legend.allowOverflow(true);
        legend.verticalAlignment(VerticalAlignment.CENTER);
        legend.gap(4);
        legend.child(label("+" + overflow.size() + " more ·", BazaarStyles.MUTED_TEXT));
        addStatusCount(legend, undercut, "outdated");
        addStatusCount(legend, matched, "matched");
        addStatusCount(legend, top, "best_order");
        addStatusCount(legend, unknown, "unknown");
        return legend;
    }

    private static void addStatusCount(
        io.wispforest.owo.ui.container.FlowLayout legend,
        long count,
        String iconName
    ) {
        if (count > 0) legend.child(statusCount(count, iconName));
    }

    private static UIComponent statusCount(long count, String iconName) {
        var entry = UIContainers.horizontalFlow(Sizing.content(), Sizing.fixed(9));
        entry.allowOverflow(true);
        entry.verticalAlignment(VerticalAlignment.CENTER);
        entry.gap(2);
        entry.child(label(Long.toString(count), BazaarStyles.SECONDARY_TEXT));
        var icon = UIComponents.texture(
            Identifier.fromNamespaceAndPath("btrbz", "textures/gui/status/" + iconName + ".png"),
            0, 0, 9, 9, 9, 9
        );
        icon.sizing(Sizing.fixed(9), Sizing.fixed(9));
        entry.child(icon);
        return entry;
    }

}
