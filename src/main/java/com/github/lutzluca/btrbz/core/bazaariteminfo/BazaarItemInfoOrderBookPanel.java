package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedRows;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetDisplayOptions.NumberStyle;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetScrollListComponent;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.data.LiveProductSnapshot;
import com.github.lutzluca.btrbz.data.MarketSide;
import com.github.lutzluca.btrbz.data.PriceLevel;
import com.github.lutzluca.btrbz.data.Totals;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Retained, screen-owned Order Book panel with independent side scrollers. */
public final class BazaarItemInfoOrderBookPanel {
    private static final long TOAST_NANOS = 2_000_000_000L;
    private static final int SIDE_PANEL_COLOR = 0x66101319;
    private static final int SIDE_PADDING = 5;
    private static final int COLUMN_GAP_ALLOWANCE = 8;
    private static final int ORDER_ROW_VERTICAL_PADDING = 3;
    private static final String INTERACTION_HINT = "Scroll each side. Click a price to copy.";

    private final FlowLayout root = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    private final FlowLayout holder = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    private final FlowLayout sideBySide = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
    private final FlowLayout stacked = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    private final Side buy = new Side(
        "Buy Price Levels", "Lowest price first", "No Buy Price levels", BazaarStyles.BUY_ACCENT);
    private final Side sell = new Side(
        "Sell Price Levels", "Highest price first", "No Sell Price levels", BazaarStyles.SELL_ACCENT);
    private final LabelComponent toast = BazaarUi.label(INTERACTION_HINT, BazaarStyles.MUTED_TEXT);

    private boolean stackedLayout;
    private long toastExpiresAt;

    public BazaarItemInfoOrderBookPanel() {
        this.root.gap(WidgetLayoutTokens.SECTION_GAP);
        this.sideBySide.gap(WidgetLayoutTokens.SECTION_GAP + 2);
        this.stacked.gap(WidgetLayoutTokens.SECTION_GAP + 2);
        this.root.child(this.holder);
        this.root.child(this.toast);
    }

    public FlowLayout root() {
        return this.root;
    }

    public void update(
        LiveProductSnapshot snapshot,
        BazaarItemInfoViewData.Preferences preferences,
        int availableWidth,
        int availableHeight
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(preferences, "preferences");
        boolean nextStacked = useStacked(
            availableWidth,
            minimumSideWidth(
                preferences.volumeNumberStyle(),
                preferences.showPerLevelOrderCount(),
                preferences.showCumulativeVolume()));
        int rowHeight = orderRowHeight(Minecraft.getInstance().font.lineHeight);
        int marketDepth = Math.max(snapshot.buyOrders().levels().size(), snapshot.sellOffers().levels().size());
        int visibleRows = effectiveVisibleRows(
            nextStacked,
            preferences.visibleOrderBookRows(),
            availableHeight,
            rowHeight,
            marketDepth);
        int viewportHeight = WidgetLayoutTokens.listViewportHeight(
            rowHeight, visibleRows);
        this.buy.update(snapshot.sellOffers(), preferences.volumeNumberStyle(),
            preferences.showPerLevelOrderCount(), preferences.showCumulativeVolume(), rowHeight, viewportHeight);
        this.sell.update(snapshot.buyOrders(), preferences.volumeNumberStyle(),
            preferences.showPerLevelOrderCount(), preferences.showCumulativeVolume(), rowHeight, viewportHeight);

        if (this.holder.children().isEmpty() || nextStacked != this.stackedLayout) {
            this.stackedLayout = nextStacked;
            this.holder.clearChildren();
            this.sideBySide.clearChildren();
            this.stacked.clearChildren();
            if (nextStacked) {
                this.buy.root.horizontalSizing(Sizing.fill(100));
                this.sell.root.horizontalSizing(Sizing.fill(100));
                this.stacked.child(this.buy.root);
                this.stacked.child(this.sell.root);
                this.holder.child(this.stacked);
            } else {
                this.buy.root.horizontalSizing(Sizing.expand(50));
                this.sell.root.horizontalSizing(Sizing.expand(50));
                this.sideBySide.child(this.buy.root);
                this.sideBySide.child(this.sell.root);
                this.holder.child(this.sideBySide);
            }
        }
    }

