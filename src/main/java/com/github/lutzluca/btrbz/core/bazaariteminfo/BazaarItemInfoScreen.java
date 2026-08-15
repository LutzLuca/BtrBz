package com.github.lutzluca.btrbz.core.bazaariteminfo;

import com.github.lutzluca.btrbz.core.widgets.ui.BazaarStyles;
import com.github.lutzluca.btrbz.core.widgets.ui.BazaarUi;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetLayoutTokens;
import com.github.lutzluca.btrbz.core.widgets.ui.WidgetSurfaces;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.coflnet.CoflnetBazaarClient;
import io.vavr.control.Try;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.net.URI;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Full-screen Coflnet Bazaar snapshot and history view for a resolved hovered item. */
public final class BazaarItemInfoScreen extends BaseOwoScreen<FlowLayout> {
    private static final String COFLNET_ITEM_URL = "https://sky.coflnet.com/item/%s";
    private static final int ROOT_PADDING = 16;
    private static final int PANEL_WIDTH_PERCENT = 86;
    private static final int PANEL_HORIZONTAL_PADDING = 10;
    private static final int HEADER_ICON_SIZE = 20;
    private static final int PANEL_COLOR = 0xEE181B22;
    private static final int BUTTON_NORMAL = 0xFF2C3340;
    private static final int BUTTON_HOVER = 0xFF384252;
    private static final int BUTTON_DISABLED = 0xFF20242D;
    private static final int BUTTON_SELECTED = 0xFF315B45;
    private static final int BUTTON_SELECTED_HOVER = 0xFF3A7053;

    private final Screen parent;
    private final ProductIdentity product;
    private final String productTag;
    private final ItemStack itemStack;
    private final BazaarItemInfoDataProvider dataProvider;

    private final EnumMap<BazaarItemInfoRange, ButtonComponent> rangeButtons = new EnumMap<>(BazaarItemInfoRange.class);

    private @Nullable LabelComponent buyPrice;
    private @Nullable LabelComponent sellPrice;
    private @Nullable LabelComponent status;
    private @Nullable BazaarHistoryChartComponent chart;

    private BazaarItemInfoRange range = BazaarItemInfoRange.Day;
    private boolean showBuy = true;
    private boolean showSell = true;
    private boolean showBands = true;
    private boolean snapshotLoading = true;
    private boolean historyLoading = true;
    private boolean snapshotAvailable;
    private boolean historyAvailable;
    private @Nullable String snapshotError;
    private @Nullable String historyError;
    private boolean closed;
    private boolean openingLinkConfirmation;

    public BazaarItemInfoScreen(
        Screen parent,
        ProductIdentity product,
        String productTag,
        ItemStack itemStack,
        CoflnetBazaarClient coflnet
    ) {
        super(Component.literal(product.strippedName() + " Bazaar Info"));
        this.parent = Objects.requireNonNull(parent, "parent");
        this.product = Objects.requireNonNull(product, "product");
        this.productTag = Objects.requireNonNull(productTag, "productTag");
        this.itemStack = Objects.requireNonNull(itemStack, "itemStack").copy();
        this.dataProvider = new BazaarItemInfoDataProvider(
            Objects.requireNonNull(coflnet, "coflnet"),
            state -> this.onClient(() -> this.applyState(state)));
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

        var panel = UIContainers.verticalFlow(Sizing.fill(PANEL_WIDTH_PERCENT), Sizing.fill(88));
        panel.padding(Insets.both(PANEL_HORIZONTAL_PADDING, 9));
        panel.gap(WidgetLayoutTokens.SECTION_GAP + 2);
        panel.surface(WidgetSurfaces.roundedPanel(PANEL_COLOR, 6));

        panel.child(this.header());
        panel.child(this.currentPrices());
        panel.child(this.rangeAndFilterControls());

        this.chart = new BazaarHistoryChartComponent(Sizing.fill(100), Sizing.expand(100));
        this.chart.visibility(this.showBuy, this.showSell, this.showBands);
        panel.child(this.chart);

        this.status = BazaarUi.label("Loading Coflnet data...", BazaarStyles.MUTED_TEXT);
        panel.child(this.status);
        panel.child(this.footer());

        root.child(panel);
        this.requestData(this.range);
    }

