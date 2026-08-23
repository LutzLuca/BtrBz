package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoConfig.ActivityMode;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Empty;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Failure;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.Loading;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.NotRequested;
import com.github.lutzluca.btrbz.core.bazaariteminfo.BazaarItemInfoViewData.ScreenState;
import com.github.lutzluca.btrbz.core.widgets.data.BazaarWidgetViewData;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.RetainedFlowLayout;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetScrollContainer;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.Utils;
import com.github.lutzluca.coflnet.CoflnetBazaarClient;
import io.vavr.control.Try;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Unified History and Order Book screen for one resolved Bazaar item. */
public final class BazaarItemInfoScreen extends BaseOwoScreen<FlowLayout> {
    private static final String COFLNET_ITEM_URL = "https://sky.coflnet.com/item/%s";
    private static final int ROOT_PADDING = 12;
    private static final int PANEL_WIDTH_PERCENT = 90;
    private static final int PANEL_HORIZONTAL_PADDING = 10;
    private static final int HEADER_ICON_SIZE = 20;
    private static final int HEADER_HEIGHT = 98;
    private static final int HEADER_INSET = 5;
    private static final int PANEL_COLOR = 0xEE181B22;
    private static final int HEADER_COLOR = 0x76101319;
    private static final int BUTTON_NORMAL = 0xFF2C3340;
    private static final int BUTTON_HOVER = 0xFF384252;
    private static final int BUTTON_DISABLED = 0xFF20242D;
    private static final int BUTTON_SELECTED = 0xFF315B45;
    private static final int BUTTON_SELECTED_HOVER = 0xFF3A7053;
    private static final int BUY_FILTER_SELECTED = 0xFF285A3B;
    private static final int BUY_FILTER_SELECTED_HOVER = 0xFF326F49;
    private static final int SELL_FILTER_SELECTED = 0xFF65501D;
    private static final int SELL_FILTER_SELECTED_HOVER = 0xFF7A6123;
    private static final int RANGE_FILTER_SELECTED = 0xFF444D5C;
    private static final int RANGE_FILTER_SELECTED_HOVER = 0xFF525D70;
    private static final int CONTROL_STRIP_COLOR = 0x50101319;
    private static final int ACTIVITY_CHART_HEIGHT = 72;

    private final Screen parent;
    private final ProductIdentity product;
    private final String productTag;
    private final ItemStack itemStack;
    private final BazaarItemInfoDataProvider dataProvider;
    private final EnumMap<BazaarItemInfoRange, ButtonComponent> rangeButtons = new EnumMap<>(BazaarItemInfoRange.class);
    private final EnumMap<InitialMode, ButtonComponent> tabButtons = new EnumMap<>(InitialMode.class);
    private final BazaarItemInfoOrderBookPanel orderBook = new BazaarItemInfoOrderBookPanel();
    private final BazaarHistoryPanelController historyController = new BazaarHistoryPanelController();

    private @Nullable FlowLayout body;
    private @Nullable WidgetScrollContainer<RetainedFlowLayout> historyScroller;
    private @Nullable WidgetScrollContainer<FlowLayout> orderBookScroller;
    private @Nullable LabelComponent buyPrice;
    private @Nullable LabelComponent sellPrice;
    private @Nullable LabelComponent spreadValue;
    private @Nullable LabelComponent buyComparison;
    private @Nullable LabelComponent sellComparison;
    private @Nullable LabelComponent freshness;
    private @Nullable BazaarHistoryChartComponent chart;
    private @Nullable BazaarActivityChartComponent activityChart;
    private @Nullable BazaarTimeAxisComponent timeAxis;
    private @Nullable ButtonComponent refresh;
    private @Nullable ButtonComponent activity;
    private @Nullable ButtonComponent buyFilter;
    private @Nullable ButtonComponent sellFilter;
    private @Nullable ButtonComponent bandsFilter;
    private @Nullable FlowLayout filters;
    private @Nullable ButtonComponent recoverFilters;
    private @Nullable ScreenState latestState;
    private @Nullable BazaarItemInfoRange lastAppliedRange;
    private boolean closed;
    private boolean openingLinkConfirmation;

