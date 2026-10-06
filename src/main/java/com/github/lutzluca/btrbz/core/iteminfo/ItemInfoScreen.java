package com.github.lutzluca.btrbz.core.iteminfo;

import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryAnalysis;
import com.github.lutzluca.btrbz.core.iteminfo.charts.HistoryViewport;
import com.github.lutzluca.btrbz.core.ui.ProductSearchControls;
import com.github.lutzluca.btrbz.core.ui.UiControls;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetTooltips;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.coflnet.HistoryQuery;
import com.github.lutzluca.coflnet.HistoryResponse;
import com.mojang.blaze3d.platform.InputConstants;
import io.vavr.control.Try;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Positioning;
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
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    private final LiveQuoteStrip quotes = new LiveQuoteStrip();
    private @Nullable LabelComponent source;
    private @Nullable FlowLayout productHeader;
    private @Nullable ButtonComponent coflnet;
    private @Nullable ProductIdentity headerProduct;
    private boolean rebuilding;
    private @Nullable FlowLayout popover;
    private @Nullable UIComponent popoverAnchor;
    private @Nullable UIComponent previousFocus;
    private int feedbackTicks;
    private int bodyWidth;
    private int bodyHeight;
    private int panelHeight;
    private int ageTicks;
    private boolean focusStart;
    private boolean focusEnd;
    private @Nullable HistoryResponse averageHistory;
    private @Nullable HistoryQuery averageQuery;
    private @Nullable Double buyAverage;
    private @Nullable Double sellAverage;
    private @Nullable ButtonComponent rangeMenu;
    private @Nullable ButtonComponent tableOptions;
    private @Nullable FlowLayout mainPanel;
    private boolean keyboardInput;

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
        this.history = new HistoryPanel(config, save, this.viewport, this::showPopover, this.session::mayorVisible);
        this.book = new OrderBookPanel(config, price -> {
            if (this.session.isCurrent() && this.session.data().live().isPresent()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(price);
                this.feedback = "Price copied";
                this.feedbackTicks = 40;
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
        this.layoutViews();
    }

    @Override
    protected void build(FlowLayout root) {
        this.rebuilding = true;
        root.surface(Surface.flat(0x90000000));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        int panelWidth = Math.max(160, Math.min(720, this.width - (this.width >= 800 ? 64 : 24)));
        this.panelHeight = Math.max(100, Math.min(420, this.height - (this.height >= 400 ? 48 : 16)));
        this.bodyWidth = panelWidth - 20;
        this.bodyHeight = Math.max(70, this.panelHeight - 160);
        this.search = null;
        this.startBox = null;
        this.endBox = null;
        this.modalScroll = null;
        this.bodyScroll = null;
        this.feedbackLabel = null;
        this.ranges.clear();
        this.rangeMenu = null;
        this.tableOptions = null;
        var panel = UIContainers.verticalFlow(Sizing.fixed(panelWidth), Sizing.fixed(this.panelHeight));
        panel.padding(Insets.of(10));
        panel.gap(4);
        panel.surface(WidgetSurfaces.roundedPanel(0xFC181B20, 6));
        this.mainPanel = panel;
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
        this.quotes.margins(Insets.of(8, 4, 0, 0));
        this.quotes.layoutFor(this.bodyWidth);
        panel.child(this.quotes);
        this.coflnet = UiControls.iconButton("View on Coflnet", new ItemStack(Items.GOLD_BLOCK),
            () -> this.showModal(Modal.Link));
        var tabs = UiControls.row(UiControls.tab("History", () -> this.switchTab(false), !this.orderBook),
            UiControls.tab("Order Book", () -> this.switchTab(true), this.orderBook));
        if (this.orderBook) {
            this.tableOptions = UiControls.button(this.bodyWidth < 300 ? "Options" : "Table options",
                this::showTableOptions);
            this.tableOptions.horizontalSizing(Sizing.content(28));
            this.tableOptions.renderer((graphics, button, delta) -> {
                UiControls.quietRenderer().draw(graphics, button, delta);
                int x = button.getX() + button.getWidth() - 9;
                int y = button.getY() + button.getHeight() / 2 - 1;
                graphics.fill(x - 2, y, x + 3, y + 1, UiStyles.palette().muted());
                graphics.fill(x - 1, y + 1, x + 2, y + 2, UiStyles.palette().muted());
                graphics.fill(x, y + 2, x + 1, y + 3, UiStyles.palette().muted());
            });
            tabs.child(BazaarUi.spacer()).child(this.tableOptions);
        }
        panel.child(tabs);
        this.buildToolbar(panel);
        var body = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        if (this.orderBook) {
            this.book.layoutFor(this.bodyWidth, this.bodyHeight);
            body.child(this.book);
        } else {
            this.history.layoutFor(this.bodyWidth, this.bodyHeight);
            body.child(this.history);
        }
        this.source = UiControls.text("", UiStyles.palette().muted()).maxWidth(this.bodyWidth);
        this.source.verticalSizing(Sizing.fixed(20));
        this.bodyScroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100), body);
        this.bodyScroll.scrollbarThiccness(3);
        this.bodyScroll.restoreScrollOffset(this.orderBook ? this.bookOffset : this.historyOffset);
        panel.child(this.bodyScroll);
        this.feedbackLabel = UiControls.text(this.feedback, UiStyles.palette().muted());
        int feedbackWidth = Minecraft.getInstance().font.width("Price copied");
        this.feedbackLabel.sizing(Sizing.fixed(feedbackWidth), Sizing.fixed(9));
        if (this.bodyWidth >= 420) {
            int linkWidth = Minecraft.getInstance().font.width(this.coflnet.getMessage()) + 36;
            this.source.maxWidth(this.bodyWidth - linkWidth - feedbackWidth - 12)
                .horizontalSizing(Sizing.expand(100));
            panel.child(UiControls.row(this.source, this.feedbackLabel, this.coflnet));
        } else {
            this.source.horizontalSizing(Sizing.fill(100));
            panel.child(this.source);
            panel.child(UiControls.row(this.feedbackLabel, BazaarUi.spacer(), this.coflnet));
        }
        this.updateFeedback();
    }

    private void buildToolbar(FlowLayout panel) {
        if (this.orderBook) {
            return;
        }
        var toolbar = UiControls.row();
        toolbar.gap(3);
        if (this.bodyWidth < 360) {
            this.rangeMenu = UiControls.button("Range: " + this.session.data().range().name(), () -> {});
            this.rangeMenu.renderer(UiControls.segmentRenderer(true));
            this.rangeMenu.onPress(_ -> this.showPopover(this.rangeMenu, content -> {
                content.child(UiControls.text("History range", UiStyles.palette().primary()));
                for (var range : ItemInfoRange.values()) {
                    content.child(UiControls.button(range.name(), () -> this.selectRange(range))
                        .renderer(UiControls.segmentRenderer(range == this.session.data().range())));
                }
            }));
            toolbar.child(this.rangeMenu);
        } else {
            var presets = UiControls.row();
            presets.horizontalSizing(Sizing.content());
            presets.gap(1);
            for (var range : ItemInfoRange.values()) {
                var button = UiControls.button(range.name(), () -> this.selectRange(range));
                button.verticalSizing(Sizing.fixed(20));
                button.renderer(UiControls.segmentRenderer(range == this.session.data().range()));
                this.ranges.put(range, button);
                presets.child(button);
            }
            toolbar.child(presets);
        }
        var reset = UiControls.button("Reset", this.viewport::reset).renderer(UiControls.quietRenderer());
        var refresh = UiControls.button("Refresh", this.session::refresh).renderer(UiControls.quietRenderer());
        reset.verticalSizing(Sizing.fixed(20));
        refresh.verticalSizing(Sizing.fixed(20));
        if (this.bodyWidth >= 440 || this.rangeMenu != null) {
            toolbar.child(BazaarUi.spacer()).child(reset).child(refresh);
            panel.child(toolbar);
        } else {
            panel.child(toolbar);
            panel.child(UiControls.row(reset, refresh));
        }
    }

    private void selectRange(ItemInfoRange range) {
        this.defer(() -> {
            this.closePopover();
            if (range == ItemInfoRange.Custom) {
                this.showModal(Modal.Custom);
            } else {
                this.config.range = range;
                this.save.run();
                this.session.range(range);
            }
        });
    }

    private void layoutViews() {
        if (this.modal != Modal.None || this.bodyScroll == null || this.bodyScroll.width() <= 0) {
            return;
        }
        if (this.orderBook) {
            this.book.layoutFor(this.bodyScroll.width(), this.bodyScroll.height());
        } else {
            this.history.layoutFor(this.bodyScroll.width(), this.bodyScroll.height());
        }
    }

    private void showTableOptions() {
        var anchor = this.tableOptions;
        boolean keyboard = this.keyboardInput;
        this.defer(() -> {
            if (anchor == null || anchor != this.tableOptions || this.mainPanel == null) {
                return;
            }
            if (this.popoverAnchor == anchor) {
                this.closePopover();
                return;
            }
            this.closePopover();
            var root = this.uiAdapter.rootComponent;
            var handler = root.focusHandler();
            this.previousFocus = anchor;
            var cumulative = this.tablePreference("Cumulative items", this.config.showCumulative,
                value -> this.config.showCumulative = value);
            var orders = this.tablePreference("Order counts", this.config.showOrders,
                value -> this.config.showOrders = value);
            var bars = this.tablePreference("Item bars", this.config.showBars, value -> this.config.showBars = value);
            var content = UIContainers.verticalFlow(Sizing.content(), Sizing.content()).gap(5);
            content.children(List.of(cumulative, orders, bars));
            int leftBound = this.mainPanel.x() + 8;
            int rightBound = this.mainPanel.x() + this.mainPanel.width() - 8;
            int topBound = this.mainPanel.y() + 8;
            int bottomBound = this.mainPanel.y() + this.mainPanel.height() - 8;
            content.inflate(Size.of(rightBound - leftBound - 16, bottomBound - topBound - 16));
            int width = Math.min(rightBound - leftBound, Math.max(180, content.width() + 16));
            var panel = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content());
            panel.padding(Insets.of(8));
            panel.surface(WidgetSurfaces.roundedPanel(0xFF24282E, 4));
            panel.child(content);
            panel.inflate(Size.of(width, bottomBound - topBound));
            int left = Math.clamp(anchor.x() + anchor.width() - width, leftBound, rightBound - width);
            int top = anchor.y() + anchor.height() + 3;
            if (top + panel.height() > bottomBound) {
                top = anchor.y() - panel.height() - 3;
            }
            top = Math.clamp(top, topBound, Math.max(topBound, bottomBound - panel.height()));
            panel.positioning(Positioning.absolute(left, top));
            this.popover = panel;
            this.popoverAnchor = anchor;
            root.child(panel);
            if (keyboard && handler != null) {
                handler.focus(cumulative, UIComponent.FocusSource.KEYBOARD_CYCLE);
            }
        });
    }

    private SmallCheckboxComponent tablePreference(String label, boolean checked, Consumer<Boolean> write) {
        var checkbox = UIComponents.smallCheckbox(Component.literal(label).withColor(UiStyles.palette().label()));
        checkbox.checked(checked);
        checkbox.onChanged().subscribe(value -> {
            write.accept(value);
            this.save.run();
            this.defer(this.book::refresh);
        });
        return checkbox;
    }

    private void showPopover(UIComponent anchor, Consumer<FlowLayout> build) {
        this.defer(() -> {
            if (this.popoverAnchor == anchor) {
                this.closePopover();
                return;
            }
            this.closePopover();
            var root = this.uiAdapter.rootComponent;
            var handler = root.focusHandler();
            this.previousFocus = handler == null ? null : handler.focused();
            int width = Math.min(286, this.width - 16);
            int maximumHeight = Math.min(300, this.height - 16);
            var content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(6);
            build.accept(content);
            var panel = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content()).gap(4);
            panel.padding(Insets.of(8));
            panel.surface(WidgetSurfaces.roundedPanel(0xFF24282E, 4));
            var close = UiControls.button("Close", () -> this.defer(this::closePopover));
            close.renderer(UiControls.quietRenderer());
            close.verticalSizing(Sizing.fixed(18));
            panel.child(close);
            panel.child(content);
            panel.inflate(Size.of(width, maximumHeight));
            if (panel.height() > maximumHeight) {
                panel.removeChild(content);
                panel.verticalSizing(Sizing.fixed(maximumHeight));
                var scroll = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100), content);
                scroll.scrollbarThiccness(3);
                panel.child(scroll);
                panel.inflate(Size.of(width, maximumHeight));
            }
            int left = Math.clamp(anchor.x() + anchor.width() - width, 8, Math.max(8, this.width - width - 8));
            int top = anchor.y() + anchor.height() + 4;
            if (top + panel.height() > this.height - 8) {
                top = anchor.y() - panel.height() - 4;
            }
            top = Math.clamp(top, 8, Math.max(8, this.height - panel.height() - 8));
            panel.positioning(Positioning.absolute(left, top));
            this.popover = panel;
            this.popoverAnchor = anchor;
            root.child(panel);
            if (handler != null) {
                handler.focus(close, UIComponent.FocusSource.KEYBOARD_CYCLE);
            }
        });
    }

    private void closePopover() {
        if (this.popover == null || this.uiAdapter == null) {
            return;
        }
        var root = this.uiAdapter.rootComponent;
        var handler = root.focusHandler();
        if (handler != null) {
            handler.focus(this.previousFocus != null && this.previousFocus.focusHandler() == handler
                ? this.previousFocus : null,
                this.popoverAnchor == this.tableOptions && !this.keyboardInput
                    ? UIComponent.FocusSource.MOUSE_CLICK : UIComponent.FocusSource.KEYBOARD_CYCLE);
        }
        root.removeChild(this.popover);
        this.popover = null;
        this.popoverAnchor = null;
        this.previousFocus = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        this.keyboardInput = false;
        if (this.popover != null && !this.popover.isInBoundingBox(click.x(), click.y())) {
            this.closePopover();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void buildModal(FlowLayout panel) {
        String title = switch (this.modal) {
            case Search -> "Find a product";
            case Custom -> "Custom range (UTC)";
            case Link -> "Open Coflnet";
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
                    Math.max(60, this.panelHeight - 100), this.searchQuery, query -> this.searchQuery = query,
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
            case Link -> {
                this.paragraph(content, "Open sky.coflnet.com in your browser?");
                content.child(UiControls.button("Open website", this::openLink));
            }
            case None -> {
            }
        }
        this.feedbackLabel = UiControls.text(this.feedback, UiStyles.palette().muted()).maxWidth(this.bodyWidth);
        panel.child(this.feedbackLabel);
        this.updateFeedback();
    }

    private void paragraph(FlowLayout content, String value) {
        content.child(UiControls.text(value, UiStyles.palette().label()).maxWidth(this.bodyWidth));
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
        String hypixel = data.live().flatMap(value -> value.sourceUpdatedAt()).map(value -> "Hypixel " + age(value))
            .orElse("Hypixel unavailable");
        this.quotes.update(buyPrice, sellPrice, this.buyAverage, this.sellAverage, hypixel,
            this.referenceDetail(data, buyPrice, this.buyAverage, true),
            this.referenceDetail(data, sellPrice, this.sellAverage, false));
        var historyData = data.history().value() == null ? data.reference().value() : data.history().value();
        String coflnetText = historyData == null
            ? "Coflnet history" : "Coflnet checked " + age(historyData.checkedAt())
                + ", sample " + age(historyData.coverageEnd());
        String status = this.orderBook ? "" : this.resultStatus(data.history());
        if (status.isEmpty() && this.config.showMayors && data.mayors().failure() != null) {
            status = "Mayor timeline unavailable";
        }
        this.source.text(Component.literal(coflnetText + (status.isEmpty() ? "" : "\n" + status)));
        this.source.tooltip(WidgetTooltips.wrapped(List.of(Component.literal(coflnetText),
            Component.literal(status), Component.literal("History provided by Coflnet"))));
        this.coflnet.active(data.product() != null && data.product().bazaarProductId().isPresent());
        this.ranges
            .forEach((range, button) -> button.renderer(UiControls.segmentRenderer(range == data.range())));
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

    private List<Component> referenceDetail(ItemInfoSession.Data data, Double price, Double average, boolean buy) {
        var result = data.reference();
        var palette = UiStyles.palette();
        var detail = new ArrayList<Component>();
        detail.add(Component.literal("Compared with the 7-day average").withColor(palette.primary()));
        detail.add(Component.literal((buy ? "Buy" : "Sell") + " average: ").withColor(palette.muted())
            .append(Component.literal(HistoryAnalysis.exact(average)
                + (average == null || !Double.isFinite(average) ? "" : " coins"))
                .withColor(buy ? palette.buy() : palette.sell())));
        Double percent = LiveQuoteStrip.comparisonPercent(price, average);
        String comparison = percent == null
            ? "Current comparison unavailable"
            : String.format(java.util.Locale.ROOT, "Current: %.1f%% %s average",
                Math.abs(percent), percent >= 0 ? "above" : "below");
        detail.add(Component.literal(comparison).withColor(palette.label()));
        detail.add(Component.literal("Returned Coflnet samples are equally weighted.").withColor(palette.muted()));
        if (result.query() != null) {
            detail.add(Component.literal("Period: " + UTC.format(result.query().start()) + " to "
                + UTC.format(result.query().end()) + " UTC").withColor(palette.muted()));
        }
        if (result.value() != null) {
            detail.add(
                Component.literal("Coflnet checked " + age(result.value().checkedAt())).withColor(palette.muted()));
        }
        if (result.updating()) {
            detail.add(Component.literal("Updating reference...").withColor(palette.muted()));
        } else if (result.failure() != null) {
            detail.add(Component.literal("Reference refresh unavailable.").withColor(palette.muted()));
        }
        return List.copyOf(detail);
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
            if (this.modal != Modal.None) {
                this.feedbackLabel.verticalSizing(Sizing.fixed(this.feedback.isEmpty() ? 0 : 12));
            }
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
            this.feedbackTicks = 0;
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
            this.feedbackTicks = 0;
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
        this.closePopover();
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
        this.closePopover();
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
        this.layoutViews();
        if (this.feedbackTicks > 0 && --this.feedbackTicks == 0) {
            this.feedback = "";
            this.updateFeedback();
        }
        if (++this.ageTicks >= 20) {
            this.ageTicks = 0;
            this.refreshData();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        this.keyboardInput = true;
        if (this.popover != null && event.isEscape()) {
            this.closePopover();
            return true;
        }
        if (this.popover != null && event.key() == InputConstants.KEY_TAB) {
            var targets = new ArrayList<UIComponent>();
            this.collectFocusable(this.popover, targets);
            var handler = this.uiAdapter.rootComponent.focusHandler();
            if (handler != null && !targets.isEmpty()) {
                int current = targets.indexOf(handler.focused());
                int next = current < 0
                    ? (event.hasShiftDown() ? targets.size() - 1 : 0)
                    : Math.floorMod(current + (event.hasShiftDown() ? -1 : 1), targets.size());
                handler.focus(targets.get(next), UIComponent.FocusSource.KEYBOARD_CYCLE);
            }
            return true;
        }
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

    private void collectFocusable(UIComponent component, List<UIComponent> targets) {
        if (component.canFocus(UIComponent.FocusSource.KEYBOARD_CYCLE)) {
            targets.add(component);
        }
        if (component instanceof ParentUIComponent parent) {
            parent.children().forEach(child -> this.collectFocusable(child, targets));
        }
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
        this.closePopover();
        this.deferred.clear();
        this.session.close();
        super.removed();
    }

    private enum Modal {
        None, Search, Custom, Link
    }
}
