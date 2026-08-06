package com.github.lutzluca.btrbz.core.widgets.pricedifference;

import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.utils.GameUtils;
import com.github.lutzluca.btrbz.utils.ScreenInfoHelper;
import com.github.lutzluca.btrbz.utils.Utils;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PriceDifferenceWidgetData {
    private static final int PRODUCT_SLOT = 13;
    private static final int SELL_INSTANTLY_SLOT = 11;
    private final BazaarData market;

    public record StateKey(String productName, double perItem, int quantity) {
        static StateKey empty() {
            return new StateKey("", 0, 0);
        }
    }

    public PriceDifferenceWidgetData(BazaarData market) {
        this.market = market;
    }

    public StateKey stateKey() {
        var info = ScreenInfoHelper.get().getCurrInfo();
        int quantity = info.getItemStack(SELL_INSTANTLY_SLOT).flatMap(this::listedCount).orElse(0);
        if (quantity <= 0) {
            return StateKey.empty();
        }

        var productStack = info.getItemStack(PRODUCT_SLOT);
        if (productStack.isEmpty()) {
            return StateKey.empty();
        }

        var stack = productStack.orElseThrow();
        var spread = this.market.productSpread(this.market.resolveProduct(stack));
        if (spread.isEmpty()) {
            return StateKey.empty();
        }

        return new StateKey(stack.getHoverName().getString(), spread.get(), quantity);
    }

    public Snapshot snapshot() {
        return this.snapshotFor(this.stateKey());
    }

    public Snapshot snapshotFor(StateKey key) {
        if (key.quantity() <= 0) {
            return empty();
        }
        return ScreenInfoHelper.get().getCurrInfo().getItemStack(PRODUCT_SLOT)
            .map(stack -> new Snapshot(key.productName(), Optional.of(stack), key.perItem(), key.quantity()))
            .orElseGet(PriceDifferenceWidgetData::empty);
    }

    public static Snapshot preview() {
        return new Snapshot(
            "Enchanted Diamond", Optional.of(new ItemStack(Items.DIAMOND)), 12_450, 640
        );
    }

    private Optional<Integer> listedCount(ItemStack stack) {
        return GameUtils.getLore(stack).stream()
            .filter(line -> line.startsWith("Inventory"))
            .findFirst()
            .flatMap(line -> Utils.parseUsFormattedNumber(
                line.replace("Inventory:", "").replace("items", "").trim()
            ).toJavaOptional())
            .map(Number::intValue);
    }

    private static Snapshot empty() {
        return new Snapshot("", Optional.empty(), 0, 0);
    }

    public record Snapshot(String productName, Optional<ItemStack> itemStack, double perItem, int quantity) {
        public Snapshot {
            itemStack = itemStack.map(ItemStack::copy);
        }

        @Override
        public Optional<ItemStack> itemStack() {
            return this.itemStack.map(ItemStack::copy);
        }

        public double total() {
            return this.perItem * this.quantity;
        }
    }
}
