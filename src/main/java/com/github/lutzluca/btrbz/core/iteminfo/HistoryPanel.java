package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.Assets;
import com.github.lutzluca.btrbz.core.iteminfo.charts.ChartOptions;
import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryAnalysis;
import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryViewport;
import com.github.lutzluca.btrbz.core.iteminfo.charts.MayorLaneComponent;
import com.github.lutzluca.btrbz.core.iteminfo.charts.PriceChartComponent;
import com.github.lutzluca.btrbz.core.iteminfo.charts.QuantityChartComponent;
import com.github.lutzluca.btrbz.core.iteminfo.charts.QuantityMetric;
import com.github.lutzluca.btrbz.core.ui.UiControls;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.IconButton;
import com.github.lutzluca.coflnet.HistoryResponse;
import com.github.lutzluca.coflnet.HistoryQuery;
import com.github.lutzluca.coflnet.MayorTerm;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
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
    private final BiConsumer<UIComponent, Consumer<FlowLayout>> showPopover;
    private final Consumer<Boolean> mayorVisible;
    private final ButtonComponent buy;
    private final ButtonComponent sell;
    private final ButtonComponent settings;
    private final ButtonComponent summary;
    private final PriceChartComponent priceChart;
    private final QuantityChartComponent quantityChart;
    private final MayorLaneComponent mayorLane;
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
        BiConsumer<UIComponent, Consumer<FlowLayout>> showPopover,
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
        this.settings = UiControls.button("Chart options", () -> {});
        this.settings.renderer(UiControls.quietRenderer());
        this.settings.verticalSizing(Sizing.fixed(18));
        this.settings.onPress(_ -> this.showPopover.accept(this.settings, this::buildSettings));
        this.summary = UiControls.button("Range summary", () -> {});
        this.summary.renderer(UiControls.quietRenderer());
        this.summary.verticalSizing(Sizing.fixed(18));
        this.summary.onPress(_ -> this.showPopover.accept(this.summary, this::buildSummary));
        this.priceChart = new PriceChartComponent(this.viewport, this::options);
        this.quantityChart = new QuantityChartComponent(this.viewport, this::options);
        this.mayorLane = new MayorLaneComponent(this.viewport, () -> this.mayors);
        this.rebuildLayout();
        this.refreshPreferences();
    }

    public void layoutFor(int width, int height) {
        int nextHeight = Math.max(180, height);
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
                selectionChanged || selectedDataArrived);
        }
        var mayorData = data.mayors().value();
        this.mayors = mayorData == null ? List.of() : mayorData.terms();
    }

    private void rebuildLayout() {
        this.viewport.clearHover();
        this.clearChildren();
        var controls = UiControls.row(UiControls.text("Price history", UiStyles.palette().label()), this.buy, this.sell,
            this.settings);
        controls.gap(3);
        this.child(controls);
        boolean singlePlot = this.config.showQuantity && this.availableHeight < 255;
        if (singlePlot) {
            var price = UiControls.button("Price", () -> this.selectPlot(false));
            var quantity = UiControls.button("Quantity", () -> this.selectPlot(true));
            price.renderer(UiControls.segmentRenderer(!this.quantitySelected));
            quantity.renderer(UiControls.segmentRenderer(this.quantitySelected));
            price.verticalSizing(Sizing.fixed(18));
            quantity.verticalSizing(Sizing.fixed(18));
            this.child(UiControls.row(price, quantity));
        }
        boolean showPrice = !singlePlot || !this.quantitySelected;
        boolean showQuantity = this.config.showQuantity && (!singlePlot || this.quantitySelected);
        int chrome = 18 + 18 + 9 + (singlePlot ? 21 : 0) + (showQuantity ? 25 : 0)
            + (showPrice && this.config.showMayors ? 21 : 0);
        int plotHeight = Math.max(showPrice && showQuantity ? 192 : 112, this.availableHeight - chrome);
        int quantityHeight = showPrice && showQuantity ? Math.max(76, (int) (plotHeight * .32)) : plotHeight;
        if (showPrice) {
            if (this.config.showMayors) {
                this.child(this.mayorLane);
            }
            int priceHeight = showQuantity ? plotHeight - quantityHeight : plotHeight;
            this.priceChart.verticalSizing(Sizing.fixed(priceHeight));
            this.priceChart.timeAxisVisible(!showQuantity);
            this.child(this.priceChart);
        }
        if (showQuantity) {
            this.child(this.quantityControls());
            this.quantityChart.verticalSizing(Sizing.fixed(quantityHeight));
            this.quantityChart.timeAxisVisible(true);
            this.child(this.quantityChart);
        }
        this.child(this.summary);
    }

    private void selectPlot(boolean quantity) {
        this.quantitySelected = quantity;
        this.queue(this::rebuildLayout);
    }

    private FlowLayout quantityControls() {
        this.metricButtons.clear();
        var controls = UiControls.row(UiControls.text("Quantity", UiStyles.palette().label()));
        controls.gap(3);
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
            var selector = UiControls.button(this.config.quantityMetric.label() + " v", () -> {});
            selector.verticalSizing(Sizing.fixed(18));
            selector.renderer(UiControls.segmentRenderer(true));
            selector.onPress(_ -> this.showPopover.accept(selector, content -> {
                content.child(UiControls.text("Quantity metric", UiStyles.palette().primary()));
                var choices = new ArrayList<ButtonComponent>();
                for (var metric : QuantityMetric.values()) {
                    var choice = UiControls.button(metric.label(), () -> {
                        this.selectMetric(metric);
                        for (int index = 0; index < choices.size(); index++) {
                            choices.get(index)
                                .renderer(UiControls.segmentRenderer(QuantityMetric.values()[index] == metric));
                        }
                    });
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
        information.onPress(_ -> this.showPopover.accept(information, this::buildQuantityHelp));
        controls.child(information);
        return controls;
    }

    private void selectMetric(QuantityMetric metric) {
        this.config.quantityMetric = metric;
        this.save.run();
        this.refreshPreferences();
    }

    public void refreshPreferences() {
        this.buy.renderer(UiControls.seriesRenderer(this.config.showBuy, UiStyles.palette().buy()));
        this.sell.renderer(UiControls.seriesRenderer(this.config.showSell, UiStyles.palette().sell()));
        if (this.metricButtons.size() == 1) {
            this.metricButtons.getFirst().setMessage(Component.literal(this.config.quantityMetric.label() + " v"));
        } else {
            for (int index = 0; index < this.metricButtons.size(); index++) {
                this.metricButtons.get(index).renderer(UiControls.segmentRenderer(
                    QuantityMetric.values()[index] == this.config.quantityMetric));
            }
        }
    }

    private void buildSettings(FlowLayout content) {
        content.child(UiControls.text("Chart options", UiStyles.palette().primary()));
        this.toggle(content, "Min/max shading", () -> this.config.showBands, value -> this.config.showBands = value,
            false);
        this.toggle(content, "Mayor timeline", () -> this.config.showMayors, value -> {
            this.config.showMayors = value;
            this.mayorVisible.accept(value);
        }, true);
        this.toggle(content, "Quantity plot", () -> this.config.showQuantity, value -> this.config.showQuantity = value,
            true);
    }

    private void toggle(
        FlowLayout content,
        String label,
        BooleanSupplier read,
        Consumer<Boolean> write,
        boolean layout
    ) {
        var button = UiControls.button(label + ": " + (read.getAsBoolean() ? "On" : "Off"), () -> {});
        button.renderer(UiControls.quietRenderer());
        button.onPress(_ -> {
            write.accept(!read.getAsBoolean());
            button.setMessage(Component.literal(label + ": " + (read.getAsBoolean() ? "On" : "Off")));
            this.preferenceChanged(layout);
        });
        content.child(button);
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
        content.child(UiControls.text("Visible range summary", UiStyles.palette().primary()));
        content.child(UiControls.text("Returned price samples have equal weight.", UiStyles.palette().muted())
            .horizontalSizing(Sizing.fill(100)));
        for (boolean buy : new boolean[]{true, false}) {
            var values = HistoryAnalysis.stats(this.viewport.history(), this.viewport.start(), this.viewport.end(),
                buy);
            content.child(UiControls.text(buy ? "Buy Price" : "Sell Price",
                buy ? UiStyles.palette().buy() : UiStyles.palette().sell()));
            if (values.isEmpty()) {
                content.child(UiControls.text("No price samples", UiStyles.palette().muted()));
                continue;
            }
            var stats = values.orElseThrow();
            content.child(UiControls.text("Start to end: " + (stats.percent() == null
                ? "Unavailable"
                : String.format(Locale.ROOT, "%+.1f%%", stats.percent())), UiStyles.palette().label()));
            content.child(UiControls.text("Low: " + HistoryAnalysis.exact(stats.low()), UiStyles.palette().label()));
            content.child(UiControls.text("High: " + HistoryAnalysis.exact(stats.high()), UiStyles.palette().label()));
            content.child(UiControls.text("Sample average: " + HistoryAnalysis.exact(stats.average()),
                UiStyles.palette().label()));
        }
    }

    private ChartOptions options() {
        return new ChartOptions(this.config.showBuy, this.config.showSell, this.config.showBands,
            this.config.quantityMetric);
    }
}
