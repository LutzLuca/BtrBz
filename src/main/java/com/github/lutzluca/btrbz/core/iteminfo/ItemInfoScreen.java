package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryAnalysis;
import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryViewport;
import com.github.lutzluca.btrbz.core.ui.ProductSearchControls;
import com.github.lutzluca.btrbz.core.ui.UiControls;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.coflnet.HistoryQuery;
import com.github.lutzluca.coflnet.HistoryResponse;
import io.vavr.control.Try;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One inspection session, with local dialogs that block background input. */
public final class ItemInfoScreen extends BaseOwoScreen<FlowLayout> {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
        .withResolverStyle(ResolverStyle.STRICT).withZone(ZoneOffset.UTC);
    private final @Nullable Screen parent;
    private final BazaarData market;
    private final ItemInfoSession session;
    private final ItemInfoConfig config;
    private final Runnable save;
    private final HistoryViewport viewport = new HistoryViewport();
    private final HistoryPanel history;
    private final OrderBookPanel book;
    private final List<Runnable> deferred = new ArrayList<>();
    private final EnumMap<ItemInfoRange, ButtonComponent> ranges = new EnumMap<>(ItemInfoRange.class);
    private boolean orderBook;
    private Modal modal;
    private String searchQuery;
    private String customStart;
    private String customEnd;
    private String feedback = "";
    private double historyOffset;
    private double bookOffset;
    private double modalOffset;
    private @Nullable ProductSearchControls.ViewState searchState;
    private @Nullable RestorableVerticalScrollContainer<FlowLayout> bodyScroll;
    private @Nullable RestorableVerticalScrollContainer<FlowLayout> modalScroll;
    private @Nullable ProductSearchControls search;
    private @Nullable TextBoxComponent startBox;
    private @Nullable TextBoxComponent endBox;
    private @Nullable LabelComponent feedbackLabel;
    private @Nullable LabelComponent buy;
    private @Nullable LabelComponent sell;
    private @Nullable LabelComponent spread;
    private @Nullable LabelComponent source;
    private @Nullable LabelComponent historyStatus;
    private @Nullable LabelComponent mayorStatus;
    private @Nullable FlowLayout productHeader;
    private @Nullable ButtonComponent coflnet;
    private @Nullable ProductIdentity headerProduct;
    private boolean rebuilding;
    private int bodyWidth;
    private int bodyHeight;
    private int ageTicks;
    private boolean focusStart;
    private boolean focusEnd;
    private @Nullable HistoryResponse averageHistory;
    private @Nullable HistoryQuery averageQuery;
    private @Nullable Double buyAverage;
    private @Nullable Double sellAverage;
    private @Nullable ButtonComponent rangeMenu;

