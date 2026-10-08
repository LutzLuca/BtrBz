package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.Assets;
import com.github.lutzluca.btrbz.core.iteminfo.charts.ChartOptions;
import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryAnalysis;
import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryViewport;
import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryTime;
import com.github.lutzluca.btrbz.core.iteminfo.charts.RangeSummaryComponent;
import com.github.lutzluca.btrbz.core.iteminfo.charts.PriceChartComponent;
import com.github.lutzluca.btrbz.core.iteminfo.charts.QuantityChartComponent;
import com.github.lutzluca.btrbz.core.iteminfo.charts.QuantityMetric;
import com.github.lutzluca.btrbz.core.ui.UiControls;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.IconButton;
import com.github.lutzluca.coflnet.HistoryResponse;
import com.github.lutzluca.coflnet.HistoryQuery;
import com.github.lutzluca.coflnet.MayorTerm;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Stable plots with secondary detail in anchored popovers. */
public final class HistoryPanel extends FlowLayout {
    private final ItemInfoConfig config;
    private final Runnable save;
    private final HistoryViewport viewport;
    private final PopoverOpener showPopover;
    private final Consumer<Boolean> mayorVisible;
    private final ButtonComponent buy;
    private final ButtonComponent sell;
    private final ButtonComponent summary;
    private final PriceChartComponent priceChart;
    private final QuantityChartComponent quantityChart;
    private final List<ButtonComponent> metricButtons = new ArrayList<>();
    private List<MayorTerm> mayors = List.of();
    private @Nullable HistoryResponse lastHistory;
    private @Nullable String productId;
    private @Nullable HistoryQuery lastQuery;
    private @Nullable HistoryQuery selectedQuery;
    private @Nullable ItemInfoRange selectedRange;
    private boolean initialized;
    private boolean awaitingSelection;
    private boolean quantitySelected;
    private int availableWidth = 500;
    private int availableHeight = 300;

    public HistoryPanel(
        ItemInfoConfig config,
        Runnable save,
        HistoryViewport viewport,
        PopoverOpener showPopover,
        Consumer<Boolean> mayorVisible
    ) {
        super(Sizing.fill(100), Sizing.content(), Algorithm.VERTICAL);
        this.config = config;
        this.save = save;
        this.viewport = viewport;
        this.showPopover = showPopover;
        this.mayorVisible = mayorVisible;
        this.gap(3);
        this.buy = UiControls.button("Buy", () -> {
            this.config.showBuy = !this.config.showBuy;
            this.preferenceChanged(false);
        });
        this.sell = UiControls.button("Sell", () -> {
            this.config.showSell = !this.config.showSell;
            this.preferenceChanged(false);
        });
        this.buy.sizing(Sizing.content(12), Sizing.fixed(18));
        this.sell.sizing(Sizing.content(12), Sizing.fixed(18));
        this.summary = UiControls.button("Range summary", () -> {});
        this.summary.renderer(UiControls.secondaryRenderer());
        this.summary.verticalSizing(Sizing.fixed(18));
        this.summary.onPress(_ -> this.showPopover.open(this.summary, 340, this::buildSummary));
        this.priceChart = new PriceChartComponent(this.viewport, this::options);
        this.quantityChart = new QuantityChartComponent(this.viewport, this::options);
        this.priceChart.mayors(() -> this.mayors, () -> this.config.showMayors);
        this.quantityChart.mayors(() -> this.mayors, () -> this.config.showMayors);
        this.rebuildLayout();
        this.refreshPreferences();
    }