    public void tick() {
        if (this.toastExpiresAt != 0 && System.nanoTime() >= this.toastExpiresAt) {
            this.toastExpiresAt = 0;
            this.toast.text(Component.literal(INTERACTION_HINT));
            this.toast.color(BazaarStyles.color(BazaarStyles.MUTED_TEXT));
        }
    }

    public boolean stackedLayout() {
        return this.stackedLayout;
    }

    public static boolean useStacked(int availableWidth, int minimumSideWidth) {
        return Math.max(0, availableWidth) < Math.max(0, minimumSideWidth) * 2 + WidgetLayoutTokens.SECTION_GAP + 2;
    }

    public static int minimumSideWidth(NumberStyle style, boolean showOrders, boolean showCumulative) {
        var font = Minecraft.getInstance().font;
        String items = style == NumberStyle.Compact ? "999.9M" : "999,999,999";
        int columns = Math.max(
            BazaarItemInfoOrderText.width(font, "Price"),
            BazaarItemInfoOrderText.width(font, "9,999,999.9"))
            + Math.max(
                BazaarItemInfoOrderText.width(font, "Items"),
                BazaarItemInfoOrderText.width(font, items));
        int count = 2;
        if (showCumulative) {
            String cumulative = style == NumberStyle.Compact ? "999.9M" : "9,999,999,999";
            columns += Math.max(
                BazaarItemInfoOrderText.width(font, "Cumulative"),
                BazaarItemInfoOrderText.width(font, cumulative));
            count++;
        }
        if (showOrders) {
            columns += Math.max(
                BazaarItemInfoOrderText.width(font, "Orders"),
                BazaarItemInfoOrderText.width(font, "99,999"));
            count++;
        }
        return columns
            + COLUMN_GAP_ALLOWANCE * (count - 1)
            + SIDE_PADDING * 2
            + WidgetLayoutTokens.rowTrailingInset(true);
    }

    public static int minimumSideWidth(NumberStyle style, boolean showOrders) {
        return minimumSideWidth(style, showOrders, false);
    }

    static int effectiveVisibleRows(
        boolean stacked,
        int configuredRows,
        int availableHeight,
        int rowHeight,
        int marketDepth
    ) {
        int configured = Math.max(1, configuredRows);
        if (stacked) {
            return configured;
        }
        int safeRowHeight = Math.max(1, rowHeight);
        int nonListHeight = safeRowHeight * 4 + SIDE_PADDING * 2 + WidgetLayoutTokens.SECTION_GAP * 3;
        int rowsThatFit = Math.max(1, (Math.max(1, availableHeight) - nonListHeight)
            / (safeRowHeight + WidgetLayoutTokens.LIST_GAP));
        int usefulDepth = Math.max(configured, Math.max(0, marketDepth));
        return Math.max(configured, Math.min(usefulDepth, rowsThatFit));
    }

    static int orderRowHeight(int fontLineHeight) {
        return BazaarItemInfoOrderText.lineHeight(fontLineHeight) + ORDER_ROW_VERTICAL_PADDING * 2;
    }

    public static String clipboardPrice(double price) {
        return String.format(Locale.ROOT, "%.1f", price);
    }

    private void copy(double price, int accent) {
        String value = clipboardPrice(price);
        Minecraft.getInstance().keyboardHandler.setClipboard(value);
        this.toast.text(Component.literal("Copied " + value));
        this.toast.color(BazaarStyles.color(accent));
        this.toastExpiresAt = System.nanoTime() + TOAST_NANOS;
    }

    private final class Side {
        private final int accent;
        private final String titleText;
        private final String emptyText;
        private final FlowLayout root = UIContainers.verticalFlow(Sizing.expand(50), Sizing.content());
        private final BazaarItemInfoOrderSideHeaderComponent sideHeader;
        private final BazaarItemInfoOrderHeaderComponent headings = new BazaarItemInfoOrderHeaderComponent(13);
        private final WidgetScrollListComponent list = new WidgetScrollListComponent(
            1, WidgetLayoutTokens.LIST_GAP, true, BazaarStyles.SCROLLBAR);
        private final RetainedRows<String, BazaarItemInfoOrderRowComponent> retainedRows = new RetainedRows<>();