    private FlowLayout header() {
        var header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(WidgetLayoutTokens.HEADER_GAP);

        header.child(BazaarUi.item(this.itemStack, HEADER_ICON_SIZE));
        var itemName = UIComponents.label(Component.literal(BazaarUi.truncate(
            this.product.strippedName(),
            headerNameWidth(this.width))));
        itemName.color(BazaarStyles.color(BazaarStyles.PRIMARY_TEXT));
        itemName.shadow(false);
        header.child(itemName);
        return header;
    }

    static int headerNameWidth(int screenWidth) {
        int rootContentWidth = Math.max(0, screenWidth - 2 * ROOT_PADDING);
        int panelWidth = rootContentWidth * PANEL_WIDTH_PERCENT / 100;
        return Math.max(
            0,
            panelWidth - 2 * PANEL_HORIZONTAL_PADDING - HEADER_ICON_SIZE - WidgetLayoutTokens.HEADER_GAP);
    }

    private FlowLayout currentPrices() {
        var prices = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        prices.verticalAlignment(VerticalAlignment.CENTER);
        prices.gap(WidgetLayoutTokens.VALUE_GAP);

        this.buyPrice = BazaarUi.boldLabel("Buy: -", BazaarStyles.BUY_ACCENT);
        this.sellPrice = BazaarUi.boldLabel("Sell: -", BazaarStyles.SELL_ACCENT);
        prices.child(this.buyPrice);
        prices.child(this.sellPrice);
        return prices;
    }

    private FlowLayout rangeAndFilterControls() {
        var controls = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(42));
        controls.gap(2);

        var ranges = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        ranges.verticalAlignment(VerticalAlignment.CENTER);
        ranges.gap(4);

        ranges.child(BazaarUi.label("Range", BazaarStyles.MUTED_TEXT));
        for (var candidate : BazaarItemInfoRange.values()) {
            var button = UIComponents.button(
                Component.literal(candidate.label()),
                _ -> this.selectRange(candidate));
            button.sizing(Sizing.fixed(48), Sizing.fixed(18));
            button.textShadow(false);
            this.rangeButtons.put(candidate, button);
            ranges.child(button);
        }
        this.updateRangeButtonRenderers();

        var filters = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        filters.verticalAlignment(VerticalAlignment.CENTER);
        filters.gap(4);
        filters.child(BazaarUi.label("Show", BazaarStyles.MUTED_TEXT));
        filters.child(this.filter("Buy", true, value -> this.showBuy = value));
        filters.child(this.filter("Sell", true, value -> this.showSell = value));
        filters.child(this.filter("Bands", true, value -> this.showBands = value));