    public BazaarItemInfoScreen(
        Screen parent,
        ProductIdentity product,
        String productTag,
        ItemStack itemStack,
        BazaarData bazaarData,
        CoflnetBazaarClient coflnet,
        InitialMode initialMode
    ) {
        super(Component.literal(product.strippedName() + " Bazaar Info"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.product = Objects.requireNonNull(product, "product");
        this.productTag = Objects.requireNonNull(productTag, "productTag");
        this.itemStack = Objects.requireNonNull(itemStack, "itemStack").copy();
        this.dataProvider = new BazaarItemInfoDataProvider(
            Objects.requireNonNull(bazaarData, "bazaarData"),
            Objects.requireNonNull(coflnet, "coflnet"),
            product,
            productTag,
            Objects.requireNonNull(initialMode, "initialMode"),
            Minecraft.getInstance()::execute,
            this::applyState);
        this.latestState = this.dataProvider.state();
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.horizontalAlignment(HorizontalAlignment.CENTER);
        root.verticalAlignment(VerticalAlignment.CENTER);
        root.padding(Insets.of(ROOT_PADDING));

        var panel = UIContainers.verticalFlow(Sizing.fill(PANEL_WIDTH_PERCENT), Sizing.fill(92));
        panel.padding(Insets.both(PANEL_HORIZONTAL_PADDING, 8));
        panel.gap(WidgetLayoutTokens.SECTION_GAP + 1);
        panel.surface(WidgetSurfaces.roundedPanel(PANEL_COLOR, 6));
        panel.child(this.header());
        this.body = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand(100));
        this.historyScroller = this.createHistoryBody();
        this.orderBookScroller = new WidgetScrollContainer<>(
            Sizing.fill(100), Sizing.fill(100), this.orderBook.root(), true);
        this.orderBookScroller.scrollbarThiccness(WidgetLayoutTokens.SCROLLBAR_THICKNESS);
        this.orderBookScroller.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(BazaarStyles.SCROLLBAR)));
        panel.child(this.body);
        panel.child(this.footer());
        root.child(panel);
        if (this.latestState != null) {
            this.applyState(this.latestState);
        }
    }

    private FlowLayout header() {
        var header = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(HEADER_HEIGHT));
        header.padding(Insets.of(HEADER_INSET));
        header.gap(2);
        header.surface(WidgetSurfaces.roundedPanel(HEADER_COLOR, 4));
        header.child(this.navigation());
        header.child(this.summary());
        return header;
    }

    private FlowLayout navigation() {
        var navigation = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(40));
        navigation.gap(2);
        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(WidgetLayoutTokens.HEADER_GAP);
        header.child(BazaarUi.item(this.itemStack, HEADER_ICON_SIZE));
        var name = BazaarUi.label("", BazaarStyles.PRIMARY_TEXT);
        name.text(BazaarUi.ellipsizeComponent(
            Utils.legacyFormattedComponent(this.product.visualName()),
            headerNameWidth(this.width)));
        name.horizontalSizing(Sizing.expand(100));
        header.child(name);
        var open = UIComponents.button(Component.literal("Open in Coflnet"), _ -> this.openInCoflnet());
        open.sizing(Sizing.fixed(112), Sizing.fixed(18));
        open.renderer(ButtonComponent.Renderer.flat(BUTTON_NORMAL, BUTTON_HOVER, BUTTON_DISABLED));
        open.textShadow(false);
        header.child(open);
        var tabs = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        tabs.gap(4);
        tabs.child(tab("History", InitialMode.History));
        tabs.child(tab("Order Book", InitialMode.OrderBook));
        navigation.child(header);
        navigation.child(tabs);
        return navigation;
    }

    private ButtonComponent tab(String text, InitialMode mode) {
        var button = UIComponents.button(Component.literal(text), _ -> this.dataProvider.setMode(mode));
        button.sizing(Sizing.fixed(mode == InitialMode.History ? 62 : 78), Sizing.fixed(18));
        button.textShadow(false);
        this.tabButtons.put(mode, button);
        return button;
    }

    static int headerNameWidth(int screenWidth) {
        int panelWidth = Math.max(0, screenWidth - 2 * ROOT_PADDING) * PANEL_WIDTH_PERCENT / 100;
        return Math.max(30, panelWidth - 2 * PANEL_HORIZONTAL_PADDING - HEADER_ICON_SIZE
            - 2 * WidgetLayoutTokens.HEADER_GAP - 112 - 2 * HEADER_INSET);
    }

    private FlowLayout summary() {
        var block = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(46));
        block.gap(2);
        var metrics = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        metrics.gap(5);
        var buyMetric = UIContainers.verticalFlow(Sizing.expand(40), Sizing.fixed(30));
        var spreadMetric = UIContainers.verticalFlow(Sizing.expand(20), Sizing.fixed(30));
        var sellMetric = UIContainers.verticalFlow(Sizing.expand(40), Sizing.fixed(30));
        buyMetric.padding(Insets.both(4, 2));
        spreadMetric.padding(Insets.both(4, 2));
        sellMetric.padding(Insets.both(4, 2));
        buyMetric.gap(1);
        spreadMetric.gap(1);
        sellMetric.gap(1);
        buyMetric.surface(WidgetSurfaces.roundedPanel(0x40181B22, 3));
        spreadMetric.surface(WidgetSurfaces.roundedPanel(0x40181B22, 3));
        sellMetric.surface(WidgetSurfaces.roundedPanel(0x40181B22, 3));
        this.buyPrice = BazaarUi.boldLabel("Buy Price  -", BazaarStyles.BUY_ACCENT);
        this.sellPrice = BazaarUi.boldLabel("Sell Price  -", BazaarStyles.SELL_ACCENT);
        var spreadTitle = BazaarUi.label("Spread", BazaarStyles.SECONDARY_TEXT);
        this.spreadValue = BazaarUi.boldLabel("-", BazaarStyles.PRIMARY_TEXT);
        this.buyComparison = BazaarUi.label("7d avg  Open History to load", BazaarStyles.MUTED_TEXT);
        this.sellComparison = BazaarUi.label("7d avg  Open History to load", BazaarStyles.MUTED_TEXT);
        this.buyPrice.horizontalSizing(Sizing.fill(100));
        this.sellPrice.horizontalSizing(Sizing.fill(100));
        this.buyComparison.horizontalSizing(Sizing.fill(100));
        this.sellComparison.horizontalSizing(Sizing.fill(100));
        spreadTitle.horizontalSizing(Sizing.fill(100));
        this.spreadValue.horizontalSizing(Sizing.fill(100));
        this.sellPrice.horizontalTextAlignment(HorizontalAlignment.RIGHT);
        this.sellComparison.horizontalTextAlignment(HorizontalAlignment.RIGHT);
        spreadTitle.horizontalTextAlignment(HorizontalAlignment.CENTER);
        this.spreadValue.horizontalTextAlignment(HorizontalAlignment.CENTER);
        buyMetric.child(this.buyPrice);
        buyMetric.child(this.buyComparison);
        spreadMetric.child(spreadTitle);
        spreadMetric.child(this.spreadValue);
        sellMetric.child(this.sellPrice);
        sellMetric.child(this.sellComparison);
        metrics.child(buyMetric);
        metrics.child(spreadMetric);
        metrics.child(sellMetric);
        this.freshness = BazaarUi.label("Waiting for Bazaar data...", BazaarStyles.MUTED_TEXT);
        this.freshness.horizontalSizing(Sizing.fill(100));
        this.freshness.horizontalTextAlignment(HorizontalAlignment.CENTER);
        block.child(metrics);
        block.child(this.freshness);
        return block;
    }

    private WidgetScrollContainer<RetainedFlowLayout> createHistoryBody() {
        var root = RetainedFlowLayout.vertical(Sizing.fill(100), Sizing.content());
        root.gap(3);
        var ranges = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        ranges.padding(Insets.of(2));
        ranges.gap(2);
        ranges.surface(WidgetSurfaces.roundedPanel(CONTROL_STRIP_COLOR, 3));
        for (var candidate : BazaarItemInfoRange.values()) {
            var button = UIComponents.button(Component.literal(candidate.label()),
                _ -> this.dataProvider.selectRange(candidate));
            button.sizing(Sizing.fixed(48), Sizing.fixed(18));
            button.textShadow(false);
            this.rangeButtons.put(candidate, button);
            ranges.child(button);
        }
        this.refresh = UIComponents.button(Component.literal("Refresh"), _ -> this.dataProvider.refresh());
        this.refresh.sizing(Sizing.fixed(58), Sizing.fixed(18));
        this.refresh.renderer(ButtonComponent.Renderer.flat(BUTTON_NORMAL, BUTTON_HOVER, BUTTON_DISABLED));
        this.refresh.textShadow(false);
        ranges.child(this.refresh);
        this.activity = UIComponents.button(Component.literal("Activity"), _ -> this.toggleActivity());
        this.activity.sizing(Sizing.fixed(84), Sizing.fixed(18));
        this.activity.textShadow(false);
        ranges.child(this.activity);

        this.filters = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        this.filters.padding(Insets.of(2));
        this.filters.gap(2);
        this.filters.surface(WidgetSurfaces.roundedPanel(CONTROL_STRIP_COLOR, 3));
        this.buyFilter = filter("Buy Price", 66,
            () -> this.dataProvider.state().preferences().showBuy(), this.dataProvider::showBuy);
        this.sellFilter = filter("Sell Price", 66,
            () -> this.dataProvider.state().preferences().showSell(), this.dataProvider::showSell);
        this.bandsFilter = filter("Range Shading", 82,
            () -> this.dataProvider.state().preferences().showBands(), this.dataProvider::showBands);
        this.filters.child(this.buyFilter);
        this.filters.child(this.sellFilter);
        this.filters.child(this.bandsFilter);
        this.recoverFilters = UIComponents.button(Component.literal("Show both prices"),
            _ -> this.dataProvider.showBuyAndSell());
        this.recoverFilters.sizing(Sizing.fixed(106), Sizing.fixed(18));
        this.recoverFilters.renderer(ButtonComponent.Renderer.flat(BUTTON_NORMAL, BUTTON_HOVER, BUTTON_DISABLED));
        this.recoverFilters.textShadow(false);

        this.chart = new BazaarHistoryChartComponent(
            Sizing.fill(100), Sizing.fixed(150), this.historyController);
        this.activityChart = new BazaarActivityChartComponent(
            Sizing.fill(100), Sizing.fixed(ACTIVITY_CHART_HEIGHT), this.historyController);
        this.timeAxis = new BazaarTimeAxisComponent(
            Sizing.fill(100), Sizing.fixed(12), this.historyController);
        root.child(ranges);
        root.child(this.filters);
        root.child(this.historyLegend());
        root.child(this.chart);
        root.child(this.activityChart);
        root.child(this.timeAxis);
        var scroller = new WidgetScrollContainer<>(Sizing.fill(100), Sizing.fill(100), root, true);
        scroller.scrollbarThiccness(WidgetLayoutTokens.SCROLLBAR_THICKNESS);
        scroller.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(BazaarStyles.SCROLLBAR)));
        return scroller;
    }

    private ButtonComponent filter(
        String label,
        int width,
        java.util.function.BooleanSupplier selected,
        java.util.function.Consumer<Boolean> setter
    ) {
        var button = UIComponents.button(Component.literal(label), _ -> setter.accept(!selected.getAsBoolean()));
        button.sizing(Sizing.fixed(width), Sizing.fixed(18));
        button.textShadow(false);
        return button;
    }

    private FlowLayout historyLegend() {
        var legend = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(12));
        legend.verticalAlignment(VerticalAlignment.CENTER);
        legend.gap(6);
        var title = BazaarUi.label("Price history: hover for exact values", BazaarStyles.SECONDARY_TEXT);
        title.horizontalSizing(Sizing.expand(100));
        legend.child(title);
        legend.child(BazaarUi.label("Buy Price", BazaarStyles.BUY_ACCENT));
        legend.child(BazaarUi.label("Sell Price", BazaarStyles.SELL_ACCENT));
        legend.child(BazaarUi.label("Range shading", BazaarStyles.MUTED_TEXT));
        return legend;
    }

    private void toggleActivity() {
        var state = this.dataProvider.state();
        this.dataProvider.activityMode(state.preferences().activityMode() == ActivityMode.Off
            ? ActivityMode.IntervalItems
            : ActivityMode.Off);
    }

    private FlowLayout footer() {
        var footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(14));
        footer.child(BazaarUi.label("Live market data: Hypixel | History: Coflnet", BazaarStyles.MUTED_TEXT));
        return footer;
    }

    private void applyState(ScreenState state) {
        if (this.closed) {
            return;
        }
        this.latestState = state;
        if (this.body == null || this.chart == null
            || this.buyPrice == null
            || this.sellPrice == null
            || this.spreadValue == null
            || this.buyComparison == null
            || this.sellComparison == null) {
            return;
        }
        this.body.clearChildren();
        if (state.activeMode() == InitialMode.History) {
            this.body.child(Objects.requireNonNull(this.historyScroller));
        } else {
            int availableWidth = Math.max(1, this.width * PANEL_WIDTH_PERCENT / 100 - 2 * PANEL_HORIZONTAL_PADDING);
            this.orderBook.update(
                state.live(), state.preferences(), availableWidth, this.orderBookAvailableHeight());
            this.body.child(Objects.requireNonNull(this.orderBookScroller));
        }
        this.buyPrice.text(Component.literal("Buy Price  " + price(state.live().buyPrice())));
        this.sellPrice.text(Component.literal("Sell Price  " + price(state.live().sellPrice())));
        this.spreadValue.text(Component.literal(spreadText(state.live())));
        if (this.lastAppliedRange != null && this.lastAppliedRange != state.selectedRange()
            && this.historyScroller != null) {
            this.historyScroller.scrollOffset(0);
        }
        this.lastAppliedRange = state.selectedRange();
        this.historyController.update(
            state.selectedHistory().data().retainedValue()
                .map(BazaarItemInfoViewData.History::points).orElse(List.of()),
            state.selectedRange(),
            state.preferences().showBuy(),
            state.preferences().showSell(),
            state.preferences().showBands(),
            state.preferences().activityMode());
        if (this.activityChart != null) {
            this.activityChart.verticalSizing(state.preferences().activityMode() == ActivityMode.Off
                ? Sizing.fixed(0)
                : Sizing.fixed(ACTIVITY_CHART_HEIGHT));
        }
        this.rangeButtons.forEach((range, button) -> button.renderer(
            range == state.selectedRange()
                ? ButtonComponent.Renderer.flat(BUTTON_SELECTED, BUTTON_SELECTED_HOVER, BUTTON_DISABLED)
                : ButtonComponent.Renderer.flat(BUTTON_NORMAL, BUTTON_HOVER, BUTTON_DISABLED)));
        this.tabButtons.forEach((mode, button) -> button.renderer(
            mode == state.activeMode()
                ? ButtonComponent.Renderer.flat(BUTTON_SELECTED, BUTTON_SELECTED_HOVER, BUTTON_DISABLED)
                : ButtonComponent.Renderer.flat(BUTTON_NORMAL, BUTTON_HOVER, BUTTON_DISABLED)));
        if (this.refresh != null) {
            this.refresh.active(state.refreshEnabled());
        }
        if (this.activity != null) {
            boolean activityEnabled = state.preferences().activityMode() != ActivityMode.Off;
            this.activity.setMessage(Component.literal(activityEnabled ? "Activity On" : "Activity Off"));
            applyToggleStyle(
                this.activity, activityEnabled, BUTTON_SELECTED, BUTTON_SELECTED_HOVER);
        }
        if (this.buyFilter != null && this.sellFilter != null && this.bandsFilter != null) {
            applyToggleStyle(this.buyFilter, state.preferences().showBuy(),
                BUY_FILTER_SELECTED, BUY_FILTER_SELECTED_HOVER);
            applyToggleStyle(this.sellFilter, state.preferences().showSell(),
                SELL_FILTER_SELECTED, SELL_FILTER_SELECTED_HOVER);
            applyToggleStyle(this.bandsFilter, state.preferences().showBands(),
                RANGE_FILTER_SELECTED, RANGE_FILTER_SELECTED_HOVER);
        }
        if (this.filters != null && this.recoverFilters != null) {
            boolean allHidden = !state.preferences().showBuy() && !state.preferences().showSell();
            boolean recoveryVisible = this.filters.children().contains(this.recoverFilters);
            if (allHidden && !recoveryVisible) {
                this.filters.child(this.recoverFilters);
            } else if (!allHidden && recoveryVisible) {
                this.filters.removeChild(this.recoverFilters);
            }
        }
        this.buyComparison.text(Component.literal(comparisonText(state, true)));
        this.sellComparison.text(Component.literal(comparisonText(state, false)));
        this.refreshFreshness();
    }

    private static String price(java.util.Optional<Double> value) {
        return value.map(BazaarItemInfoScreen::summaryPrice).orElse("-");
    }

    static String summaryPrice(double value) {
        double absolute = Math.abs(value);
        if (absolute >= 1_000_000) {
            return Utils.formatCompact(value, 2);
        }
        if (absolute >= 100_000) {
            return Utils.formatCompact(value, 1);
        }
        return BazaarWidgetViewData.formatPrice(value);
    }

    private static String spreadText(com.github.lutzluca.btrbz.data.LiveProductSnapshot live) {
        return spreadSummary(live)
            .map(spread -> BazaarWidgetViewData.formatCompact(Math.round(spread.amount()))
                + String.format(java.util.Locale.ROOT, "  (%.1f%%)", spread.percent()))
            .orElse("-");
    }

    private static void applyToggleStyle(
        ButtonComponent button,
        boolean selected,
        int selectedColor,
        int selectedHoverColor
    ) {
        button.renderer(selected
            ? ButtonComponent.Renderer.flat(selectedColor, selectedHoverColor, BUTTON_DISABLED)
            : ButtonComponent.Renderer.flat(BUTTON_NORMAL, BUTTON_HOVER, BUTTON_DISABLED));
    }

    static Optional<SpreadSummary> spreadSummary(
        com.github.lutzluca.btrbz.data.LiveProductSnapshot live
    ) {
        Objects.requireNonNull(live, "live");
        if (live.buyPrice().isEmpty() || live.sellPrice().isEmpty()) {
            return Optional.empty();
        }
        double buy = live.buyPrice().orElseThrow();
        double sell = live.sellPrice().orElseThrow();
        if (!Double.isFinite(buy) || !Double.isFinite(sell) || buy <= 0) {
            return Optional.empty();
        }
        double amount = buy - sell;
        double percent = amount / buy * 100;
        return Double.isFinite(amount) && Double.isFinite(percent)
            ? Optional.of(new SpreadSummary(amount, percent))
            : Optional.empty();
    }

    private int orderBookAvailableHeight() {
        if (this.body != null && this.body.height() > 0) {
            return this.body.height();
        }
        int panelHeight = this.height * 92 / 100;
        int panelGaps = (WidgetLayoutTokens.SECTION_GAP + 1) * 2;
        return Math.max(1, panelHeight - 16 - HEADER_HEIGHT - 14 - panelGaps);
    }

    private static String historyStatus(ScreenState state) {
        var range = state.selectedHistory();
        var data = range.data();
        if (data instanceof NotRequested<?>) {
            return "Open History to load";
        }
        if (data instanceof Loading<?>) {
            return data.retainedValue().isPresent() ? "Updating..." : "Loading...";
        }
        if (data instanceof Failure<?>) {
            return data.retainedValue().isPresent()
                ? "Update failed. Showing cached data"
                : "History unavailable: " + ((Failure<?>) data).message();
        }
        if (data instanceof Empty<?>) {
            return "No history available for this range";
        }
        return range.checkedAt().map(checked -> "History checked " + age(checked) + " ago").orElse("");
    }

    private static String comparisonText(ScreenState state, boolean buy) {
        var retained = state.comparison().retainedValue();
        if (state.comparison() instanceof NotRequested<?>) {
            return "7d avg  Open History to load";
        }
        if (retained.isEmpty()) {
            return state.comparison() instanceof Loading<?> ? "7d avg  Loading..." : "7d avg  Unavailable";
        }
        var result = retained.orElseThrow();
        String label = comparisonLabel(state.comparison(), result.label());
        return label + "  " + sideComparison(buy ? result.buy() : result.sell());
    }

    static String comparisonLabel(BazaarItemInfoViewData.LoadState<?> comparison, String loadedLabel) {
        Objects.requireNonNull(comparison, "comparison");
        Objects.requireNonNull(loadedLabel, "loadedLabel");
        return comparison instanceof Failure<?> ? "7d cached" : loadedLabel;
    }

    private static String sideComparison(SevenDayComparison.Side side) {
        if (side.average().isEmpty()) {
            return "Unavailable";
        }
        String text = summaryPrice(side.average().getAsDouble());
        if (side.deltaPercent().isPresent()) {
            text += String.format(java.util.Locale.ROOT, " (%+.1f%%)", side.deltaPercent().getAsDouble());
        }
        return text;
    }

    private void refreshFreshness() {
        if (this.freshness == null || this.latestState == null) {
            return;
        }
        var state = this.latestState;
        if (state.activeMode() == InitialMode.History) {
            var status = Component.literal(historyStatus(state));
            this.freshness.text(status);
            return;
        }
        if (state.live().lastUpdated().isEmpty()) {
            this.freshness.text(Component.literal("Waiting for Bazaar data..."));
            return;
        }
        if (!state.live().marketDataAvailable()) {
            this.freshness.text(Component.literal("No current Hypixel market data"));
            return;
        }
        var updated = state.live().lastUpdated().orElseThrow();
        long seconds = Math.max(0, Duration.between(updated, Instant.now()).toSeconds());
        String text = seconds >= 60
            ? "Data may be stale. " + age(updated) + " old"
            : "Market updated " + age(updated) + " ago";
        this.freshness.text(Component.literal(text));
    }

    private static String age(Instant instant) {
        long seconds = Math.max(0, Duration.between(instant, Instant.now()).toSeconds());
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        return remainder == 0 ? minutes + "m" : minutes + "m " + remainder + "s";
    }

    private void openInCoflnet() {
        String link = COFLNET_ITEM_URL.formatted(this.productTag);
        var client = Minecraft.getInstance();
        this.openingLinkConfirmation = true;
        client.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) {
                Try.run(() -> net.minecraft.util.Util.getPlatform().openUri(new URI(link)))
                    .onFailure(_ -> Notifier.notifyPlayer(Component.literal("Failed to open link: ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(link).withStyle(ChatFormatting.UNDERLINE, ChatFormatting.BLUE))));
            }
            this.openingLinkConfirmation = false;
            client.setScreen(this);
        }, link, true));
    }

    @Override
    public void tick() {
        super.tick();
        this.orderBook.tick();
        this.refreshFreshness();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        this.historyController.clearSelection();
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xA0000000);
    }

    @Override
    public void onClose() {
        this.closed = true;
        this.dataProvider.dispose();
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void removed() {
        super.removed();
        if (!this.openingLinkConfirmation) {
            this.closed = true;
            this.dataProvider.dispose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    record SpreadSummary(double amount, double percent) {}
}