    public ItemInfoScreen(
        @Nullable Screen parent,
        BazaarData market,
        ItemInfoSession session,
        ItemInfoConfig config,
        Runnable save,
        String initialSearch,
        boolean initialOrderBook
    ) {
        super(Component.literal("BtrBz Item Info"));
        this.parent = parent;
        this.market = market;
        this.session = session;
        this.config = config;
        this.save = save;
        this.searchQuery = initialSearch;
        this.orderBook = initialOrderBook;
        this.modal = session.data().product() == null ? Modal.Search : Modal.None;
        this.customStart = UTC.format(session.data().query().start());
        this.customEnd = UTC.format(session.data().query().end());
        this.history = new HistoryPanel(config, save, this.viewport, () -> this.showModal(Modal.Quantity));
        this.book = new OrderBookPanel(config, price -> {
            if (this.session.isCurrent() && this.session.data().live().isPresent()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(price);
                this.feedback = "Price copied";
                this.updateFeedback();
            }
        });
        this.session.onChanged(this::refreshData);
        this.session.historyVisible(!this.orderBook);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void init() {
        super.init();
        this.runDeferred();
    }

    @Override
    protected void build(FlowLayout root) {
        this.rebuilding = true;
        root.surface(Surface.flat(0x90000000));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        this.bodyWidth = Math.max(140, this.width - 40);
        this.bodyHeight = Math.max(70, this.height - 200);
        this.search = null;
        this.startBox = null;
        this.endBox = null;
        this.modalScroll = null;
        this.bodyScroll = null;
        this.feedbackLabel = null;
        this.ranges.clear();
        this.rangeMenu = null;
        var panel = UIContainers.verticalFlow(Sizing.fixed(Math.max(160, this.width - 20)),
            Sizing.fixed(Math.max(100, this.height - 20)));
        panel.padding(Insets.of(10));
        panel.gap(6);
        panel.surface(WidgetSurfaces.roundedPanel(0xF0100F0E, 6));
        root.child(panel);
        if (this.modal != Modal.None) {
            this.buildModal(panel);
        } else {
            this.buildMain(panel);
        }
        this.rebuilding = false;
        this.refreshData();
    }

    private void buildMain(FlowLayout panel) {
        this.productHeader = UIContainers.horizontalFlow(Sizing.expand(100), Sizing.content());
        this.productHeader.gap(5);
        this.productHeader.verticalAlignment(VerticalAlignment.CENTER);
        this.productHeader.child(UiControls.text("Item Info", UiStyles.palette().primary()));
        this.headerProduct = null;
        panel.child(UiControls.row(this.productHeader, UiControls.button("Search", () -> this.showModal(Modal.Search)),
            UiControls.button("\u00d7", this::onClose).horizontalSizing(Sizing.fixed(22))));
        boolean quoteColumns = this.bodyWidth >= 500;
        int quoteWidth = quoteColumns ? (this.bodyWidth - 12) / 3 : this.bodyWidth;
        this.buy = UiControls.text("", UiStyles.palette().buy()).maxWidth(quoteWidth);
        this.sell = UiControls.text("", UiStyles.palette().sell()).maxWidth(quoteWidth);
        this.spread = UiControls.text("", UiStyles.palette().label()).maxWidth(quoteWidth);
        var quotes = quoteColumns
            ? UiControls.row()
            : UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(2);
        if (quoteColumns) {
            this.buy.horizontalSizing(Sizing.expand(33));
            this.sell.horizontalSizing(Sizing.expand(33));
            this.spread.horizontalSizing(Sizing.expand(33));
        }
        if (quoteColumns) {
            quotes.child(this.buy).child(this.spread).child(this.sell);
        } else {
            quotes.child(this.buy).child(this.sell).child(this.spread);
        }
        panel.child(quotes);
        this.coflnet = UiControls.button("View on Coflnet", () -> this.showModal(Modal.Link));
        panel.child(UiControls.row(UiControls.tab("History", () -> this.switchTab(false), !this.orderBook),
            UiControls.tab("Order Book", () -> this.switchTab(true), this.orderBook)));
        this.buildToolbar(panel);
        this.historyStatus = UiControls.text("", UiStyles.palette().muted()).maxWidth(this.bodyWidth);
        this.mayorStatus = UiControls.text("", UiStyles.palette().muted()).maxWidth(this.bodyWidth);
        panel.child(this.historyStatus);
        panel.child(this.mayorStatus);
        var body = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        if (this.orderBook) {
            this.book.layoutFor(this.bodyWidth, this.bodyHeight);
            body.child(this.book);
        } else {
            this.history.layoutFor(this.bodyWidth);
            body.child(this.history);
        }
        this.source = UiControls.text("", UiStyles.palette().muted()).maxWidth(this.bodyWidth);
        body.gap(6);
        this.bodyScroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100), body);
        this.bodyScroll.scrollbarThiccness(3);
        this.bodyScroll.restoreScrollOffset(this.orderBook ? this.bookOffset : this.historyOffset);
        panel.child(this.bodyScroll);
        var referral = UiControls.row(BazaarUi.item(new ItemStack(Items.GOLD_BLOCK), 14), this.coflnet);
        if (this.bodyWidth >= 420) {
            this.source.maxWidth(this.bodyWidth - 145).horizontalSizing(Sizing.expand(100));
            referral.horizontalSizing(Sizing.fixed(139));
            panel.child(UiControls.row(this.source, referral));
        } else {
            panel.child(this.source);
            panel.child(referral);
        }
        this.feedbackLabel = UiControls.text(this.feedback, UiStyles.palette().label()).maxWidth(this.bodyWidth);
        panel.child(this.feedbackLabel);
        this.updateFeedback();
    }

    private void buildToolbar(FlowLayout panel) {
        if (!this.orderBook) {
            if (this.bodyWidth < 330 || this.height < 300) {
                this.rangeMenu = UiControls.button("Range", () -> this.showModal(Modal.Range));
                panel.child(this.rangeMenu);
            } else {
                var presets = UiControls.row();
                for (var range : ItemInfoRange.values()) {
                    var button = UiControls.button(range.name(), () -> {
                        if (range == ItemInfoRange.Custom) {
                            this.showModal(Modal.Custom);
                        } else {
                            this.config.range = range;
                            this.save.run();
                            this.session.range(range);
                        }
                    });
                    button.horizontalSizing(Sizing.expand(16));
                    this.ranges.put(range, button);
                    presets.child(button);
                }
                panel.child(presets);
            }
        }
        var actions = UiControls.row();
        if (!this.orderBook) {
            actions
                .child(UiControls.button("+", () -> this.viewport.zoom(1.5, 0.5)).horizontalSizing(Sizing.fixed(20)));
            actions.child(
                UiControls.button("-", () -> this.viewport.zoom(1 / 1.5, 0.5)).horizontalSizing(Sizing.fixed(20)));
            actions.child(UiControls.button("Reset", this.viewport::reset).horizontalSizing(Sizing.fixed(40)));
            if (this.bodyWidth < 220) {
                panel.child(actions);
                actions = UiControls.row();
            }
        }
        actions.child(UiControls.button(this.orderBook ? "Refresh reference" : "Refresh", this.session::refresh)
            .horizontalSizing(Sizing.fixed(this.orderBook ? 104 : 50)));
        actions.child(
            UiControls.button("Display", () -> this.showModal(Modal.Display)).horizontalSizing(Sizing.fixed(50)));
        panel.child(actions);
    }

    private void buildModal(FlowLayout panel) {
        String title = switch (this.modal) {
            case Search -> "Find a product";
            case Display -> "Display";
            case Custom -> "Custom range (UTC)";
            case Quantity -> "What quantity means";
            case Link -> "Open Coflnet";
            case Range -> "History range";
            case None -> "Item Info";
        };
        panel.child(UiControls.row(UiControls.text(title, UiStyles.palette().primary()), BazaarUi.spacer(),
            UiControls.button("Close", this::closeModal)));
        var content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.gap(7);
        this.modalScroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100), content);
        this.modalScroll.scrollbarThiccness(3);
        this.modalScroll.restoreScrollOffset(this.modalOffset);
        panel.child(this.modalScroll);
        switch (this.modal) {
            case Search -> {
                this.search = new ProductSearchControls(this.market,
                    query -> this.market.searchIndexedProducts(query, 12), () -> "", this.bodyWidth,
                    Math.max(60, this.height - 120), this.searchQuery, query -> this.searchQuery = query,
                    product -> this.defer(() -> this.selectProduct(ProductIdentity.fromIndex(product))));
                content.child(this.search);
                var controls = this.search;
                this.defer(() -> {
                    if (this.search == controls) {
                        if (this.searchState != null) {
                            controls.restoreViewState(this.searchState);
                        } else {
                            controls.focusSearch();
                        }
                    }
                });
            }
            case Display -> {
                this.toggle(content, "Price bands", () -> this.config.showBands,
                    value -> this.config.showBands = value);
                this.toggle(content, "Mayor timeline", () -> this.config.showMayors, value -> {
                    this.config.showMayors = value;
                    this.session.mayorVisible(value);
                });
                this.toggle(content, "Quantity chart", () -> this.config.showQuantity,
                    value -> this.config.showQuantity = value);
                this.toggle(content, "Cumulative items", () -> this.config.showCumulative,
                    value -> this.config.showCumulative = value);
                this.toggle(content, "Order counts", () -> this.config.showOrders,
                    value -> this.config.showOrders = value);
                this.toggle(content, "Relative item bars", () -> this.config.showBars,
                    value -> this.config.showBars = value);
            }
            case Custom -> {
                this.paragraph(content, "Use yyyy-MM-dd HH:mm in UTC");
                content.child(UiControls.text("Start", UiStyles.palette().label()));
                this.startBox = UIComponents.textBox(Sizing.fill(100)).text(this.customStart);
                this.startBox.onChanged().subscribe(value -> this.customStart = value);
                content.child(this.startBox);
                content.child(UiControls.text("End", UiStyles.palette().label()));
                this.endBox = UIComponents.textBox(Sizing.fill(100)).text(this.customEnd);
                this.endBox.onChanged().subscribe(value -> this.customEnd = value);
                content.child(this.endBox);
                content.child(UiControls.button("Apply", this::applyCustom));
                this.defer(() -> {
                    var target = this.focusEnd ? this.endBox : this.startBox;
                    if (this.modal == Modal.Custom && (this.focusStart || this.focusEnd)
                        && target != null
                        && target.focusHandler() != null) {
                        target.focusHandler().focus(target, UIComponent.FocusSource.MOUSE_CLICK);
                    }
                });
            }
            case Quantity -> {
                this.paragraph(content,
                    "Open-order quantity: Items waiting in outstanding orders. "
                        + "Changes include placements, fills and cancellations.");
                this.paragraph(content,
                    "7-day moving volume: Activity across the preceding seven days at each timestamp, "
                        + "including Hypixel's live-state component. Older activity leaves the window continuously.");
                this.paragraph(content, "Neither measures exact trades per interval.");
            }
            case Link -> {
                this.paragraph(content, "Open sky.coflnet.com in your browser?");
                content.child(UiControls.button("Open website", this::openLink));
            }
            case Range -> {
                for (var range : ItemInfoRange.values()) {
                    content.child(UiControls.button(range.name(), () -> this.defer(() -> {
                        if (range == ItemInfoRange.Custom) {
                            this.showModal(Modal.Custom);
                        } else {
                            this.config.range = range;
                            this.save.run();
                            this.session.range(range);
                            this.closeModal();
                        }
                    })));
                }
            }
            case None -> {
            }
        }
        this.feedbackLabel = UiControls.text(this.feedback, UiStyles.palette().label()).maxWidth(this.bodyWidth);
        panel.child(this.feedbackLabel);
        this.updateFeedback();
    }

    private void paragraph(FlowLayout content, String value) {
        content.child(UiControls.text(value, UiStyles.palette().label()).maxWidth(this.bodyWidth));
    }

    private void toggle(FlowLayout content, String label, BooleanSupplier value, Consumer<Boolean> set) {
        var button = UiControls.button(label + ": " + (value.getAsBoolean() ? "On" : "Off"), () -> {});
        button.onPress(_ -> this.defer(() -> {
            set.accept(!value.getAsBoolean());
            this.save.run();
            this.history.refreshPreferences();
            this.book.refresh();
            button.setMessage(Component.literal(label + ": " + (value.getAsBoolean() ? "On" : "Off")));
            button.renderer(UiControls.buttonRenderer(false, value.getAsBoolean()));
        }));
        button.renderer(UiControls.buttonRenderer(false, value.getAsBoolean()));
        content.child(button);
    }

    private void selectProduct(ProductIdentity product) {
        this.session.select(product);
        this.viewport.clearPin();
        this.historyOffset = 0;
        this.bookOffset = 0;
        this.book.restoreViewState(new OrderBookPanel.ViewState(0, 0));
        this.closeModal();
    }

    private void applyCustom() {
        var parsed = Try.of(() -> {
            var start = LocalDateTime.parse(this.customStart, UTC).toInstant(ZoneOffset.UTC);
            var end = LocalDateTime.parse(this.customEnd, UTC).toInstant(ZoneOffset.UTC);
            if (!start.isBefore(end) || end.isAfter(Instant.now())) {
                throw new IllegalArgumentException("Invalid range");
            }
            return new com.github.lutzluca.coflnet.HistoryQuery(start, end);
        });
        if (parsed.isFailure()) {
            this.feedback = "Enter valid UTC dates with start before end and no future end";
            this.updateFeedback();
            return;
        }
        this.config.range = ItemInfoRange.Custom;
        this.save.run();
        this.session.customRange(parsed.get().start(), parsed.get().end());
        this.closeModal();
    }

    private void openLink() {
        var product = this.session.data().product();
        if (!this.session.isCurrent() || product == null || product.bazaarProductId().isEmpty()) {
            return;
        }
        var result = Try.run(() -> Util.getPlatform().openUri(URI.create("https://sky.coflnet.com/item/"
            + URLEncoder.encode(product.bazaarProductId().orElseThrow(), StandardCharsets.UTF_8))));
        if (result.isFailure()) {
            this.feedback = "Could not open your browser";
            this.updateFeedback();
        } else {
            this.closeModal();
        }
    }

    private void refreshData() {
        if (this.rebuilding || this.uiAdapter == null || !this.session.isCurrent()) {
            return;
        }
        var data = this.session.data();
        this.history.update(data);
        this.book.update(data.live());
        if (this.search != null) {
            this.search.refresh(false);
        }
        if (this.modal != Modal.None) {
            return;
        }
        if (!Objects.equals(this.headerProduct, data.product()) && this.productHeader != null) {
            this.headerProduct = data.product();
            this.productHeader.clearChildren();
            if (data.product() == null) {
                this.productHeader.child(UiControls.text("Item Info", UiStyles.palette().primary()));
            } else {
                this.market.productStack(data.product())
                    .ifPresent(stack -> this.productHeader.child(BazaarUi.item(stack, 18)));
                var name = UiControls.text("", UiStyles.palette().primary())
                    .maxWidth(Math.max(40, this.bodyWidth - 100));
                name.text(GameUtils.legacyFormattedComponent(data.product().visualName()));
                name.tooltip(Component.literal(data.product().bazaarProductId().orElse(data.product().strippedName())));
                this.productHeader.child(name);
            }
        }
        Double buyPrice = data.live().flatMap(value -> value.buyPrice()).orElse(null);
        Double sellPrice = data.live().flatMap(value -> value.sellPrice()).orElse(null);
        this.cacheAverages(data);
        boolean compact = this.height < 300;
        this.buy.text(Component.literal("Buy Price " + HistoryAnalysis.exact(buyPrice) + (compact
            ? "" : "\n"
                + this.average(buyPrice, this.buyAverage))));
        this.sell.text(Component.literal("Sell Price " + HistoryAnalysis.exact(sellPrice) + (compact
            ? "" : "\n"
                + this.average(sellPrice, this.sellAverage))));
        this.buy.tooltip(Component.literal(this.referenceDetail(data, buyPrice, this.buyAverage)));
        this.sell.tooltip(Component.literal(this.referenceDetail(data, sellPrice, this.sellAverage)));
        Double difference = buyPrice == null || sellPrice == null ? null : buyPrice - sellPrice;
        String percent = difference == null || buyPrice == 0
            ? "Unavailable"
            : String.format(java.util.Locale.ROOT, "%.1f%%", difference / buyPrice * 100);
        this.spread.text(Component.literal("Spread " + HistoryAnalysis.exact(difference)
            + (compact ? " (" + percent + ")" : "\n" + percent)));
        String historyText = this.orderBook ? "" : this.resultStatus(data.history());
        this.historyStatus.text(Component.literal(historyText));
        this.historyStatus.verticalSizing(historyText.isEmpty() ? Sizing.fixed(0) : Sizing.content());
        String mayorText = !this.orderBook && this.config.showMayors && data.mayors().failure() != null
            ? "Mayor timeline unavailable. Refresh to retry." : "";
        this.mayorStatus.text(Component.literal(mayorText));
        this.mayorStatus.verticalSizing(mayorText.isEmpty() ? Sizing.fixed(0) : Sizing.content());
        String hypixel = data.live().flatMap(value -> value.sourceUpdatedAt()).map(value -> "Hypixel " + age(value))
            .orElse("Hypixel timestamp unavailable");
        var historyData = data.history().value() == null ? data.reference().value() : data.history().value();
        String coflnetText = historyData == null
            ? "Coflnet history" : "Coflnet checked " + age(historyData.checkedAt())
                + ", sample " + age(historyData.coverageEnd());
        this.source.text(Component.literal(hypixel + "\n" + coflnetText));
        this.coflnet.active(data.product() != null && data.product().bazaarProductId().isPresent());
        this.ranges
            .forEach((range, button) -> button.renderer(UiControls.buttonRenderer(false, range == data.range())));
        if (this.rangeMenu != null) {
            this.rangeMenu.setMessage(Component.literal("Range: " + data.range().name()));
        }
    }

    private void cacheAverages(ItemInfoSession.Data data) {
        var reference = data.reference().value();
        var query = data.reference().query();
        if (reference == this.averageHistory && Objects.equals(query, this.averageQuery)) {
            return;
        }
        this.averageHistory = reference;
        this.averageQuery = query;
        this.buyAverage = reference == null || query == null
            ? null
            : HistoryAnalysis.stats(reference.points(), query.start(), query.end(), true)
                .map(HistoryAnalysis.Stats::average).orElse(null);
        this.sellAverage = reference == null || query == null
            ? null
            : HistoryAnalysis.stats(reference.points(), query.start(), query.end(), false)
                .map(HistoryAnalysis.Stats::average).orElse(null);
    }

    private String average(Double current, Double average) {
        String comparison = current == null || average == null || average == 0
            ? "comparison unavailable"
            : String.format(java.util.Locale.ROOT, "%+.1f%%", (current - average) / average * 100);
        return "7d sample avg " + HistoryAnalysis.exact(average) + " (" + comparison + ")";
    }

    private String referenceDetail(ItemInfoSession.Data data, Double price, Double average) {
        var result = data.reference();
        String detail = this.average(price, average) + "\nReturned samples have equal weight.";
        if (result.query() != null) {
            detail += "\n" + UTC.format(result.query().start()) + " to " + UTC.format(result.query().end()) + " UTC";
        }
        if (result.value() != null) {
            detail += "\nCoflnet checked " + age(result.value().checkedAt());
        }
        if (result.updating()) {
            detail += "\nUpdating reference...";
        } else if (result.failure() != null) {
            detail += "\nReference refresh unavailable.";
        }
        return detail;
    }

    private String resultStatus(ItemInfoSession.Result<?> result) {
        if (result.updating()) {
            return result.value() == null ? "Loading Coflnet history..." : "Updating Coflnet history...";
        }
        if (result.failure() != null) {
            return result.value() == null
                ? "Coflnet history unavailable. Refresh to retry."
                : "Refresh failed. Showing retained Coflnet history.";
        }
        return this.session.data().history().value() != null && this.session.data().history().value().points().isEmpty()
            ? "No history samples in this range" : "";
    }

    private static String age(@Nullable Instant time) {
        if (time == null) {
            return "unavailable";
        }
        long seconds = Math.max(0, Duration.between(time, Instant.now()).getSeconds());
        return seconds < 60
            ? seconds + "s ago" : seconds < 3600
                ? seconds / 60 + "m ago"
                : seconds < 86400 ? seconds / 3600 + "h ago" : seconds / 86400 + "d ago";
    }

    private void updateFeedback() {
        if (this.feedbackLabel != null) {
            this.feedbackLabel.text(Component.literal(this.feedback));
            this.feedbackLabel.verticalSizing(this.feedback.isEmpty() ? Sizing.fixed(0) : Sizing.content());
        }
    }

    private void switchTab(boolean book) {
        this.defer(() -> {
            if (this.orderBook == book) {
                return;
            }
            this.captureView();
            this.orderBook = book;
            this.session.historyVisible(!book);
            this.rebuild();
        });
    }

    private void showModal(Modal modal) {
        this.defer(() -> {
            this.captureView();
            this.modal = modal;
            this.modalOffset = 0;
            this.feedback = "";
            if (modal == Modal.Custom) {
                this.focusStart = true;
                this.focusEnd = false;
            }
            this.rebuild();
        });
    }

    private void closeModal() {
        this.defer(() -> {
            if (this.modal == Modal.Search && this.session.data().product() == null) {
                this.onClose();
                return;
            }
            this.captureView();
            this.modal = Modal.None;
            this.feedback = "";
            this.rebuild();
        });
    }

    private void captureView() {
        if (this.bodyScroll != null) {
            if (this.orderBook) {
                this.bookOffset = this.bodyScroll.savedScrollOffset();
            } else {
                this.historyOffset = this.bodyScroll.savedScrollOffset();
            }
        }
        if (this.modalScroll != null) {
            this.modalOffset = this.modalScroll.savedScrollOffset();
        }
        if (this.search != null) {
            this.searchState = this.search.saveViewState();
        }
        if (this.modal == Modal.Custom && this.uiAdapter != null) {
            var handler = this.uiAdapter.rootComponent.focusHandler();
            if (handler != null) {
                this.focusStart = handler.focused() == this.startBox;
                this.focusEnd = handler.focused() == this.endBox;
            }
        }
    }

    private void rebuild() {
        if (this.uiAdapter == null || !this.session.isCurrent() || GameUtils.screen() != this) {
            return;
        }
        var root = this.uiAdapter.rootComponent;
        if (root.focusHandler() != null) {
            root.focusHandler().focus(null, UIComponent.FocusSource.MOUSE_CLICK);
        }
        root.clearChildren();
        this.build(root);
    }

    private void defer(Runnable action) {
        this.deferred.add(action);
    }

    private void runDeferred() {
        if (this.uiAdapter == null || this.uiAdapter.rootComponent.focusHandler() == null) {
            return;
        }
        // Builds can schedule focus before mount or enqueue another action during a rebuild.
        var actions = List.copyOf(this.deferred);
        this.deferred.clear();
        for (var action : actions) {
            if (this.session.isCurrent() && GameUtils.screen() == this) {
                action.run();
            }
        }
    }

    @Override
    public void resize(int width, int height) {
        this.captureView();
        super.resize(width, height);
        this.rebuild();
    }

    @Override
    public void tick() {
        if (!this.session.isCurrent()) {
            this.session.close();
            GameUtils.setScreen(null);
            return;
        }
        super.tick();
        this.runDeferred();
        this.history.tick();
        if (++this.ageTicks >= 20) {
            this.ageTicks = 0;
            this.refreshData();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.modal != Modal.None) {
            if (event.isEscape()) {
                this.closeModal();
                return true;
            }
            var selected = this.search == null ? null : this.search.selectionFor(event);
            if (selected != null) {
                this.defer(() -> this.selectProduct(ProductIdentity.fromIndex(selected)));
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Screen target = null;
        if (this.session.isCurrent() && !(this.parent instanceof ChatScreen)
            && !(this.parent instanceof ItemInfoScreen)) {
            target = this.parent;
            if (target instanceof AbstractContainerScreen<?> container
                && (Minecraft.getInstance().player == null
                    || Minecraft.getInstance().player.containerMenu != container.getMenu())) {
                target = null;
            }
        }
        this.session.close();
        GameUtils.setScreen(target);
    }

    @Override
    public void removed() {
        this.deferred.clear();
        this.session.close();
        super.removed();
    }

    private enum Modal {
        None, Search, Display, Custom, Quantity, Link, Range
    }
}
