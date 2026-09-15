package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.Activation;
import com.github.lutzluca.btrbz.core.AlertManager;
import com.github.lutzluca.btrbz.core.AlertManager.Alert;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.alert.AlertType.Side;
import com.github.lutzluca.btrbz.core.widgets.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.vavr.control.Try;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Single-screen editor and manager for Bazaar price alerts. */
public final class AlertScreen extends BaseOwoScreen<FlowLayout> {
    private static final int PANEL_MAX_WIDTH = 760;
    private static final int PANEL_MAX_HEIGHT = 520;
    private static final int SEARCH_LIMIT = 8;
    private static final int TEXT_PRIMARY = 0xFFE8EDF5;
    private static final int TEXT_MUTED = 0xFF949EAD;
    private static final int TEXT_ERROR = 0xFFFF6B6B;
    private static final int TEXT_SUCCESS = 0xFF73D6A2;
    private static final int BUY_COLOR = 0xFF69A7FF;
    private static final int SELL_COLOR = 0xFFFFA763;
    private static final DateTimeFormatter CAPTURE_TIME = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final @Nullable Screen parent;
    private final BazaarData data;
    private final AlertManager manager;
    private final Activation activation;
    private final long activationGeneration;
    private final @Nullable Object parentLevel;
    private final @Nullable Object parentConnection;

    private Tab tab = Tab.Editor;
    private Side side = Side.Buy;
    private PriceSource source = PriceSource.BuyOrder;
    private @Nullable IndexedProduct selectedProduct;
    private @Nullable UUID editingId;
    private String searchQuery = "";
    private String basicInput = "";
    private String advancedInput = "";
    private boolean advanced;

    private int contentWidth;
    private FlowLayout content;
    private @Nullable RestorableVerticalScrollContainer<FlowLayout> scroller;
    private @Nullable FlowLayout searchResults;
    private @Nullable FlowLayout activeRows;
    private @Nullable FlowLayout advancedArea;
    private @Nullable TextBoxComponent searchBox;
    private @Nullable TextBoxComponent basicBox;
    private @Nullable TextBoxComponent advancedBox;
    private @Nullable LabelComponent selectedLabel;
    private @Nullable LabelComponent explanationLabel;
    private @Nullable LabelComponent buyOrderLabel;
    private @Nullable LabelComponent sellOfferLabel;
    private @Nullable LabelComponent previewLabel;
    private @Nullable LabelComponent outcomeLabel;
    private @Nullable LabelComponent enabledLabel;
    private @Nullable ButtonComponent buyButton;
    private @Nullable ButtonComponent sellButton;
    private @Nullable ButtonComponent buyOrderButton;
    private @Nullable ButtonComponent sellOfferButton;
    private @Nullable ButtonComponent advancedButton;
    private @Nullable ButtonComponent saveButton;
    private @Nullable ButtonComponent activeTabButton;
    private @Nullable ButtonComponent useBuyOrderButton;
    private @Nullable ButtonComponent useSellOfferButton;

    private @Nullable String activeMessage;
    private int activeMessageColor = TEXT_SUCCESS;

    private long marketRevision = Long.MIN_VALUE;
    private long indexRevision = Long.MIN_VALUE;
    private long alertRevision = Long.MIN_VALUE;
    private boolean enabledState;

