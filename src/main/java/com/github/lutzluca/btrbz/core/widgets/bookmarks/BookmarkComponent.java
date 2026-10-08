package com.github.lutzluca.btrbz.core.widgets.bookmarks;

import com.github.lutzluca.btrbz.BtrBz;
import com.github.lutzluca.btrbz.cache.CacheToken;
import com.github.lutzluca.btrbz.core.trackedorders.TrackedOrderManager;
import com.github.lutzluca.btrbz.core.widgets.bookmarks.BookmarksWidgetConfig.BookmarkedProduct;
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
import lombok.Getter;
import lombok.experimental.Accessors;
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

    @Getter
    @Accessors(fluent = true)
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

        if (this.bookmarks().removeIf(Objects::isNull)) {
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
        return this.bookmarks().stream().map(BookmarkedProduct::product).map(product -> new Snapshot(
            product.productId(),
            product.strippedName(),
            product.formattedName(),
            this.bazaarData.productStack(product).orElse(ItemStack.EMPTY),
            this.buyProducts.contains(product.productId()),
            this.sellProducts.contains(product.productId()))).toList();
    }

    public boolean contains(String productId) {
        return this.bookmarks().stream().anyMatch(bookmark -> bookmark.product().productId().equals(productId));
    }

    public boolean open(String productId) {
        return this.bookmarks().stream()
            .map(BookmarkedProduct::product)
            .filter(product -> product.productId().equals(productId))
            .findFirst()
            .map(product -> {
                GameUtils.runCommand("bz " + product.strippedName());
                return true;
            })
            .orElse(false);
    }

    public boolean remove(String productId) {
        boolean changed = this.bookmarks().removeIf(bookmark -> bookmark.product().productId().equals(productId));

        if (changed) {
            this.dataChanges.invalidate("bookmark removed");
            this.save.run();
        }

        return changed;
    }

    /** Uses a drop-boundary insertion index in {@code 0..size}. */
    public boolean reorder(String productId, int insertionIndex) {
        var bookmarks = this.bookmarks();

        if (insertionIndex < 0 || insertionIndex > bookmarks.size()) {
            return false;
        }

        int source = -1;

        for (int i = 0; i < bookmarks.size(); i++) {
            if (bookmarks.get(i).product().productId().equals(productId)) {
                source = i;
                break;
            }
        }

        if (source < 0) {
            return false;
        }

        var bookmark = bookmarks.remove(source);
        int target = insertionIndex > source ? insertionIndex - 1 : insertionIndex;

        bookmarks.add(Math.min(target, bookmarks.size()), bookmark);

        this.dataChanges.invalidate("bookmarks reordered");
        this.save.run();

        return true;
    }

    private boolean toggle() {
        var product = this.productContext.openedProduct();

        if (product == null) {
            return false;
        }

        if (this.contains(product.productId())) {
            this.remove(product.productId());
            return false;
        }

        this.bookmarks().add(new BookmarkedProduct(product));

        this.dataChanges.invalidate("bookmark added");
        this.save.run();

        return true;
    }

    private void refreshProducts() {
        boolean changed = false;
        var iterator = this.bookmarks().listIterator();

        while (iterator.hasNext()) {
            var product = iterator.next().product();
            var refreshed = this.bazaarData.refreshIndexedProduct(product);

            if (!refreshed.equals(product)) {
                iterator.set(new BookmarkedProduct(refreshed));
                changed = true;
            }
        }

        this.dataChanges.invalidate("bookmark conversion index refreshed");
        if (changed) {
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

    private List<BookmarkedProduct> bookmarks() {
        return this.config.get().products;
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

            raw.set(BtrBz.BOOKMARKED, toggle());

            return SlotClickResult.Consume;
        }
    }
}
