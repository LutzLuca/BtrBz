package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.core.ui.UiControls;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.data.LiveProductSnapshot;
import com.github.lutzluca.btrbz.data.LiveProductSnapshot.MarketSide;
import com.github.lutzluca.btrbz.utils.Utils;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Exact live levels, with independently retained scroll positions on both sides. */
public final class OrderBookPanel extends FlowLayout {
    private static final int BUY = 0xFF55FF55;
    private static final int SELL = 0xFFFFAA00;
    private static final int CARD_PADDING = 8;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_GUTTER = SCROLLBAR_WIDTH + 5;
    private static final int ROW_HEIGHT = 15;
    private final ItemInfoConfig config;
    private final Consumer<String> copyPrice;
    private Optional<LiveProductSnapshot> snapshot = Optional.empty();
    private RestorableVerticalScrollContainer<FlowLayout> buyScroll;
    private RestorableVerticalScrollContainer<FlowLayout> sellScroll;
    private ButtonComponent buySelector;
    private ButtonComponent sellSelector;
    private ViewState viewState = new ViewState(0, 0);
    private final Map<PriceFocus, ButtonComponent> prices = new HashMap<>();
    private int availableWidth = 500;
    private int availableHeight = 220;
    private boolean selectedBuy = true;

    public OrderBookPanel(
        ItemInfoConfig config,
        Consumer<String> copyPrice
    ) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.config = config;
        this.copyPrice = copyPrice;
        this.gap(5);
        this.refresh();
    }

    public void update(Optional<LiveProductSnapshot> snapshot) {
        if (!this.snapshot.equals(snapshot)) {
            this.snapshot = snapshot;
            this.refresh();
        }
    }

    public void layoutFor(int width, int height) {
        int nextWidth = Math.max(140, width);
        int nextHeight = Math.max(100, height);
        if (this.availableWidth != nextWidth || this.availableHeight != nextHeight) {
            this.availableWidth = nextWidth;
            this.availableHeight = nextHeight;
            this.refresh();
        }
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
        var focused = handler == null ? null : handler.focused();
        PriceFocus focusedPrice = handler == null
            ? null : this.prices.entrySet().stream()
                .filter(entry -> entry.getValue() == focused).map(Map.Entry::getKey).findFirst().orElse(null);
        boolean buyScrollFocus = focused != null && focused == this.buyScroll;
        boolean sellScrollFocus = focused != null && focused == this.sellScroll;
        boolean buySelectorFocus = focused != null && focused == this.buySelector;
        boolean sellSelectorFocus = focused != null && focused == this.sellSelector;
        boolean ownedFocus = focusedPrice != null || buyScrollFocus
            || sellScrollFocus
            || buySelectorFocus
            || sellSelectorFocus;
        var source = handler == null || handler.lastFocusSource() == null
            ? UIComponent.FocusSource.KEYBOARD_CYCLE : handler.lastFocusSource();
        if (ownedFocus) {
            handler.focus(null, source);
        }
        this.viewState = this.saveViewState();
        this.clearChildren();
        this.prices.clear();
        this.buyScroll = null;
        this.sellScroll = null;
        this.buySelector = null;
        this.sellSelector = null;
        var buyMarket = this.snapshot.map(LiveProductSnapshot::sellOffers)
            .orElse(new MarketSide(List.of(), Optional.empty()));
        var sellMarket = this.snapshot.map(LiveProductSnapshot::buyOrders)
            .orElse(new MarketSide(List.of(), Optional.empty()));
        int halfWidth = (this.availableWidth - 8) / 2;
        boolean columns = this.minimumSideWidth(buyMarket, true) <= halfWidth
            && this.minimumSideWidth(sellMarket, false) <= halfWidth;
        if (!columns) {
            var buy = UiControls.button("Buy Price", () -> this.selectSide(true));
            var sell = UiControls.button("Sell Price", () -> this.selectSide(false));
            buy.renderer(UiControls.segmentRenderer(this.selectedBuy));
            sell.renderer(UiControls.segmentRenderer(!this.selectedBuy));
            buy.horizontalSizing(Sizing.expand(50));
            sell.horizontalSizing(Sizing.expand(50));
            this.buySelector = buy;
            this.sellSelector = sell;
            this.child(UiControls.row(buy, sell));
        }
        int width = columns ? halfWidth : this.availableWidth;
        int height = Math.max(100, this.availableHeight - (columns ? 0 : 27));
        var sides = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        sides.gap(8);
        if (columns || this.selectedBuy) {
            sides.child(this.side(buyMarket, BUY, width, height, true));
        }
        if (columns || !this.selectedBuy) {
            sides.child(this.side(sellMarket, SELL, width, height, false));
        }
        this.child(sides);
        this.restoreViewState(this.viewState);
        if (ownedFocus && this.focusHandler() == handler && handler.focused() == null) {
            UIComponent replacement = this.prices.get(focusedPrice);
            if (buyScrollFocus) {
                replacement = this.buyScroll;
            } else if (sellScrollFocus) {
                replacement = this.sellScroll;
            } else if (buySelectorFocus) {
                replacement = this.buySelector;
            } else if (sellSelectorFocus) {
                replacement = this.sellSelector;
            }
            if (replacement != null && replacement.focusHandler() == handler) {
                handler.focus(replacement, source);
            }
        }
    }

    private void selectSide(boolean buy) {
        this.queue(() -> {
            this.selectedBuy = buy;
            this.refresh();
        });
    }

    private FlowLayout side(MarketSide market, int accent, int width, int height, boolean buy) {
        var panel = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.fixed(height));
        panel.padding(Insets.of(CARD_PADDING));
        panel.gap(3);
        panel.surface(WidgetSurfaces.roundedPanel(0xFF1D2127, 4));
        var title = UiControls.text(buy ? "Buy Price" : "Sell Price", accent);
        title.tooltip(Component.literal((buy ? "Sell offers, lowest first." : "Buy orders, highest first.")
            + " Click a price to copy it."));
        int innerWidth = width - CARD_PADDING * 2;
        String levelsText = market.levels().size() + " levels shown";
        if (Minecraft.getInstance().font.width(title.text()) + Minecraft.getInstance().font.width(levelsText)
            + 12 > innerWidth) {
            levelsText = market.levels().size() + " levels";
        }
        var levels = UiControls.text(levelsText, UiStyles.palette().muted());
        var heading = UiControls.row(title, BazaarUi.spacer(), levels);
        panel.child(heading);
        String exactTotals = market.totals().map(value -> integer(value.items()) + " items"
            + (this.config.showOrders ? ", " + integer(value.orders()) + " orders" : ""))
            .orElse("Full-side totals unavailable");
        var font = Minecraft.getInstance().font;
        String totals = font.width(exactTotals) <= innerWidth
            ? exactTotals
            : market.totals().map(value -> Utils.formatCompact(value.items()) + " items"
                + (this.config.showOrders ? ", " + Utils.formatCompact(value.orders()) + " orders" : ""))
                .orElse("Totals unavailable");
        var totalsLabel = UiControls.text(totals, UiStyles.palette().muted()).maxWidth(innerWidth);
        totalsLabel.margins(Insets.bottom(3));
        totalsLabel.tooltip(Component.literal("Full side: " + exactTotals
            + ". Includes levels outside this returned summary."));
        panel.child(totalsLabel);
        var columns = this.measureColumns(market).spreadTo(innerWidth - SCROLLBAR_GUTTER);
        int tableWidth = columns.totalWidth() + SCROLLBAR_GUTTER;
        boolean horizontalScroll = tableWidth > innerWidth;
        int metadataHeight = font.lineHeight * (1 + font.split(Component.literal(totals), innerWidth).size()) + 9;
        int availableRows = height - CARD_PADDING * 2 - metadataHeight - ROW_HEIGHT - 2
            - (horizontalScroll ? 5 : 0);
        int rowsHeight = Math.max(ROW_HEIGHT, (availableRows + 1) / (ROW_HEIGHT + 1) * (ROW_HEIGHT + 1) - 1);
        var table = UIContainers.verticalFlow(Sizing.fixed(tableWidth), Sizing.fixed(ROW_HEIGHT + 2 + rowsHeight));
        table.gap(2);
        table.child(new TableRow(columns, new String[]{"Price", "Items", "Cumul.", "Orders"},
            UiStyles.palette().muted(), 0, false, null));
        // The same fixed data width is used by headings and rows, even when the bar is hidden.
        var rows = UIContainers.verticalFlow(Sizing.fixed(columns.totalWidth()), Sizing.content());
        rows.gap(1);
        long largest = market.levels().stream().mapToLong(LiveProductSnapshot.PriceLevel::items).max().orElse(1);
        long cumulative = 0;
        for (var level : market.levels()) {
            cumulative = cumulative > Long.MAX_VALUE - level.items() ? Long.MAX_VALUE : cumulative + level.items();
            var price = new PriceCell(decimal(level.price(), true),
                () -> this.copyPrice.accept(decimal(level.price(), false)));
            price.sizing(Sizing.fixed(columns.price()), Sizing.fixed(ROW_HEIGHT));
            price.tooltip(Component.literal("Copy price " + decimal(level.price(), true)));
            this.prices.put(new PriceFocus(this.snapshot.orElseThrow().product().bazaarProductId().orElse(null),
                buy, level.price()), price);
            rows.child(new TableRow(columns,
                new String[]{decimal(level.price(), true), integer(level.items()), integer(cumulative),
                    integer(level.orders())},
                accent, this.config.showBars && largest > 0 ? (double) level.items() / largest : 0, true, price));
        }
        if (market.levels().isEmpty()) {
            rows.child(UiControls.text(this.snapshot.isEmpty() ? "Live book unavailable" : "No returned levels",
                UiStyles.palette().muted()));
        }
        var scroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.fixed(rowsHeight), rows);
        scroll.scrollbarThiccness(SCROLLBAR_WIDTH);
        table.child(scroll);
        if (horizontalScroll) {
            var horizontal = UIContainers.horizontalScroll(Sizing.fill(100),
                Sizing.fixed(ROW_HEIGHT + 2 + rowsHeight + 5), table);
            horizontal.scrollbarThiccness(SCROLLBAR_WIDTH);
            panel.child(horizontal);
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
        int price = font.width("Price") + 2;
        int items = font.width("Items") + 2;
        int cumulativeWidth = font.width("Cumul.") + 2;
        int orders = font.width("Orders") + 2;
        long cumulative = 0;
        for (var level : market.levels()) {
            cumulative = cumulative > Long.MAX_VALUE - level.items() ? Long.MAX_VALUE : cumulative + level.items();
            price = Math.max(price, font.width(decimal(level.price(), true)) + 2);
            items = Math.max(items, font.width(integer(level.items())) + 2);
            cumulativeWidth = Math.max(cumulativeWidth, font.width(integer(cumulative)) + 2);
            orders = Math.max(orders, font.width(integer(level.orders())) + 2);
        }
        return new Columns(price, items, this.config.showCumulative ? cumulativeWidth : 0,
            this.config.showOrders ? orders : 0);
    }

    private int minimumSideWidth(MarketSide market, boolean buy) {
        var font = Minecraft.getInstance().font;
        int headingWidth = font.width(buy ? "Buy Price" : "Sell Price")
            + font.width(market.levels().size() + " levels shown") + 12;
        return CARD_PADDING * 2 + Math.max(headingWidth, this.measureColumns(market).totalWidth() + SCROLLBAR_GUTTER);
    }

    private record Columns(int price, int items, int cumulative, int orders) {
        int totalWidth() {
            return this.price + this.items + this.cumulative + this.orders + 6 * (1 + (this.cumulative > 0 ? 1 : 0)
                + (this.orders > 0 ? 1 : 0));
        }

        Columns spreadTo(int width) {
            int extra = Math.max(0, width - this.totalWidth());
            int count = 2 + (this.cumulative > 0 ? 1 : 0);
            int share = extra / count;
            return new Columns(this.price + share, this.items + share + extra % count,
                this.cumulative > 0 ? this.cumulative + share : 0, this.orders);
        }
    }

    private static final class PriceCell extends ButtonComponent {
        private final Component description;

        private PriceCell(String price, Runnable copy) {
            super(Component.empty(), _ -> copy.run());
            this.description = Component.literal("Copy price " + price);
            this.renderer((graphics, button, delta) -> {});
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return Component.translatable("gui.narrate.button", this.description);
        }
    }

    private static final class TableRow extends FlowLayout {
        private final Columns columns;
        private final String[] values;
        private final int accent;
        private final double ratio;
        private final boolean data;
        private final ButtonComponent price;

        private TableRow(
            Columns columns,
            String[] values,
            int accent,
            double ratio,
            boolean data,
            ButtonComponent price
        ) {
            super(Sizing.fixed(columns.totalWidth()), Sizing.fixed(ROW_HEIGHT), Algorithm.HORIZONTAL);
            this.columns = columns;
            this.values = values;
            this.accent = accent;
            this.ratio = ratio;
            this.data = data;
            this.price = price;
            this.verticalAlignment(VerticalAlignment.CENTER);
            if (price != null) {
                this.child(price);
            }
        }

        @Override
        public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            if (this.data && (this.isInBoundingBox(mouseX, mouseY)
                || (this.price != null && this.price.isHoveredOrFocused()))) {
                graphics.fill(this.x(), this.y(), this.x() + this.width(), this.y() + this.height(), 0x203E3935);
            }
            int itemLeft = this.x() + this.columns.price() + 6;
            if (this.ratio > 0) {
                graphics.fill(itemLeft, this.y() + 1, itemLeft + (int) (this.columns.items() * this.ratio),
                    this.y() + this.height() - 1, this.accent == BUY ? 0x4055FF55 : 0x40FFAA00);
            }
            var font = Minecraft.getInstance().font;
            int baseline = this.y() + (this.height() - font.lineHeight) / 2;
            int x = this.x();
            int[] widths = {this.columns.price(), this.columns.items(), this.columns.cumulative(),
                this.columns.orders()};
            for (int index = 0; index < widths.length; index++) {
                if (widths[index] == 0) {
                    continue;
                }
                int color = !this.data
                    ? this.accent : index == 0
                        ? this.accent
                        : index == 1 ? UiStyles.palette().quantity() : UiStyles.palette().label();
                graphics.text(font, this.values[index], x + widths[index] - font.width(this.values[index]), baseline,
                    color, false);
                x += widths[index] + 6;
            }
            super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        }
    }

    private record PriceFocus(String productId, boolean buy, double price) {}

    private static String integer(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private static String decimal(double value, boolean grouping) {
        return new DecimalFormat(grouping ? "#,##0.0" : "0.0", DecimalFormatSymbols.getInstance(Locale.US))
            .format(value);
    }
}