    public AlertScreen(
        @Nullable Screen parent,
        BazaarData data,
        AlertManager manager,
        Activation activation
    ) {
        super(Component.literal("BtrBz Alerts"));
        this.parent = parent;
        this.data = Objects.requireNonNull(data, "data");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.activation = Objects.requireNonNull(activation, "activation");
        this.activationGeneration = activation.generation();
        this.parentLevel = Minecraft.getInstance().level;
        this.parentConnection = Minecraft.getInstance().getConnection();
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        int panelWidth = Math.max(120, Math.min(PANEL_MAX_WIDTH, this.width - 20));
        int panelHeight = Math.max(80, Math.min(PANEL_MAX_HEIGHT, this.height - 20));
        this.contentWidth = panelWidth - 28;

        root.surface(Surface.flat(0xCC10141B));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        root.padding(Insets.of(10));

        var panel = UIContainers.verticalFlow(Sizing.fixed(panelWidth), Sizing.fixed(panelHeight));
        panel.surface(Surface.flat(0xF0222832).and(Surface.outline(0xFF465064)));
        panel.padding(Insets.of(10));
        panel.gap(7);

        var title = label("Bazaar Alerts", TEXT_PRIMARY).shadow(true);
        title.horizontalSizing(Sizing.expand(100));
        var close = compactButton("Close", _ -> this.onClose());
        close.sizing(Sizing.fixed(64), Sizing.fixed(20));
        panel.child(row(title, close));

        this.enabledLabel = wrapped("", TEXT_MUTED);
        panel.child(this.enabledLabel);
        panel.child(this.tabBar());

        this.content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.content.gap(7);
        this.scroller = new RestorableVerticalScrollContainer<>(
            Sizing.fill(100), Sizing.expand(100), this.content);
        this.scroller.scrollbarThiccness(4);
        this.scroller.scrollStep(20);
        panel.child(this.scroller);
        root.child(panel);

        this.rebuildTab();
        this.captureRevisions();
        this.refreshEnabledStatus();
    }

    private FlowLayout tabBar() {
        var editor = compactButton("Editor", _ -> this.switchTab(Tab.Editor));
        this.activeTabButton = compactButton("Active alerts (" + this.manager.alerts().size() + ")",
            _ -> this.switchTab(Tab.Active));
        editor.horizontalSizing(Sizing.expand(50));
        this.activeTabButton.horizontalSizing(Sizing.expand(50));
        if (this.tab == Tab.Editor) {
            editor.renderer(selectedRenderer());
        } else {
            this.activeTabButton.renderer(selectedRenderer());
        }
        return row(editor, this.activeTabButton);
    }

    private void switchTab(Tab next) {
        if (this.tab == next) {
            return;
        }
        this.tab = next;
        // Rebuilding the fixed header as well keeps the selected tab and alert count truthful.
        this.uiAdapter.rootComponent.clearChildren();
        this.build(this.uiAdapter.rootComponent);
    }

    private void rebuildTab() {
        this.content.clearChildren();
        this.searchResults = null;
        this.activeRows = null;
        this.advancedArea = null;
        this.searchBox = null;
        this.basicBox = null;
        this.advancedBox = null;

        if (this.tab == Tab.Editor) {
            this.buildEditor();
        } else {
            this.buildActiveList();
        }
    }

