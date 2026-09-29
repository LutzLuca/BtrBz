package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;

import com.github.lutzluca.btrbz.Assets;
import com.github.lutzluca.btrbz.core.Activation;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreenController;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.IconButton;
import com.github.lutzluca.btrbz.core.widgets.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.mojang.blaze3d.platform.InputConstants;
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
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Product picking and alert editing share a retained, live market view. */
public final class AlertScreen extends BaseOwoScreen<FlowLayout> {
    private static final int SEARCH_LIMIT = 12;
    private static final int PANEL = 0xEF0C0C0C;
    private static final int CARD = 0xB51A1A1A;
    private static final DateTimeFormatter CAPTURE_TIME = DateTimeFormatter
        .ofPattern("dd MMM, HH:mm:ss").withZone(ZoneId.systemDefault());

    private final @Nullable Screen parent;
    private final BazaarData data;
    private final AlertManager manager;
    private final Activation activation;
    private final OrderBookScreenController orderBook;
    private final long activationGeneration;
    private final @Nullable Object parentLevel;
    private final @Nullable Object parentConnection;
    private final AlertEditorState editor = new AlertEditorState();
    private final List<ActiveQuote> activeQuotes = new ArrayList<>();

    private Tab tab = Tab.Editor;
    private int contentWidth;
    private int productWidth;
    private int settingsWidth;
    private int resultHeight;
    private String message = "";
    private int messageColor = BazaarStyles.SECONDARY_TEXT;
    private List<IndexedProduct> matches = List.of();
    private long marketRevision;
    private long indexRevision;
    private long alertRevision;
    private boolean alertsEnabled;

    private FlowLayout content;
    private RestorableVerticalScrollContainer<FlowLayout> scroller;
    private @Nullable FlowLayout productPane;
    private @Nullable FlowLayout resultRows;
    private @Nullable FlowLayout alertRows;
    private @Nullable TextBoxComponent searchBox;
    private @Nullable TextBoxComponent expressionBox;
    private @Nullable LabelComponent buyQuote;
    private @Nullable LabelComponent sellQuote;
    private @Nullable LabelComponent previewValue;
    private LabelComponent status;
    private LabelComponent feedback;
    private ButtonComponent editorTabButton;
    private ButtonComponent priceModeButton;
    private ButtonComponent liquidityModeButton;
    private ButtonComponent activeTabButton;
    private ButtonComponent reachedTabButton;
    private @Nullable ButtonComponent buyButton;
    private @Nullable ButtonComponent sellButton;
    private @Nullable ButtonComponent belowButton;
    private @Nullable ButtonComponent aboveButton;
    private @Nullable ButtonComponent saveButton;
    private @Nullable TextBoxComponent quantityBox;
    private @Nullable ButtonComponent orderBookButton;

    public AlertScreen(
        @Nullable Screen parent,
        BazaarData data,
        AlertManager manager,
        Activation activation,
        OrderBookScreenController orderBook
    ) {
        super(Component.literal("BtrBz Alerts"));
        this.parent = parent;
        this.data = Objects.requireNonNull(data, "data");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.activation = Objects.requireNonNull(activation, "activation");
        this.orderBook = Objects.requireNonNull(orderBook, "orderBook");
        this.activationGeneration = activation.generation();
        this.parentLevel = Minecraft.getInstance().level;
        this.parentConnection = Minecraft.getInstance().getConnection();
    }

    public void preselectProduct(IndexedProduct product) {
        this.editor.select(this.data.refreshIndexedProduct(Objects.requireNonNull(product, "product")));
        this.tab = Tab.Editor;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        int width = Math.max(200, Math.min(600, this.width - 24));
        int height = Math.max(140, Math.min(366, this.height - 24));
        this.contentWidth = width - 24;
        boolean columns = width >= 450;
        this.productWidth = columns ? (this.contentWidth - 10) * 2 / 5 : this.contentWidth;
        this.settingsWidth = columns ? this.contentWidth - this.productWidth - 10 : this.contentWidth;
        this.resultHeight = Math.max(48, Math.min(110, height - 184));

        root.surface(Surface.flat(0x70000000));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        var panel = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.fixed(height));
        panel.surface(WidgetSurfaces.roundedPanel(PANEL, 6));
        panel.padding(Insets.of(12));
        panel.gap(8);

