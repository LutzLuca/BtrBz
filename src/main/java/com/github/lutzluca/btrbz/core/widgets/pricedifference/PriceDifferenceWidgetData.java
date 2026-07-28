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

    public PriceDifferenceWidgetData(BazaarData market) {
        this.market = market;
    }

    public Snapshot snapshot() {
        var info = ScreenInfoHelper.get().getCurrInfo();
        var productStack = info.getItemStack(PRODUCT_SLOT);
        int quantity = info.getItemStack(SELL_INSTANTLY_SLOT).flatMap(this::listedCount).orElse(0);
        if (productStack.isEmpty() || quantity <= 0) return empty();
        var stack = productStack.orElseThrow();
        var product = this.market.resolveProduct(stack);
        var spread = this.market.productSpread(product);
        if (spread.isEmpty()) return empty();
        return new Snapshot(
            stack.getHoverName().getString(), Optional.of(stack), Math.round(spread.get()), quantity
        );
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

    public record Snapshot(String productName, Optional<ItemStack> itemStack, long perItem, int quantity) {
        public Snapshot {
            itemStack = itemStack.map(ItemStack::copy);
        }

        @Override
        public Optional<ItemStack> itemStack() {
            return this.itemStack.map(ItemStack::copy);
        }

        public long total() {
            return this.perItem * this.quantity;
        }
    }
}
