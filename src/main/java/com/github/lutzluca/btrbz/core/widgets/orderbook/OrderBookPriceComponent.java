package com.github.lutzluca.btrbz.core.widgets.orderbook;

import com.github.lutzluca.btrbz.core.ProductInfoProvider;
import com.github.lutzluca.btrbz.core.fliphelper.FlipProductContext;
import com.github.lutzluca.btrbz.core.fliphelper.FlipSubmissionTracker;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.IndexedProduct;
import com.github.lutzluca.btrbz.data.OrderModels.OrderType;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.Notifier;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.BazaarMenuType;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper.ScreenInfo;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.network.chat.Component;

/** Price-entry workflow facts, copy, and sign submission. */
public final class OrderBookPriceComponent {
    private final BazaarData bazaarData;
    private final ProductInfoProvider productInfoProvider;
    private final FlipProductContext flipProductContext;
    private final FlipSubmissionTracker flipSubmissionTracker;

    public OrderBookPriceComponent(
        BazaarData bazaarData,
        ProductInfoProvider productInfoProvider,
        FlipProductContext flipProductContext,
        FlipSubmissionTracker flipSubmissionTracker
    ) {
        this.bazaarData = bazaarData;
        this.productInfoProvider = productInfoProvider;
        this.flipProductContext = flipProductContext;
        this.flipSubmissionTracker = flipSubmissionTracker;
    }

    public Optional<Workflow> currentWorkflow() {
        var current = ScreenInfoHelper.get().getCurrInfo();
        var previous = ScreenInfoHelper.get().getPrevInfo();

        if (!(current.getScreen() instanceof SignEditScreen)) {
            return Optional.empty();
        }

        var product = this.resolveProduct(previous);
        var side = this.resolveSide(previous);

        if (product.isEmpty() || side.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new Workflow(ProductIdentity.fromIndex(product.get()), side.get()));
    }

    public Optional<Snapshot> currentSnapshot() {
        return this.currentWorkflow().map(workflow -> {
            var lists = this.bazaarData.getOrderLists(workflow.product());
            var levels = new ArrayList<PriceLevel>();
            double cumulative = 0;

            var summaries = workflow.side() == OrderType.Buy ? lists.buyOrders() : lists.sellOffers();

            for (var summary : summaries) {
                cumulative += summary.getAmount();

                levels.add(new PriceLevel(
                    summary.getPricePerUnit(),
                    summary.getAmount(),
                    (int) summary.getOrders(),
                    cumulative));
            }

            return new Snapshot(workflow, levels);
        });
    }

    public boolean selectPrice(double rawPrice, boolean copyOnly) {
        var workflow = this.currentWorkflow();
        var current = ScreenInfoHelper.get().getCurrInfo();

        if (workflow.isEmpty() || !(current.getScreen() instanceof SignEditScreen sign)) {
            return false;
        }

        if (copyOnly) {
            String formatted = Utils.formatDecimal(rawPrice, 1, false);

            GameUtils.copyToClipboard(formatted);

            Notifier.notifyPlayer(Notifier.prefix()
                .append(Component.literal("Copied price ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(formatted).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" to clipboard").withStyle(ChatFormatting.GRAY)));

            return true;
        }

        double adjusted = adjustPrice(rawPrice, workflow.get().side());

        if (ScreenInfoHelper.get().getPrevInfo().inMenu(BazaarMenuType.OrderOptions)) {
            this.flipSubmissionTracker.recordSubmittedFlip(workflow.get().product(), adjusted);
        }

        GameUtils.submitSignValue(sign, Utils.formatDecimal(adjusted, 1, false));

        return true;
    }

    public static double adjustPrice(double price, OrderType side) {
        return side == OrderType.Buy ? price + 0.1 : Math.max(price - 0.1, 0.1);
    }

    private Optional<IndexedProduct> resolveProduct(ScreenInfo previous) {
        if (previous.inMenu(BazaarMenuType.OrderOptions)) {
            return this.flipProductContext.getSelectedProduct();
        }

        return Optional.ofNullable(this.productInfoProvider.getOpenedProduct());
    }

    private Optional<OrderType> resolveSide(ScreenInfo previous) {
        if (previous.inMenu(BazaarMenuType.BuyOrderSetupPrice)) {
            return Optional.of(OrderType.Buy);
        }

        if (previous.inMenu(BazaarMenuType.SellOfferSetup, BazaarMenuType.OrderOptions)) {
            return Optional.of(OrderType.Sell);
        }

        return Optional.empty();
    }

    public record Workflow(ProductIdentity product, OrderType side) {}

    public record Snapshot(Workflow workflow, List<PriceLevel> levels) {
        public Snapshot {
            levels = List.copyOf(levels);
        }
    }

    public record PriceLevel(double price, double volume, int orders, double cumulativeVolume) {}
}