    public void layoutFor(int width, int height) {
        int nextHeight = Math.max(130, height);
        if (this.availableWidth == width && this.availableHeight == nextHeight) {
            return;
        }
        this.availableWidth = width;
        this.availableHeight = nextHeight;
        this.rebuildLayout();
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
                history == null ? null : history.source().cadence(),
                selectionChanged || selectedDataArrived);
        }
        var mayorData = data.mayors().value();
        this.mayors = mayorData == null ? List.of() : mayorData.terms();
    }

    private void rebuildLayout() {
        this.viewport.clearHover();
        this.clearChildren();
        this.quantityChart.mayorNamesVisible(false);
        var quantityControls = this.quantityControls();
        quantityControls.inflate(Size.of(this.availableWidth, this.availableHeight));
        var controls = this.plotHeading(false);
        controls.inflate(Size.of(this.availableWidth, this.availableHeight));
        int quantityControlHeight = quantityControls.height();
        int dualOverhead = controls.height() + quantityControlHeight + 9
            + this.priceChart.verticalOverhead(false) + this.quantityChart.verticalOverhead(true);
        boolean singlePlot = this.config.showQuantity && this.availableHeight - dualOverhead < 155;
        if (singlePlot) {
            controls.clearChildren();
            controls = this.plotHeading(true);
            controls.inflate(Size.of(this.availableWidth, this.availableHeight));
        }
        int headingHeight = controls.height();
        this.child(controls);
        boolean showPrice = !singlePlot || !this.quantitySelected;
        boolean showQuantity = this.config.showQuantity && (!singlePlot || this.quantitySelected);
        this.quantityChart.mayorNamesVisible(!showPrice);
        int chrome = headingHeight + (showQuantity ? quantityControlHeight + 6 : 0) + 3;
        int priceOverhead = showPrice ? this.priceChart.verticalOverhead(!showQuantity) : 0;
        int quantityOverhead = showQuantity ? this.quantityChart.verticalOverhead(true) : 0;
        int drawable = Math.max(showPrice && showQuantity ? 155 : showPrice ? 100 : 55,
            this.availableHeight - chrome - priceOverhead - quantityOverhead);
        int quantityDrawable = showPrice && showQuantity ? Math.max(55, (int) (drawable * .30)) : drawable;
        if (showPrice) {
            int priceHeight = (showQuantity ? drawable - quantityDrawable : drawable) + priceOverhead;
            this.priceChart.verticalSizing(Sizing.fixed(priceHeight));
            this.priceChart.timeAxisVisible(!showQuantity);
            this.child(this.priceChart);
        }
        if (showQuantity) {
            this.child(quantityControls);
            this.quantityChart.verticalSizing(Sizing.fixed(quantityDrawable + quantityOverhead));
            this.quantityChart.timeAxisVisible(true);
            this.child(this.quantityChart);
        }
    }

    private FlowLayout plotHeading(boolean singlePlot) {
        var heading = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(3);
        var controls = UiControls.row();
        controls.gap(3);
        if (singlePlot) {
            var price = UiControls.button("Price", () -> this.selectPlot(false));
            var quantity = UiControls.button(this.availableWidth < 200 ? "Qty" : "Quantity",
                () -> this.selectPlot(true));
            price.renderer(UiControls.segmentRenderer(!this.quantitySelected));
            quantity.renderer(UiControls.segmentRenderer(this.quantitySelected));
            price.verticalSizing(Sizing.fixed(18));
            quantity.verticalSizing(Sizing.fixed(18));
            controls.child(price).child(quantity);
        } else {
            controls.child(UiControls.text("Price history", UiStyles.palette().label()));
        }
        this.summary.setMessage(Component.literal(this.availableWidth < 200
            ? "Stats"
            : this.availableWidth < 330 ? "Summary" : "Range summary"));
        // Inflating an unfinished flow caches its layout without mounting later additions.
        // Measure only the leaves until the heading has all of its children.
        for (var control : controls.children()) {
            control.inflate(Size.of(this.availableWidth, this.availableHeight));
        }
        var series = UiControls.row(this.buy, this.sell);
        series.horizontalSizing(Sizing.content());
        series.gap(3);
        series.inflate(Size.of(this.availableWidth, this.availableHeight));
        this.summary.inflate(Size.of(this.availableWidth, this.availableHeight));
        int used = controls.children().stream().mapToInt(UIComponent::width).sum()
            + Math.max(0, controls.children().size() - 1) * 3;
        boolean wrap = used + series.width() + this.summary.width() + 12 > this.availableWidth;
        if (!wrap) {
            controls.child(series);
        }
        controls.child(BazaarUi.spacer()).child(this.summary);
        heading.child(controls);
        if (wrap) {
            heading.child(series);
        }
        return heading;
    }

    private void selectPlot(boolean quantity) {
        this.quantitySelected = quantity;
        this.queue(this::rebuildLayout);
    }

    private FlowLayout quantityControls() {
        this.metricButtons.clear();
        var controls = UiControls.row();
        controls.gap(3);
        if (this.availableWidth >= 230) {
            controls.child(UiControls.text("Quantity", UiStyles.palette().label()));
        }
        int metricWidth = Minecraft.getInstance().font.width(QuantityMetric.OpenOrders.label())
            + Minecraft.getInstance().font.width(QuantityMetric.MovingWeek.label()) + 128;
        if (this.availableWidth >= metricWidth) {
            var segments = UiControls.row();
            segments.horizontalSizing(Sizing.content());
            segments.gap(1);
            for (var metric : QuantityMetric.values()) {
                var button = UiControls.button(metric.label(), () -> this.selectMetric(metric));
                button.verticalSizing(Sizing.fixed(18));
                button.renderer(UiControls.segmentRenderer(metric == this.config.quantityMetric));
                this.metricButtons.add(button);
                segments.child(button);
            }
            controls.child(segments);
        } else {
            var selector = UiControls.button(this.metricSelectorLabel() + " v", () -> {});
            selector.verticalSizing(Sizing.fixed(18));
            selector.renderer(UiControls.segmentRenderer(true));
            selector.onPress(_ -> this.showPopover.open(selector, 180, content -> {
                var choices = new ArrayList<ButtonComponent>();
                for (var metric : QuantityMetric.values()) {
                    var choice = UiControls.button(metric.label(), () -> {
                        this.selectMetric(metric);
                        for (int index = 0; index < choices.size(); index++) {
                            choices.get(index)
                                .renderer(UiControls.segmentRenderer(QuantityMetric.values()[index] == metric));
                        }
                    });
                    choice.verticalSizing(Sizing.fixed(18));
                    choice.renderer(UiControls.segmentRenderer(metric == this.config.quantityMetric));
                    choices.add(choice);
                    content.child(choice);
                }
            }));
            this.metricButtons.add(selector);
            controls.child(selector);
        }
        var information = new IconButton(Assets.INFO_ICON, Component.literal("About quantities"), () -> {}, 64,
            UiControls.quietRenderer());
        information.onPress(_ -> this.showPopover.open(information, 280, this::buildQuantityHelp));
        controls.child(information);
        return controls;
    }

    private String metricSelectorLabel() {
        if (this.availableWidth >= 200) {
            return this.config.quantityMetric.label();
        }
        return this.config.quantityMetric == QuantityMetric.MovingWeek ? "7d moving volume" : "Open-order qty";
    }

    private void selectMetric(QuantityMetric metric) {
        this.config.quantityMetric = metric;
        this.save.run();
        this.refreshPreferences();
    }

    public void refreshPreferences() {
        this.buy.setMessage(Component.literal("Buy").withColor(this.config.showBuy
            ? UiStyles.palette().primary() : UiStyles.palette().muted()));
        this.sell.setMessage(Component.literal("Sell").withColor(this.config.showSell
            ? UiStyles.palette().primary() : UiStyles.palette().muted()));
        this.buy.renderer(UiControls.seriesRenderer(this.config.showBuy, UiStyles.palette().buy()));
        this.sell.renderer(UiControls.seriesRenderer(this.config.showSell, UiStyles.palette().sell()));
        if (this.metricButtons.size() == 1) {
            this.metricButtons.getFirst().setMessage(Component.literal(this.metricSelectorLabel() + " v"));
        } else {
            for (int index = 0; index < this.metricButtons.size(); index++) {
                this.metricButtons.get(index).renderer(UiControls.segmentRenderer(
                    QuantityMetric.values()[index] == this.config.quantityMetric));
            }
        }
    }

    public void buildSettings(FlowLayout content) {
        this.toggle(content, "Min/max shading", () -> this.config.showBands, value -> this.config.showBands = value,
            false);
        this.toggle(content, "Mayors", () -> this.config.showMayors, value -> {
            this.config.showMayors = value;
            this.mayorVisible.accept(value);
        }, true);
        this.toggle(content, "Quantity plot", () -> this.config.showQuantity, value -> this.config.showQuantity = value,
            true);
        boolean hasBands = this.lastHistory != null
            && this.lastHistory.points().stream().anyMatch(point -> point.minBuy() != null && point.maxBuy() != null
                || point.minSell() != null && point.maxSell() != null);
        if (!hasBands) {
            content.child(UiControls.text("Min/max shading is unavailable for this response.",
                UiStyles.palette().muted()).horizontalSizing(Sizing.fill(100)));
        }
    }

    private void toggle(
        FlowLayout content,
        String label,
        BooleanSupplier read,
        Consumer<Boolean> write,
        boolean layout
    ) {
        var checkbox = UIComponents.smallCheckbox(Component.literal(label).withColor(UiStyles.palette().label()));
        checkbox.checked(read.getAsBoolean());
        checkbox.onChanged().subscribe(value -> {
            write.accept(value);
            this.preferenceChanged(layout);
        });
        content.child(checkbox);
    }

    private void preferenceChanged(boolean layout) {
        this.save.run();
        this.refreshPreferences();
        if (layout) {
            this.queue(this::rebuildLayout);
        }
    }

    private void buildQuantityHelp(FlowLayout content) {
        this.explainMetric(content, this.config.quantityMetric);
        this.explainMetric(content, this.config.quantityMetric == QuantityMetric.MovingWeek
            ? QuantityMetric.OpenOrders : QuantityMetric.MovingWeek);
        content.child(UiControls.text("Neither measures exact trades per interval.", UiStyles.palette().muted())
            .horizontalSizing(Sizing.fill(100)));
    }

    private void explainMetric(FlowLayout content, QuantityMetric metric) {
        content.child(UiControls.text(metric.label(), UiStyles.palette().primary()));
        String explanation = metric == QuantityMetric.OpenOrders
            ? "Items waiting in outstanding orders. Changes include placements, fills and cancellations."
            : "Activity across the preceding seven days at each timestamp, including Hypixel's live-state component. "
                + "Older activity leaves the window continuously.";
        content.child(UiControls.text(explanation, UiStyles.palette().muted()).horizontalSizing(Sizing.fill(100)));
    }

    private void buildSummary(FlowLayout content) {
        var buyStats = HistoryAnalysis.stats(this.viewport.history(), this.viewport.start(), this.viewport.end(), true);
        var sellStats = HistoryAnalysis.stats(this.viewport.history(), this.viewport.start(), this.viewport.end(),
            false);
        content.child(UiControls.text("Range summary", UiStyles.palette().primary()));
        content.child(new RangeSummaryComponent(buyStats.orElse(null), sellStats.orElse(null)));
        var observations = this.viewport.history().stream()
            .filter(point -> !point.timestamp().isBefore(this.viewport.start())
                && !point.timestamp().isAfter(this.viewport.end()))
            .toList();
        String coverage = observations.isEmpty()
            ? "No observations in this view"
            : observations.size() + " samples\nFirst: " + HistoryTime.detailed(observations.getFirst().timestamp())
                + "\nLast: " + HistoryTime.detailed(observations.getLast().timestamp());
        content.child(UiControls.text(coverage, UiStyles.palette().muted()).horizontalSizing(Sizing.fill(100)));
        content.child(UiControls.text("Returned samples have equal weight.", UiStyles.palette().muted())
            .horizontalSizing(Sizing.fill(100)));
    }

    private ChartOptions options() {
        return new ChartOptions(this.config.showBuy, this.config.showSell, this.config.showBands,
            this.config.quantityMetric);
    }

    @FunctionalInterface
    public interface PopoverOpener {
        void open(UIComponent anchor, int preferredWidth, Consumer<FlowLayout> build);
    }
}
