package com.github.lutzluca.btrbz.core.alert;

import com.github.lutzluca.btrbz.core.ui.UiComponents;
import com.github.lutzluca.btrbz.Assets;
import java.util.function.Consumer;

import io.wispforest.owo.ui.core.Color;
import com.github.lutzluca.btrbz.core.ui.UiStyles;
import com.github.lutzluca.btrbz.core.appearance.UiColor;

import com.github.lutzluca.btrbz.core.alert.AlertCondition.Kind;
import com.github.lutzluca.btrbz.core.alert.AlertCondition.LiquiditySide;

import com.github.lutzluca.btrbz.core.runtime.FeatureRuntime;
import com.github.lutzluca.btrbz.core.alert.AlertType.Direction;
import com.github.lutzluca.btrbz.core.alert.AlertType.PriceSource;
import com.github.lutzluca.btrbz.core.orderbook.OrderBookScreenController;
import com.github.lutzluca.btrbz.core.ui.UiButton;
import com.github.lutzluca.btrbz.core.ui.RestorableVerticalScrollContainer;
import com.github.lutzluca.btrbz.core.ui.UiSurfaces;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import io.vavr.control.Try;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
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
    private static final DateTimeFormatter CAPTURE_TIME = DateTimeFormatter
        .ofPattern("dd MMM, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter CHECK_TIME = DateTimeFormatter
        .ofPattern("HH:mm:ss", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private final @Nullable Screen parent;
    private final BazaarData data;
    private final AlertManager manager;
    private final FeatureRuntime runtime;
    private final OrderBookScreenController orderBook;
    private final long sessionGeneration;
    private final @Nullable Object parentLevel;
    private final @Nullable Object parentConnection;
    private final AlertEditorState editor = new AlertEditorState();
    private final List<ActiveQuote> activeQuotes = new ArrayList<>();

    private Tab tab;
    private int contentWidth;
    private int resultHeight;
    private String message = "";
    private UiColor messageColor = UiColor.TextSecondary;
    private long appearanceRevision = UiStyles.appearanceRevision();
    private long marketRevision;
    private long indexRevision;
    private long alertRevision;
    private boolean alertsEnabled;

    private FlowLayout content;
    private RestorableVerticalScrollContainer<FlowLayout> scroller;
    private @Nullable FlowLayout productPane;
    private @Nullable AlertSearchControls searchControls;
    private @Nullable FlowLayout alertRows;
    private @Nullable LabelComponent buyQuote;
    private @Nullable LabelComponent sellQuote;
    private @Nullable LabelComponent previewValue;
    private LabelComponent status;
    private LabelComponent feedback;
    private UiButton activeTabButton;
    private UiButton inboxTabButton;
    private @Nullable UiButton historyButton;
    private @Nullable UiButton dismissAllButton;
    private @Nullable UiButton saveButton;

    public AlertScreen(
        @Nullable Screen parent,
        BazaarData data,
        AlertManager manager,
        FeatureRuntime runtime,
        OrderBookScreenController orderBook
    ) {
        super(Component.literal("BtrBz Alerts"));
        this.parent = parent;
        this.data = Objects.requireNonNull(data, "data");
        this.manager = Objects.requireNonNull(manager, "manager");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.orderBook = Objects.requireNonNull(orderBook, "orderBook");
        this.sessionGeneration = runtime.sessionGeneration();
        this.parentLevel = Minecraft.getInstance().level;
        this.parentConnection = Minecraft.getInstance().getConnection();
        this.tab = manager.pendingCount() > 0 ? Tab.Inbox : Tab.Active;
    }

    public void preselectProduct(IndexedProduct product) {
        this.editor.select(this.data.refreshIndexedProduct(Objects.requireNonNull(product, "product")));
        this.tab = Tab.Editor;
    }

    public boolean showingInbox() {
        return this.tab == Tab.Inbox;
    }

    public void openInbox() {
        this.tab = Tab.Inbox;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        int width = Math.max(200, Math.min(460, this.width - 24));
        int maxHeight = Math.max(140, Math.min(this.tab == Tab.Editor ? 460 : 410, this.height - 24));
        this.contentWidth = width - 24;
        boolean columns = width >= 400;
        this.resultHeight = Math.max(48, Math.min(200, maxHeight - 150));

        root.surface(Surface.flat(UiStyles.palette().screenBackdrop()));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        var panel = new FlowLayout(Sizing.fixed(width), Sizing.content(), FlowLayout.Algorithm.VERTICAL) {
            @Override
            public void layout(Size space) {
                super.layout(space);
                int chromeHeight = this.height() - AlertScreen.this.scroller.height();
                int viewportLimit = Math.max(1, maxHeight - chromeHeight);
                if (AlertScreen.this.scroller.maxContentHeight() != viewportLimit) {
                    AlertScreen.this.scroller.maxContentHeight(viewportLimit);
                    super.layout(space);
                }
            }
        };
        panel.surface(UiSurfaces.roundedPanel(UiStyles.palette().panelBackground(), 6));
        panel.padding(Insets.of(12));
        panel.gap(8);

        var title = text(this.tab == Tab.Editor
            ? this.editor.editingId() == null ? "New alert" : "Edit alert"
            : this.tab == Tab.History ? "Alert history" : "Alerts", UiStyles.palette().textPrimary()).shadow(true);
        this.status = text("", UiStyles.palette().textSecondary());
        var close = UiButton.text("×", this::onClose);
        close.tooltip(Component.literal(this.tab == Tab.Editor ? "Close and discard this draft" : "Close alerts"));
        close.horizontalSizing(Sizing.fixed(22));
        var heading = row();
        if (this.tab == Tab.Editor) {
            var cancel = UiButton.text("Cancel", () -> this.defer(() -> {
                this.editor.reset();
                this.switchTab(this.manager.pendingCount() > 0 ? Tab.Inbox : Tab.Active);
            }));
            cancel.tooltip(Component.literal("Discard this draft and return to your alerts"));
            heading.child(cancel);
        }
        heading.child(title).child(UiComponents.spacer());
        if (this.tab != Tab.Editor || this.contentWidth >= 360) {
            heading.child(this.status);
        }
        panel.child(heading.child(close));
        this.historyButton = null;
        this.dismissAllButton = null;
        if (this.tab != Tab.Editor) {
            this.inboxTabButton = UiButton.text("Inbox", () -> this.defer(() -> this.switchTab(Tab.Inbox)));
            this.activeTabButton = UiButton.text("Watching", () -> this.defer(() -> this.switchTab(Tab.Active)));
            var create = UiButton.text("New alert", () -> this.defer(() -> {
                this.editor.reset();
                this.switchTab(Tab.Editor);
                this.focusSearch();
            }));
            var tabs = splitRow(this.inboxTabButton, this.activeTabButton);
            tabs.horizontalSizing(Sizing.expand(100));
            panel.child(row(tabs, create));
        }

        this.content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.content.gap(8);
        this.scroller = new RestorableVerticalScrollContainer<>(Sizing.fill(100), maxHeight, this.content);
        this.scroller.scrollbarThiccness(3);
        panel.child(this.scroller);
        if (this.tab != Tab.Editor) {
            this.historyButton = UiButton.text("History", () -> this.defer(() -> this.switchTab(Tab.History)));
            var footer = row(this.historyButton);
            if (this.tab == Tab.Inbox) {
                this.dismissAllButton = UiButton.text("Dismiss all", () -> this.defer(this::dismissAll));
                this.dismissAllButton
                    .tooltip(Component.literal("Move these alerts to History and stop their reminders. "
                        + "Repeating watches resume after dismissal."));
                footer.child(this.dismissAllButton);
            }
            panel.child(footer);
        }
        this.feedback = text(this.message, this.messageColor.color(UiStyles.palette())).maxWidth(this.contentWidth);
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
        this.searchControls = null;
        this.alertRows = null;
        this.buyQuote = null;
        this.sellQuote = null;
        this.previewValue = null;
        this.saveButton = null;
        if (this.tab != Tab.Editor) {
            this.alertRows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            this.alertRows.gap(6);
            this.content.child(this.alertRows);
            this.refreshAlertRows();
        } else {
            this.productPane = card();
            this.content.child(this.productPane);
            if (!this.editor.searching()) {
                this.content.child(this.buildSettings(columns));
            }
            this.rebuildProductPane();
            this.refreshPreview();
        }
    }

    private FlowLayout buildSettings(boolean columns) {
        var settings = card();
        settings.child(text("Alert type", UiStyles.palette().textPrimary()));
        settings.child(segments(this.choices(this.editor.mode(), this::switchMode,
            new Choice<>(Kind.Price, "Price", "Watch the instant buy or sell price."),
            new Choice<>(Kind.Liquidity, "Liquidity",
                "Watch quantities within your price limit. Hypixel provides the top 30 orders per side."))));

        var trade = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(5);
        var target = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(5);
        var priceLimit = text("Target price", UiStyles.palette().textPrimary());
        if (this.editor.mode() == Kind.Price) {
            trade.child(text("Price to watch", UiStyles.palette().textPrimary()));
            trade.child(segments(this.choices(this.editor.source(), this.editor::source,
                new Choice<>(PriceSource.Buy, "Buy price", "Lowest sell offer"),
                new Choice<>(PriceSource.Sell, "Sell price", "Highest buy order"))));
            target.child(text("Alert when", UiStyles.palette().textPrimary()));
            target.child(segments(this.choices(this.editor.direction(), this.editor::direction,
                new Choice<>(Direction.Below, Direction.Below.label(), "At or below your target, including equality"),
                new Choice<>(Direction.Above, Direction.Above.label(),
                    "At or above your target, including equality"))));
        } else {
            trade.child(text("I want to", UiStyles.palette().textPrimary()));
            Consumer<LiquiditySide> select = side -> {
                this.editor.liquiditySide(side);
                priceLimit.text(Component.literal(side == LiquiditySide.SellOffers
                    ? "Maximum price per item" : "Minimum price per item"));
            };
            select.accept(this.editor.liquiditySide());
            trade.child(segments(this.choices(this.editor.liquiditySide(), select,
                new Choice<>(LiquiditySide.SellOffers, "Buy instantly",
                    "Buy from sell offers at or below your maximum price."),
                new Choice<>(LiquiditySide.BuyOrders, "Sell instantly",
                    "Sell to buy orders at or above your minimum price."))));
            target.child(text("Item quantity", UiStyles.palette().textPrimary()));
            var quantity = AlertFields.quantity();
            quantity.setMaxLength(32);
            quantity.text(this.editor.quantity());
            quantity.onChanged().subscribe(value -> {
                this.editor.quantity(value);
                this.clearMessage();
                this.refreshPreview();
            });
            target.child(AlertFields.frame(quantity));
        }
        settings.child(columns
            ? splitRow(trade, target).gap(12)
            : UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).child(trade).child(target).gap(7));
        settings.child(row(priceLimit, Assets.HELP.component(10, Component.literal("""
            Expressions
            Use +, -, *, / and parentheses ().
            Reference current prices with buy and sell.
            Examples: buy * 1.1, sell - 10k or 2.5m
            Expressions resolve to a fixed target when saved."""))));
        var expression = AlertFields.textBox("e.g. 1,250 or buy * 1.1");
        expression.setMaxLength(256);
        expression.text(this.editor.expression());
        expression.onChanged().subscribe(value -> {
            this.editor.expression(value);
            this.clearMessage();
            this.refreshPreview();
        });
        settings.child(AlertFields.frame(expression));
        var hint = text("", UiStyles.palette().textSecondary()).maxWidth(this.contentWidth - 16);
        Consumer<AlertLifecycle> lifecycle = value -> {
            this.editor.lifecycle(value);
            hint.text(Component.literal(value.description()
                + (value == AlertLifecycle.Repeat ? "\nAt least one minute between alerts." : "")));
        };
        lifecycle.accept(this.editor.lifecycle());
        var choices = this.choices(this.editor.lifecycle(), lifecycle,
            new Choice<>(AlertLifecycle.Once, AlertLifecycle.Once.label(), AlertLifecycle.Once.description()),
            new Choice<>(AlertLifecycle.Repeat, AlertLifecycle.Repeat.label(), AlertLifecycle.Repeat.description()));
        settings.child(text("Trigger again", UiStyles.palette().textPrimary()));
        settings.child(this.contentWidth < 360
            ? UIContainers.verticalFlow(Sizing.fill(100), Sizing.content()).gap(4)
                .child(choices[0].horizontalSizing(Sizing.fill(100)))
                .child(choices[1].horizontalSizing(Sizing.fill(100)))
            : row(choices));
        settings.child(hint);
        this.previewValue = text("", UiStyles.palette().textPrimary()).maxWidth(this.contentWidth - 16);
        this.previewValue.margins(Insets.vertical(4));
        settings.child(this.previewValue);
        this.saveButton = UiButton.text(this.editor.editingId() == null ? "Create alert" : "Save changes", this::save);
        this.saveButton.sizing(Sizing.fill(100), Sizing.fixed(24));
        settings.child(this.saveButton.primary(true));
        return settings;
    }

    @SafeVarargs
    private final <E> UiButton[] choices(E selected, Consumer<E> select, Choice<E>... choices) {
        var buttons = new UiButton[choices.length];
        for (int index = 0; index < choices.length; index++) {
            var choice = choices[index];
            buttons[index] = UiButton.text(choice.label(), () -> this.defer(() -> {
                select.accept(choice.value());
                for (int item = 0; item < choices.length; item++) {
                    buttons[item].selected(choices[item].value().equals(choice.value()));
                }
                this.clearMessage();
                this.refreshPreview();
            })).selected(choice.value().equals(selected));
            buttons[index].tooltip(Component.literal(choice.description()));
        }
        return buttons;
    }

    private void rebuildProductPane() {
        if (this.productPane == null) {
            return;
        }
        this.productPane.clearChildren();
        this.searchControls = null;
        this.buyQuote = null;
        this.sellQuote = null;
        if (this.editor.searching()) {
            var heading = row(text("Search items", UiStyles.palette().textPrimary()), UiComponents.spacer());
            if (this.editor.product() != null) {
                heading.child(UiButton.text("Cancel", () -> this.defer(this::cancelSearch)));
            }
            this.productPane.child(heading);
            this.searchControls = new AlertSearchControls(this.data, this.contentWidth, this.resultHeight,
                this.editor.query(), this.editor::query, product -> this.defer(() -> this.selectProduct(product)));
            this.productPane.child(this.searchControls);
        } else {
            var changeItem = UiButton.text("Change item", () -> this.defer(this::beginSearch));
            var orderBook = UiButton.text("Order book", this::openOrderBook);
            var actions = row(orderBook, changeItem).horizontalSizing(Sizing.content());
            boolean inline = this.contentWidth >= 360;
            int nameWidth = this.contentWidth - (inline ? 180 : 16);
            var product = new AlertProductRow(this.data, this.editor.product(), nameWidth, false, null);
            product.horizontalSizing(Sizing.expand(100));
            if (inline) {
                this.productPane.child(row(product, actions));
            } else {
                this.productPane.child(product).child(actions);
            }
            int quoteWidth = this.contentWidth - 16;
            this.buyQuote = text("", UiStyles.palette().textPrimary()).maxWidth(quoteWidth);
            this.sellQuote = text("", UiStyles.palette().textPrimary()).maxWidth(quoteWidth);
            var quotes = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            quotes.gap(4);
            quotes.child(this.buyQuote).child(this.sellQuote);
            this.productPane.child(quotes);
            this.refreshQuotes();
        }
    }

    private void openOrderBook() {
        var product = this.editor.product();
        if (product == null || !this.sameSession()) {
            return;
        }
        this.orderBook.open(this, product);
    }

    private void selectProduct(IndexedProduct product) {
        this.editor.select(this.data.refreshIndexedProduct(product));
        this.clearUiFocus();
        this.clearMessage();
        this.rebuild();
    }

    private void beginSearch() {
        this.editor.beginSearch();
        this.clearMessage();
        this.rebuild();
        this.focusSearch();
    }

    private void cancelSearch() {
        this.editor.cancelSearch();
        this.clearUiFocus();
        this.rebuild();
    }

    private void switchMode(Kind mode) {
        if (this.editor.mode() != mode) {
            this.editor.mode(mode);
            this.message = "";
            this.rebuild();
        }
    }

    private void refreshQuotes() {
        if (this.buyQuote == null || this.editor.product() == null) {
            return;
        }
        var prices = this.data.getMarketPrices(ProductIdentity.fromIndex(this.editor.product()));
        this.buyQuote.text(priceLabel(PriceSource.Buy).append(Component.literal("   "))
            .append(PriceSource.Buy.price(prices).<Component>map(AlertNotice::coins)
                .orElse(Component.literal("unavailable").withStyle(UiStyles.muted()))));
        this.sellQuote.text(priceLabel(PriceSource.Sell).append(Component.literal("   "))
            .append(PriceSource.Sell.price(prices).<Component>map(AlertNotice::coins)
                .orElse(Component.literal("unavailable").withStyle(UiStyles.muted()))));
    }

    private Try<Void> validateDraftSelection() {
        if (!this.editor.hasSelection()) {
            return Try.failure(new IllegalArgumentException("Choose an item to continue."));
        }
        if (this.editor.editingId() != null && this.manager.alerts().stream()
            .noneMatch(alert -> alert.id.equals(this.editor.editingId()))) {
            return Try
                .failure(new IllegalArgumentException("This alert no longer exists. Cancel the edit to continue."));
        }
        return Try.success(null);
    }

    private Try<AlertDefinition> resolveDraft() {
        return this.validateDraftSelection().flatMap(
            _ -> this.editor.resolve(this.data.getMarketPrices(ProductIdentity.fromIndex(this.editor.product())),
                System.currentTimeMillis()));
    }

    private void refreshPreview() {
        if (this.previewValue == null) {
            return;
        }
        var resolved = this.resolveDraft();
        this.saveButton.active(resolved.isSuccess());
        this.previewValue.color(Color.ofArgb(UiStyles.palette().textPrimary()));
        if (resolved.isFailure()) {
            String error = errorMessage(resolved.getCause());
            this.previewValue.text(Component.literal(this.editor.mode() == Kind.Price
                && this.editor.expression().isBlank()
                && this.editor.hasSelection() ? "Enter your target price" : error));
            this.saveButton.tooltip(Component.literal(error));
            return;
        }
        var definition = resolved.get();
        var preview = AlertNotice.conditionText(definition.condition());
        if (definition.condition() instanceof AlertCondition.Price price
            && price.type().source().price(this.data.getMarketPrices(ProductIdentity.fromIndex(definition.product())))
                .isEmpty()) {
            preview.append(Component.literal("\nWaiting for a current quote.")
                .withStyle(UiStyles.primary()));
        }
        this.previewValue.text(preview);
        this.saveButton.tooltip(List.<Component>of());
    }

    private void save() {
        var result = this.resolveDraft()
            .flatMap(
                definition -> this.manager.saveAlert(this.editor.editingId(), definition, this.editor.lifecycle()));
        if (result.isFailure()) {
            this.setMessage(errorMessage(result.getCause()), UiColor.Error);
            this.refreshPreview();
            return;
        }
        var alert = result.get();
        this.editor.reset();
        this.tab = this.manager.inbox().stream().anyMatch(entry -> entry.alert().id.equals(alert.id))
            ? Tab.Inbox : Tab.Active;
        this.message = this.tab == Tab.Active
            ? "You're watching " + alert.productName() + "."
            : "Your target is already met. You'll find the alert in your Inbox.";
        this.messageColor = UiColor.TextSecondary;
        this.rebuild();
    }

    private void refreshAlertRows() {
        if (this.alertRows == null) {
            return;
        }
        double offset = this.scrollOffset();
        this.alertRows.clearChildren();
        this.activeQuotes.clear();
        if (this.tab == Tab.Active) {
            for (var alert : this.manager.alerts()) {
                this.alertRows.child(this.activeAlertRow(alert));
            }
        } else {
            for (var entry : this.tab == Tab.Inbox ? this.manager.inbox() : this.manager.history()) {
                this.alertRows.child(this.reachedAlertRow(entry));
            }
        }
        if (this.alertRows.children().isEmpty()) {
            this.alertRows.child(text(switch (this.tab) {
                case Inbox -> "You're all caught up. New alerts will appear here.";
                case Active -> "What would you like to watch? Choose New alert to get started.";
                default -> "Dismissed alerts will appear here. Their reminders have stopped.";
            }, UiStyles.palette().textPrimary()).maxWidth(this.contentWidth - 12));
        }
        this.refreshActiveQuotes();
        this.restoreScroll(offset);
    }

    private FlowLayout activeAlertRow(Alert alert) {
        var product = this.data.refreshIndexedProduct(alert.product);
        var current = text("", UiStyles.palette().textPrimary());
        this.activeQuotes.add(new ActiveQuote(alert, current, false));
        return this.alertRow(alert, product, alert.lifecycle.label(), null, current,
            UiButton.text("Edit", () -> this.defer(() -> this.edit(alert.id))),
            UiButton.text("Delete", () -> this.defer(() -> this.delete(alert.id, false))));
    }

    private FlowLayout reachedAlertRow(ReachedAlert entry) {
        var alert = entry.alert();
        var product = this.data.refreshIndexedProduct(alert.product);
        var observed = AlertNotice.from(entry, product).observation();
        LabelComponent current = null;
        var buttons = new ArrayList<UiButton>();
        if (entry.pending()) {
            current = text("", UiStyles.palette().textPrimary());
            this.activeQuotes.add(new ActiveQuote(alert, current, true));
            buttons.add(UiButton.text("Open Bazaar", () -> this.openBazaar(product)).primary(true));
            var done = UiButton.text("Dismiss", () -> this.defer(() -> this.dismiss(entry.id())));
            done.tooltip(Component.literal("Move to History and stop reminders. A repeating watch resumes."));
            buttons.add(done);
        } else if (this.manager.alerts().stream().noneMatch(active -> active.matches(alert))) {
            var again = UiButton.text("Watch again", () -> this.defer(() -> this.watchAgain(entry.id())));
            again.tooltip(
                Component
                    .literal("Watch this condition again. If it is still met, wait for it to stop being met first."));
            buttons.add(again);
        }
        if (!entry.pending()) {
            buttons.add(UiButton.text("Delete", () -> this.defer(() -> this.delete(entry.id(), true))));
        }
        String timestamp = entry.pending()
            ? captureTime(entry.reachedAt())
            : "Dismissed " + captureTime(entry.dismissedAt());
        var details = Component.literal("Triggered " + captureTime(entry.reachedAt()) + "\n")
            .append(observed);
        return this.alertRow(alert, product, timestamp, details, current,
            buttons.toArray(UiButton[]::new));
    }

    private void openBazaar(IndexedProduct product) {
        if (this.sameSession()) {
            GameUtils.setScreen(null);
            GameUtils.runCommand("bz " + product.strippedName());
        }
    }

    private FlowLayout alertRow(
        Alert alert,
        IndexedProduct product,
        String caption,
        @Nullable Component details,
        @Nullable LabelComponent current,
        UiButton... buttons
    ) {
        var card = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(UiSurfaces.roundedPanel(UiStyles.palette().surfaceRaised(), 4));
        card.padding(Insets.of(6));
        card.gap(3);
        int detailsWidth = this.contentWidth - 18;
        int captionWidth = Minecraft.getInstance().font.width(caption);
        int actionWidth = 0;
        for (var button : buttons) {
            actionWidth += Minecraft.getInstance().font.width(button.getMessage()) + 16;
        }
        actionWidth += Math.max(0, buttons.length - 1) * 6;
        var conditionText = AlertNotice.conditionText(alert.condition);
        boolean inlineActions = detailsWidth >= Minecraft.getInstance().font.width(conditionText) + actionWidth + 6;
        int textWidth = inlineActions ? detailsWidth - actionWidth - 6 : detailsWidth;
        boolean stackCaption = detailsWidth < 320;
        var name = new AlertProductRow(this.data, product,
            stackCaption ? detailsWidth : detailsWidth - captionWidth - 6,
            false, null);
        var captionLabel = text(caption, UiStyles.palette().textSecondary());
        var header = row();
        name.horizontalSizing(Sizing.expand(100));
        if (stackCaption) {
            card.child(name).child(captionLabel);
        } else {
            card.child(header.child(name).child(captionLabel));
        }
        var lines = UIContainers.verticalFlow(Sizing.expand(100), Sizing.content());
        lines.gap(3);
        var condition = text("", UiStyles.palette().textPrimary()).maxWidth(textWidth);
        condition.text(conditionText);
        lines.child(condition);
        if (details != null) {
            condition.tooltip(details);
            captionLabel.tooltip(details);
        }
        if (current != null) {
            lines.child(current.maxWidth(textWidth));
        }
        var actions = row(buttons).horizontalSizing(Sizing.content());
        if (inlineActions) {
            card.child(row(lines, actions));
        } else {
            lines.horizontalSizing(Sizing.fill(100));
            card.child(lines).child(actions);
        }
        return card;
    }

    private void refreshActiveQuotes() {
        for (var quote : this.activeQuotes) {
            var alert = quote.alert();
            var observation = this.manager.currentObservation(alert);
            if (observation.isEmpty()) {
                quote.label().text(Component.literal("Waiting for fresh market data.").withStyle(UiStyles.primary()));
                continue;
            }
            boolean reached = alert.condition.isReached(observation.get());
            String state = quote.event() ? reached ? "Still met" : "No longer met" : reached ? "Target met" : "Waiting";
            if (!quote.event() && this.manager.awaitingDismissal(alert)) {
                state = "Paused until dismissed";
            } else if (!quote.event() && alert.waitingForReset()) {
                state = "Waiting for a new crossing";
            }
            var value = Component.literal(state + ": ").withStyle(UiStyles.heading())
                .append(AlertNotice.valueText(observation.get()));
            MutableComponent tooltip;
            switch (observation.get()) {
                case AlertCondition.Observation.Price price -> {
                    double difference = price.value() - ((AlertCondition.Price) alert.condition).price();
                    tooltip = value.copy();
                    if (difference != 0) {
                        tooltip.append(Component.literal(" (").withStyle(UiStyles.primary()))
                            .append(AlertNotice.coins(Math.abs(difference)))
                            .append(Component.literal(difference > 0 ? " above)" : " below)")
                                .withStyle(UiStyles.primary()));
                    }
                }
                case AlertCondition.Observation.Liquidity liquidity -> {
                    long difference = liquidity.quantity() - ((AlertCondition.Liquidity) alert.condition).quantity();
                    tooltip = value.copy().append(Component.literal(" (" + AlertNotice.items(Math.abs(difference))
                        + (difference < 0 ? " short)" : " extra)"))
                        .withStyle(UiStyles.primary()));
                }
            }
            quote.label().text(value);
            quote.label().tooltip(tooltip);
        }
    }

    private void dismiss(UUID id) {
        this.manager.dismiss(id);
        this.rebuild();
    }

    private void dismissAll() {
        this.manager.dismissAll();
        this.rebuild();
    }

    private void edit(UUID id) {
        var alert = this.manager.alerts().stream().filter(candidate -> candidate.id.equals(id)).findFirst();
        if (alert.isEmpty()) {
            this.setMessage("That alert is no longer active.", UiColor.TextSecondary);
            this.refreshAlertRows();
            return;
        }
        var selected = alert.get();
        this.editor.edit(id, this.data.refreshIndexedProduct(selected.product), selected.condition);
        this.editor.lifecycle(selected.lifecycle);
        this.message = "";
        this.tab = Tab.Editor;
        this.rebuild();
    }

    private void watchAgain(UUID id) {
        var result = this.manager.watchAgain(id);
        if (result.isFailure()) {
            this.setMessage(errorMessage(result.getCause()), UiColor.Error);
            this.refreshAlertRows();
            return;
        }
        var alert = result.get();
        this.tab = Tab.Active;
        this.message = alert.waitingForReset()
            ? "Watching again. You'll get another alert after the condition stops being met and is met again."
            : "Watching again. You'll find the previous alert in History.";
        this.messageColor = UiColor.TextSecondary;
        this.rebuild();
    }

    private void delete(UUID id, boolean history) {
        var result = Try.of(() -> history ? this.manager.removeReachedAlert(id) : this.manager.removeAlert(id));
        this.setMessage(result.isFailure()
            ? errorMessage(result.getCause())
            : result.get() ? "Alert deleted." : "That alert no longer exists.",
            result.isFailure() ? UiColor.Error : UiColor.TextSecondary);
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
            ? "Alerts paused"
            : !this.runtime.isRunning()
                ? "Monitoring inactive"
                : !this.data.hasMarketData() ? "Waiting for data" : "Monitoring active";
        this.status.text(Component.literal(state));
        this.status.maxWidth(Math.max(60, this.contentWidth - 120));
        this.status.tooltip(Component.literal(this.manager.lastCheckedAt() < 0
            ? "Waiting for a market update."
            : "Last checked " + CHECK_TIME.format(Instant.ofEpochMilli(this.manager.lastCheckedAt())) + "."));
        if (this.tab != Tab.Editor) {
            this.activeTabButton.setMessage(Component.literal("Watching (" + this.manager.alerts().size() + ")"));
            this.inboxTabButton.setMessage(Component.literal("Inbox (" + this.manager.pendingCount() + ")"));
            this.activeTabButton.selected(this.tab == Tab.Active);
            this.inboxTabButton.selected(this.tab == Tab.Inbox);
            this.historyButton.selected(this.tab == Tab.History);
            if (this.dismissAllButton != null) {
                this.dismissAllButton.active(this.manager.pendingCount() > 0);

            }
        }
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
        if (this.appearanceRevision != UiStyles.appearanceRevision()) {
            this.appearanceRevision = UiStyles.appearanceRevision();
            double offset = this.scrollOffset();
            var search = this.searchControls == null ? null : this.searchControls.saveViewState();
            this.rebuild();
            this.restoreScroll(offset);
            if (this.searchControls != null && search != null) {
                this.searchControls.restoreViewState(search);
            }
        }
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
                if (this.searchControls != null) {
                    this.searchControls.refresh(false);
                }
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
            var selection = this.searchControls == null ? null : this.searchControls.selectionFor(event);
            if (selection != null) {
                this.selectProduct(selection);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void defer(Runnable action) {
        this.uiAdapter.rootComponent.queue(action);
    }

    private void focusSearch() {
        if (this.searchControls != null) {
            this.searchControls.focusSearch();
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
        var searchState = this.searchControls == null ? null : this.searchControls.saveViewState();
        super.resize(width, height);
        if (this.uiAdapter != null) {
            this.rebuild();
            this.restoreScroll(offset);
            if (this.searchControls != null && searchState != null) {
                this.searchControls.restoreViewState(searchState);
            }
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
        this.editor.reset();
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
        return this.runtime.isRunning()
            && Minecraft.getInstance().level == this.parentLevel
            && Minecraft.getInstance().getConnection() == this.parentConnection
            && this.runtime.sessionGeneration() == this.sessionGeneration;
    }

    private void setMessage(String message, UiColor color) {
        this.message = message;
        this.messageColor = color;
        this.feedback.text(Component.literal(message));
        this.feedback.color(Color.ofArgb(color.color(UiStyles.palette())));
        this.feedback.verticalSizing(message.isEmpty() ? Sizing.fixed(0) : Sizing.content());
    }

    private void clearMessage() {
        this.setMessage("", UiColor.TextSecondary);
    }

    private static FlowLayout card() {
        var card = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.padding(Insets.of(8));
        card.gap(7);
        card.surface(UiSurfaces.roundedPanel(UiStyles.palette().surfaceRaised(), 4));
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

    private static FlowLayout segments(UiButton... buttons) {
        return splitRow(buttons[0], buttons[1]);
    }

    private static FlowLayout splitRow(UIComponent first, UIComponent second) {
        var row = new FlowLayout(Sizing.fill(100), Sizing.content(), FlowLayout.Algorithm.HORIZONTAL) {
            @Override
            public void layout(Size space) {
                int available = Math.max(0, this.width() - this.padding().get().horizontal() - this.gap());
                int firstWidth = available / 2;
                first.horizontalSizing(Sizing.fixed(firstWidth));
                second.horizontalSizing(Sizing.fixed(available - firstWidth));
                super.layout(space);
            }
        };
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.child(first).child(second);
        return row;
    }

    private static LabelComponent text(String value, int color) {
        return UiComponents.label(value, color);
    }

    private static MutableComponent priceLabel(PriceSource source) {
        return Component.literal(source == PriceSource.Buy ? "Buy price" : "Sell price")
            .withStyle(UiStyles.primary());
    }

    private static String captureTime(long timestamp) {
        return CAPTURE_TIME.format(Instant.ofEpochMilli(timestamp));
    }

    private static String errorMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record Choice<E>(E value, String label, String description) {}

    private record ActiveQuote(Alert alert, LabelComponent label, boolean event) {}

    private enum Tab {
        Editor,
        Active,
        Inbox,
        History
    }

}