    private void buildEditor() {
        this.content.child(section("1. Product"));
        this.content.child(wrapped(
            "Searches tradeable products by name. Items without a current Bazaar quote are hidden.",
            TEXT_MUTED));

        this.searchBox = UIComponents.textBox(Sizing.fill(100));
        this.searchBox.setMaxLength(120);
        this.searchBox.text(this.searchQuery);
        this.searchBox.onChanged().subscribe(query -> {
            this.searchQuery = query;
            this.refreshSearchResults();
        });
        this.content.child(this.searchBox);

        this.selectedLabel = wrapped("", TEXT_PRIMARY);
        this.content.child(this.selectedLabel);
        this.searchResults = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.searchResults.gap(3);
        this.content.child(this.searchResults);

        this.content.child(section("2. Alert direction"));
        this.buyButton = compactButton("Buy", _ -> this.setSide(Side.Buy));
        this.sellButton = compactButton("Sell", _ -> this.setSide(Side.Sell));
        this.buyButton.horizontalSizing(Sizing.expand(50));
        this.sellButton.horizontalSizing(Sizing.expand(50));
        this.content.child(row(this.buyButton, this.sellButton));

        this.content.child(section("3. Price source"));
        this.buyOrderButton = compactButton("Buy Order", _ -> this.setSource(PriceSource.BuyOrder));
        this.sellOfferButton = compactButton("Sell Offer", _ -> this.setSource(PriceSource.SellOffer));
        this.buyOrderButton.horizontalSizing(Sizing.expand(50));
        this.sellOfferButton.horizontalSizing(Sizing.expand(50));
        this.content.child(row(this.buyOrderButton, this.sellOfferButton));
        this.explanationLabel = wrapped("", TEXT_MUTED);
        this.content.child(this.explanationLabel);

        this.content.child(section("4. Current market and threshold"));
        this.buyOrderLabel = wrapped("", TEXT_PRIMARY);
        this.sellOfferLabel = wrapped("", TEXT_PRIMARY);
        this.content.child(this.buyOrderLabel);
        this.content.child(this.sellOfferLabel);
        this.useBuyOrderButton = compactButton("Use Buy Order", _ -> this.useCurrentQuote(PriceSource.BuyOrder));
        this.useSellOfferButton = compactButton("Use Sell Offer", _ -> this.useCurrentQuote(PriceSource.SellOffer));
        this.useBuyOrderButton.horizontalSizing(Sizing.expand(50));
        this.useSellOfferButton.horizontalSizing(Sizing.expand(50));
        this.content.child(row(this.useBuyOrderButton, this.useSellOfferButton));

        this.basicBox = UIComponents.textBox(Sizing.fill(100));
        this.basicBox.setMaxLength(32);
        this.basicBox.setFilter(value -> value.matches("[0-9._,kKmMbB]*"));
        this.basicBox.text(this.basicInput);
        this.basicBox.onChanged().subscribe(value -> {
            this.basicInput = value;
            this.refreshPreview();
        });
        this.content.child(this.basicBox);
        this.content.child(wrapped("Basic threshold accepts values such as 125000, 125k, 2.5m, or 1b.", TEXT_MUTED));

        this.advancedButton = compactButton("", _ -> this.setAdvanced(!this.advanced));
        this.advancedButton.horizontalSizing(Sizing.fill(100));
        this.content.child(this.advancedButton);
        this.advancedArea = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.advancedArea.gap(4);
        this.content.child(this.advancedArea);

        this.previewLabel = wrapped("", TEXT_PRIMARY);
        this.outcomeLabel = wrapped("", TEXT_MUTED);
        this.content.child(this.previewLabel);
        this.content.child(this.outcomeLabel);

        this.saveButton = compactButton("", _ -> this.save());
        this.saveButton.sizing(Sizing.fill(100), Sizing.fixed(22));
        this.content.child(this.saveButton);
        if (this.editingId != null) {
            this.content.child(compactButton("Cancel edit and start a new alert", _ -> this.newAlert()));
        }

        this.refreshSearchResults();
        this.refreshEditorControls();
        this.refreshAdvancedArea();
        this.refreshMarketAndPreview();
    }

    private void refreshSearchResults() {
        double offset = this.scrollOffset();
        this.rebuildSearchResults();
        this.restoreScrollOffset(offset);
    }

    private void rebuildSearchResults() {
        if (this.searchResults == null) {
            return;
        }
        this.searchResults.clearChildren();
        this.refreshSelectedLabel();

        if (this.searchQuery.isBlank()) {
            this.searchResults.child(wrapped("Type an item name to see matches.", TEXT_MUTED));
            return;
        }

        List<IndexedProduct> matches = this.data.searchProducts(this.searchQuery, SEARCH_LIMIT);
        if (matches.isEmpty()) {
            this.searchResults.child(wrapped(
                this.data.hasMarketData()
                    ? "No quoted tradeable products matched that name."
                    : "Market data is unavailable; quoted products cannot be searched yet.",
                TEXT_MUTED));
            return;
        }

        for (var product : matches) {
            long sameName = matches.stream()
                .filter(candidate -> candidate.strippedName().toLowerCase(Locale.ROOT)
                    .equals(product.strippedName().toLowerCase(Locale.ROOT)))
                .count();
            var select = compactButton(product.strippedName(), _ -> this.selectProduct(product));
            select.horizontalSizing(Sizing.fill(100));
            select.tooltip(Component.literal("Bazaar product ID: " + product.productId()));
            this.searchResults.child(select);
            if (sameName > 1) {
                this.searchResults.child(wrapped("ID: " + product.productId(), TEXT_MUTED));
            }
        }
    }

    private void selectProduct(IndexedProduct product) {
        this.selectedProduct = this.data.refreshIndexedProduct(product);
        this.searchQuery = this.selectedProduct.strippedName();
        if (this.searchBox != null && !this.searchBox.getValue().equals(this.searchQuery)) {
            this.searchBox.setValue(this.searchQuery);
        }
        this.clearOutcome();
        this.refreshSelectedLabel();
        this.refreshMarketAndPreview();
    }