        private Side(String title, String relationshipText, String emptyText, int accent) {
            this.accent = accent;
            this.titleText = title;
            this.emptyText = emptyText;
            int rowHeight = orderRowHeight(Minecraft.getInstance().font.lineHeight);
            this.sideHeader = new BazaarItemInfoOrderSideHeaderComponent(
                title, relationshipText, accent, rowHeight);
            this.root.gap(2);
            this.root.padding(Insets.of(SIDE_PADDING));
            this.root.surface(WidgetSurfaces.roundedPanel(SIDE_PANEL_COLOR, 4));
            this.root.child(this.sideHeader);
            this.root.child(this.headings);
            this.root.child(this.list);
        }

        private void update(
            MarketSide side,
            NumberStyle numberStyle,
            boolean showOrders,
            boolean showCumulative,
            int rowHeight,
            int viewportHeight
        ) {
            this.sideHeader.update(totalsText(side.totals(), numberStyle), rowHeight);
            this.headings.update(showCumulative, showOrders, rowHeight);
            var models = new ArrayList<BazaarItemInfoOrderRowComponent.Row>();
            List<PriceLevel> levels = side.levels();
            var accumulated = accumulatedItems(levels);
            long largestLevel = levels.stream().mapToLong(level -> Math.max(0, level.items())).max().orElse(0);
            for (int index = 0; index < levels.size(); index++) {
                var level = levels.get(index);
                String items = number(level.items(), numberStyle);
                models.add(new BazaarItemInfoOrderRowComponent.Row(
                    this.titleText + "-" + Double.doubleToLongBits(level.price()),
                    level.price(), BazaarWidgetViewData.formatPrice(level.price()), items,
                    number(accumulated.get(index), numberStyle), Integer.toString(level.orders()),
                    this.accent, index + 1, itemFraction(level.items(), largestLevel),
                    showCumulative, showOrders, false,
                    value -> BazaarItemInfoOrderBookPanel.this.copy(value, this.accent)));
            }
            if (models.isEmpty()) {
                models.add(new BazaarItemInfoOrderRowComponent.Row(
                    this.titleText + "-empty", 0, "",
                    this.emptyText, "", "", BazaarStyles.MUTED_TEXT, 0,
                    0, showCumulative, showOrders, true, _ -> {}));
            }
            var rows = this.retainedRows.reconcile(
                models,
                BazaarItemInfoOrderRowComponent.Row::id,
                (model, _) -> new BazaarItemInfoOrderRowComponent(model, rowHeight),
                (row, model, _) -> row.update(model, rowHeight));
            this.list.updateRows(rows, viewportHeight, true);
        }
    }

    static String totalsText(Totals totals, NumberStyle style) {
        if (totals instanceof Totals.Available available) {
            return number(available.items(), style) + " items, "
                + BazaarWidgetViewData.formatInt(available.orders()) + " orders";
        }
        return "Totals unavailable";
    }

    static String number(long value, NumberStyle style) {
        return style == NumberStyle.Compact
            ? BazaarWidgetViewData.formatCompact(value)
            : BazaarWidgetViewData.formatInt(value);
    }

    static List<Long> accumulatedItems(List<PriceLevel> levels) {
        Objects.requireNonNull(levels, "levels");
        var totals = new ArrayList<Long>(levels.size());
        long running = 0;
        for (var level : levels) {
            long items = Math.max(0, Objects.requireNonNull(level, "level").items());
            running = running > Long.MAX_VALUE - items ? Long.MAX_VALUE : running + items;
            totals.add(running);
        }
        return List.copyOf(totals);
    }

    static double itemFraction(long items, long largestLevel) {
        if (items <= 0 || largestLevel <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (double) items / largestLevel));
    }
}
