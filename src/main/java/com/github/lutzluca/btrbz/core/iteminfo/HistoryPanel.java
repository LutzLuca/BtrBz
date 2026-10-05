package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.Assets;
import com.github.lutzluca.btrbz.core.iteminfo.charts.ChartOptions;
import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryAnalysis;
import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryViewport;
import com.github.lutzluca.btrbz.core.iteminfo.charts.PriceChartComponent;
import com.github.lutzluca.btrbz.core.iteminfo.charts.QuantityChartComponent;
import com.github.lutzluca.btrbz.core.iteminfo.charts.QuantityMetric;
import com.github.lutzluca.btrbz.core.ui.UiControls;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.coflnet.HistoryResponse;
import com.github.lutzluca.coflnet.HistoryQuery;
import com.github.lutzluca.coflnet.MayorTerm;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/** The two plots share time and inspection, while retaining separate quantity and price scales. */
public final class HistoryPanel extends FlowLayout {
    private final ItemInfoConfig config;
    private final Runnable save;
    private final HistoryViewport viewport;
    private final FlowLayout quantity = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    private final FlowLayout statistics = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
    private final ButtonComponent metric;
    private final ButtonComponent buy;
    private final ButtonComponent sell;
    private final QuantityChartComponent quantityChart;
    private final FlowLayout quantityControls;
    private final FlowLayout priceControls;
    private final FlowLayout quantityHelp = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(0));
    private boolean quantityHelpVisible;
    private List<MayorTerm> mayors = List.of();
    private @Nullable HistoryResponse lastHistory;
    private @Nullable String productId;
    private @Nullable HistoryQuery lastQuery;
    private @Nullable HistoryQuery selectedQuery;
    private @Nullable ItemInfoRange selectedRange;
    private boolean initialized;
    private boolean awaitingSelection;
    private boolean quantityMounted;
    private long statisticsRevision = -1;
    private int availableWidth = 500;

    public HistoryPanel(ItemInfoConfig config, Runnable save, HistoryViewport viewport) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.config = config;
        this.save = save;
        this.viewport = viewport;
        this.gap(5);
        this.buy = UiControls.button("Buy Price", () -> {
            this.config.showBuy = !this.config.showBuy;
            this.preferenceChanged();
        });
        this.sell = UiControls.button("Sell Price", () -> {
            this.config.showSell = !this.config.showSell;
            this.preferenceChanged();
        });
        this.priceControls = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(4);
        this.child(this.priceControls);
        this.child(new PriceChartComponent(this.viewport, this::options,
            () -> this.config.showMayors ? this.mayors : List.of()));
        this.metric = UiControls.button(this.config.quantityMetric.label(), () -> {
            this.config.quantityMetric = this.config.quantityMetric == QuantityMetric.MovingWeek
                ? QuantityMetric.OpenOrders : QuantityMetric.MovingWeek;
            this.preferenceChanged();
        });
        this.metric.tooltip(net.minecraft.network.chat.Component.literal("Choose the other quantity metric"));
        this.quantityChart = new QuantityChartComponent(this.viewport, this::options,
            () -> this.config.showMayors ? this.mayors : List.of());
        this.quantityControls = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(4);
        this.quantityHelp.gap(4);
        this.layoutControls();
        this.quantity.gap(4);
        this.child(this.quantity);
        this.child(UiControls.text("Ctrl+scroll zooms. Drag pans. Left-click pins. Right-click clears.",
            UiStyles.palette().muted()).horizontalSizing(Sizing.fill(100)));
        this.statistics.gap(4);
        this.child(this.statistics);
        this.refreshPreferences();
    }

    public void layoutFor(int width) {
        if (this.availableWidth == width) {
            return;
        }
        this.availableWidth = width;
        this.layoutControls();
        this.refreshQuantityHelp();
        this.statisticsRevision = -1;
        this.tick();
    }

    public void update(ItemInfoSession.Data data) {
        var id = data.product() == null ? null : data.product().bazaarProductId().orElse(null);
        boolean productChanged = this.initialized && !Objects.equals(id, this.productId);
        boolean selectionChanged = productChanged || (this.initialized && (data.range() != this.selectedRange
            || (data.range() == ItemInfoRange.Custom && !data.query().equals(this.selectedQuery))));
        if (productChanged) {
            this.viewport.clearPin();
            this.viewport.clearHover();
        }
        if (selectionChanged) {
            this.awaitingSelection = true;
            this.viewport.clearHover();
        }
        this.productId = id;
        this.selectedRange = data.range();
        this.selectedQuery = data.query();
        this.initialized = true;
        var history = data.history().value();
        var query = history == null || data.history().query() == null ? data.query() : data.history().query();
        if (this.awaitingSelection && !query.equals(data.query())) {
            history = null;
            query = data.query();
        }
        boolean selectedDataArrived = this.awaitingSelection && history != null;
        if (selectedDataArrived) {
            this.awaitingSelection = false;
        }
        if (history != this.lastHistory || !query.equals(this.lastQuery) || selectionChanged) {
            this.lastHistory = history;
            this.lastQuery = query;
            this.viewport.update(history == null ? List.of() : history.points(), query.start(), query.end(),
                selectionChanged || selectedDataArrived);
        }
        var mayorData = data.mayors().value();
        this.mayors = mayorData == null ? List.of() : mayorData.terms();
        this.tick();
    }

    public void tick() {
        if (this.statisticsRevision == this.viewport.revision()) {
            return;
        }
        this.statisticsRevision = this.viewport.revision();
        this.statistics.clearChildren();
        var buyStats = this.stats(true);
        var sellStats = this.stats(false);
        if (this.availableWidth >= 440) {
            buyStats.horizontalSizing(Sizing.expand(50));
            sellStats.horizontalSizing(Sizing.expand(50));
            this.statistics.child(UiControls.row(buyStats, sellStats));
        } else {
            this.statistics.child(buyStats);
            this.statistics.child(sellStats);
        }
    }

    private FlowLayout stats(boolean buy) {
        var column = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        column.gap(3);
        int accent = buy ? UiStyles.palette().buy() : UiStyles.palette().sell();
        column.child(UiControls.text("Visible range: " + (buy ? "Buy Price" : "Sell Price"), accent));
        Optional<HistoryAnalysis.Stats> result = HistoryAnalysis.stats(this.viewport.history(),
            this.viewport.start(), this.viewport.end(), buy);
        if (result.isEmpty()) {
            column.child(UiControls.text("No price samples", UiStyles.palette().muted()));
            return column;
        }
        var values = result.orElseThrow();
        column.child(UiControls.text("Start to end: " + (values.percent() == null
            ? "Unavailable"
            : String.format(Locale.ROOT, "%+.1f%%", values.percent())), UiStyles.palette().label())
            .horizontalSizing(Sizing.fill(100)));
        column.child(UiControls.text("Low: " + HistoryAnalysis.exact(values.low()), UiStyles.palette().label())
            .horizontalSizing(Sizing.fill(100)));
        column.child(UiControls.text("High: " + HistoryAnalysis.exact(values.high()), UiStyles.palette().label())
            .horizontalSizing(Sizing.fill(100)));
        column.child(UiControls.text("Sample average: " + HistoryAnalysis.exact(values.average()),
            UiStyles.palette().label()).horizontalSizing(Sizing.fill(100)));
        return column;
    }

    public void refreshPreferences() {
        this.buy.renderer(UiControls.buttonRenderer(false, this.config.showBuy, UiStyles.palette().buy()));
        this.sell.renderer(UiControls.buttonRenderer(false, this.config.showSell, UiStyles.palette().sell()));
        this.metric.setMessage(net.minecraft.network.chat.Component.literal(this.config.quantityMetric.label()));
        if (this.quantityMounted != this.config.showQuantity) {
            if (!this.config.showQuantity) {
                this.viewport.clearHover();
            }
            this.quantity.clearChildren();
            if (this.config.showQuantity) {
                this.quantity.child(this.quantityControls);
                this.quantity.child(this.quantityHelp);
                this.quantity.child(this.quantityChart);
            }
            this.quantityMounted = this.config.showQuantity;
        }
    }

    private void layoutControls() {
        this.priceControls.clearChildren();
        var prices = UiControls.row(this.buy, this.sell);
        prices.horizontalSizing(Sizing.content());
        var priceTitle = UiControls.text("Price history (coins per item)", UiStyles.palette().label());
        if (this.availableWidth >= 440) {
            this.priceControls.child(UiControls.row(priceTitle, prices));
        } else {
            this.priceControls.child(priceTitle);
            this.priceControls.child(prices);
        }
        this.quantityControls.clearChildren();
        var about = UiControls.iconButton("About quantities", Assets.INFO_ICON, 64, () -> {
            this.quantityHelpVisible = !this.quantityHelpVisible;
            this.refreshQuantityHelp();
        });
        var metrics = this.availableWidth >= 440
            ? UiControls.row(this.metric, about)
            : UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(4).child(this.metric).child(about);
        metrics.horizontalSizing(Sizing.content());
        var quantityTitle = UiControls.text("Quantity (items)", UiStyles.palette().label());
        if (this.availableWidth >= 540) {
            this.quantityControls.child(UiControls.row(quantityTitle, metrics));
        } else {
            this.quantityControls.child(quantityTitle);
            this.quantityControls.child(metrics);
        }
    }

    private void refreshQuantityHelp() {
        this.quantityHelp.clearChildren();
        this.quantityHelp.verticalSizing(this.quantityHelpVisible ? Sizing.content() : Sizing.fixed(0));
        if (!this.quantityHelpVisible) {
            return;
        }
        this.quantityHelp.child(UiControls.text(
            "Open-order quantity: Items waiting in outstanding orders. "
                + "Changes include placements, fills and cancellations.",
            UiStyles.palette().label())
            .horizontalSizing(Sizing.fill(100)));
        this.quantityHelp.child(UiControls.text(
            "7-day moving volume: Activity across the preceding seven days at each timestamp, "
                + "including Hypixel's live-state component. Older activity leaves the window continuously.",
            UiStyles.palette().label()).horizontalSizing(Sizing.fill(100)));
        this.quantityHelp.child(UiControls.text("Neither measures exact trades per interval.",
            UiStyles.palette().muted()).horizontalSizing(Sizing.fill(100)));
    }

    private void preferenceChanged() {
        this.save.run();
        this.refreshPreferences();
    }

    private ChartOptions options() {
        return new ChartOptions(this.config.showBuy, this.config.showSell,
            this.config.showBands, this.config.quantityMetric);
    }
}
