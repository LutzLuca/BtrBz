package com.github.lutzluca.btrbz.core.widgets;

import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.widgets.framework.ui.WidgetScrollState;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static com.github.lutzluca.btrbz.core.widgets.BazaarUi.*;

final class BazaarExtraComponents {
    private BazaarExtraComponents() {}

    static UIComponent embeddedOrderBook(BazaarData.OrderBookData book,
            BazaarWidgetOptions.EmbeddedOrderBook options, boolean interactive,
            WidgetScrollState buyScroll, WidgetScrollState sellScroll,
            Consumer<BazaarAction> actions) {
        int effectiveWidth = options.layout() == BazaarWidgetOptions.EmbeddedBookLayout.STACKED
            ? Math.max(180, (options.contentWidth() - 4) / 2)
            : options.contentWidth();
        var root = panel(effectiveWidth);
        if (options.showHeader()) root.child(line(icon(book.iconCopy()), text(book.itemName(), BazaarStyles.PRIMARY_TEXT), text("Prices", BazaarStyles.MUTED_TEXT)));
        var sides = options.layout() == BazaarWidgetOptions.EmbeddedBookLayout.SPLIT
            ? UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
            : UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        sides.gap(4);
        if (options.showBuy()) sides.child(bookLevels(
            "Buy", book.buyOffers(), BazaarStyles.BUY_ACCENT, options, buyScroll, interactive, actions
        ));
        if (options.showSell()) sides.child(bookLevels(
            "Sell", book.sellOffers(), BazaarStyles.SELL_ACCENT, options, sellScroll, interactive, actions
        ));
        root.child(sides);
        return root;
    }

    static UIComponent bookmarks(List<BazaarData.Bookmark> source, BazaarWidgetOptions.Bookmarks options,
            boolean interactive, WidgetScrollState scrollState,
            BazaarData.BookmarkDragController drag, Consumer<BazaarAction> actions) {
        var data = sortedBookmarks(source, options.sort());
        var root = panel(options.contentWidth());
        root.child(text("Bookmarks", BazaarStyles.PRIMARY_TEXT));
        root.child(new BazaarBookmarkListComponent(
            data, options, interactive, scrollState, drag, actions
        ));
        return root;
    }

    static List<BazaarData.Bookmark> sortedBookmarks(
        List<BazaarData.Bookmark> source,
        BazaarWidgetOptions.BookmarkSort sort
    ) {
        var data = new ArrayList<>(source);
        if (sort == BazaarWidgetOptions.BookmarkSort.ALPHABETICAL) {
            data.sort(Comparator.comparing(BazaarData.Bookmark::productName, String.CASE_INSENSITIVE_ORDER));
        }
        return data;
    }

    static UIComponent presets(List<BazaarData.Preset> presets,
            BazaarWidgetOptions.Presets options, boolean interactive,
            WidgetScrollState scrollState,
            Consumer<BazaarAction> actions) {
        var root = panel(options.contentWidth());
        root.child(text("Order Presets", BazaarStyles.PRIMARY_TEXT));
        var rows = new ArrayList<BazaarOrderRowComponent.BazaarRow>();
        int index = 0;
        for (var preset : presets) {
            if (preset.label().equals("Maximum") && !options.maximum()) continue;
            if (preset.label().equals("Clipboard") && !options.clipboard()) continue;
            if (!preset.available() && !options.showDisabled()) continue;
            List<Component> tooltip = options.showTooltips() && interactive
                ? List.of(Component.literal(preset.tooltip())) : List.of();
            Consumer<Boolean> click = preset.available() && interactive
                ? ignored -> actions.accept(new BazaarAction.ApplyPreset(preset.preset()))
                : null;
            int background = switch (preset.label()) {
                case "Maximum" -> 0x80404020;
                case "Clipboard" -> 0x80204080;
                default -> 0x00000000;
            };
            rows.add(new BazaarOrderRowComponent.BazaarRow(
                "preset-" + index++, preset.label(),
                preset.available() ? BazaarStyles.PRIMARY_TEXT : BazaarStyles.MUTED_TEXT,
                "", "", BazaarStyles.MUTED_TEXT, 0, tooltip, click, false, background
            ));
        }
        int rowHeight = rowHeight();
        root.child(new BazaarOrderListComponent(
            rows, interactive, rowHeight,
            WidgetLayoutTokens.listViewportHeight(rowHeight, Math.min(5, Math.max(1, rows.size()))),
            scrollState
        ));
        return root;
    }

