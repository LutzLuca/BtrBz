package com.github.lutzluca.btrbz.core.widgets.productcontext;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.data.ProductIdentity;
import com.github.lutzluca.btrbz.cache.CacheDependencies;
import com.github.lutzluca.btrbz.core.widgets.cache.WidgetDataSource;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetProductContext;
import com.github.lutzluca.btrbz.core.widgets.session.WidgetSession;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.screen.ScreenTracker;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ProductContextWidgetData implements WidgetDataSource<ProductContextWidgetData.Snapshot> {
    private static final int PRODUCT_SLOT = 13;
    private static final int SELL_INSTANTLY_SLOT = 11;
    private final BazaarData market;
    private final CacheDependencies dependencies;

    public ProductContextWidgetData(BazaarData market) {
        this.market = market;
        var screens = ScreenTracker.get();
        this.dependencies = CacheDependencies.of(
            screens.inventoryChanges(), market.marketChanges(), market.indexChanges());
    }

    @Override
    public CacheDependencies cacheDependencies() {
        return this.dependencies;
    }

    @Override
    public boolean sessionSensitive() {
        return false;
    }

    @Override
    public Snapshot snapshot(WidgetSession session) {
        var info = ScreenTracker.get().getCurrInfo();
        var productStack = info.getItemStack(PRODUCT_SLOT);
        if (productStack.isEmpty()) {
            return new Snapshot(Optional.empty(), emptyPrices(), Optional.empty(), OptionalInt.empty());
        }

        var stack = productStack.orElseThrow();
        var identity = this.market.resolveProduct(stack);
        var product = new WidgetProductContext(identity, stack.getHoverName(), Optional.of(stack));
        var count = info.getItemStack(SELL_INSTANTLY_SLOT).flatMap(this::listedCount);
        var prices = this.market.getMarketPrices(identity);

        return new Snapshot(Optional.of(product), new BazaarData.MarketPrices(
            usablePrice(prices.highestBuyOrderPrice()), usablePrice(prices.lowestSellOfferPrice())),
            this.market.productActivity(identity), count.map(OptionalInt::of).orElseGet(OptionalInt::empty));
    }

    public static Snapshot preview() {
        return new Snapshot(
            Optional.of(new WidgetProductContext(ProductIdentity.fromName("Enchanted Diamond"),
                Component.literal("Enchanted Diamond").withStyle(ChatFormatting.GREEN),
                Optional.of(new ItemStack(Items.DIAMOND)))),
            new BazaarData.MarketPrices(Optional.of(1_250.4), Optional.of(1_262.8)),
            Optional.of(new BazaarData.ProductActivity(
                new BazaarData.SideActivity(1_234, 1_842_560, 82_656_000),
                new BazaarData.SideActivity(876, 964_320, 62_193_600))),
            OptionalInt.of(640));
    }

    private Optional<Integer> listedCount(ItemStack stack) {
        return GameUtils.getLore(stack).stream()
            .filter(line -> line.startsWith("Inventory"))
            .findFirst()
            .flatMap(line -> Utils.parseUsFormattedNumber(
                line.replace("Inventory:", "").replace("items", "").trim()).toJavaOptional())
            .map(Number::intValue)
            .filter(count -> count >= 0);
    }

    private static Optional<Double> usablePrice(Optional<Double> price) {
        return price.filter(value -> Double.isFinite(value) && value > 0);
    }

    private static BazaarData.MarketPrices emptyPrices() {
        return new BazaarData.MarketPrices(Optional.empty(), Optional.empty());
    }

    public record Snapshot(
        Optional<WidgetProductContext> product,
        BazaarData.MarketPrices prices,
        Optional<BazaarData.ProductActivity> activity,
        OptionalInt quantity
    ) {
        public Optional<Double> perItem() {
            return Utils.zipOptionals(this.prices.lowestSellOfferPrice(), this.prices.highestBuyOrderPrice())
                .map(pair -> pair.getLeft() - pair.getRight());
        }

        public Optional<Double> total() {
            return this.quantity.isPresent()
                ? this.perItem().map(spread -> spread * this.quantity.getAsInt()) : Optional.empty();
        }
    }
}
