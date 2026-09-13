package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig.BookmarkedItem;
import com.github.lutzluca.btrbz.data.BazaarData;
import com.github.lutzluca.btrbz.screen.BazaarProductContext;
import com.github.lutzluca.btrbz.screen.ScreenTracker.BazaarMenuType;
import com.github.lutzluca.btrbz.screen.slot.SlotClickContext;
import com.github.lutzluca.btrbz.screen.slot.SlotClickResult;
import com.github.lutzluca.btrbz.screen.slot.SlotHook;
import com.github.lutzluca.btrbz.screen.slot.SlotHookRegistry;
import com.github.lutzluca.btrbz.screen.slot.SlotRenderContext;
import com.github.lutzluca.btrbz.screen.slot.SlotView;
import com.github.lutzluca.btrbz.utils.GameUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** Bookmark storage and semantic operations without presentation ownership. */
public final class BookmarkComponent {
    private static final int PRODUCT_SLOT = 13;

    private final BazaarData bazaarData;
    private final BazaarProductContext productContext;
    private final TrackedOrderManager trackedOrders;
    private final Supplier<BookmarksWidgetConfig> config;
    private final Runnable save;

    private final Set<String> buyProducts = new HashSet<>();
    private final Set<String> sellProducts = new HashSet<>();

    private final CacheToken dataChanges = CacheToken.named("bookmarks.data");

    public BookmarkComponent(
        BazaarData bazaarData,
        BazaarProductContext productContext,
        TrackedOrderManager trackedOrders,
        Supplier<BookmarksWidgetConfig> config,
        Runnable save
    ) {
        this.bazaarData = bazaarData;
        this.productContext = productContext;
        this.trackedOrders = trackedOrders;
        this.config = config;
        this.save = save;

        if (this.items().removeIf(Objects::isNull)) {
            this.save.run();
        }

        this.rebuildOrderCache();

        trackedOrders.addOnOrderAddedListener(_ -> this.rebuildOrderCache());
        trackedOrders.addOnOrderRemovedListener(_ -> this.rebuildOrderCache());
        trackedOrders.addOnOrderUpdatedListener(_ -> this.rebuildOrderCache());
        trackedOrders.addOnOrdersResetListener(this::rebuildOrderCache);

        bazaarData.addIndexChangeListener(this::refreshProducts);
        SlotHookRegistry.register(new BookmarkHook());
    }

    public List<Snapshot> currentBookmarks() {
        return this.items().stream().map(item -> new Snapshot(
            item.product().productId(),
            item.productName(),
            item.product().formattedName(),
            item.itemStack(),
            this.buyProducts.contains(item.product().productId()),
            this.sellProducts.contains(item.product().productId()))).toList();
    }

    public boolean contains(String productId) {
        return this.items().stream().anyMatch(item -> item.product().productId().equals(productId));
    }

    public boolean open(String productId) {
        return this.items().stream()
            .filter(item -> item.product().productId().equals(productId))
            .findFirst()
            .map(item -> {
                GameUtils.runCommand("bz " + item.productName());
                return true;
            })
            .orElse(false);
    }

    public boolean remove(String productId) {
        boolean changed = this.items().removeIf(item -> item.product().productId().equals(productId));

        if (changed) {
            this.dataChanges.invalidate("bookmark removed");
            this.save.run();
        }

        return changed;
    }

    /** Uses a drop-boundary insertion index in {@code 0..size}. */
    public boolean reorder(String productId, int insertionIndex) {
        var items = this.items();

        if (insertionIndex < 0 || insertionIndex > items.size()) {
            return false;
        }

        int source = -1;

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).product().productId().equals(productId)) {
                source = i;
                break;
            }
        }

        if (source < 0) {
            return false;
        }

        var item = items.remove(source);
        int target = insertionIndex > source ? insertionIndex - 1 : insertionIndex;

        items.add(Math.min(target, items.size()), item);

        this.dataChanges.invalidate("bookmarks reordered");
        this.save.run();

        return true;
    }

    private boolean toggle(ItemStack stack) {
        var product = this.productContext.openedProduct();

        if (product == null) {
            return false;
        }

        if (this.contains(product.productId())) {
            this.remove(product.productId());
            return false;
        }

        this.items().add(new BookmarkedItem(product, stack.copy()));

        this.dataChanges.invalidate("bookmark added");
        this.save.run();

        return true;
    }

    private void refreshProducts() {
        boolean changed = false;
        var iterator = this.items().listIterator();

        while (iterator.hasNext()) {
            var item = iterator.next();
            var refreshed = this.bazaarData.refreshIndexedProduct(item.product());

            if (!refreshed.equals(item.product())) {
                iterator.set(new BookmarkedItem(refreshed, item.itemTemplate()));
                changed = true;
            }
        }

        if (changed) {
            this.dataChanges.invalidate("bookmark products refreshed");
            this.save.run();
        }
    }

    private void rebuildOrderCache() {
        this.buyProducts.clear();
        this.sellProducts.clear();

        this.trackedOrders.getTrackedOrders().forEach(order -> order.product.bazaarProductId().ifPresent(id -> {
            switch (order.type) {
                case Buy -> this.buyProducts.add(id);
                case Sell -> this.sellProducts.add(id);
            }
        }));

        this.dataChanges.invalidate("bookmark order indicators rebuilt");
    }

    private List<BookmarkedItem> items() {
        return this.config.get().items;
    }

    public CacheToken dataChanges() {
        return this.dataChanges;
    }

    public record Snapshot(
        String productId,
        String productName,
        String formattedName,
        ItemStack itemStack,
        boolean hasBuyOrder,
        boolean hasSellOffer
    ) {
        public Snapshot {
            itemStack = itemStack.copy();
        }

        @Override
        public ItemStack itemStack() {
            return this.itemStack.copy();
        }
    }

    private final class BookmarkHook implements SlotHook {
        @Override
        public boolean matches(SlotView view) {
            return BookmarkComponent.this.config.get().frame.enabled
                && view.slotIdx() == PRODUCT_SLOT
                && view.getCurrInfo().inMenu(BazaarMenuType.Item);
        }

        @Override
        public ItemStack createDisplayStack(SlotRenderContext context) {
            var raw = context.view().getRawStack();
            var product = BookmarkComponent.this.productContext.openedProduct();

            if (raw.isEmpty() || context.view().playerInventorySlot() || product == null) {
                return null;
            }

            raw.set(BtrBz.BOOKMARKED, contains(product.productId()));

            return raw;
        }

        @Override
        public SlotClickResult onClick(SlotClickContext context) {
            if (!BookmarkComponent.this.config.get().frame.enabled) {
                return SlotClickResult.Pass;
            }

            var raw = context.view().getRawStack();

            if (raw.get(BtrBz.BOOKMARKED) == null
                || BookmarkComponent.this.productContext.openedProduct() == null) {
                return SlotClickResult.Pass;
            }

            raw.set(BtrBz.BOOKMARKED, toggle(raw));

            return SlotClickResult.Consume;
        }
    }
}