        controls.child(ranges);
        controls.child(filters);
        return controls;
    }

    private io.wispforest.owo.ui.component.CheckboxComponent filter(
        String label,
        boolean selected,
        java.util.function.Consumer<Boolean> setter
    ) {
        return UIComponents.checkbox(Component.literal(label))
            .checked(selected)
            .onChanged(value -> {
                setter.accept(value);
                if (this.chart != null) {
                    this.chart.visibility(this.showBuy, this.showSell, this.showBands);
                }
            });
    }

    private FlowLayout footer() {
        var footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        footer.verticalAlignment(VerticalAlignment.CENTER);

        footer.child(BazaarUi.label("Data provided by Coflnet", BazaarStyles.MUTED_TEXT));
        footer.child(BazaarUi.spacer());

        var open = UIComponents.button(
            Component.literal("Open in Coflnet"),
            _ -> this.openInCoflnet());
        open.sizing(Sizing.fixed(112), Sizing.fixed(18));
        open.renderer(ButtonComponent.Renderer.flat(BUTTON_NORMAL, BUTTON_HOVER, BUTTON_DISABLED));
        open.textShadow(false);
        footer.child(open);
        return footer;
    }

    private void selectRange(BazaarItemInfoRange selected) {
        if (this.range == selected && !this.historyAvailable && this.historyLoading) {
            return;
        }
        this.range = selected;
        this.updateRangeButtonRenderers();
        this.requestData(selected);
    }

    private void updateRangeButtonRenderers() {
        this.rangeButtons.forEach((candidate, button) -> button.renderer(
            candidate == this.range
                ? ButtonComponent.Renderer.flat(BUTTON_SELECTED, BUTTON_SELECTED_HOVER, BUTTON_DISABLED)
                : ButtonComponent.Renderer.flat(BUTTON_NORMAL, BUTTON_HOVER, BUTTON_DISABLED)));
    }

    private void requestData(BazaarItemInfoRange selected) {
        this.dataProvider.load(this.productTag, selected.sdkRange());
    }

    private void applyState(BazaarItemInfoViewData.ScreenState state) {
        if (this.closed || this.buyPrice == null || this.sellPrice == null || this.chart == null) {
            return;
        }

        var current = state.currentPrices();
        var history = state.history();
        var currentValue = current.retainedValue();
        var historyValue = history.retainedValue();

        this.snapshotLoading = current.isLoading();
        this.historyLoading = history.isLoading();
        this.snapshotAvailable = currentValue.isPresent();
        this.historyAvailable = historyValue
            .map(value -> !value.points().isEmpty())
            .orElse(false);
        this.snapshotError = current instanceof BazaarItemInfoViewData.Failure<?> failure
            ? failure.message()
            : null;
        this.historyError = history instanceof BazaarItemInfoViewData.Failure<?> failure
            ? failure.message()
            : null;

        if (currentValue.isEmpty()) {
            this.buyPrice.text(Component.literal("Buy: -"));
            this.sellPrice.text(Component.literal("Sell: -"));
        } else {
            var value = currentValue.orElseThrow();
            this.buyPrice.text(Component.literal("Buy: " + value.buyText() + " coins"));
            this.sellPrice.text(Component.literal("Sell: " + value.sellText() + " coins"));
        }
        this.chart.history(historyValue.map(BazaarItemInfoViewData.History::points).orElse(List.of()));
        this.refreshStatus();
    }

    private void refreshStatus() {
        if (this.status == null) {
            return;
        }
        String text;
        int color = BazaarStyles.MUTED_TEXT;
        if (this.snapshotLoading || this.historyLoading) {
            text = "Loading Coflnet data...";
        } else if (this.snapshotError != null || this.historyError != null) {
            text = this.snapshotError != null && this.historyError != null
                ? "Could not load Coflnet prices or history"
                : this.snapshotError != null
                    ? "Current prices unavailable: " + this.snapshotError
                    : "History unavailable: " + this.historyError;
            color = BazaarStyles.STATUS_UNDERCUT;
        } else if (!this.snapshotAvailable && !this.historyAvailable) {
            text = "Coflnet has no Bazaar data for this item";
        } else if (!this.snapshotAvailable) {
            text = "Current prices are unavailable";
        } else if (!this.historyAvailable) {
            text = "No history available for this range";
        } else {
            text = "";
        }
        this.status.text(Component.literal(text));
        this.status.color(BazaarStyles.color(color));
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
                        .append(Component.literal(link).withStyle(
                            ChatFormatting.UNDERLINE,
                            ChatFormatting.BLUE))));
            }
            this.openingLinkConfirmation = false;
            client.setScreen(this);
        }, link, true));
    }

    private void onClient(Runnable action) {
        Minecraft.getInstance().execute(action);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xA0000000);
    }

    @Override
    public void onClose() {
        this.closed = true;
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void removed() {
        super.removed();
        if (!this.openingLinkConfirmation) {
            this.closed = true;
            this.dispose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