    private void refreshSelectedLabel() {
        if (this.selectedLabel == null) {
            return;
        }
        this.selectedLabel.text(Component.literal(this.selectedProduct == null
            ? "Selected: none"
            : "Selected: " + this.selectedProduct.strippedName() + " [" + this.selectedProduct.productId() + "]"));
    }

    private void setSide(Side side) {
        this.side = side;
        this.clearOutcome();
        this.refreshEditorControls();
        this.refreshPreview();
    }

    private void setSource(PriceSource source) {
        this.source = source;
        this.clearOutcome();
        this.refreshEditorControls();
        this.refreshPreview();
    }

    private void refreshEditorControls() {
        if (this.buyButton == null) {
            return;
        }
        this.buyButton.renderer(this.side == Side.Buy ? selectedRenderer(BUY_COLOR) : normalRenderer());
        this.sellButton.renderer(this.side == Side.Sell ? selectedRenderer(SELL_COLOR) : normalRenderer());
        this.buyOrderButton.renderer(this.source == PriceSource.BuyOrder ? selectedRenderer() : normalRenderer());
        this.sellOfferButton.renderer(this.source == PriceSource.SellOffer ? selectedRenderer() : normalRenderer());
        String sourceExplanation = switch (AlertType.of(this.side, this.source)) {
            case BuyOrder -> "Use the highest Buy Order to price a Buy Order. ";
            case SellOffer -> "Use the lowest Sell Offer to price a Sell Offer. ";
            case InstaBuy -> "An instant Buy fills the lowest Sell Offer. ";
            case InstaSell -> "An instant Sell fills the highest Buy Order. ";
        };
        this.explanationLabel.text(Component.literal(sourceExplanation + (this.side == Side.Buy
            ? "Trigger at or below the saved threshold (≤)."
            : "Trigger at or above the saved threshold (≥).")));
        this.saveButton.setMessage(Component.literal(this.editingId == null ? "Create alert" : "Save edited alert"));
        this.advancedButton.setMessage(Component.literal(this.advanced
            ? "Advanced expression: On (collapse)"
            : "Advanced expression (optional)"));
        this.advancedButton.tooltip(Component.literal(this.advanced
            ? "Collapse, discard the expression, and return to the basic threshold."
            : "Expand to use arithmetic and live price references."));
        this.advancedButton.renderer(this.advanced ? selectedRenderer() : normalRenderer());
        this.basicBox.active = !this.advanced;
    }

    private void setAdvanced(boolean advanced) {
        this.advanced = advanced;
        if (!advanced) {
            this.advancedInput = "";
        }
        this.clearOutcome();
        this.refreshAdvancedArea();
        this.refreshEditorControls();
        this.refreshPreview();
    }

    private void refreshAdvancedArea() {
        if (this.advancedArea == null) {
            return;
        }
        this.advancedArea.clearChildren();
        this.advancedBox = null;
        if (!this.advanced) {
            return;
        }

        this.advancedArea.child(wrapped(
            "Advanced mode is active. This expression replaces the basic threshold for preview and save.",
            0xFFFFD479));
        this.advancedArea.child(wrapped(
            "Examples: buy_order * 1.05, sell_offer - 10k, or 2.5m. References stay live until Save.",
            TEXT_MUTED));
        this.advancedBox = UIComponents.textBox(Sizing.fill(100));
        this.advancedBox.setMaxLength(160);
        this.advancedBox.text(this.advancedInput);
        this.advancedBox.onChanged().subscribe(value -> {
            this.advancedInput = value;
            this.refreshPreview();
        });
        this.advancedArea.child(this.advancedBox);
    }