        var title = text("Alerts", BazaarStyles.PRIMARY_TEXT).shadow(true);
        this.status = text("", BazaarStyles.MUTED_TEXT);
        var close = button("×", this::onClose);
        close.horizontalSizing(Sizing.fixed(22));
        panel.child(row(title, BazaarUi.spacer(), this.status, close));
        this.priceModeButton = button("Price", () -> this.switchMode(Kind.Price));
        this.liquidityModeButton = button("Liquidity", () -> this.switchMode(Kind.Liquidity));
        panel.child(segments(this.priceModeButton, this.liquidityModeButton));
        this.editorTabButton = button("Editor", () -> this.switchTab(Tab.Editor));
        this.activeTabButton = button("Active alerts", () -> this.switchTab(Tab.Active));
        this.reachedTabButton = button("Reached", () -> this.switchTab(Tab.Reached));
        this.editorTabButton.horizontalSizing(Sizing.expand(34));
        this.activeTabButton.horizontalSizing(Sizing.expand(33));
        this.reachedTabButton.horizontalSizing(Sizing.expand(33));
        panel.child(row(this.editorTabButton, this.activeTabButton, this.reachedTabButton));

        this.content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.content.gap(8);
        this.scroller = new RestorableVerticalScrollContainer<>(Sizing.fill(100), Sizing.expand(100), this.content);
        this.scroller.scrollbarThiccness(3);
        panel.child(this.scroller);
        this.feedback = text(this.message, this.messageColor).maxWidth(this.contentWidth);
        this.feedback.verticalSizing(this.message.isEmpty() ? Sizing.fixed(0) : Sizing.content());
        panel.child(this.feedback);
        root.child(panel);
        this.buildContent(columns);
        this.refreshStatus();
        this.captureRevisions();
    }

    private void buildContent(boolean columns) {
        this.content.clearChildren();
        this.activeQuotes.clear();
        this.productPane = null;
        this.resultRows = null;
        this.alertRows = null;
        this.searchBox = null;
        this.expressionBox = null;
        this.buyQuote = null;
        this.sellQuote = null;
        this.previewValue = null;
        this.saveButton = null;
        this.quantityBox = null;
        this.orderBookButton = null;
        this.buyButton = null;
        this.sellButton = null;
        this.belowButton = null;
        this.aboveButton = null;
        if (this.tab != Tab.Editor) {
            var create = button("New alert", () -> {
                this.editor.reset();
                this.message = "";
                this.tab = Tab.Editor;
                this.rebuild();
                this.focusSearch();
            });
            this.content.child(row(text(this.tab == Tab.Reached ? "Last 10 reached alerts" : "Your saved alerts",
                BazaarStyles.SECONDARY_TEXT), BazaarUi.spacer(), create));
            this.alertRows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            this.alertRows.gap(6);
            this.content.child(this.alertRows);
            this.refreshAlertRows();
        } else {
            var workspace = columns
                ? UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
                : UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            workspace.gap(10);
            this.productPane = card(this.productWidth);
            var settings = this.buildSettings();
            workspace.child(this.productPane);
            workspace.child(settings);
            this.content.child(workspace);
            this.rebuildProductPane();
            this.refreshControls();
            this.refreshPreview();
        }
    }

    private FlowLayout buildSettings() {
        var settings = card(this.settingsWidth);
        if (this.editor.mode() == Kind.Price) {
            this.buyButton = button(PriceSource.Buy.label(), () -> this.setSource(PriceSource.Buy));
            this.sellButton = button(PriceSource.Sell.label(), () -> this.setSource(PriceSource.Sell));
            settings.child(segments(this.buyButton, this.sellButton));
            this.belowButton = button("Below", () -> this.setDirection(Direction.Below));
            this.aboveButton = button("Above", () -> this.setDirection(Direction.Above));
            settings.child(segments(this.belowButton, this.aboveButton));
        } else {
            this.buyButton = button("Buy Side", () -> this.setLiquiditySide(LiquiditySide.BuyOrders));
            this.buyButton.tooltip(Component.literal("Buy Side: buy orders you can sell to instantly (Sell Price)."));
            this.sellButton = button("Sell Side", () -> this.setLiquiditySide(LiquiditySide.SellOffers));
            this.sellButton
                .tooltip(Component.literal("Sell Side: sell offers you can buy from instantly (Buy Price)."));
            settings.child(segments(this.buyButton, this.sellButton));
            settings.child(text("Required items", BazaarStyles.SECONDARY_TEXT));
            this.quantityBox = UIComponents.textBox(Sizing.fill(100));
            this.quantityBox.setMaxLength(20);
            this.quantityBox.text(this.editor.quantity());
            this.quantityBox.onChanged().subscribe(value -> {
                this.editor.quantity(value);
                this.clearMessage();
                this.refreshPreview();
            });
            settings.child(this.quantityBox);
        }
        var help = UIComponents.texture(Assets.INFO_ICON, 0, 0, 64, 64, 64, 64).blend(true);
        help.sizing(Sizing.fixed(12));
        help.tooltip(Component.literal("""
            Expressions
            Use +, -, *, / and parentheses ().
            Reference current prices with buy and sell.
            Examples: buy * 1.1, sell - 10k or 2.5m"""));
        settings.child(row(text(this.editor.mode() == Kind.Price ? "Threshold" : "Price limit per item",
            BazaarStyles.SECONDARY_TEXT), help));
        this.expressionBox = UIComponents.textBox(Sizing.fill(100));
        this.expressionBox.setMaxLength(256);
        this.expressionBox.text(this.editor.expression());
        this.expressionBox.onChanged().subscribe(value -> {
            this.editor.expression(value);
            this.clearMessage();
            this.refreshPreview();
        });
        settings.child(this.expressionBox);
        if (this.editor.mode() == Kind.Liquidity) {
            settings.child(text("Hypixel shows only the top 30 orders on each side. "
                + "Orders beyond that limit cannot be checked for liquidity.",
                BazaarStyles.MUTED_TEXT).maxWidth(this.settingsWidth - 20));
        }
        var footer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        footer.gap(7);
        settings.child(footer);
        var preview = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        preview.padding(Insets.of(7));
        preview.gap(4);
        preview.surface(WidgetSurfaces.roundedPanel(0x60000000, 3));
        this.previewValue = text("", BazaarStyles.PRIMARY_TEXT).maxWidth(this.settingsWidth - 30);
        preview.child(this.previewValue);
        footer.child(preview);
        this.saveButton = button(this.editor.editingId() == null ? "Create alert" : "Save changes", this::save);
        this.saveButton.sizing(Sizing.fill(100), Sizing.fixed(24));
        this.saveButton.renderer(buttonRenderer(true, false));
        footer.child(this.saveButton);
        if (this.editor.editingId() != null) {
            footer.child(button("Cancel edit", () -> {
                this.editor.reset();
                this.switchTab(Tab.Active);
            }));
        }
        return settings;
    }

    private void rebuildProductPane() {
        if (this.productPane == null) {
            return;
        }
        this.productPane.clearChildren();
        this.searchBox = null;
        this.resultRows = null;
        this.buyQuote = null;
        this.sellQuote = null;
        this.matches = List.of();
        if (this.editor.searching()) {
            var heading = row(text("Item", BazaarStyles.PRIMARY_TEXT), BazaarUi.spacer());
            if (this.editor.product() != null) {
                heading.child(button("Cancel", () -> this.defer(this::cancelSearch)));
            }
            this.productPane.child(heading);
            this.searchBox = UIComponents.textBox(Sizing.fill(100));
            this.searchBox.setMaxLength(120);
            this.searchBox.text(this.editor.query());
            this.searchBox.onChanged().subscribe(query -> {
                this.editor.query(query);
                this.refreshSearchResults(true);
            });
            this.productPane.child(this.searchBox);
            this.resultRows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            this.resultRows.gap(3);
            var results = UIContainers.verticalScroll(Sizing.fill(100), Sizing.fixed(this.resultHeight),
                this.resultRows);
            results.scrollbarThiccness(3);
            this.productPane.child(results);
            this.refreshSearchResults(true);
        } else {
            this.productPane.child(row(text("Item", BazaarStyles.SECONDARY_TEXT), BazaarUi.spacer(),
                button("Change", () -> this.defer(this::beginSearch))));
            this.productPane.child(new AlertProductRow(this.data, this.editor.product(), this.productWidth - 16,
                false, null));
            this.buyQuote = text("", BazaarStyles.BUY_ACCENT).maxWidth(this.productWidth - 16);
            this.sellQuote = text("", BazaarStyles.SELL_ACCENT).maxWidth(this.productWidth - 16);
            this.productPane.child(this.buyQuote);
            this.productPane.child(this.sellQuote);
            this.orderBookButton = button("Open order book", this::openOrderBook);
            this.orderBookButton.horizontalSizing(Sizing.fill(100));
            String unavailable = this.orderBook.unavailableReason();
            this.orderBookButton.active(unavailable == null);
            if (unavailable != null) {
                this.orderBookButton.tooltip(Component.literal(unavailable));
                this.productPane.child(text(unavailable,
                    BazaarStyles.MUTED_TEXT).maxWidth(this.productWidth - 16));
            }
            this.productPane.child(this.orderBookButton);
            this.refreshQuotes();
        }
    }

    private void openOrderBook() {
        String unavailable = this.orderBook.unavailableReason();
        if (unavailable != null) {
            this.setMessage(unavailable, BazaarStyles.SECONDARY_TEXT);
            return;
        }
        var product = this.editor.product();
        if (product == null || !this.sameSession()) {
            return;
        }
        this.orderBook.open(this, product);
    }

    private void refreshSearchResults(boolean force) {
        if (this.resultRows == null) {
            return;
        }
        var next = this.data.searchProducts(this.editor.query(), SEARCH_LIMIT);
        if (!force && next.equals(this.matches)) {
            return;
        }
        this.matches = next;
        this.resultRows.clearChildren();
        if (next.isEmpty()) {
            String hint = !this.data.hasMarketData()
                ? "Waiting for market data…"
                : this.editor.query().isBlank() ? "" : "No matches. Try a shorter name.";
            if (!hint.isEmpty()) {
                this.resultRows.child(text(hint, BazaarStyles.MUTED_TEXT).maxWidth(this.productWidth - 24));
            }
            return;
        }
        for (var product : next) {
            boolean duplicate = next.stream().filter(other -> other.strippedName()
                .equalsIgnoreCase(product.strippedName())).count() > 1;
            this.resultRows.child(new AlertProductRow(this.data, product, this.productWidth - 20, duplicate,
                () -> this.defer(() -> this.selectProduct(product))));
        }
    }

    private void selectProduct(IndexedProduct product) {
        this.editor.select(this.data.refreshIndexedProduct(product));
        this.clearUiFocus();
        this.clearMessage();
        this.rebuildProductPane();
        this.expressionBox.setValue(this.editor.expression());
        this.refreshControls();
        this.refreshPreview();
    }

    private void beginSearch() {
        this.editor.beginSearch();
        this.clearMessage();
        this.rebuildProductPane();
        this.refreshControls();
        this.refreshPreview();
        this.focusSearch();
    }

    private void cancelSearch() {
        this.editor.cancelSearch();
        this.clearUiFocus();
        this.rebuildProductPane();
        this.refreshControls();
        this.refreshPreview();
    }

    private void setSource(PriceSource source) {
        this.editor.source(source);
        this.clearMessage();
        this.refreshControls();
        this.refreshPreview();
    }

    private void setDirection(Direction direction) {
        this.editor.direction(direction);
        this.clearMessage();
        this.refreshControls();
        this.refreshPreview();
    }

    private void setLiquiditySide(LiquiditySide side) {
        this.editor.liquiditySide(side);
        this.clearMessage();
        this.refreshControls();
        this.refreshPreview();
    }

    private void switchMode(Kind mode) {
        if (this.editor.mode() == mode) {
            return;
        }
        this.editor.mode(mode);
        this.message = "";
        this.rebuild();
    }

    private void refreshControls() {
        boolean selected = this.editor.hasSelection();
        for (var button : new ButtonComponent[]{this.buyButton, this.sellButton, this.belowButton, this.aboveButton}) {
            if (button != null) {
                button.active(selected);
            }
        }
        this.expressionBox.active = selected;
        if (this.quantityBox != null) {
            this.quantityBox.active = selected;
        }
        if (this.editor.mode() == Kind.Price) {
            this.buyButton
                .renderer(buttonRenderer(false, this.editor.source() == PriceSource.Buy, BazaarStyles.BUY_ACCENT));
            this.sellButton
                .renderer(buttonRenderer(false, this.editor.source() == PriceSource.Sell, BazaarStyles.SELL_ACCENT));
            this.buyButton.setMessage(Component.literal(PriceSource.Buy.label()).withColor(BazaarStyles.BUY_ACCENT));
            this.sellButton.setMessage(Component.literal(PriceSource.Sell.label()).withColor(BazaarStyles.SELL_ACCENT));
            this.belowButton.renderer(buttonRenderer(false, this.editor.direction() == Direction.Below));
            this.aboveButton.renderer(buttonRenderer(false, this.editor.direction() == Direction.Above));
        } else {
            this.buyButton.renderer(buttonRenderer(false, this.editor.liquiditySide() == LiquiditySide.BuyOrders,
                BazaarStyles.BUY_ACCENT));
            this.sellButton.renderer(buttonRenderer(false, this.editor.liquiditySide() == LiquiditySide.SellOffers,
                BazaarStyles.SELL_ACCENT));
            this.buyButton.setMessage(Component.literal("Buy Side").withColor(BazaarStyles.BUY_ACCENT));
            this.sellButton.setMessage(Component.literal("Sell Side").withColor(BazaarStyles.SELL_ACCENT));
        }
    }

    private void refreshQuotes() {
        if (this.buyQuote == null || this.editor.product() == null) {
            return;
        }
        var prices = this.data.getMarketPrices(ProductIdentity.fromIndex(this.editor.product()));
        this.buyQuote.text(priceLabel(PriceSource.Buy).append(Component.literal("   "))
            .append(PriceSource.Buy.price(prices).<Component>map(AlertNotice::coins)
                .orElse(Component.literal("unavailable").withColor(BazaarStyles.MUTED_TEXT))));
        this.sellQuote.text(priceLabel(PriceSource.Sell).append(Component.literal("   "))
            .append(PriceSource.Sell.price(prices).<Component>map(AlertNotice::coins)
                .orElse(Component.literal("unavailable").withColor(BazaarStyles.MUTED_TEXT))));
    }

    private Try<Void> validateDraftSelection() {
        if (!this.editor.hasSelection()) {
            return Try.failure(new IllegalArgumentException("Choose an item to continue."));
        }
        if (this.editor.editingId() != null && this.manager.alerts().stream()
            .noneMatch(alert -> alert.id.equals(this.editor.editingId()) && alert.kind() == this.editor.mode())) {
            return Try
                .failure(new IllegalArgumentException("This alert no longer exists. Cancel the edit to continue."));
        }
        return Try.success(null);
    }

    private Try<AlertDefinition> resolveDraft() {
        return this.validateDraftSelection().flatMap(_ -> this.editor.draft()
            .resolve(this.data.getMarketPrices(ProductIdentity.fromIndex(this.editor.product())),
                System.currentTimeMillis()));
    }

    private void refreshPreview() {
        if (this.previewValue == null) {
            return;
        }
        var resolved = this.resolveDraft();
        this.saveButton.active(resolved.isSuccess());
        this.previewValue.color(BazaarStyles.color(BazaarStyles.SECONDARY_TEXT));
        if (resolved.isFailure()) {
            String error = errorMessage(resolved.getCause());
            this.previewValue.text(Component.literal(this.editor.mode() == Kind.Price
                && this.editor.expression().isBlank()
                && this.editor.hasSelection() ? "Enter a threshold" : error));
            this.saveButton.tooltip(Component.literal(error));
            return;
        }
        var definition = resolved.get();
        var preview = AlertNotice.conditionText(definition.condition());
        if (definition.condition() instanceof AlertCondition.Price price
            && price.type().source().price(this.data.getMarketPrices(ProductIdentity.fromIndex(definition.product())))
                .isEmpty()) {
            preview.append(Component.literal("\nWaiting for a current quote.").withColor(BazaarStyles.SECONDARY_TEXT));
        }
        this.previewValue.text(preview);
        this.saveButton.tooltip(List.<Component>of());
    }

    private void save() {
        var result = this.resolveDraft()
            .flatMap(definition -> this.manager.saveAlert(this.editor.editingId(), definition));
        if (result.isFailure()) {
            this.setMessage(errorMessage(result.getCause()), BazaarStyles.STATUS_UNDERCUT);
            this.refreshPreview();
            return;
        }
        var alert = result.get();
        this.editor.reset();
        this.tab = this.manager.alerts().stream().anyMatch(candidate -> candidate.id.equals(alert.id))
            ? Tab.Active : Tab.Reached;
        this.message = this.tab == Tab.Active
            ? "Alert active since " + captureTime(alert.createdAt) + "."
            : "Target reached at " + captureTime(alert.createdAt) + ".";
        this.messageColor = BazaarStyles.SECONDARY_TEXT;
        this.rebuild();
    }

    private void refreshAlertRows() {
        if (this.alertRows == null) {
            return;
        }
        double offset = this.scrollOffset();
        this.alertRows.clearChildren();
        this.activeQuotes.clear();
        boolean history = this.tab == Tab.Reached;
        if (history) {
            for (var entry : this.manager.reachedAlerts()) {
                if (entry.alert().kind() == this.editor.mode()) {
                    this.alertRows.child(this.reachedAlertRow(entry));
                }
            }
        } else {
            for (var alert : this.manager.alerts()) {
                if (alert.kind() == this.editor.mode()) {
                    this.alertRows.child(this.activeAlertRow(alert));
                }
            }
        }
        if (this.alertRows.children().isEmpty()) {
            this.alertRows
                .child(text(history ? "No alerts reached yet." : "No active alerts yet.", BazaarStyles.MUTED_TEXT));
        }
        this.refreshActiveQuotes();
        this.restoreScroll(offset);
    }

    private FlowLayout activeAlertRow(Alert alert) {
        var product = this.data.refreshIndexedProduct(alert.product);
        var current = text("", BazaarStyles.SECONDARY_TEXT);
        this.activeQuotes.add(new ActiveQuote(alert, current));
        return this.alertRow(alert, product, current, 76,
            button("Edit", () -> this.edit(alert.id)).horizontalSizing(Sizing.fixed(48)),
            new IconButton(Assets.TRASHCAN, Component.literal("Delete alert for " + product.strippedName()),
                () -> this.delete(alert.id, false), 32, buttonRenderer(false, false)));
    }

    private FlowLayout reachedAlertRow(ReachedAlert entry) {
        var alert = entry.alert();
        var product = this.data.refreshIndexedProduct(alert.product);
        MutableComponent observed = switch (entry.observation()) {
            case AlertCondition.Observation.Price price -> Component.literal("Reached at ")
                .withColor(BazaarStyles.MUTED_TEXT).append(AlertNotice.coins(price.value()));
            case AlertCondition.Observation.Liquidity liquidity -> Component.literal("Reached with "
                + items(liquidity.quantity()) + " items").withColor(BazaarStyles.MUTED_TEXT);
        };
        var current = text("", BazaarStyles.SECONDARY_TEXT);
        current.text(observed.append(Component.literal(" on " + captureTime(entry.reachedAt()))
            .withColor(BazaarStyles.MUTED_TEXT)));
        return this.alertRow(alert, product, current, 136,
            button("Open Bazaar", () -> {
                GameUtils.setScreen(null);
                GameUtils.runCommand("bz " + product.strippedName());
            }).horizontalSizing(Sizing.fixed(80)),
            new IconButton(Assets.REDO_ICON, Component.literal("Watch again: " + product.strippedName()),
                () -> this.watchAgain(alert.id), 64, buttonRenderer(false, false)),
            new IconButton(Assets.TRASHCAN, Component.literal("Delete alert for " + product.strippedName()),
                () -> this.delete(alert.id, true), 32, buttonRenderer(false, false)));
    }

    private FlowLayout alertRow(
        Alert alert,
        IndexedProduct product,
        LabelComponent current,
        int actionsWidth,
        UIComponent... buttons
    ) {
        var card = row();
        card.surface(WidgetSurfaces.roundedPanel(CARD, 4));
        card.padding(Insets.of(6));
        var details = UIContainers.verticalFlow(Sizing.expand(100), Sizing.content());
        details.gap(3);
        int detailsWidth = this.contentWidth - actionsWidth - 18;
        details.child(new AlertProductRow(this.data, product, detailsWidth, false, null));
        var condition = text("", BazaarStyles.SECONDARY_TEXT).maxWidth(detailsWidth);
        condition.text(AlertNotice.conditionText(alert.condition));
        details.child(condition);
        details.child(current.maxWidth(detailsWidth));
        card.child(details);
        card.child(row(buttons).horizontalSizing(Sizing.fixed(actionsWidth)));
        return card;
    }

    private void refreshActiveQuotes() {
        for (var quote : this.activeQuotes) {
            var alert = quote.alert();
            if (alert.condition instanceof AlertCondition.Price price) {
                var current = price.type().source()
                    .price(this.data.getMarketPrices(ProductIdentity.fromIndex(alert.product)));
                quote.label().text(Component.literal("Now ").withColor(BazaarStyles.MUTED_TEXT)
                    .append(current.<Component>map(AlertNotice::coins)
                        .orElse(Component.literal("unavailable").withColor(BazaarStyles.MUTED_TEXT)))
                    .append(Component.literal(", captured " + captureTime(alert.createdAt))
                        .withColor(BazaarStyles.MUTED_TEXT)));
            } else {
                var progress = this.manager.liquidityProgress(alert);
                quote.label().text(Component.literal(progress.isPresent()
                    ? items(progress.getAsLong()) + " / "
                        + items(((AlertCondition.Liquidity) alert.condition).quantity())
                        + " items meeting your price target"
                    : "Liquidity data unavailable"));
            }
        }
    }

    private void edit(UUID id) {
        var alert = this.manager.alerts().stream().filter(candidate -> candidate.id.equals(id)).findFirst();
        if (alert.isEmpty()) {
            this.setMessage("That alert is no longer active.", BazaarStyles.SECONDARY_TEXT);
            this.refreshAlertRows();
            return;
        }
        var selected = alert.get();
        this.editor.edit(id, this.data.refreshIndexedProduct(selected.product), selected.condition);
        this.message = "";
        this.tab = Tab.Editor;
        this.rebuild();
    }

    private void watchAgain(UUID id) {
        var result = this.manager.watchAgain(id);
        if (result.isFailure()) {
            this.setMessage(errorMessage(result.getCause()), BazaarStyles.STATUS_UNDERCUT);
            this.refreshAlertRows();
            return;
        }
        var alert = result.get();
        this.tab = this.manager.alerts().stream().anyMatch(candidate -> candidate.id.equals(alert.id))
            ? Tab.Active : Tab.Reached;
        this.message = this.tab == Tab.Active ? "Watching again." : "Target reached again.";
        this.messageColor = BazaarStyles.SECONDARY_TEXT;
        this.rebuild();
    }

    private void delete(UUID id, boolean history) {
        var result = Try.of(() -> history ? this.manager.removeReachedAlert(id) : this.manager.removeAlert(id));
        this.setMessage(result.isFailure()
            ? errorMessage(result.getCause())
            : result.get() ? "Alert deleted." : "That alert no longer exists.",
            result.isFailure() ? BazaarStyles.STATUS_UNDERCUT : BazaarStyles.SECONDARY_TEXT);
        this.refreshAlertRows();
        this.refreshStatus();
    }

    private void switchTab(Tab tab) {
        if (this.tab == tab) {
            return;
        }
        this.tab = tab;
        this.message = "";
        this.rebuild();
    }

    private void refreshStatus() {
        String state = !this.manager.enabled()
            ? "Alerts paused" : !this.data.hasMarketData() ? "Waiting for prices" : "";
        this.status.text(Component.literal(state));
        long activeCount = this.manager.alerts().stream().filter(entry -> entry.kind() == this.editor.mode()).count();
        long reachedCount = this.manager.reachedAlerts().stream()
            .filter(entry -> entry.alert().kind() == this.editor.mode()).count();
        this.activeTabButton.setMessage(Component.literal((this.contentWidth < 426 ? "Active" : "Active alerts")
            + " (" + activeCount + ")"));
        this.reachedTabButton.setMessage(Component.literal("Reached (" + reachedCount + ")"));
        this.priceModeButton.renderer(buttonRenderer(false, this.editor.mode() == Kind.Price));
        this.liquidityModeButton.renderer(buttonRenderer(false, this.editor.mode() == Kind.Liquidity));
        this.editorTabButton.renderer(buttonRenderer(false, this.tab == Tab.Editor));
        this.activeTabButton.renderer(buttonRenderer(false, this.tab == Tab.Active));
        this.reachedTabButton.renderer(buttonRenderer(false, this.tab == Tab.Reached));
    }

    private void captureRevisions() {
        this.marketRevision = this.data.marketChanges().revision();
        this.indexRevision = this.data.indexChanges().revision();
        this.alertRevision = this.manager.changes().revision();
        this.alertsEnabled = this.manager.enabled();
    }

    @Override
    public void tick() {
        super.tick();
        boolean marketChanged = this.marketRevision != this.data.marketChanges().revision();
        boolean indexChanged = this.indexRevision != this.data.indexChanges().revision();
        boolean alertsChanged = this.alertRevision != this.manager.changes().revision();
        boolean enabledChanged = this.alertsEnabled != this.manager.enabled();
        if (!marketChanged && !indexChanged && !alertsChanged && !enabledChanged) {
            return;
        }
        this.captureRevisions();
        if (indexChanged && this.editor.product() != null) {
            this.editor.refreshProduct(this.data.refreshIndexedProduct(this.editor.product()));
            if (this.tab == Tab.Editor && !this.editor.searching()) {
                this.rebuildProductPane();
            }
        }
        if (this.tab != Tab.Editor) {
            if (alertsChanged || indexChanged) {
                this.refreshAlertRows();
            } else if (marketChanged) {
                this.refreshActiveQuotes();
            }
        } else {
            if (marketChanged || indexChanged) {
                this.refreshSearchResults(false);
                this.refreshQuotes();
            }
            this.refreshPreview();
        }
        this.refreshStatus();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.tab == Tab.Editor && this.editor.searching()) {
            if (event.isEscape() && this.editor.product() != null) {
                this.cancelSearch();
                return true;
            }
            if (this.searchBox != null && this.uiAdapter.rootComponent.focusHandler().focused() == this.searchBox
                && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)
                && !this.matches.isEmpty()) {
                this.selectProduct(this.matches.getFirst());
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void defer(Runnable action) {
        this.uiAdapter.rootComponent.queue(action);
    }

    private void focusSearch() {
        if (this.searchBox != null && this.uiAdapter.rootComponent.focusHandler() != null) {
            this.uiAdapter.rootComponent.focusHandler().focus(this.searchBox, UIComponent.FocusSource.MOUSE_CLICK);
        }
    }

    private void clearUiFocus() {
        if (this.uiAdapter != null && this.uiAdapter.rootComponent.focusHandler() != null) {
            this.uiAdapter.rootComponent.focusHandler().focus(null, UIComponent.FocusSource.MOUSE_CLICK);
        }
    }

    private void rebuild() {
        this.clearUiFocus();
        this.uiAdapter.rootComponent.clearChildren();
        this.build(this.uiAdapter.rootComponent);
    }

    @Override
    public void resize(int width, int height) {
        double offset = this.scrollOffset();
        super.resize(width, height);
        if (this.uiAdapter != null) {
            this.rebuild();
            this.restoreScroll(offset);
        }
    }

    private double scrollOffset() {
        return this.scroller == null ? 0.0 : this.scroller.savedScrollOffset();
    }

    private void restoreScroll(double offset) {
        if (this.scroller != null) {
            this.scroller.restoreScrollOffset(offset);
            this.scroller.onChildMutated(this.content);
        }
    }

    @Override
    public void onClose() {
        GameUtils.setScreen(this.returnScreen());
    }

    private @Nullable Screen returnScreen() {
        if (!this.sameSession() || this.parent instanceof ChatScreen) {
            return null;
        }
        if (this.parent instanceof AbstractContainerScreen<?> container
            && (Minecraft.getInstance().player == null
                || Minecraft.getInstance().player.containerMenu != container.getMenu())) {
            return null;
        }
        return this.parent;
    }

    private boolean sameSession() {
        return this.activation.isActive()
            && Minecraft.getInstance().level == this.parentLevel
            && Minecraft.getInstance().getConnection() == this.parentConnection
            && this.activation.generation() == this.activationGeneration;
    }

    private void setMessage(String message, int color) {
        this.message = message;
        this.messageColor = color;
        this.feedback.text(Component.literal(message));
        this.feedback.color(BazaarStyles.color(color));
        this.feedback.verticalSizing(message.isEmpty() ? Sizing.fixed(0) : Sizing.content());
    }

    private void clearMessage() {
        this.setMessage("", BazaarStyles.SECONDARY_TEXT);
    }

    private static FlowLayout card(int width) {
        var card = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content());
        card.padding(Insets.of(8));
        card.gap(7);
        card.surface(WidgetSurfaces.roundedPanel(CARD, 4));
        return card;
    }

    private static FlowLayout row(UIComponent... children) {
        var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        for (var child : children) {
            row.child(child);
        }
        return row;
    }

    private static FlowLayout segments(ButtonComponent first, ButtonComponent second) {
        first.horizontalSizing(Sizing.expand(50));
        second.horizontalSizing(Sizing.expand(50));
        return row(first, second);
    }

    private static LabelComponent text(String value, int color) {
        return BazaarUi.text(value, color);
    }

    private static ButtonComponent button(String label, Runnable action) {
        var button = UIComponents.button(Component.literal(label), _ -> action.run());
        button.textShadow(false);
        button.sizing(Sizing.content(8), Sizing.fixed(22));
        button.renderer(buttonRenderer(false, false));
        return button;
    }

    private static ButtonComponent.Renderer buttonRenderer(boolean primary, boolean selected) {
        return buttonRenderer(primary, selected, 0xFF89929C);
    }

    private static ButtonComponent.Renderer buttonRenderer(boolean primary, boolean selected, int accent) {
        return (graphics, button, delta) -> {
            boolean hover = button.isHoveredOrFocused();
            int color = primary
                ? (button.active() ? (hover ? 0xFF555C65 : 0xFF3F454C) : (hover ? 0xFF2D3136 : 0xFF22252A))
                : (hover ? 0xFF484D54 : selected ? 0xFF3B3F44 : 0xFF25282C);
            WidgetSurfaces.drawRoundedPanel(graphics, button.getX(), button.getY(), button.getWidth(),
                button.getHeight(),
                color, 3);
            int border = (primary || selected) && button.active() ? accent : hover ? 0xFF707780 : 0xFF45494E;
            graphics.fill(button.getX() + 3, button.getY() + button.getHeight() - 1,
                button.getX() + button.getWidth() - 3, button.getY() + button.getHeight(), border);
        };
    }

    private static MutableComponent priceLabel(PriceSource source) {
        return Component.literal(source.label())
            .withColor(source == PriceSource.Buy ? BazaarStyles.BUY_ACCENT : BazaarStyles.SELL_ACCENT);
    }

    private static String items(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    private static String captureTime(long timestamp) {
        return CAPTURE_TIME.format(Instant.ofEpochMilli(timestamp));
    }

    private static String errorMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record ActiveQuote(Alert alert, LabelComponent label) {}

    private enum Tab {
        Editor,
        Active,
        Reached
    }

}