    static UIComponent orderLimit(long used, long limit, BazaarWidgetOptions.OrderLimit options) {
        var root = panel(options.contentWidth());
        if (options.showHeader()) root.child(text("Daily Limit", BazaarStyles.PRIMARY_TEXT));
        int percent = (int) Math.round(used * 100.0 / limit);
        int color = percent >= options.criticalThreshold()
            ? BazaarStyles.STATUS_UNDERCUT
            : percent >= options.warningThreshold() ? BazaarStyles.SELL_ACCENT : BazaarStyles.BUY_ACCENT;
        String usedText = number(used, options.numberStyle());
        String limitText = number(limit, options.numberStyle());
        String value = switch (options.display()) {
            case USED_LIMIT -> usedText + " / " + limitText;
            case REMAINING -> number(limit - used, options.numberStyle()) + " remaining";
            case PERCENTAGE -> percent + "% used";
            case COMPACT -> "Limit " + usedText + "/" + limitText;
        };
        root.child(text(value, color));
        return root;
    }

    static UIComponent priceDiff(BazaarData.PriceDifferenceData diff, BazaarWidgetOptions.PriceDiff options) {
        var root = panel(options.contentWidth());
        if (options.showProduct()) root.child(line(
            options.showItems() ? icon(diff.iconCopy()) : null,
            text(diff.productName(), BazaarStyles.PRIMARY_TEXT)
        ));
        int color = diff.total() >= 0 ? BazaarStyles.BUY_ACCENT : BazaarStyles.STATUS_UNDERCUT;
        if (options.display() != BazaarWidgetOptions.DiffDisplay.TOTAL) {
            root.child(valueLine("Per item", signed(diff.perItem(), options.numberStyle()), color, 0));
        }
        if (options.display() != BazaarWidgetOptions.DiffDisplay.PER_ITEM) {
            root.child(valueLine(
                "Total (" + BazaarData.formatInt(diff.quantity()) + " items)",
                signed(diff.total(), options.numberStyle()),
                color,
                1
            ));
        }
        return root;
    }

    private static FlowLayout bookLevels(String title, List<BazaarData.OrderBookEntry> entries,
            int color, BazaarWidgetOptions.EmbeddedOrderBook options,
            WidgetScrollState scrollState, boolean interactive, Consumer<BazaarAction> actions) {
        boolean amounts = options.showAmounts();
        boolean counts = options.showOrderCount();
        var side = UIContainers.verticalFlow(
            options.layout() == BazaarWidgetOptions.EmbeddedBookLayout.SPLIT
                ? Sizing.expand(50) : Sizing.fill(100),
            Sizing.content()
        );
        side.gap(WidgetLayoutTokens.LINE_GAP);
        side.child(text(title, color));
        var rows = new ArrayList<BazaarOrderRowComponent.BazaarRow>();
        int index = 0;
        for (var entry : entries) {
            String tail = (amounts ? entry.quantityText() : "") + (counts ? " ×" + entry.orders() : "");
            rows.add(new BazaarOrderRowComponent.BazaarRow(
                title + "-price-" + index++, entry.priceText(), color, "", tail,
                BazaarStyles.MUTED_TEXT, 0,
                interactive ? List.of(Component.literal(
                    "Click to use " + entry.priceText() + "; Ctrl-click to copy"
                )) : List.of(),
                interactive ? copyOnly -> actions.accept(
                    new BazaarAction.SelectPrice(entry.price(), copyOnly)
                ) : null,
                true
            ));
        }
        int rowHeight = rowHeight();
        side.child(new BazaarOrderListComponent(
            rows, interactive, rowHeight,
            WidgetLayoutTokens.listViewportHeight(rowHeight, options.visibleRows()), scrollState
        ));
        return side;
    }

    private static int rowHeight() {
        return WidgetLayoutTokens.singleLineRowHeight(Minecraft.getInstance().font.lineHeight);
    }

    private static FlowLayout valueLine(String label, String value, int color, int labelInset) {
        var labelComponent = text(label, BazaarStyles.SECONDARY_TEXT);
        labelComponent.margins(Insets.left(labelInset));
        var line = line(labelComponent);
        line.child(spacer());
        line.child(text(value, color));
        return line;
    }

    private static String number(long value, BazaarWidgetOptions.NumberStyle format) {
        return format == BazaarWidgetOptions.NumberStyle.COMPACT ? BazaarData.formatCompact(value) : BazaarData.formatInt(value);
    }

    private static String signed(long value, BazaarWidgetOptions.NumberStyle format) {
        return (value >= 0 ? "+" : "") + number(value, format);
    }
}