    private void refreshMarketAndPreview() {
        if (this.tab != Tab.Editor) {
            return;
        }
        var prices = this.selectedProduct == null
            ? null
            : this.data.getMarketPrices(ProductIdentity.fromIndex(this.selectedProduct));
        var buyOrder = prices == null ? Optional.<Double>empty() : PriceSource.BuyOrder.price(prices);
        var sellOffer = prices == null ? Optional.<Double>empty() : PriceSource.SellOffer.price(prices);

        this.buyOrderLabel.text(Component.literal("Highest Buy Order: " + quote(buyOrder.orElse(null))));
        this.sellOfferLabel.text(Component.literal("Lowest Sell Offer: " + quote(sellOffer.orElse(null))));
        this.buyOrderLabel.color(Color.ofArgb(buyOrder.isPresent() ? BUY_COLOR : TEXT_MUTED));
        this.sellOfferLabel.color(Color.ofArgb(sellOffer.isPresent() ? SELL_COLOR : TEXT_MUTED));
        this.useBuyOrderButton.active(buyOrder.isPresent());
        this.useSellOfferButton.active(sellOffer.isPresent());
        this.useBuyOrderButton.setMessage(Component.literal(
            buyOrder.isPresent() ? "Use Buy Order" : "Buy Order unavailable"));
        this.useSellOfferButton.setMessage(Component.literal(
            sellOffer.isPresent() ? "Use Sell Offer" : "Sell Offer unavailable"));
        this.refreshPreview();
    }

    private boolean useCurrentQuote(PriceSource reference) {
        if (this.selectedProduct == null) {
            return false;
        }
        var price = reference.price(this.data.getMarketPrices(ProductIdentity.fromIndex(this.selectedProduct)));
        if (price.isEmpty()) {
            return false;
        }
        this.useQuote(reference, price.get());
        return true;
    }

    private void useQuote(PriceSource reference, double value) {
        if (this.advanced) {
            if (this.advancedBox != null) {
                this.advancedBox.insertText(reference.reference());
                this.advancedInput = this.advancedBox.getValue();
            }
        } else {
            this.basicInput = editablePrice(value);
            if (this.basicBox != null) {
                this.basicBox.setValue(this.basicInput);
            }
        }
        this.clearOutcome();
        this.refreshPreview();
    }

    private void refreshPreview() {
        if (this.previewLabel == null) {
            return;
        }
        this.saveButton.active(false);
        if (this.editingId != null
            && this.manager.alerts().stream().noneMatch(alert -> alert.id.equals(this.editingId))) {
            this.preview("This alert no longer exists. Cancel the edit to start a new alert.", TEXT_ERROR);
            return;
        }
        if (this.selectedProduct == null) {
            this.preview("Select a product to preview this alert.", TEXT_MUTED);
            return;
        }
        if (!this.data.hasMarketData()) {
            this.preview("Market data is unavailable. Preview will resume after the next update.", TEXT_ERROR);
            return;
        }

        var identity = ProductIdentity.fromIndex(this.selectedProduct);
        if (!this.data.contains(identity)) {
            this.preview("The selected product has no current Bazaar market entry.", TEXT_ERROR);
            return;
        }

        String input = this.advanced ? this.advancedInput : this.basicInput;
        if (input.isBlank()) {
            this.preview(this.advanced
                ? "Enter an advanced expression to preview its captured value."
                : "Enter a numeric threshold to preview this alert.", TEXT_MUTED);
            return;
        }

        var draft = new AlertDraft(this.selectedProduct, AlertType.of(this.side, this.source), input, this.advanced);
        var resolved = draft.resolve(this.data.getMarketPrices(identity), System.currentTimeMillis());
        if (resolved.isFailure()) {
            this.preview(errorMessage(resolved.getCause()), TEXT_ERROR);
            return;
        }

        var definition = resolved.get();
        String comparator = this.side == Side.Buy ? "≤" : "≥";
        boolean sourceMissing = this.source.price(this.data.getMarketPrices(identity)).isEmpty();
        this.preview(
            (sourceMissing
                ? "Current " + this.source.label() + " quote unavailable; this alert will remain pending. " : "")
                + "Live preview: " + this.side + " when " + this.source.label() + " " + comparator + " "
                + quote(definition.price()) + ". Save captures this fixed value at that moment.",
            sourceMissing ? 0xFFFFD479 : TEXT_SUCCESS);
        this.saveButton.active(true);
    }

    private void preview(String text, int color) {
        this.previewLabel.text(Component.literal(text));
        this.previewLabel.color(Color.ofArgb(color));
    }

    private void save() {
        if (this.selectedProduct == null) {
            this.outcome("Select a product before saving.", TEXT_ERROR);
            return;
        }
        if (!this.data.hasMarketData()) {
            this.outcome("Market data is unavailable. Wait for the next update before saving.", TEXT_ERROR);
            return;
        }
        var identity = ProductIdentity.fromIndex(this.selectedProduct);
        if (!this.data.contains(identity)) {
            this.outcome("The selected product has no current Bazaar market entry.", TEXT_ERROR);
            return;
        }
        String input = this.advanced ? this.advancedInput : this.basicInput;
        var draft = new AlertDraft(
            this.selectedProduct,
            AlertType.of(this.side, this.source),
            input,
            this.advanced);
        var definition = draft.resolve(
            this.data.getMarketPrices(identity),
            System.currentTimeMillis());
        if (definition.isFailure()) {
            this.outcome(errorMessage(definition.getCause()), TEXT_ERROR);
            return;
        }

        var saved = this.manager.saveAlert(this.editingId, definition.get());
        if (saved.isFailure()) {
            this.outcome(errorMessage(saved.getCause()), TEXT_ERROR);
            return;
        }

        Alert alert = saved.get();
        this.editingId = null;
        this.outcome(
            "Saved " + alert.product.strippedName() + " at a fixed target of " + quote(alert.price)
                + ", captured " + capturedTime(alert.createdAt) + ".",
            TEXT_SUCCESS);
        this.activeMessage = "Saved " + alert.product.strippedName() + " at a fixed target of "
            + quote(alert.price) + ", captured " + capturedTime(alert.createdAt) + ".";
        this.activeMessageColor = TEXT_SUCCESS;
        this.tab = Tab.Active;
        this.rebuildWholeScreen();
    }

    private void buildActiveList() {
        this.content.child(section("Active alerts"));
        this.content.child(wrapped(
            "Saved targets are fixed values captured when you saved. Current quotes below continue to update.",
            TEXT_MUTED));
        this.content.child(compactButton("New alert", _ -> this.newAlert()));
        if (this.activeMessage != null) {
            this.content.child(wrapped(this.activeMessage, this.activeMessageColor));
        }
        this.activeRows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.activeRows.gap(6);
        this.content.child(this.activeRows);
        this.refreshActiveRows();
    }

    private void refreshActiveRows() {
        double offset = this.scrollOffset();
        this.rebuildActiveRows();
        this.restoreScrollOffset(offset);
    }

    private void rebuildActiveRows() {
        if (this.activeRows == null) {
            return;
        }
        this.activeRows.clearChildren();
        List<Alert> alerts = this.manager.alerts();
        if (alerts.isEmpty()) {
            this.activeRows.child(wrapped("No active alerts. Use the Editor tab to create one.", TEXT_MUTED));
            return;
        }

        for (var alert : alerts) {
            var row = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            row.surface(Surface.flat(0xFF191F28).and(Surface.outline(0xFF394352)));
            row.padding(Insets.of(7));
            row.gap(3);

            var refreshed = this.data.refreshIndexedProduct(alert.product);
            row.child(wrapped(refreshed.strippedName() + "  [" + refreshed.productId() + "]", TEXT_PRIMARY));
            String comparator = alert.type.side() == Side.Buy ? "≤" : "≥";
            row.child(wrapped(
                alert.type.side() + " · " + alert.type.priceSource().label() + " " + comparator + " "
                    + quote(alert.price),
                alert.type.side() == Side.Buy ? BUY_COLOR : SELL_COLOR));
            row.child(wrapped("Fixed target captured " + capturedTime(alert.createdAt), TEXT_MUTED));

            var identity = ProductIdentity.fromIndex(refreshed);
            String current = this.data.hasMarketData() && this.data.contains(identity)
                ? alert.type.priceSource().price(this.data.getMarketPrices(identity))
                    .map(AlertScreen::quote)
                    .orElse("unavailable")
                : "unavailable";
            row.child(wrapped("Current " + alert.type.priceSource().label() + ": " + current, TEXT_MUTED));

            var edit = compactButton("Edit", _ -> this.edit(alert));
            var delete = compactButton("Delete", _ -> this.delete(alert.id));
            edit.horizontalSizing(Sizing.expand(50));
            delete.horizontalSizing(Sizing.expand(50));
            row.child(row(edit, delete));
            this.activeRows.child(row);
        }
    }

    private void edit(Alert alert) {
        // Re-read the manager-owned list so a row made stale by an automatic trigger cannot be recreated.
        var current = this.manager.alerts().stream().filter(candidate -> candidate.id.equals(alert.id)).findFirst();
        if (current.isEmpty()) {
            this.refreshActiveRows();
            return;
        }
        Alert selected = current.get();
        this.editingId = selected.id;
        this.selectedProduct = this.data.refreshIndexedProduct(selected.product);
        this.side = selected.type.side();
        this.source = selected.type.priceSource();
        this.basicInput = editablePrice(selected.price);
        this.advancedInput = "";
        this.advanced = false;
        this.searchQuery = this.selectedProduct.strippedName();
        this.tab = Tab.Editor;
        this.rebuildWholeScreen();
    }

    private void newAlert() {
        this.editingId = null;
        this.selectedProduct = null;
        this.side = Side.Buy;
        this.source = PriceSource.BuyOrder;
        this.basicInput = "";
        this.advancedInput = "";
        this.advanced = false;
        this.searchQuery = "";
        this.activeMessage = null;
        this.tab = Tab.Editor;
        this.rebuildWholeScreen();
    }

    private void delete(UUID id) {
        double offset = this.scrollOffset();
        var result = Try.of(() -> this.manager.removeAlert(id));
        if (result.isFailure()) {
            this.activeMessage = "Could not delete alert: " + errorMessage(result.getCause());
            this.activeMessageColor = TEXT_ERROR;
        } else if (!result.get()) {
            this.activeMessage = "That alert no longer exists; the list was refreshed.";
            this.activeMessageColor = TEXT_ERROR;
        } else {
            this.activeMessage = "Alert deleted.";
            this.activeMessageColor = TEXT_SUCCESS;
        }
        this.rebuildWholeScreen();
        this.restoreScrollOffset(offset);
    }

    private void captureRevisions() {
        this.marketRevision = this.data.marketChanges().revision();
        this.indexRevision = this.data.indexChanges().revision();
        this.alertRevision = this.manager.changes().revision();
        this.enabledState = this.manager.enabled();
    }

    @Override
    public void tick() {
        super.tick();
        long nextMarket = this.data.marketChanges().revision();
        long nextIndex = this.data.indexChanges().revision();
        long nextAlerts = this.manager.changes().revision();
        boolean nextEnabled = this.manager.enabled();
        boolean marketChanged = nextMarket != this.marketRevision;
        boolean indexChanged = nextIndex != this.indexRevision;
        boolean alertsChanged = nextAlerts != this.alertRevision;
        boolean enabledChanged = nextEnabled != this.enabledState;
        if (!marketChanged && !indexChanged && !alertsChanged && !enabledChanged) {
            return;
        }

        this.marketRevision = nextMarket;
        this.indexRevision = nextIndex;
        this.alertRevision = nextAlerts;
        this.enabledState = nextEnabled;
        if (indexChanged && this.selectedProduct != null) {
            this.selectedProduct = this.data.refreshIndexedProduct(this.selectedProduct);
        }
        if ((marketChanged || indexChanged) && this.tab == Tab.Editor) {
            this.refreshSearchResults();
        }
        if (marketChanged || indexChanged || alertsChanged) {
            this.refreshMarketAndPreview();
        }
        if (this.tab == Tab.Active && (marketChanged || indexChanged || alertsChanged)) {
            this.refreshActiveRows();
        }
        if (alertsChanged && this.activeTabButton != null) {
            this.activeTabButton.setMessage(Component.literal("Active alerts (" + this.manager.alerts().size() + ")"));
        }
        if (alertsChanged && this.editingId != null
            && this.manager.alerts().stream().noneMatch(alert -> alert.id.equals(this.editingId))) {
            this.outcome("This alert no longer exists. Your edit cannot be saved as a new alert.", TEXT_ERROR);
        }
        this.refreshEnabledStatus();
    }

    private void refreshEnabledStatus() {
        if (this.enabledLabel == null) {
            return;
        }
        String status;
        int color;
        if (!this.manager.enabled()) {
            status = "Alerts are disabled in settings. Saved alerts remain listed but will not trigger.";
            color = TEXT_ERROR;
        } else if (!this.data.hasMarketData()) {
            status = "Market data unavailable. Quotes and live previews will update when data arrives.";
            color = 0xFFFFD479;
        } else {
            status = "Alerts enabled · market data available";
            color = TEXT_SUCCESS;
        }
        this.enabledLabel.text(Component.literal(status));
        this.enabledLabel.color(Color.ofArgb(color));
    }

    private void rebuildWholeScreen() {
        this.uiAdapter.rootComponent.clearChildren();
        this.build(this.uiAdapter.rootComponent);
    }

    private double scrollOffset() {
        return this.scroller == null ? 0.0 : this.scroller.savedScrollOffset();
    }

    private void restoreScrollOffset(double offset) {
        if (this.scroller != null) {
            this.scroller.restoreScrollOffset(offset);
            this.scroller.onChildMutated(this.content);
        }
    }

    @Override
    public void resize(int width, int height) {
        double offset = this.scrollOffset();
        super.resize(width, height);
        if (this.uiAdapter != null) {
            this.rebuildWholeScreen();
            this.restoreScrollOffset(offset);
        }
    }

    private void clearOutcome() {
        if (this.outcomeLabel != null) {
            this.outcomeLabel.text(Component.empty());
        }
    }

    private void outcome(String text, int color) {
        if (this.outcomeLabel == null) {
            return;
        }
        this.outcomeLabel.text(Component.literal(text));
        this.outcomeLabel.color(Color.ofArgb(color));
    }

    @Override
    public void onClose() {
        GameUtils.setScreen(this.safeParent());
    }

    private @Nullable Screen safeParent() {
        if (this.parent == null
            || this.parent instanceof ChatScreen
            || this.parent instanceof AbstractContainerScreen<?>
            || Minecraft.getInstance().level != this.parentLevel
            || Minecraft.getInstance().getConnection() != this.parentConnection
            || this.activation.generation() != this.activationGeneration) {
            return null;
        }
        return this.parent;
    }

    private FlowLayout row(io.wispforest.owo.ui.core.UIComponent... children) {
        var row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(5);
        row.verticalAlignment(VerticalAlignment.CENTER);
        for (var child : children) {
            row.child(child);
        }
        return row;
    }

    private LabelComponent section(String text) {
        return wrapped(text, 0xFFBFC9D8).shadow(true);
    }

    private LabelComponent wrapped(String text, int color) {
        return label(text, color).maxWidth(Math.max(80, this.contentWidth));
    }

    private static LabelComponent label(String text, int color) {
        return UIComponents.label(Component.literal(text)).color(Color.ofArgb(color));
    }

    private static ButtonComponent compactButton(String text, Consumer<ButtonComponent> onPress) {
        var button = UIComponents.button(Component.literal(text), onPress);
        button.renderer(normalRenderer());
        button.textShadow(false);
        button.sizing(Sizing.content(8), Sizing.fixed(20));
        return button;
    }

    private static ButtonComponent.Renderer normalRenderer() {
        return ButtonComponent.Renderer.flat(0xFF2C3441, 0xFF3B4657, 0xFF202630);
    }

    private static ButtonComponent.Renderer selectedRenderer() {
        return selectedRenderer(0xFF5676A6);
    }

    private static ButtonComponent.Renderer selectedRenderer(int accent) {
        return ButtonComponent.Renderer.flat(accent, brighten(accent), 0xFF202630);
    }

    private static int brighten(int color) {
        int red = Math.min(255, ((color >> 16) & 0xFF) + 20);
        int green = Math.min(255, ((color >> 8) & 0xFF) + 20);
        int blue = Math.min(255, (color & 0xFF) + 20);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static String quote(@Nullable Double value) {
        return value == null ? "unavailable" : editablePrice(value) + " coins";
    }

    private static String editablePrice(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String capturedTime(long timestamp) {
        return CAPTURE_TIME.format(Instant.ofEpochMilli(timestamp));
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    private enum Tab {
        Editor,
        Active
    }
}
